package com.discordcall

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object DiscordApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val BASE = "https://discord.com/api/v10"

    private fun req(token: String, path: String): Request =
        Request.Builder().url("$BASE$path").header("Authorization", token).build()

    private suspend fun get(token: String, path: String): JSONObject = withContext(Dispatchers.IO) {
        Logger.i("API", "GET $path")
        val resp = client.newCall(req(token, path)).execute()
        val body = resp.body?.string() ?: throw IOException("Empty body")
        if (!resp.isSuccessful) {
            Logger.e("API", "HTTP ${resp.code} $path")
            throw IOException("HTTP ${resp.code}: $body")
        }
        Logger.s("API", "200 $path")
        JSONObject(body)
    }

    private suspend fun getArray(token: String, path: String): JSONArray = withContext(Dispatchers.IO) {
        Logger.i("API", "GET $path")
        val resp = client.newCall(req(token, path)).execute()
        val body = resp.body?.string() ?: throw IOException("Empty body")
        if (!resp.isSuccessful) {
            Logger.e("API", "HTTP ${resp.code} $path")
            throw IOException("HTTP ${resp.code}: $body")
        }
        Logger.s("API", "200 $path")
        JSONArray(body)
    }

    private suspend fun post(token: String, path: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        Logger.i("API", "POST $path")
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val resp = client.newCall(
            Request.Builder().url("$BASE$path")
                .header("Authorization", token)
                .post(body)
                .build()
        ).execute()
        val respBody = resp.body?.string() ?: throw IOException("Empty body")
        if (!resp.isSuccessful) {
            Logger.e("API", "HTTP ${resp.code} $path")
            throw IOException("HTTP ${resp.code}: $respBody")
        }
        Logger.s("API", "200 POST $path")
        JSONObject(respBody)
    }

    suspend fun fetchMe(token: String): DiscordUser {
        val j = get(token, "/users/@me")
        fun str(k: String) = j.optString(k).takeIf { it.isNotEmpty() && it != "null" }
        return DiscordUser(
            id            = j.optString("id"),
            username      = j.optString("username"),
            discriminator = j.optString("discriminator", "0"),
            globalName    = str("global_name"),
            avatar        = str("avatar"),
            email         = str("email"),
            verified      = j.optBoolean("verified", false),
            mfaEnabled    = j.optBoolean("mfa_enabled", false),
            premiumType   = j.optInt("premium_type", 0),
            publicFlags   = j.optLong("public_flags", 0L),
            flags         = j.optLong("flags", j.optLong("public_flags", 0L))
        )
    }

    suspend fun fetchGuilds(token: String): List<DiscordGuild> {
        val arr = getArray(token, "/users/@me/guilds")
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(DiscordGuild(
                    id          = o.optString("id"),
                    name        = o.optString("name"),
                    icon        = o.optString("icon").takeIf { it.isNotEmpty() && it != "null" },
                    ownerId     = o.optString("owner_id", ""),
                    permissions = o.optLong("permissions", 0L)
                ))
            }
        }
    }

    suspend fun fetchGuildChannels(token: String, guildId: String): List<VoiceChannel> {
        val arr = getArray(token, "/guilds/$guildId/channels")
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val type = o.optInt("type", 0)
                if (type != 2 && type != 13) continue
                val overwrites = mutableListOf<PermissionOverwrite>()
                val ow = o.optJSONArray("permission_overwrites")
                if (ow != null) for (j in 0 until ow.length()) {
                    val p = ow.getJSONObject(j)
                    overwrites.add(PermissionOverwrite(
                        id    = p.optString("id"),
                        type  = p.optInt("type", 0),
                        allow = p.optLong("allow", 0L),
                        deny  = p.optLong("deny", 0L)
                    ))
                }
                add(VoiceChannel(
                    id                  = o.optString("id"),
                    guildId             = guildId,
                    name                = o.optString("name"),
                    type                = type,
                    position            = o.optInt("position", 0),
                    userLimit           = o.optInt("user_limit", 0),
                    bitrate             = o.optInt("bitrate", 64000),
                    parentId            = o.optString("parent_id").takeIf { it.isNotEmpty() && it != "null" },
                    permissionOverwrites= overwrites
                ))
            }
        }
    }

    suspend fun fetchGuildCategories(token: String, guildId: String): List<ChannelCategory> {
        val arr = getArray(token, "/guilds/$guildId/channels")
        val cats  = mutableMapOf<String, ChannelCategory>()
        val vcs   = mutableListOf<VoiceChannel>()
        for (i in 0 until arr.length()) {
            val o    = arr.getJSONObject(i)
            val type = o.optInt("type", 0)
            if (type == 4) cats[o.optString("id")] = ChannelCategory(
                id       = o.optString("id"),
                name     = o.optString("name"),
                position = o.optInt("position", 0)
            )
        }
        for (i in 0 until arr.length()) {
            val o    = arr.getJSONObject(i)
            val type = o.optInt("type", 0)
            if (type != 2 && type != 13) continue
            val overwrites = mutableListOf<PermissionOverwrite>()
            val ow = o.optJSONArray("permission_overwrites")
            if (ow != null) for (j in 0 until ow.length()) {
                val p = ow.getJSONObject(j)
                overwrites.add(PermissionOverwrite(p.optString("id"), p.optInt("type", 0), p.optLong("allow", 0L), p.optLong("deny", 0L)))
            }
            vcs.add(VoiceChannel(
                id = o.optString("id"), guildId = guildId, name = o.optString("name"),
                type = type, position = o.optInt("position", 0), userLimit = o.optInt("user_limit", 0),
                bitrate = o.optInt("bitrate", 64000),
                parentId = o.optString("parent_id").takeIf { it.isNotEmpty() && it != "null" },
                permissionOverwrites = overwrites
            ))
        }
        vcs.forEach { vc ->
            val cat = if (vc.parentId != null) cats[vc.parentId] else null
            if (cat != null) cat.channels.add(vc)
            else {
                val uncategorized = cats.getOrPut("__none__") {
                    ChannelCategory("__none__", "Voice Channels", -1)
                }
                uncategorized.channels.add(vc)
            }
        }
        return cats.values.sortedBy { it.position }.filter { it.channels.isNotEmpty() }.also { list ->
            list.forEach { cat -> cat.channels.sortBy { it.position } }
        }
    }

    suspend fun fetchGuildVoiceStates(token: String, guildId: String): Map<String, List<VoiceState>> {
        return try {
            val j = get(token, "/guilds/$guildId/voice-states")
            val map = mutableMapOf<String, MutableList<VoiceState>>()
            val arr = j.optJSONArray("voice_states") ?: return emptyMap()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val member = o.optJSONObject("member")
                val user   = member?.optJSONObject("user") ?: continue
                val channelId = o.optString("channel_id").takeIf { it.isNotEmpty() && it != "null" } ?: continue
                val vs = VoiceState(
                    userId     = user.optString("id"),
                    username   = member.optString("nick").takeIf { it.isNotEmpty() && it != "null" } ?: user.optString("username"),
                    avatar     = user.optString("avatar").takeIf { it.isNotEmpty() && it != "null" },
                    channelId  = channelId,
                    selfMute   = o.optBoolean("self_mute", false),
                    selfDeaf   = o.optBoolean("self_deaf", false),
                    selfVideo  = o.optBoolean("self_video", false),
                    selfStream = o.optBoolean("self_stream", false),
                    serverMute = o.optBoolean("mute", false),
                    serverDeaf = o.optBoolean("deaf", false),
                    suppress   = o.optBoolean("suppress", false)
                )
                map.getOrPut(channelId) { mutableListOf() }.add(vs)
            }
            map
        } catch (e: Exception) {
            Logger.w("API", "fetchGuildVoiceStates failed: ${e.message}")
            emptyMap()
        }
    }

    suspend fun fetchMessages(token: String, channelId: String, limit: Int = 50): List<Message> {
        return try {
            val arr = getArray(token, "/channels/$channelId/messages?limit=$limit")
            buildList {
                for (i in 0 until arr.length()) {
                    val o      = arr.getJSONObject(i)
                    val author = o.optJSONObject("author") ?: continue
                    val attArr = o.optJSONArray("attachments")
                    val attachments = if (attArr != null) buildList {
                        for (j in 0 until attArr.length()) {
                            val a = attArr.getJSONObject(j)
                            add(MessageAttachment(
                                id          = a.optString("id"),
                                filename    = a.optString("filename"),
                                url         = a.optString("url"),
                                proxyUrl    = a.optString("proxy_url"),
                                size        = a.optInt("size", 0),
                                width       = a.optInt("width", 0).takeIf { it > 0 },
                                height      = a.optInt("height", 0).takeIf { it > 0 },
                                contentType = a.optString("content_type").takeIf { it.isNotEmpty() }
                            ))
                        }
                    } else emptyList()
                    add(Message(
                        id           = o.optString("id"),
                        authorId     = author.optString("id"),
                        authorName   = author.optString("global_name").takeIf { it.isNotEmpty() && it != "null" } ?: author.optString("username"),
                        authorAvatar = author.optString("avatar").takeIf { it.isNotEmpty() && it != "null" },
                        content      = o.optString("content"),
                        timestamp    = parseTimestamp(o.optString("timestamp")),
                        attachments  = attachments
                    ))
                }
            }.reversed()
        } catch (e: Exception) {
            Logger.e("API", "fetchMessages: ${e.message}")
            emptyList()
        }
    }

    suspend fun sendMessage(token: String, channelId: String, content: String): Boolean {
        return try {
            post(token, "/channels/$channelId/messages", JSONObject().put("content", content))
            true
        } catch (e: Exception) {
            Logger.e("API", "sendMessage: ${e.message}")
            false
        }
    }

    suspend fun fetchGuildMember(token: String, guildId: String, userId: String): GuildMember? {
        return try {
            val j = get(token, "/guilds/$guildId/members/$userId")
            GuildMember(
                userId      = j.optJSONObject("user")?.optString("id") ?: userId,
                nick        = j.optString("nick").takeIf { it.isNotEmpty() && it != "null" },
                roles       = buildList {
                    val r = j.optJSONArray("roles") ?: return@buildList
                    for (i in 0 until r.length()) add(r.getString(i))
                },
                permissions = j.optLong("permissions", 0L)
            )
        } catch (e: Exception) {
            Logger.w("API", "fetchGuildMember: ${e.message}")
            null
        }
    }

    suspend fun fetchGuildRoles(token: String, guildId: String): Map<String, Long> {
        return try {
            val arr = getArray(token, "/guilds/$guildId/roles")
            buildMap {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    put(o.optString("id"), o.optLong("permissions", 0L))
                }
            }
        } catch (e: Exception) {
            Logger.w("API", "fetchGuildRoles: ${e.message}")
            emptyMap()
        }
    }


    suspend fun fetchDmChannels(token: String): List<DmChannel> {
        return try {
            val arr = getArray(token, "/users/@me/channels")
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val type = o.optInt("type", 0)
                    if (type != 1 && type != 3) continue
                    add(parseDmChannel(o))
                }
            }.sortedByDescending { it.lastMessageId }
        } catch (e: Exception) {
            Logger.e("API", "fetchDmChannels: ${e.message}")
            emptyList()
        }
    }

    suspend fun openDmChannel(token: String, userId: String): DmChannel? {
        return try {
            val j = post(token, "/users/@me/channels", JSONObject().put("recipient_id", userId))
            parseDmChannel(j)
        } catch (e: Exception) {
            Logger.e("API", "openDmChannel: ${e.message}")
            null
        }
    }

    suspend fun startDmCall(token: String, channelId: String): Boolean {
        return try {
            post(token, "/channels/$channelId/call/ring", JSONObject().put("recipients", JSONArray()))
            Logger.s("API", "DM call ring sent $channelId")
            true
        } catch (e: Exception) {
            Logger.e("API", "startDmCall: ${e.message}")
            false
        }
    }

    suspend fun declineCall(token: String, channelId: String): Boolean {
        return try {
            val resp = client.newCall(
                Request.Builder()
                    .url("$BASE/channels/$channelId/call/stop-ringing")
                    .header("Authorization", token)
                    .post(JSONObject().toString().toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
            Logger.s("API", "Call declined $channelId code=${resp.code}")
            resp.isSuccessful
        } catch (e: Exception) {
            Logger.e("API", "declineCall: ${e.message}")
            false
        }
    }

    suspend fun fetchFriends(token: String): List<DmRecipient> {
        return try {
            val arr = getArray(token, "/users/@me/relationships")
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optInt("type", 0) != 1) continue
                    val u = o.optJSONObject("user") ?: continue
                    add(DmRecipient(
                        id            = u.optString("id"),
                        username      = u.optString("username"),
                        globalName    = u.optString("global_name").takeIf { it.isNotEmpty() && it != "null" },
                        avatar        = u.optString("avatar").takeIf       { it.isNotEmpty() && it != "null" },
                        discriminator = u.optString("discriminator", "0")
                    ))
                }
            }.sortedBy { it.displayName }
        } catch (e: Exception) {
            Logger.e("API", "fetchFriends: ${e.message}")
            emptyList()
        }
    }

    private fun parseDmChannel(o: JSONObject): DmChannel {
        val recipientsArr = o.optJSONArray("recipients") ?: JSONArray()
        val recipients = buildList {
            for (j in 0 until recipientsArr.length()) {
                val u = recipientsArr.getJSONObject(j)
                add(DmRecipient(
                    id            = u.optString("id"),
                    username      = u.optString("username"),
                    globalName    = u.optString("global_name").takeIf { it.isNotEmpty() && it != "null" },
                    avatar        = u.optString("avatar").takeIf       { it.isNotEmpty() && it != "null" },
                    discriminator = u.optString("discriminator", "0")
                ))
            }
        }
        val callObj = o.optJSONObject("call")
        val callParticipants = buildList {
            val arr = callObj?.optJSONArray("participants") ?: JSONArray()
            for (j in 0 until arr.length()) add(arr.getString(j))
        }
        return DmChannel(
            id               = o.optString("id"),
            type             = o.optInt("type", 1),
            recipients       = recipients,
            name             = o.optString("name").takeIf { it.isNotEmpty() && it != "null" },
            icon             = o.optString("icon").takeIf  { it.isNotEmpty() && it != "null" },
            lastMessageId    = o.optString("last_message_id").takeIf { it.isNotEmpty() && it != "null" },
            callActive       = callObj != null,
            callParticipants = callParticipants
        )
    }

    private fun parseTimestamp(ts: String): Long {
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US).parse(ts)?.time
                ?: java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).parse(ts)?.time
                ?: System.currentTimeMillis()
        } catch (_: Exception) { System.currentTimeMillis() }
    }
}
