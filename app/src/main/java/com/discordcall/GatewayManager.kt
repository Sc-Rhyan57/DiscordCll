package com.discordcall

import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class GatewayManager {

    // ─── Callbacks ────────────────────────────────────────────────────────────
    var onReady:               ((userId: String) -> Unit)?                           = null
    var onVoiceStateUpdate:    ((VoiceState) -> Unit)?                               = null
    var onVoiceServerUpdate:   ((token: String, guildId: String, endpoint: String) -> Unit)? = null
    var onDmVoiceStateUpdate:  ((channelId: String, VoiceState) -> Unit)?            = null
    var onDmVoiceServerUpdate: ((token: String, channelId: String, endpoint: String) -> Unit)? = null
    var onGuildVoiceStates:    ((guildId: String, List<VoiceState>) -> Unit)?        = null
    var onMessageCreate:       ((channelId: String, Message) -> Unit)?               = null
    var onSpeakingUpdate:      ((userId: String, speaking: Boolean) -> Unit)?        = null
    var onCallCreate:          ((IncomingCall) -> Unit)?                             = null
    var onCallDelete:          ((channelId: String) -> Unit)?                        = null

    // ─── State ────────────────────────────────────────────────────────────────
    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var ws:          WebSocket? = null
    private var token:       String     = ""
    private var sessionId:   String     = ""
    private var resumeUrl:   String     = ""
    private var sequence:    Int        = 0
    private var heartbeatJob: Job?      = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var myUserId = ""

    // ─── Public API ───────────────────────────────────────────────────────────

    fun connect(token: String) {
        this.token = token
        openWs("wss://gateway.discord.gg/?v=10&encoding=json")
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        ws?.close(1000, "logout")
        ws = null
        sequence = 0
    }

    fun subscribeToGuild(guildId: String) {
        // op 14 — lazy load guild (user client)
        send(JSONObject().apply {
            put("op", 14)
            put("d", JSONObject().apply {
                put("guild_id", guildId)
                put("typing",    true)
                put("activities",true)
                put("threads",   false)
                put("channels",  JSONObject())
            })
        }.toString())
    }

    fun sendVoiceStateUpdate(guildId: String, channelId: String?, selfMute: Boolean, selfDeaf: Boolean) {
        send(JSONObject().apply {
            put("op", 4)
            put("d", JSONObject().apply {
                put("guild_id",   guildId)
                put("channel_id", channelId ?: JSONObject.NULL)
                put("self_mute",  selfMute)
                put("self_deaf",  selfDeaf)
            })
        }.toString())
        Logger.i("Gateway", "VoiceStateUpdate guild=$guildId channel=$channelId")
    }

    fun sendDmVoiceStateUpdate(channelId: String?, selfMute: Boolean, selfDeaf: Boolean) {
        // For DM calls, guild_id is null
        send(JSONObject().apply {
            put("op", 4)
            put("d", JSONObject().apply {
                put("guild_id",   JSONObject.NULL)
                put("channel_id", channelId ?: JSONObject.NULL)
                put("self_mute",  selfMute)
                put("self_deaf",  selfDeaf)
            })
        }.toString())
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private fun openWs(url: String) {
        ws?.close(1000, "reconnect")
        ws = http.newWebSocket(
            Request.Builder().url(url).build(),
            GatewayListener()
        )
        Logger.i("Gateway", "Connecting → $url")
    }

    private fun send(payload: String) {
        ws?.send(payload)
    }

    private fun sendIdentify() {
        val payload = JSONObject().apply {
            put("op", 2)
            put("d", JSONObject().apply {
                put("token", token)
                put("capabilities", 16381)
                put("properties", JSONObject().apply {
                    put("\$os",      "android")
                    put("\$browser", "Discord Android")
                    put("\$device",  "Discord Android")
                })
                put("presence", JSONObject().apply {
                    put("status",     "online")
                    put("since",       0)
                    put("activities", JSONArray())
                    put("afk",         false)
                })
                put("compress", false)
                put("intents",  0)  // user client — no intents needed
            })
        }
        send(payload.toString())
        Logger.i("Gateway", "Sent IDENTIFY")
    }

    private fun resume() {
        send(JSONObject().apply {
            put("op", 6)
            put("d", JSONObject().apply {
                put("token",      token)
                put("session_id", sessionId)
                put("seq",        sequence)
            })
        }.toString())
        Logger.i("Gateway", "Sent RESUME seq=$sequence")
    }

    private fun startHeartbeat(intervalMs: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            delay((intervalMs * (0.5 + Random.nextDouble() * 0.5)).toLong())
            while (isActive) {
                val hb = JSONObject().apply {
                    put("op", 1)
                    put("d",  if (sequence > 0) sequence else JSONObject.NULL)
                }
                send(hb.toString())
                delay(intervalMs)
            }
        }
    }

    // ─── Event dispatch ───────────────────────────────────────────────────────

    private fun handleDispatch(event: String, d: JSONObject) {
        when (event) {
            "READY" -> {
                myUserId  = d.optJSONObject("user")?.optString("id", "") ?: ""
                sessionId = d.optString("session_id", "")
                resumeUrl = d.optString("resume_gateway_url", "wss://gateway.discord.gg/?v=10&encoding=json")
                Logger.s("Gateway", "READY userId=$myUserId")
                onReady?.invoke(myUserId)
            }

            "VOICE_STATE_UPDATE" -> {
                val vs = parseVoiceState(d)
                // Capture our own session_id for voice connection
                if (vs.userId == myUserId) {
                    Logger.s("Gateway", "Own VOICE_STATE_UPDATE session=${vs.sessionId} channel=${vs.channelId}")
                }
                val guildId = d.optString("guild_id", "")
                if (guildId.isNotEmpty()) {
                    onVoiceStateUpdate?.invoke(vs)
                } else {
                    onDmVoiceStateUpdate?.invoke(vs.channelId, vs)
                }
            }

            "VOICE_SERVER_UPDATE" -> {
                val vToken   = d.optString("token", "")
                val guildId  = d.optString("guild_id", "")
                val endpoint = d.optString("endpoint", "")
                Logger.s("Gateway", "VOICE_SERVER_UPDATE guild=$guildId ep=$endpoint")
                if (guildId.isNotEmpty()) {
                    onVoiceServerUpdate?.invoke(vToken, guildId, endpoint)
                } else {
                    onDmVoiceServerUpdate?.invoke(vToken, "", endpoint)
                }
            }

            "MESSAGE_CREATE" -> {
                val msg = parseMessage(d)
                val channelId = d.optString("channel_id", "")
                if (channelId.isNotEmpty()) onMessageCreate?.invoke(channelId, msg)
            }

            "CALL_CREATE" -> {
                val channelId = d.optString("channel_id", "")
                val callerId  = d.optJSONArray("ringing")?.optString(0) ?: ""
                if (callerId.isNotEmpty() && channelId.isNotEmpty()) {
                    onCallCreate?.invoke(IncomingCall(
                        channelId    = channelId,
                        callerId     = callerId,
                        callerName   = "Desconhecido",
                        callerAvatar = null,
                        isGroup      = false,
                        groupName    = null
                    ))
                }
            }

            "CALL_DELETE" -> {
                val channelId = d.optString("channel_id", "")
                if (channelId.isNotEmpty()) onCallDelete?.invoke(channelId)
            }
        }
    }

    private fun parseVoiceState(d: JSONObject): VoiceState {
        val member = d.optJSONObject("member")
        val user   = d.optJSONObject("user") ?: member?.optJSONObject("user") ?: JSONObject()
        val uid    = user.optString("id",       d.optString("user_id", ""))
        val uname  = user.optString("username", "Unknown")
        val avatar = user.optString("avatar",   null)

        return VoiceState(
            userId     = uid,
            username   = member?.optJSONObject("nick")?.optString("nick") ?: uname,
            avatar     = avatar,
            channelId  = d.optString("channel_id", ""),
            sessionId  = d.optString("session_id", ""),
            selfMute   = d.optBoolean("self_mute",   false),
            selfDeaf   = d.optBoolean("self_deaf",   false),
            selfVideo  = d.optBoolean("self_video",  false),
            selfStream = d.optBoolean("self_stream", false),
            serverMute = d.optBoolean("mute",        false),
            serverDeaf = d.optBoolean("deaf",        false),
            suppress   = d.optBoolean("suppress",    false)
        )
    }

    private fun parseMessage(d: JSONObject): Message {
        val author = d.optJSONObject("author") ?: JSONObject()
        return Message(
            id          = d.optString("id", ""),
            channelId   = d.optString("channel_id", ""),
            content     = d.optString("content", ""),
            authorId    = author.optString("id", ""),
            authorName  = author.optString("username", "Unknown"),
            avatar      = author.optString("avatar", null),
            timestamp   = System.currentTimeMillis(),
            embeds      = parseEmbeds(d.optJSONArray("embeds")),
            attachments = parseAttachments(d.optJSONArray("attachments")),
            reactions   = parseReactions(d.optJSONArray("reactions")),
            type        = d.optInt("type", 0),
            stickers    = parseStickerItems(d.optJSONArray("sticker_items"))
        )
    }

    private fun parseEmbeds(arr: JSONArray?): List<MessageEmbed> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { e ->
            MessageEmbed(
                title       = e.optString("title",       null),
                description = e.optString("description", null),
                url         = e.optString("url",         null),
                color       = e.optInt("color",          -1).takeIf { it >= 0 },
                authorName  = e.optJSONObject("author")?.optString("name"),
                authorIcon  = e.optJSONObject("author")?.optString("icon_url"),
                footerText  = e.optJSONObject("footer")?.optString("text"),
                imageUrl    = e.optJSONObject("image")?.optString("url"),
                thumbnailUrl = e.optJSONObject("thumbnail")?.optString("url"),
                fields      = run {
                    val fa = e.optJSONArray("fields") ?: return@run emptyList()
                    (0 until fa.length()).map { fa.getJSONObject(it) }.map { f ->
                        EmbedField(
                            name   = f.optString("name",   ""),
                            value  = f.optString("value",  ""),
                            inline = f.optBoolean("inline", false)
                        )
                    }
                }
            )
        }
    }

    private fun parseAttachments(arr: JSONArray?): List<MessageAttachment> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { a ->
            MessageAttachment(
                id          = a.optString("id",        ""),
                filename    = a.optString("filename",  ""),
                url         = a.optString("url",       ""),
                proxyUrl    = a.optString("proxy_url", ""),
                size        = a.optLong("size",         0L),
                width       = a.optInt("width",        0).takeIf { it > 0 },
                height      = a.optInt("height",       0).takeIf { it > 0 },
                contentType = a.optString("content_type", null)
            )
        }
    }

    private fun parseReactions(arr: JSONArray?): List<MessageReaction> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { r ->
            val emoji = r.optJSONObject("emoji") ?: JSONObject()
            MessageReaction(
                emoji    = emoji.optString("name", "?"),
                emojiId  = emoji.optString("id",   null),
                count    = r.optInt("count",        0),
                me       = r.optBoolean("me",       false)
            )
        }
    }

    private fun parseStickerItems(arr: JSONArray?): List<StickerItem> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { s ->
            StickerItem(id = s.optString("id", ""), name = s.optString("name", ""))
        }
    }

    // ─── WebSocket listener ───────────────────────────────────────────────────

    inner class GatewayListener : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            Logger.i("Gateway", "WS open")
        }

        override fun onMessage(ws: WebSocket, text: String) {
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return
            val op   = json.optInt("op", -1)
            val d    = json.optJSONObject("d")
            val t    = json.optString("t",  null)
            val s    = json.optInt("s",    -1)
            if (s > 0) sequence = s

            when (op) {
                0  -> if (t != null && d != null) handleDispatch(t, d)
                1  -> send(JSONObject().put("op", 1).put("d", if (sequence > 0) sequence else JSONObject.NULL).toString())
                7  -> { Logger.w("Gateway", "Reconnect requested"); openWs(resumeUrl); resume() }
                9  -> { Logger.w("Gateway", "Invalid session, re-identifying"); scope.launch { delay(5000); sendIdentify() } }
                10 -> {
                    val intervalMs = d?.getLong("heartbeat_interval") ?: 41250L
                    startHeartbeat(intervalMs)
                    if (sessionId.isNotEmpty()) resume() else sendIdentify()
                }
                11 -> Logger.d("Gateway", "Heartbeat ACK")
                else -> Logger.d("Gateway", "Unknown op $op")
            }
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            Logger.e("Gateway", "WS failure: ${t.message}")
            scope.launch { delay(5000); openWs(resumeUrl.ifEmpty { "wss://gateway.discord.gg/?v=10&encoding=json" }) }
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            Logger.w("Gateway", "WS closed $code $reason")
        }
    }
}
