package com.discordcall

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class VoiceEngine(private val context: Context) {

    private val http = OkHttpClient.Builder().build()

    private var voiceWs: WebSocket?    = null
    private var heartbeatJob: Job?     = null
    private var peerConnection: PeerConnection? = null
    private var peerFactory: PeerConnectionFactory? = null
    private var audioSource: AudioSource?           = null
    private var audioTrack: AudioTrack?             = null
    private var videoSource: VideoSource?           = null
    private var videoTrack: VideoTrack?             = null

    private var ssrc      = 0
    private var myUserId  = ""
    private var guildId   = ""
    private var channelId = ""
    private var sessionId = ""
    private var voiceToken = ""
    private var endpoint   = ""

    var onConnected:      (() -> Unit)?                            = null
    var onDisconnected:   (() -> Unit)?                            = null
    var onSpeakingChange: ((userId: String, speaking: Boolean) -> Unit)? = null
    var onError:          ((msg: String) -> Unit)?                 = null

    private var isMuted   = false
    private var isDeafened = false

    fun initialize(userId: String) {
        myUserId = userId
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val options = PeerConnectionFactory.Options()
        peerFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(createAudioDeviceModule())
            .createPeerConnectionFactory()
        Logger.s("VoiceEngine", "PeerConnectionFactory initialized")
    }

    private fun createAudioDeviceModule(): org.webrtc.audio.JavaAudioDeviceModule {
        return org.webrtc.audio.JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
    }

    fun connect(
        vToken: String,
        guild: String,
        channel: String,
        session: String,
        endpointHost: String,
        userId: String
    ) {
        voiceToken = vToken
        guildId    = guild
        channelId  = channel
        sessionId  = session
        endpoint   = endpointHost
        myUserId   = userId

        val wsUrl = "wss://${endpointHost.substringBefore(":")}/?v=8"
        Logger.i("VoiceEngine", "Connecting to voice WS: $wsUrl")

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Logger.i("VoiceEngine", "Voice WS opened")
                sendIdentify(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleVoiceMessage(webSocket, text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Logger.e("VoiceEngine", "Voice WS failure: ${t.message}")
                onError?.invoke("Voice connection failed: ${t.message}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Logger.w("VoiceEngine", "Voice WS closed: $code $reason")
                if (code != 1000) onDisconnected?.invoke()
            }
        }

        voiceWs = http.newWebSocket(
            Request.Builder().url(wsUrl).build(),
            listener
        )
    }

    private fun sendIdentify(ws: WebSocket) {
        val payload = JSONObject().apply {
            put("op", 0)
            put("d", JSONObject().apply {
                put("server_id",  guildId)
                put("user_id",    myUserId)
                put("session_id", sessionId)
                put("token",      voiceToken)
            })
        }
        ws.send(payload.toString())
        Logger.i("VoiceEngine", "Sent voice IDENTIFY")
    }

    private fun handleVoiceMessage(ws: WebSocket, text: String) {
        try {
            val json = JSONObject(text)
            val op   = json.optInt("op", -1)
            val data = json.optJSONObject("d")

            when (op) {
                2 -> {
                    ssrc = data?.optInt("ssrc", 0) ?: 0
                    val ip   = data?.optString("ip") ?: ""
                    val port = data?.optInt("port", 0) ?: 0
                    Logger.s("VoiceEngine", "Ready ssrc=$ssrc ip=$ip port=$port")
                    sendSelectProtocol(ws, ip, port)
                }

                4 -> {
                    Logger.s("VoiceEngine", "Session description received — voice ready")
                    onConnected?.invoke()
                    sendSpeaking(ws, false)
                }

                6 -> {
                    Logger.d("VoiceEngine", "Heartbeat ACK")
                }

                8 -> {
                    val interval = data?.getLong("heartbeat_interval") ?: 30000L
                    startHeartbeat(ws, interval)
                }

                5 -> {
                    val userId   = data?.optString("user_id") ?: return
                    val speaking = (data.optInt("speaking", 0) and 1) != 0
                    onSpeakingChange?.invoke(userId, speaking)
                }

                13 -> {
                    val userId = data?.optJSONObject("member")?.optJSONObject("user")?.optString("id") ?: return
                    Logger.i("VoiceEngine", "Client disconnect: $userId")
                }
            }
        } catch (e: Exception) {
            Logger.e("VoiceEngine", "handleVoiceMessage: ${e.message}")
        }
    }

    private fun sendSelectProtocol(ws: WebSocket, udpIp: String, udpPort: Int) {
        val payload = JSONObject().apply {
            put("op", 1)
            put("d", JSONObject().apply {
                put("protocol", "webrtc")
                put("data", JSONObject().apply {
                    put("address",  udpIp)
                    put("port",     udpPort)
                    put("mode",     "aead_aes256_gcm_rtpsize")
                    put("codecs",   JSONArray().apply {
                        put(JSONObject().apply {
                            put("name",      "opus")
                            put("type",      "audio")
                            put("priority",  1000)
                            put("payload_type", 120)
                        })
                    })
                })
            })
        }
        ws.send(payload.toString())
        Logger.i("VoiceEngine", "Sent SelectProtocol (webrtc)")
    }

    private fun sendSpeaking(ws: WebSocket, speaking: Boolean) {
        val payload = JSONObject().apply {
            put("op", 5)
            put("d", JSONObject().apply {
                put("speaking", if (speaking) 1 else 0)
                put("delay",    0)
                put("ssrc",     ssrc)
            })
        }
        ws.send(payload.toString())
    }

    private fun startHeartbeat(ws: WebSocket, interval: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            delay((interval * 0.5f).toLong())
            while (true) {
                val nonce = Random.nextLong()
                ws.send(JSONObject().put("op", 3).put("d", nonce).toString())
                delay(interval)
            }
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        audioTrack?.setEnabled(!muted)
        Logger.i("VoiceEngine", "Muted=$muted")
    }

    fun setDeafened(deafened: Boolean) {
        isDeafened = deafened
        Logger.i("VoiceEngine", "Deafened=$deafened")
    }

    fun startAudio() {
        val factory = peerFactory ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl",  "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter",   "true"))
        }
        audioSource = factory.createAudioSource(constraints)
        audioTrack  = factory.createAudioTrack("audio0", audioSource)
        audioTrack?.setEnabled(!isMuted)
        Logger.s("VoiceEngine", "Audio track started")
    }

    fun stopAudio() {
        audioTrack?.setEnabled(false)
        audioTrack?.dispose()
        audioSource?.dispose()
        audioTrack  = null
        audioSource = null
        Logger.i("VoiceEngine", "Audio track stopped")
    }

    fun startVideo(quality: VideoQuality, capturer: VideoCapturer) {
        val factory = peerFactory ?: return
        val surfaceHelper = SurfaceTextureHelper.create("VideoThread", EglBase.create().eglBaseContext)
        videoSource = factory.createVideoSource(capturer.isScreencast)
        capturer.initialize(surfaceHelper, context, videoSource!!.capturerObserver)
        capturer.startCapture(quality.width, quality.height, quality.fps)
        videoTrack = factory.createVideoTrack("video0", videoSource)
        videoTrack?.setEnabled(true)
        Logger.s("VoiceEngine", "Video started ${quality.width}x${quality.height}@${quality.fps}")
    }

    fun stopVideo() {
        videoTrack?.setEnabled(false)
        videoTrack?.dispose()
        videoSource?.dispose()
        videoTrack  = null
        videoSource = null
        Logger.i("VoiceEngine", "Video stopped")
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        voiceWs?.close(1000, "user disconnect")
        voiceWs = null
        stopAudio()
        stopVideo()
        peerConnection?.close()
        peerConnection = null
        Logger.i("VoiceEngine", "Disconnected")
        onDisconnected?.invoke()
    }

    fun release() {
        disconnect()
        peerFactory?.dispose()
        peerFactory = null
    }
}
