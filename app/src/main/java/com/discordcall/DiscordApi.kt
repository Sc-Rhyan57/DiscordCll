package com.discordcall

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object DiscordApi {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val BASE = "https://discord.com/api/v10"
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    // ─── helpers ──────────────────────────────────────────────────────────────

    private fun get(token: String, path: String): JSONObject? {
        return try {
            val resp = http.newCall(
                Request.Builder()
                    .url("$BASE$path")
                    .header("Authorization", token)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "DiscordAndroid/1.0 (Android)")
                    .get()
                    .build()
            ).execute()
            if (!resp.isSuccessful) {
                Logger.w("DiscordApi", "GET $path -> ${resp.code}")
                null
            } else {
                val body = resp.body?.string() ?: return null
                JSONObject(body)
            }
        } catch (e: Exception) {
            Logger.e("DiscordApi", "GET $path: ${e.message}")
            null
        }
    }

    private fun getArray(token: String, path: String): JSONArray? {
        return try {
            val resp = http.newCall(
                Request.Builder()
                    .url("$BASE$path")
                    .header("Authorization", token)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "DiscordAndroid/1.0 (Android)")
                    .get()
                    .build()
            ).execute()
            if (!resp.isSuccessful) {
                Logger.w("DiscordApi", "GET[] $path -> ${resp.code}")
                null
            } else {
                val body = resp.body?.string() ?: return null
                JSONArray(body)
            }
        } catch (e: Exception) {
            Logger.e("DiscordApi", "GET[] $path: ${e.message}")
            null
        }
    }

    private fun post(token: String, path: String, body: JSONObject): JSONObject? {
        return try {
            val resp = http.newCall(
                Request.Builder()
                    .url("$BASE$path")
                    .header("Authorization", token)
                    .header("User-Agent", "DiscordAndroid/1.0 (Android)")
                    .post(body.toString().toRequestBody(JSON_TYPE))
                    .build()
            ).execute()
            val str = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                Logger.w("DiscordApi", "POST $path -> ${resp.code}: $str")
                null
            } else {
                if (str.isBlank() || str == "{}") JSONObject() else JSONObject(str)
            }
        } catch (e: Exception) {
            Logger.e("DiscordApi", "POST $path: ${e.message}")
            null
        }
    }

    private fun delete(token: String, path: String): Boolean {
        return try {
            val resp = http.newCall(
                Request.Builder()
                    .url("$BASE$path")
                    .header("Authorization", token)
                    .header("User-Agent", "DiscordAndroid/1.0 (Android)")
                    .delete()
                    .build()
            ).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            Logger.e("DiscordApi", "DELETE $path: ${e.message}")
            false
        }
    }

    // ─── User ─────────────────────────────────────────────────────────────────

    suspend fun fetchMe(token: String): DiscordUser {
        val j = get(token, "/users/@me") ?: throw Exception("Failed to fetch user")
        return DiscordUser(
            id           = j.optString("id",          ""),
            username     = j.optString("username",    "Unknown"),
            discriminator = j.optString("discriminator", "0"),
            avatar       = j.optString("avatar",      null),
            globalName   = j.optString("global_name", null)
        )
    }

    // ─── Guilds ───────────────────────────────────────────────────────────────

    suspend fun fetchGuilds(token: String): List<DiscordGuild> {
        val arr = getArray(token, "/users/@me/guilds") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { g ->
            DiscordGuild(
                id                     = g.optString("id",   ""),
                name                   = g.optString("name", "Unknown"),
                icon                   = g.optString("icon", null),
                ownerId                = g.optString("owner_id", ""),
                approximateMemberCount = g.optInt("approximate_member_count", 0)
            )
        }
    }

    suspend fun fetchGuildCategories(token: String, guildId: String): List<ChannelCategory> {
        val arr = getArray(token, "/guilds/$guildId/channels") ?: return emptyList()

        val allChannels = (0 until arr.length()).map { arr.getJSONObject(it) }

        // Parse permission overwrites
        fun parseOverwrites(ow: JSONArray?): List<PermissionOverwrite> {
            if (ow == null) return emptyList()
            return (0 until ow.length()).map { ow.getJSONObject(it) }.map { o ->
                PermissionOverwrite(
                    id    = o.optString("id",    ""),
                    type  = o.optInt("type",     0),
                    allow = o.optString("allow", "0").toLongOrNull() ?: 0L,
                    deny  = o.optString("deny",  "0").toLongOrNull() ?: 0L
                )
            }
        }

        // Parse voice channels (type 2 = voice, type 13 = stage)
        fun toVoiceChannel(c: JSONObject): VoiceChannel = VoiceChannel(
            id                   = c.optString("id",        ""),
            name                 = c.optString("name",      "Unnamed"),
            type                 = c.optInt("type",          2),
            position             = c.optInt("position",      0),
            userLimit            = c.optInt("user_limit",    0),
            bitrate              = c.optInt("bitrate",    64000),
            permissionOverwrites = parseOverwrites(c.optJSONArray("permission_overwrites"))
        )

        // Separate categories and voice channels
        val categoryMap = mutableMapOf<String, MutableList<VoiceChannel>>()
        val categories  = mutableListOf<Pair<JSONObject, MutableList<VoiceChannel>>>()

        // First pass: build category list
        allChannels.filter { it.optInt("type") == 4 }
            .sortedBy { it.optInt("position") }
            .forEach { cat ->
                val list = mutableListOf<VoiceChannel>()
                categoryMap[cat.optString("id", "")] = list
                categories.add(Pair(cat, list))
            }

        // Second pass: assign voice channels to categories
        val uncategorized = mutableListOf<VoiceChannel>()
        allChannels.filter { it.optInt("type") == 2 || it.optInt("type") == 13 }
            .forEach { ch ->
                val parentId = ch.optString("parent_id", null)
                val vc = toVoiceChannel(ch)
                if (parentId != null && categoryMap.containsKey(parentId)) {
                    categoryMap[parentId]!!.add(vc)
                } else {
                    uncategorized.add(vc)
                }
            }

        // Sort channels within each category by position
        categoryMap.values.forEach { it.sortBy { ch -> ch.position } }
        uncategorized.sortBy { it.position }

        val result = mutableListOf<ChannelCategory>()

        // Add uncategorized channels as a virtual category if any
        if (uncategorized.isNotEmpty()) {
            result.add(ChannelCategory(
                id       = "uncategorized",
                name     = "Canais de Voz",
                position = -1,
                channels = uncategorized
            ))
        }

        // Add real categories that have voice channels
        categories.forEach { (cat, channels) ->
            if (channels.isNotEmpty()) {
                result.add(ChannelCategory(
                    id       = cat.optString("id",   ""),
                    name     = cat.optString("name", "Categoria"),
                    position = cat.optInt("position", 0),
                    channels = channels
                ))
            }
        }

        return result.sortedBy { it.position }
    }

    suspend fun fetchGuildVoiceStates(token: String, guildId: String): Map<String, List<VoiceState>> {
        val j   = get(token, "/guilds/$guildId") ?: return emptyMap()
        val arr = j.optJSONArray("voice_states") ?: return emptyMap()
        val result = mutableMapOf<String, MutableList<VoiceState>>()
        for (i in 0 until arr.length()) {
            val vs  = arr.getJSONObject(i)
            val uid = vs.optString("user_id", "")
            val cid = vs.optString("channel_id", "")
            if (uid.isEmpty() || cid.isEmpty()) continue
            val member = vs.optJSONObject("member")
            val user   = member?.optJSONObject("user") ?: JSONObject()
            val state  = VoiceState(
                userId     = uid,
                username   = user.optString("global_name", null) ?: user.optString("username", "Unknown"),
                avatar     = user.optString("avatar", null),
                channelId  = cid,
                sessionId  = vs.optString("session_id", ""),
                selfMute   = vs.optBoolean("self_mute",   false),
                selfDeaf   = vs.optBoolean("self_deaf",   false),
                selfVideo  = vs.optBoolean("self_video",  false),
                selfStream = vs.optBoolean("self_stream", false),
                serverMute = vs.optBoolean("mute",        false),
                serverDeaf = vs.optBoolean("deaf",        false),
                suppress   = vs.optBoolean("suppress",    false)
            )
            result.getOrPut(cid) { mutableListOf() }.add(state)
        }
        return result
    }

    // ─── Guild Members / Roles ────────────────────────────────────────────────

    suspend fun fetchGuildMember(token: String, guildId: String, userId: String): GuildMember? {
        val j = get(token, "/guilds/$guildId/members/$userId") ?: return null
        val rolesArr = j.optJSONArray("roles") ?: JSONArray()
        return GuildMember(
            userId = userId,
            roles  = (0 until rolesArr.length()).map { rolesArr.getString(it) },
            nick   = j.optString("nick", null)
        )
    }

    /** Returns map of roleId -> permissions (Long) + "@everyone" -> perms */
    suspend fun fetchGuildRoles(token: String, guildId: String): Map<String, Long> {
        val arr = getArray(token, "/guilds/$guildId/roles") ?: return emptyMap()
        val result = mutableMapOf<String, Long>()
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val id   = r.optString("id",          "")
            val name = r.optString("name",         "")
            val perms = r.optString("permissions", "0").toLongOrNull() ?: 0L
            if (name == "@everyone") result[guildId] = perms
            result[id] = perms
        }
        return result
    }

    // ─── Messages ─────────────────────────────────────────────────────────────

    suspend fun fetchMessages(token: String, channelId: String, limit: Int = 50): List<Message> {
        val arr = getArray(token, "/channels/$channelId/messages?limit=$limit") ?: return emptyList()
        return (0 until arr.length())
            .map { arr.getJSONObject(it) }
            .map { parseMessage(it) }
            .reversed()
    }

    private fun parseMessage(d: JSONObject): Message {
        val author = d.optJSONObject("author") ?: JSONObject()
        return Message(
            id          = d.optString("id",         ""),
            channelId   = d.optString("channel_id", ""),
            content     = d.optString("content",    ""),
            authorId    = author.optString("id",       ""),
            authorName  = author.optString("global_name", null)
                         ?: author.optString("username", "Unknown"),
            avatar      = author.optString("avatar", null),
            timestamp   = parseTimestamp(d.optString("timestamp", "")),
            embeds      = parseEmbeds(d.optJSONArray("embeds")),
            attachments = parseAttachments(d.optJSONArray("attachments")),
            reactions   = parseReactions(d.optJSONArray("reactions")),
            type        = d.optInt("type", 0),
            stickers    = parseStickerItems(d.optJSONArray("sticker_items"))
        )
    }

    private fun parseTimestamp(ts: String): Long {
        return try {
            java.time.Instant.parse(ts).toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun parseEmbeds(arr: JSONArray?): List<MessageEmbed> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { e ->
            MessageEmbed(
                title        = e.optString("title",       null),
                description  = e.optString("description", null),
                url          = e.optString("url",         null),
                color        = e.optInt("color",          -1).takeIf { it >= 0 },
                authorName   = e.optJSONObject("author")?.optString("name"),
                authorIcon   = e.optJSONObject("author")?.optString("icon_url"),
                footerText   = e.optJSONObject("footer")?.optString("text"),
                imageUrl     = e.optJSONObject("image")?.optString("url"),
                thumbnailUrl = e.optJSONObject("thumbnail")?.optString("url"),
                fields       = run {
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
                id          = a.optString("id",           ""),
                filename    = a.optString("filename",     ""),
                url         = a.optString("url",          ""),
                proxyUrl    = a.optString("proxy_url",    ""),
                size        = a.optLong("size",            0L),
                width       = a.optInt("width",   0).takeIf { it > 0 },
                height      = a.optInt("height",  0).takeIf { it > 0 },
                contentType = a.optString("content_type", null)
            )
        }
    }

    private fun parseReactions(arr: JSONArray?): List<MessageReaction> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { r ->
            val emoji = r.optJSONObject("emoji") ?: JSONObject()
            MessageReaction(
                emoji   = emoji.optString("name",  "?"),
                emojiId = emoji.optString("id",    null),
                count   = r.optInt("count",          0),
                me      = r.optBoolean("me",       false)
            )
        }
    }

    private fun parseStickerItems(arr: JSONArray?): List<StickerItem> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { s ->
            StickerItem(id = s.optString("id", ""), name = s.optString("name", ""))
        }
    }

    suspend fun sendMessage(token: String, channelId: String, content: String): Boolean {
        val body = JSONObject().put("content", content)
        return post(token, "/channels/$channelId/messages", body) != null
    }

    // ─── DMs ──────────────────────────────────────────────────────────────────

    suspend fun fetchDmChannels(token: String): List<DmChannel> {
        val arr = getArray(token, "/users/@me/channels") ?: return emptyList()
        return (0 until arr.length())
            .map { arr.getJSONObject(it) }
            .filter { it.optInt("type") == 1 || it.optInt("type") == 3 }
            .map { parseDmChannel(it) }
    }

    private fun parseDmChannel(d: JSONObject): DmChannel {
        val recipientsArr = d.optJSONArray("recipients") ?: JSONArray()
        val recipients = (0 until recipientsArr.length()).map { recipientsArr.getJSONObject(it) }.map { u ->
            DmRecipient(
                id         = u.optString("id",          ""),
                username   = u.optString("username",    "Unknown"),
                globalName = u.optString("global_name", null),
                avatar     = u.optString("avatar",      null)
            )
        }
        return DmChannel(
            id            = d.optString("id",   ""),
            type          = d.optInt("type",      1),
            recipients    = recipients,
            name          = d.optString("name",  null),
            icon          = d.optString("icon",  null),
            lastMessageId = d.optString("last_message_id", null)
        )
    }

    suspend fun openDmChannel(token: String, userId: String): DmChannel? {
        val body = JSONObject().put("recipient_id", userId)
        val j    = post(token, "/users/@me/channels", body) ?: return null
        return parseDmChannel(j)
    }

    suspend fun fetchFriends(token: String): List<DmRecipient> {
        val arr = getArray(token, "/users/@me/relationships") ?: return emptyList()
        return (0 until arr.length())
            .map { arr.getJSONObject(it) }
            .filter { it.optInt("type") == 1 } // 1 = friend
            .mapNotNull { r ->
                val u = r.optJSONObject("user") ?: return@mapNotNull null
                DmRecipient(
                    id         = u.optString("id",          ""),
                    username   = u.optString("username",    "Unknown"),
                    globalName = u.optString("global_name", null),
                    avatar     = u.optString("avatar",      null)
                )
            }
    }

    // ─── Calls ────────────────────────────────────────────────────────────────

    suspend fun startDmCall(token: String, channelId: String): Boolean {
        // POST /channels/{channel.id}/call (user API)
        val body = JSONObject().put("recipients", JSONArray())
        return post(token, "/channels/$channelId/call", body) != null
    }

    suspend fun declineCall(token: String, channelId: String): Boolean {
        return delete(token, "/channels/$channelId/call")
    }
}
