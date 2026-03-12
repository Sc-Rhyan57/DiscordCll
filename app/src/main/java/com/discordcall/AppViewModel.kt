package com.discordcall

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val gateway     = GatewayManager()
    val voiceEngine = VoiceEngine(app)

    var currentUser     by mutableStateOf<DiscordUser?>(null)
    var token           by mutableStateOf("")
    var isLoggedIn      by mutableStateOf(false)

    var guilds          by mutableStateOf<List<DiscordGuild>>(emptyList())
    var loadingGuilds   by mutableStateOf(false)

    var selectedGuild   by mutableStateOf<DiscordGuild?>(null)
    var categories      by mutableStateOf<List<ChannelCategory>>(emptyList())
    var loadingChannels by mutableStateOf(false)

    val voiceStates     = mutableStateListOf<VoiceState>()

    var selectedChannel    by mutableStateOf<VoiceChannel?>(null)
    var isInCall           by mutableStateOf(false)
    var isMuted            by mutableStateOf(false)
    var isDeafened         by mutableStateOf(false)
    var isCameraOn         by mutableStateOf(false)
    var isScreenSharing    by mutableStateOf(false)
    var videoQuality       by mutableStateOf(VideoQuality.AUTO)

    var callSettings       by mutableStateOf(CallSettings())

    val messages           = mutableStateListOf<Message>()
    var loadingMessages    by mutableStateOf(false)
    var showChat           by mutableStateOf(false)

    var userPermissions    by mutableStateOf(0L)
    var memberRoles        by mutableStateOf<List<String>>(emptyList())

    var loadingUser        by mutableStateOf(false)
    var loginError         by mutableStateOf<String?>(null)

    var overlayEnabled     by mutableStateOf(false)
    var currentSpeakerId   by mutableStateOf<String?>(null)

    var voiceSessionId     = ""
    var voiceToken_        = ""
    var voiceEndpoint      = ""

    var dmChannels         by mutableStateOf<List<DmChannel>>(emptyList())
    var loadingDms         by mutableStateOf(false)
    var friends            by mutableStateOf<List<DmRecipient>>(emptyList())
    var loadingFriends     by mutableStateOf(false)

    var selectedDmChannel  by mutableStateOf<DmChannel?>(null)
    var activeDmCall       by mutableStateOf<DmChannel?>(null)
    var dmCallState        by mutableStateOf(DmCallState.IDLE)
    var incomingCall       by mutableStateOf<IncomingCall?>(null)
    val dmVoiceStates      = mutableStateListOf<VoiceState>()

    var homeTab            by mutableStateOf(HomeTab.SERVERS)

    private val prefs get() = getApplication<Application>().getSharedPreferences("app_prefs", 0)

    // Public helper so Composables can access Application context
    fun getApp(): Application = getApplication()

    init {
        val saved = prefs.getString("token", null)
        if (!saved.isNullOrEmpty()) {
            token = saved
            viewModelScope.launch { loginWithToken(token) }
        }
        setupGateway()
    }

    private fun setupGateway() {
        gateway.onReady = { userId ->
            Logger.s("ViewModel", "Gateway ready userId=$userId")
        }
        gateway.onVoiceStateUpdate = { vs ->
            viewModelScope.launch {
                val existing = voiceStates.indexOfFirst { it.userId == vs.userId }
                if (vs.channelId.isEmpty()) {
                    if (existing >= 0) voiceStates.removeAt(existing)
                } else {
                    if (existing >= 0) voiceStates[existing] = vs
                    else voiceStates.add(vs)
                }
            }
        }
        gateway.onVoiceServerUpdate = { vToken, gId, ep ->
            voiceToken_   = vToken
            voiceEndpoint = ep
            viewModelScope.launch {
                voiceEngine.connect(
                    vToken       = vToken,
                    guild        = gId,
                    channel      = selectedChannel?.id ?: "",
                    session      = voiceSessionId,
                    endpointHost = ep,
                    userId       = currentUser?.id ?: ""
                )
            }
        }
        gateway.onDmVoiceServerUpdate = { vToken, chanId, ep ->
            voiceToken_   = vToken
            voiceEndpoint = ep
            viewModelScope.launch {
                voiceEngine.connect(
                    vToken       = vToken,
                    guild        = "",
                    channel      = chanId,
                    session      = voiceSessionId,
                    endpointHost = ep,
                    userId       = currentUser?.id ?: ""
                )
            }
        }
        gateway.onMessageCreate = { channelId, msg ->
            viewModelScope.launch {
                val isForServerChannel = selectedChannel?.id == channelId
                val isForDmChannel     = selectedDmChannel?.id == channelId || activeDmCall?.id == channelId
                if (isForServerChannel || isForDmChannel) {
                    messages.add(msg)
                    if (messages.size > 200) messages.removeAt(0)
                }
            }
        }
        gateway.onSpeakingUpdate = { userId, speaking ->
            viewModelScope.launch {
                val idx = voiceStates.indexOfFirst { it.userId == userId }
                if (idx >= 0) voiceStates[idx] = voiceStates[idx].copy(speaking = speaking)
                val dmIdx = dmVoiceStates.indexOfFirst { it.userId == userId }
                if (dmIdx >= 0) dmVoiceStates[dmIdx] = dmVoiceStates[dmIdx].copy(speaking = speaking)
                if (speaking) currentSpeakerId = userId
                else if (currentSpeakerId == userId) currentSpeakerId = null
            }
        }
        gateway.onGuildVoiceStates = { _, states ->
            viewModelScope.launch {
                states.forEach { vs ->
                    val idx = voiceStates.indexOfFirst { it.userId == vs.userId }
                    if (idx >= 0) voiceStates[idx] = vs else voiceStates.add(vs)
                }
            }
        }
        gateway.onDmVoiceStateUpdate = { channelId, vs ->
            viewModelScope.launch {
                val idx = dmVoiceStates.indexOfFirst { it.userId == vs.userId }
                if (vs.channelId.isEmpty()) {
                    if (idx >= 0) dmVoiceStates.removeAt(idx)
                } else {
                    if (idx >= 0) dmVoiceStates[idx] = vs else dmVoiceStates.add(vs)
                }
            }
        }
        gateway.onCallCreate = { call ->
            viewModelScope.launch {
                val myId = currentUser?.id ?: return@launch
                if (call.callerId != myId) {
                    val enriched = enrichIncomingCall(call)
                    incomingCall = enriched
                    Logger.i("ViewModel", "Incoming call from ${enriched.callerName}")
                }
            }
        }
        gateway.onCallDelete = { channelId ->
            viewModelScope.launch {
                if (incomingCall?.channelId == channelId) {
                    incomingCall = null
                    Logger.i("ViewModel", "Incoming call cancelled $channelId")
                }
                if (activeDmCall?.id == channelId) {
                    leaveDmCall()
                }
            }
        }
        voiceEngine.onConnected = {
            viewModelScope.launch {
                isInCall = true
                voiceEngine.startAudio()
                Logger.s("ViewModel", "Voice connected!")
            }
        }
        voiceEngine.onDisconnected = {
            viewModelScope.launch {
                isInCall = false
                isMuted  = false
            }
        }
        voiceEngine.onSpeakingChange = { userId, speaking ->
            viewModelScope.launch {
                val idx = voiceStates.indexOfFirst { it.userId == userId }
                if (idx >= 0) voiceStates[idx] = voiceStates[idx].copy(speaking = speaking)
                val dmIdx = dmVoiceStates.indexOfFirst { it.userId == userId }
                if (dmIdx >= 0) dmVoiceStates[dmIdx] = dmVoiceStates[dmIdx].copy(speaking = speaking)
                if (speaking) currentSpeakerId = userId
                else if (currentSpeakerId == userId) currentSpeakerId = null
            }
        }
    }

    private suspend fun enrichIncomingCall(call: IncomingCall): IncomingCall {
        return try {
            val dm = dmChannels.find { it.id == call.channelId }
            if (dm != null) {
                val caller = dm.recipients.find { it.id == call.callerId }
                call.copy(
                    callerName   = caller?.displayName ?: call.callerName,
                    callerAvatar = caller?.avatar,
                    isGroup      = dm.isGroupDm,
                    groupName    = if (dm.isGroupDm) dm.displayName else null
                )
            } else call
        } catch (_: Exception) { call }
    }

    suspend fun loginWithToken(t: String): Boolean {
        loadingUser  = true
        loginError   = null
        return try {
            val user = DiscordApi.fetchMe(t)
            currentUser = user
            token       = t
            isLoggedIn  = true
            prefs.edit().putString("token", t).apply()
            voiceEngine.initialize(user.id)
            gateway.connect(t)
            viewModelScope.launch { loadGuilds() }
            viewModelScope.launch { loadDmChannels() }
            viewModelScope.launch { loadFriends() }
            Logger.s("ViewModel", "Logged in as ${user.displayName}")
            true
        } catch (e: Exception) {
            loginError = "Login falhou: ${e.message}"
            Logger.e("ViewModel", "Login error: ${e.message}")
            false
        } finally {
            loadingUser = false
        }
    }

    fun logout() {
        gateway.disconnect()
        voiceEngine.release()
        token          = ""
        isLoggedIn     = false
        currentUser    = null
        guilds         = emptyList()
        dmChannels     = emptyList()
        friends        = emptyList()
        selectedGuild  = null
        selectedChannel= null
        activeDmCall   = null
        selectedDmChannel = null
        dmCallState    = DmCallState.IDLE
        incomingCall   = null
        isInCall       = false
        messages.clear()
        voiceStates.clear()
        dmVoiceStates.clear()
        prefs.edit().remove("token").apply()
        Logger.i("ViewModel", "Logged out")
    }

    fun loadGuilds() {
        viewModelScope.launch {
            loadingGuilds = true
            try {
                guilds = DiscordApi.fetchGuilds(token).sortedBy { it.name }
                Logger.s("ViewModel", "Loaded ${guilds.size} guilds")
            } catch (e: Exception) {
                Logger.e("ViewModel", "loadGuilds: ${e.message}")
            } finally {
                loadingGuilds = false
            }
        }
    }

    fun loadDmChannels() {
        viewModelScope.launch {
            loadingDms = true
            try {
                dmChannels = DiscordApi.fetchDmChannels(token)
                Logger.s("ViewModel", "Loaded ${dmChannels.size} DM channels")
            } catch (e: Exception) {
                Logger.e("ViewModel", "loadDmChannels: ${e.message}")
            } finally {
                loadingDms = false
            }
        }
    }

    fun loadFriends() {
        viewModelScope.launch {
            loadingFriends = true
            try {
                friends = DiscordApi.fetchFriends(token)
                Logger.s("ViewModel", "Loaded ${friends.size} friends")
            } catch (e: Exception) {
                Logger.e("ViewModel", "loadFriends: ${e.message}")
            } finally {
                loadingFriends = false
            }
        }
    }

    fun selectGuild(guild: DiscordGuild) {
        selectedGuild   = guild
        selectedChannel = null
        voiceStates.clear()
        messages.clear()
        loadChannels(guild)
        gateway.subscribeToGuild(guild.id)
    }

    private fun loadChannels(guild: DiscordGuild) {
        viewModelScope.launch {
            loadingChannels = true
            try {
                categories = DiscordApi.fetchGuildCategories(token, guild.id)
                val vsMap  = DiscordApi.fetchGuildVoiceStates(token, guild.id)
                vsMap.forEach { (_, states) ->
                    states.forEach { vs ->
                        val idx = voiceStates.indexOfFirst { it.userId == vs.userId }
                        if (idx >= 0) voiceStates[idx] = vs else voiceStates.add(vs)
                    }
                }
                loadUserPermissions(guild)
                Logger.s("ViewModel", "Loaded ${categories.sumOf { it.channels.size }} voice channels")
            } catch (e: Exception) {
                Logger.e("ViewModel", "loadChannels: ${e.message}")
            } finally {
                loadingChannels = false
            }
        }
    }

    private suspend fun loadUserPermissions(guild: DiscordGuild) {
        try {
            val uid    = currentUser?.id ?: return
            val member = DiscordApi.fetchGuildMember(token, guild.id, uid)
            if (member != null) {
                memberRoles = member.roles
                val roles   = DiscordApi.fetchGuildRoles(token, guild.id)
                var perms   = 0L
                if (guild.ownerId == uid) {
                    perms = Long.MAX_VALUE
                } else {
                    roles[guild.id]?.let { perms = perms or it }
                    member.roles.forEach { roleId -> roles[roleId]?.let { perms = perms or it } }
                }
                userPermissions = perms
            }
        } catch (e: Exception) {
            Logger.w("ViewModel", "loadUserPermissions: ${e.message}")
        }
    }

    fun computeChannelPermissions(channel: VoiceChannel): Long {
        var perms = userPermissions
        if (DiscordPermissions.has(perms, DiscordPermissions.ADMINISTRATOR)) return Long.MAX_VALUE
        channel.permissionOverwrites.forEach { ow ->
            when (ow.type) {
                0 -> if (ow.id == selectedGuild?.id) perms = (perms and ow.deny.inv()) or ow.allow
                1 -> if (ow.id == currentUser?.id)   perms = (perms and ow.deny.inv()) or ow.allow
            }
        }
        memberRoles.forEach { roleId ->
            channel.permissionOverwrites.filter { it.type == 0 && it.id == roleId }.forEach { ow ->
                perms = (perms and ow.deny.inv()) or ow.allow
            }
        }
        return perms
    }

    fun selectChannel(channel: VoiceChannel) {
        selectedChannel = channel
        loadMessages(channel.id)
    }

    fun joinVoiceChannel(channel: VoiceChannel) {
        val guild = selectedGuild ?: return
        selectedChannel = channel
        gateway.sendVoiceStateUpdate(guild.id, channel.id, isMuted, isDeafened)
        viewModelScope.launch {
            var waited = 0
            while (voiceSessionId.isEmpty() && waited < 5000) { delay(100); waited += 100 }
            if (voiceSessionId.isEmpty()) Logger.e("ViewModel", "Timed out waiting for voice session")
        }
        Logger.i("ViewModel", "Joining voice channel: ${channel.name}")
    }

    fun leaveVoiceChannel() {
        val guild = selectedGuild ?: return
        gateway.sendVoiceStateUpdate(guild.id, null, false, false)
        voiceEngine.disconnect()
        isInCall        = false
        isMuted         = false
        isDeafened      = false
        isCameraOn      = false
        isScreenSharing = false
        voiceSessionId  = ""
        voiceToken_     = ""
        voiceEndpoint   = ""
        CallService.stop(getApplication())
        Logger.i("ViewModel", "Left voice channel")
    }

    fun openDmChannel(recipient: DmRecipient) {
        viewModelScope.launch {
            val dm = DiscordApi.openDmChannel(token, recipient.id)
            if (dm != null) {
                selectedDmChannel = dm
                if (!dmChannels.any { it.id == dm.id }) {
                    dmChannels = listOf(dm) + dmChannels
                }
                messages.clear()
                loadMessages(dm.id)
            }
        }
    }

    fun selectDmChannel(dm: DmChannel) {
        selectedDmChannel = dm
        messages.clear()
        loadMessages(dm.id)
    }

    fun startDmCall(dm: DmChannel) {
        viewModelScope.launch {
            dmCallState   = DmCallState.RINGING_OUT
            activeDmCall  = dm
            dmVoiceStates.clear()
            val myUser = currentUser
            if (myUser != null) {
                dmVoiceStates.add(VoiceState(
                    userId     = myUser.id,
                    username   = myUser.displayName,
                    avatar     = myUser.avatar,
                    channelId  = dm.id,
                    selfMute   = isMuted,
                    selfDeaf   = isDeafened,
                    selfVideo  = false,
                    selfStream = false,
                    serverMute = false,
                    serverDeaf = false,
                    suppress   = false
                ))
            }
            val ok = DiscordApi.startDmCall(token, dm.id)
            if (!ok) {
                dmCallState  = DmCallState.IDLE
                activeDmCall = null
                dmVoiceStates.clear()
                Logger.e("ViewModel", "Failed to start DM call")
                return@launch
            }
            gateway.sendDmVoiceStateUpdate(dm.id, isMuted, isDeafened)
            Logger.i("ViewModel", "DM call started to ${dm.displayName}")
            var waited = 0
            while (voiceSessionId.isEmpty() && waited < 8000) { delay(100); waited += 100 }
            if (voiceSessionId.isEmpty()) {
                Logger.e("ViewModel", "Timed out waiting for DM voice session")
            }
        }
    }

    fun answerIncomingCall() {
        val call = incomingCall ?: return
        val dm   = dmChannels.find { it.id == call.channelId } ?: run {
            DmChannel(
                id         = call.channelId,
                type       = 1,
                recipients = listOf(DmRecipient(call.callerId, call.callerName, null, call.callerAvatar)),
                name       = null,
                icon       = null,
                lastMessageId = null
            )
        }
        incomingCall  = null
        activeDmCall  = dm
        dmCallState   = DmCallState.ACTIVE
        dmVoiceStates.clear()
        gateway.sendDmVoiceStateUpdate(call.channelId, isMuted, isDeafened)
        viewModelScope.launch {
            var waited = 0
            while (voiceSessionId.isEmpty() && waited < 8000) { delay(100); waited += 100 }
            if (voiceSessionId.isEmpty()) Logger.e("ViewModel", "Timed out waiting for DM voice session after answer")
        }
        Logger.i("ViewModel", "Answered call from ${call.callerName}")
    }

    fun declineIncomingCall() {
        val call = incomingCall ?: return
        incomingCall = null
        viewModelScope.launch {
            DiscordApi.declineCall(token, call.channelId)
            Logger.i("ViewModel", "Declined call from ${call.callerName}")
        }
    }

    fun leaveDmCall() {
        val dm = activeDmCall ?: return
        gateway.sendDmVoiceStateUpdate(null, false, false)
        voiceEngine.disconnect()
        activeDmCall    = null
        dmCallState     = DmCallState.IDLE
        isInCall        = false
        isMuted         = false
        isDeafened      = false
        dmVoiceStates.clear()
        voiceSessionId  = ""
        voiceToken_     = ""
        voiceEndpoint   = ""
        CallService.stop(getApplication())
        Logger.i("ViewModel", "Left DM call with ${dm.displayName}")
    }

    fun toggleMute() {
        isMuted = !isMuted
        voiceEngine.setMuted(isMuted)
        val guild = selectedGuild
        if (guild != null) {
            val channel = selectedChannel ?: return
            gateway.sendVoiceStateUpdate(guild.id, channel.id, isMuted, isDeafened)
        } else {
            val dm = activeDmCall ?: return
            gateway.sendDmVoiceStateUpdate(dm.id, isMuted, isDeafened)
        }
    }

    fun toggleDeafen() {
        isDeafened = !isDeafened
        voiceEngine.setDeafened(isDeafened)
        val guild = selectedGuild
        if (guild != null) {
            val channel = selectedChannel ?: return
            gateway.sendVoiceStateUpdate(guild.id, channel.id, isMuted, isDeafened)
        } else {
            val dm = activeDmCall ?: return
            gateway.sendDmVoiceStateUpdate(dm.id, isMuted, isDeafened)
        }
    }

    fun loadMessages(channelId: String) {
        viewModelScope.launch {
            loadingMessages = true
            try {
                val fetched = DiscordApi.fetchMessages(token, channelId)
                messages.clear()
                messages.addAll(fetched)
                Logger.s("ViewModel", "Loaded ${fetched.size} messages for $channelId")
            } catch (e: Exception) {
                Logger.e("ViewModel", "loadMessages: ${e.message}")
            } finally {
                loadingMessages = false
            }
        }
    }

    fun sendMessage(content: String) {
        val channelId = selectedDmChannel?.id ?: selectedChannel?.id ?: activeDmCall?.id ?: return
        viewModelScope.launch {
            val ok = DiscordApi.sendMessage(token, channelId, content)
            if (!ok) Logger.e("ViewModel", "Failed to send message")
        }
    }

    override fun onCleared() {
        super.onCleared()
        gateway.disconnect()
        voiceEngine.release()
    }
}

enum class HomeTab { SERVERS, DMS }
