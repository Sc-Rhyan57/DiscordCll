package com.discordcall

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
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class GatewayManager {
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var sequence     = -1
    private var sessionId    = ""
    private var reconnectUrl = "wss://gateway.discord.gg"
    private var reconnectAttempts = 0
    private val maxReconnects     = 5
    private var token        = ""

    var onReady:            ((userId: String) -> Unit)?                          = null
    var onVoiceStateUpdate: ((vs: VoiceState) -> Unit)?                          = null
    var onVoiceServerUpdate:((token: String, guildId: String, endpoint: String) -> Unit)? = null
    var onPresenceUpdate:   ((userId: String, status: String) -> Unit)?          = null
    var onMessageCreate:    ((channelId: String, msg: Message) -> Unit)?         = null
    var onGuildVoiceStates: ((guildId: String, states: List<VoiceState>) -> Unit)? = null
    var onSpeakingUpdate:   ((userId: String, speaking: Boolean) -> Unit)?       = null
    var onChannelUpdate:    ((channelId: String) -> Unit)?                       = null
    var onDmVoiceServerUpdate: ((token: String, channelId: String, endpoint: String) -> Unit)? = null
    var onCallCreate:       ((call: IncomingCall) -> Unit)?                      = null
    var onCallDelete:       ((channelId: String) -> Unit)?                       = null
    var onDmVoiceStateUpdate: ((channelId: String, vs: VoiceState) -> Unit)?    = null

    fun connect(authToken: String) {
        token = authToken
        reconnectAttempts = 0
        connectInternal(false)
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        ws?.close(1000, "disconnect")
        ws = null
    }

    fun sendVoiceStateUpdate(guildId: String, channelId: String?, selfMute: Boolean, selfDeaf: Boolean) {
        val payload = JSONObject().apply {
            put("op", 4)
            put("d", JSONObject().apply {
                put("guild_id",   guildId)
                put("channel_id", channelId ?: JSONObject.NULL)
                put("self_mute",  selfMute)
                put("self_deaf",  selfDeaf)
                put("self_video", false)
            })
        }
        ws?.send(payload.toString())
        Logger.i("Gateway", "OP4 VoiceStateUpdate guild=$guildId channel=$channelId")
    }

    fun sendDmVoiceStateUpdate(channelId: String?, selfMute: Boolean, selfDeaf: Boolean) {
        val payload = JSONObject().apply {
            put("op", 4)
            put("d", JSONObject().apply {
                put("guild_id",   JSONObject.NULL)
                put("channel_id", channelId ?: JSONObject.NULL)
                put("self_mute",  selfMute)
                put("self_deaf",  selfDeaf)
                put("self_video", false)
            })
        }
        ws?.send(payload.toString())
        Logger.i("Gateway", "OP4 DmVoiceStateUpdate channel=$channelId")
    }

    fun sendSpeaking(ssrc: Int, speaking: Boolean) {
        val payload = JSONObject().apply {
            put("op", 5)
            put("d", JSONObject().apply {
                put("speaking", if (speaking) 1 else 0)
                put("delay",    0)
                put("ssrc",     ssrc)
            })
        }
        ws?.send(payload.toString())
    }

    fun subscribeToGuild(guildId: String) {
        val payload = JSONObject().apply {
            put("op", 14)
            put("d", JSONObject().apply {
                put("guild_id", guildId)
                put("typing", true)
                put("threads", false)
                put("activities", true)
            })
        }
        ws?.send(payload.toString())
        Logger.i("Gateway", "OP14 LazyGuildRequest guild=$guildId")
    }

    private fun connectInternal(resume: Boolean) {
        heartbeatJob?.cancel()
        ws?.close(4000, "reconnect")

        val url = "$reconnectUrl/?v=10&encoding=json"
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Logger.i("Gateway", "WebSocket connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(webSocket, text, resume)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Logger.e("Gateway", "Failure: ${t.message}")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Logger.w("Gateway", "Closed: $code $reason")
                if (code != 1000 && code != 4004 && code != 4011 && code != 4014) {
                    scheduleReconnect()
                }
            }
        }

        ws = client.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    private fun handleMessage(webSocket: WebSocket, text: String, resumeAttempt: Boolean) {
        try {
            val json = JSONObject(text)
            val op   = json.optInt("op", -1)
            val seq  = json.optInt("s", -1)
            if (seq > 0) sequence = seq

            when (op) {
                10 -> {
                    val interval = json.getJSONObject("d").getLong("heartbeat_interval")
                    startHeartbeat(webSocket, interval)
                    if (resumeAttempt && sessionId.isNotEmpty() && sequence > 0) {
                        webSocket.send(buildResume())
                        Logger.i("Gateway", "Sent RESUME")
                    } else {
                        webSocket.send(buildIdentify())
                        Logger.i("Gateway", "Sent IDENTIFY")
                    }
                }
                11 -> {}
                7  -> {
                    webSocket.close(4000, "server reconnect")
                    scheduleReconnect()
                }
                9  -> {
                    val resumable = json.optBoolean("d", false)
                    if (!resumable) { sessionId = ""; sequence = -1 }
                    webSocket.close(4000, "invalid session")
                    scheduleReconnect()
                }
                0  -> handleDispatch(json)
            }
        } catch (e: Exception) {
            Logger.e("Gateway", "Parse error: ${e.message}")
        }
    }

    private fun handleDispatch(json: JSONObject) {
        val t    = json.optString("t")
        val data = json.optJSONObject("d") ?: return

        when (t) {
            "READY" -> {
                reconnectAttempts = 0
                sessionId    = data.optString("session_id", "")
                reconnectUrl = data.optString("resume_gateway_url", reconnectUrl).trimEnd('/')
                val userId   = data.optJSONObject("user")?.optString("id") ?: ""
                Logger.s("Gateway", "READY userId=$userId session=$sessionId")
                onReady?.invoke(userId)

                val guilds = data.optJSONArray("guilds")
                if (guilds != null) {
                    for (i in 0 until guilds.length()) {
                        val g = guilds.getJSONObject(i)
                        val guildId = g.optString("id")
                        val voiceStates = g.optJSONArray("voice_states")
                        if (voiceStates != null && voiceStates.length() > 0) {
                            val states = parseVoiceStates(voiceStates, guildId)
                            if (states.isNotEmpty()) onGuildVoiceStates?.invoke(guildId, states)
                        }
                    }
                }
            }

            "RESUMED" -> {
                reconnectAttempts = 0
                Logger.s("Gateway", "RESUMED")
            }

            "VOICE_STATE_UPDATE" -> {
                val userId    = data.optJSONObject("member")?.optJSONObject("user")?.optString("id")
                    ?: data.optString("user_id").takeIf { it.isNotEmpty() } ?: return
                val member    = data.optJSONObject("member")
                val user      = member?.optJSONObject("user")
                val channelId = data.optString("channel_id").takeIf { it.isNotEmpty() && it != "null" }
                val username  = member?.optString("nick")?.takeIf { it.isNotEmpty() && it != "null" }
                    ?: user?.optString("global_name")?.takeIf { it.isNotEmpty() && it != "null" }
                    ?: user?.optString("username") ?: "Unknown"
                val avatar    = user?.optString("avatar")?.takeIf { it.isNotEmpty() && it != "null" }

                val vs = VoiceState(
                    userId     = userId,
                    username   = username,
                    avatar     = avatar,
                    channelId  = channelId ?: "",
                    selfMute   = data.optBoolean("self_mute", false),
                    selfDeaf   = data.optBoolean("self_deaf", false),
                    selfVideo  = data.optBoolean("self_video", false),
                    selfStream = data.optBoolean("self_stream", false),
                    serverMute = data.optBoolean("mute", false),
                    serverDeaf = data.optBoolean("deaf", false),
                    suppress   = data.optBoolean("suppress", false)
                )
                Logger.i("Gateway", "VOICE_STATE_UPDATE user=$username channel=$channelId")
                onVoiceStateUpdate?.invoke(vs)
            }

            "VOICE_SERVER_UPDATE" -> {
                val guildId  = data.optString("guild_id").takeIf { it.isNotEmpty() && it != "null" }
                val vToken   = data.optString("token")
                val endpoint = data.optString("endpoint")
                val chanId   = data.optString("channel_id").takeIf { it.isNotEmpty() && it != "null" }
                Logger.s("Gateway", "VOICE_SERVER_UPDATE guild=$guildId endpoint=$endpoint chan=$chanId")
                if (guildId != null) {
                    onVoiceServerUpdate?.invoke(vToken, guildId, endpoint)
                } else if (chanId != null) {
                    onDmVoiceServerUpdate?.invoke(vToken, chanId, endpoint)
                }
            }

            "MESSAGE_CREATE" -> {
                val channelId = data.optString("channel_id")
                val author    = data.optJSONObject("author") ?: return
                val attArr    = data.optJSONArray("attachments")
                val attachments = if (attArr != null) buildList {
                    for (j in 0 until attArr.length()) {
                        val a = attArr.getJSONObject(j)
                        add(MessageAttachment(a.optString("id"), a.optString("filename"), a.optString("url"), a.optString("proxy_url"), a.optInt("size", 0), a.optInt("width", 0).takeIf { it > 0 }, a.optInt("height", 0).takeIf { it > 0 }, a.optString("content_type").takeIf { it.isNotEmpty() }))
                    }
                } else emptyList()
                val msg = Message(
                    id           = data.optString("id"),
                    authorId     = author.optString("id"),
                    authorName   = author.optString("global_name").takeIf { it.isNotEmpty() && it != "null" } ?: author.optString("username"),
                    authorAvatar = author.optString("avatar").takeIf { it.isNotEmpty() && it != "null" },
                    content      = data.optString("content"),
                    timestamp    = System.currentTimeMillis(),
                    attachments  = attachments
                )
                onMessageCreate?.invoke(channelId, msg)
            }

            "SPEAKING" -> {
                val userId   = data.optString("user_id")
                val speaking = data.optInt("speaking", 0) != 0
                onSpeakingUpdate?.invoke(userId, speaking)
            }

            "CALL_CREATE" -> {
                val channelId = data.optString("channel_id")
                val voiceArr  = data.optJSONArray("voice_states") ?: JSONArray()
                for (i in 0 until voiceArr.length()) {
                    val vs = voiceArr.getJSONObject(i)
                    val u  = vs.optJSONObject("user") ?: continue
                    val callVs = VoiceState(
                        userId     = u.optString("id"),
                        username   = u.optString("global_name").takeIf { it.isNotEmpty() && it != "null" } ?: u.optString("username"),
                        avatar     = u.optString("avatar").takeIf { it.isNotEmpty() && it != "null" },
                        channelId  = channelId,
                        selfMute   = vs.optBoolean("self_mute", false),
                        selfDeaf   = vs.optBoolean("self_deaf", false),
                        selfVideo  = vs.optBoolean("self_video", false),
                        selfStream = false,
                        serverMute = false,
                        serverDeaf = false,
                        suppress   = false
                    )
                    onDmVoiceStateUpdate?.invoke(channelId, callVs)
                }
                val ringing = data.optJSONArray("ringing") ?: JSONArray()
                val callers = buildList { for (i in 0 until ringing.length()) add(ringing.getString(i)) }
                Logger.i("Gateway", "CALL_CREATE channelId=$channelId ringing=$callers")
                if (callers.isNotEmpty()) {
                    val inbound = IncomingCall(
                        channelId   = channelId,
                        callerId    = callers.first(),
                        callerName  = "Unknown",
                        callerAvatar= null,
                        isGroup     = false,
                        groupName   = null
                    )
                    onCallCreate?.invoke(inbound)
                }
            }

            "CALL_UPDATE" -> {
                val channelId = data.optString("channel_id")
                val ringing   = data.optJSONArray("ringing") ?: JSONArray()
                val callers   = buildList { for (i in 0 until ringing.length()) add(ringing.getString(i)) }
                Logger.i("Gateway", "CALL_UPDATE channelId=$channelId ringing=$callers")
                if (callers.isNotEmpty()) {
                    val inbound = IncomingCall(
                        channelId   = channelId,
                        callerId    = callers.first(),
                        callerName  = "Unknown",
                        callerAvatar= null,
                        isGroup     = false,
                        groupName   = null
                    )
                    onCallCreate?.invoke(inbound)
                } else {
                    onCallDelete?.invoke(channelId)
                }
            }

            "CALL_DELETE" -> {
                val channelId = data.optString("channel_id")
                Logger.i("Gateway", "CALL_DELETE channelId=$channelId")
                onCallDelete?.invoke(channelId)
            }

            "CHANNEL_RECIPIENT_ADD" -> {
                val channelId = data.optString("channel_id")
                Logger.i("Gateway", "CHANNEL_RECIPIENT_ADD $channelId")
            }

            "CHANNEL_RECIPIENT_REMOVE" -> {
                val channelId = data.optString("channel_id")
                Logger.i("Gateway", "CHANNEL_RECIPIENT_REMOVE $channelId")
            }
        }
    }

    private fun parseVoiceStates(arr: JSONArray, guildId: String): List<VoiceState> {
        val list = mutableListOf<VoiceState>()
        for (i in 0 until arr.length()) {
            val o         = arr.getJSONObject(i)
            val member    = o.optJSONObject("member")
            val user      = member?.optJSONObject("user") ?: continue
            val channelId = o.optString("channel_id").takeIf { it.isNotEmpty() && it != "null" } ?: continue
            list.add(VoiceState(
                userId     = user.optString("id"),
                username   = member.optString("nick").takeIf { it.isNotEmpty() && it != "null" } ?: user.optString("global_name").takeIf { it.isNotEmpty() && it != "null" } ?: user.optString("username"),
                avatar     = user.optString("avatar").takeIf { it.isNotEmpty() && it != "null" },
                channelId  = channelId,
                selfMute   = o.optBoolean("self_mute", false),
                selfDeaf   = o.optBoolean("self_deaf", false),
                selfVideo  = o.optBoolean("self_video", false),
                selfStream = o.optBoolean("self_stream", false),
                serverMute = o.optBoolean("mute", false),
                serverDeaf = o.optBoolean("deaf", false),
                suppress   = o.optBoolean("suppress", false)
            ))
        }
        return list
    }

    private fun startHeartbeat(webSocket: WebSocket, interval: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            delay((interval * Random.nextFloat() * 0.5f).toLong())
            while (true) {
                val s = if (sequence > 0) sequence.toString() else "null"
                webSocket.send("""{"op":1,"d":$s}""")
                delay(interval)
            }
        }
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= maxReconnects) {
            Logger.e("Gateway", "Max reconnect attempts reached")
            return
        }
        val delay = 3000L * (1L shl reconnectAttempts.coerceAtMost(4))
        reconnectAttempts++
        Logger.i("Gateway", "Reconnecting in ${delay}ms (attempt $reconnectAttempts/$maxReconnects)")
        reconnectJob?.cancel()
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            delay(delay)
            connectInternal(sessionId.isNotEmpty() && sequence > 0)
        }
    }

    private fun buildIdentify(): String = JSONObject().apply {
        put("op", 2)
        put("d", JSONObject().apply {
            put("token", token)
            put("capabilities", 16381)
            put("properties", JSONObject().apply {
                put("os", "Android")
                put("browser", "Discord Android")
                put("device", "Android")
                put("system_locale", Locale.getDefault().toLanguageTag())
                put("browser_version", "")
                put("os_version", android.os.Build.VERSION.RELEASE)
                put("referrer", "")
                put("referring_domain", "")
                put("release_channel", "stable")
                put("client_build_number", 0)
            })
            put("compress", false)
            put("presence", JSONObject().apply {
                put("status", "online")
                put("since", 0)
                put("activities", JSONArray())
                put("afk", false)
            })
        })
    }.toString()

    private fun buildResume(): String = JSONObject().apply {
        put("op", 6)
        put("d", JSONObject().apply {
            put("token", token)
            put("session_id", sessionId)
            put("seq", sequence)
        })
    }.toString()
}
