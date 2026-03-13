package com.discordcall

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HomeTab { SERVERS, DMS }

sealed class UiState {
    object Loading : UiState()
    object Login   : UiState()
    object Home    : UiState()
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    // ─── Token / Auth ─────────────────────────────────────────────────────────
    private var token: String = ""

    var loginError: String? by mutableStateOf(null)
        private set

    val isLoggedIn: Boolean get() = token.isNotEmpty() && currentUser != null

    // ─── Navigation ───────────────────────────────────────────────────────────
    var homeTab: HomeTab by mutableStateOf(HomeTab.SERVERS)

    // ─── Current User ─────────────────────────────────────────────────────────
    var currentUser: DiscordUser? by mutableStateOf(null)
        private set

    // ─── Guilds ───────────────────────────────────────────────────────────────
    var guilds: List<DiscordGuild> by mutableStateOf(emptyList())
        private set

    var loadingGuilds: Boolean by mutableStateOf(false)
        private set

    var favoriteGuildIds: Set<String> by mutableStateOf(emptySet())
        private set

    var selectedGuild: DiscordGuild? by mutableStateOf(null)
        private set

    // ─── Channels ─────────────────────────────────────────────────────────────
    var categories: List<ChannelCategory> by mutableStateOf(emptyList())
        private set

    var loadingChannels: Boolean by mutableStateOf(false)
        private set

    var selectedChannel: VoiceChannel? by mutableStateOf(null)
        private set

    // ─── Voice States ─────────────────────────────────────────────────────────
    var voiceStates: List<VoiceState> by mutableStateOf(emptyList())
        private set

    // ─── Voice Call ───────────────────────────────────────────────────────────
    var isInCall: Boolean by mutableStateOf(false)
        private set

    var isMuted: Boolean by mutableStateOf(false)
        private set

    var isDeafened: Boolean by mutableStateOf(false)
        private set

    // Public setter needed - screens toggle this directly
    var isCameraOn: Boolean by mutableStateOf(false)

    var voiceSessionId: String by mutableStateOf("")
        private set

    var currentSpeakerId: String? by mutableStateOf(null)
        private set

    var callSettings: CallSettings by mutableStateOf(CallSettings())

    var videoQuality: VideoQuality by mutableStateOf(VideoQuality.AUTO)

    var showChat: Boolean by mutableStateOf(false)

    // ─── Messages ─────────────────────────────────────────────────────────────
    var messages: List<Message> by mutableStateOf(emptyList())
        private set

    var loadingMessages: Boolean by mutableStateOf(false)
        private set

    // ─── DM Channels ──────────────────────────────────────────────────────────
    var dmChannels: List<DmChannel> by mutableStateOf(emptyList())
        private set

    var loadingDms: Boolean by mutableStateOf(false)
        private set

    // ─── Friends ──────────────────────────────────────────────────────────────
    var friends: List<DmRecipient> by mutableStateOf(emptyList())
        private set

    var loadingFriends: Boolean by mutableStateOf(false)
        private set

    // ─── DM Call ──────────────────────────────────────────────────────────────
    var activeDmCall: DmChannel? by mutableStateOf(null)
        private set

    var dmCallState: DmCallState by mutableStateOf(DmCallState.IDLE)
        private set

    var dmVoiceStates: List<VoiceState> by mutableStateOf(emptyList())
        private set

    // ─── Incoming Call ────────────────────────────────────────────────────────
    var incomingCall: IncomingCall? by mutableStateOf(null)
        private set

    // ─── Init ─────────────────────────────────────────────────────────────────
    init {
        TokenStore.init(application)
        val savedToken = TokenStore.getToken()
        if (savedToken != null) {
            token = savedToken
            viewModelScope.launch(Dispatchers.IO) {
                loginWithToken(savedToken)
            }
        }
    }

    fun getApp(): Application = getApplication()

    // ─── Auth ─────────────────────────────────────────────────────────────────

    fun loginWithToken(t: String) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { loginError = null }
            try {
                val user = DiscordApi.fetchMe(t)
                token = t
                TokenStore.saveToken(t)
                withContext(Dispatchers.Main) { currentUser = user }
                loadGuildsInternal()
                loadDmChannelsInternal()
                loadFriendsInternal()
            } catch (e: Exception) {
                Logger.e("ViewModel", "Login error: ${e.message}")
                withContext(Dispatchers.Main) {
                    loginError = e.message ?: "Falha ao autenticar"
                }
            }
        }
    }

    fun logout() {
        TokenStore.clearToken()
        token            = ""
        currentUser      = null
        guilds           = emptyList()
        dmChannels       = emptyList()
        friends          = emptyList()
        messages         = emptyList()
        categories       = emptyList()
        selectedChannel  = null
        selectedGuild    = null
        activeDmCall     = null
        dmCallState      = DmCallState.IDLE
        isInCall         = false
        loginError       = null
    }

    // ─── Guilds ───────────────────────────────────────────────────────────────

    fun loadGuilds() {
        viewModelScope.launch(Dispatchers.IO) { loadGuildsInternal() }
    }

    private suspend fun loadGuildsInternal() {
        withContext(Dispatchers.Main) { loadingGuilds = true }
        try {
            val list = DiscordApi.fetchGuilds(token)
            withContext(Dispatchers.Main) { guilds = list }
        } finally {
            withContext(Dispatchers.Main) { loadingGuilds = false }
        }
    }

    fun selectGuild(guild: DiscordGuild) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { selectedGuild = guild }
            loadChannelsInternal(guild.id)
        }
    }

    fun toggleFavoriteGuild(guildId: String) {
        favoriteGuildIds = if (guildId in favoriteGuildIds)
            favoriteGuildIds - guildId
        else
            favoriteGuildIds + guildId
    }

    // ─── Channels ─────────────────────────────────────────────────────────────

    fun loadChannels(guildId: String) {
        viewModelScope.launch(Dispatchers.IO) { loadChannelsInternal(guildId) }
    }

    private suspend fun loadChannelsInternal(guildId: String) {
        withContext(Dispatchers.Main) { loadingChannels = true }
        try {
            val cats     = DiscordApi.fetchGuildCategories(token, guildId)
            val vstatesMap = DiscordApi.fetchGuildVoiceStates(token, guildId)
            withContext(Dispatchers.Main) {
                categories  = cats
                voiceStates = vstatesMap.values.flatten()
            }
        } finally {
            withContext(Dispatchers.Main) { loadingChannels = false }
        }
    }

    fun selectChannel(channel: VoiceChannel) {
        selectedChannel = channel
    }

    fun computeChannelPermissions(channel: VoiceChannel): Long {
        // Full permissions for now; can be refined with member/role data
        return DiscordPermissions.CONNECT or DiscordPermissions.SPEAK or
               DiscordPermissions.VIDEO   or DiscordPermissions.STREAM
    }

    // ─── Voice Call ───────────────────────────────────────────────────────────

    fun joinVoiceChannel(channel: VoiceChannel) {
        selectedChannel = channel
        isInCall        = true
        Logger.i("ViewModel", "Joined: ${channel.name}")
    }

    fun leaveVoiceChannel() {
        isInCall        = false
        selectedChannel = null
        CallService.stop(getApplication())
        Logger.i("ViewModel", "Left voice channel")
    }

    fun toggleMute() {
        isMuted = !isMuted
    }

    fun toggleDeafen() {
        isDeafened = !isDeafened
    }

    // ─── Messages ─────────────────────────────────────────────────────────────

    fun loadMessages(channelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { loadingMessages = true }
            try {
                val msgs = DiscordApi.fetchMessages(token, channelId)
                withContext(Dispatchers.Main) { messages = msgs }
            } finally {
                withContext(Dispatchers.Main) { loadingMessages = false }
            }
        }
    }

    fun sendMessage(content: String) {
        val channelId = selectedChannel?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            DiscordApi.sendMessage(token, channelId, content)
            val msgs = DiscordApi.fetchMessages(token, channelId)
            withContext(Dispatchers.Main) { messages = msgs }
        }
    }

    fun sendMessage(channelId: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DiscordApi.sendMessage(token, channelId, content)
            val msgs = DiscordApi.fetchMessages(token, channelId)
            withContext(Dispatchers.Main) { messages = msgs }
        }
    }

    // ─── DM Channels ──────────────────────────────────────────────────────────

    fun loadDmChannels() {
        viewModelScope.launch(Dispatchers.IO) { loadDmChannelsInternal() }
    }

    private suspend fun loadDmChannelsInternal() {
        withContext(Dispatchers.Main) { loadingDms = true }
        try {
            val list = DiscordApi.fetchDmChannels(token)
            withContext(Dispatchers.Main) { dmChannels = list }
        } finally {
            withContext(Dispatchers.Main) { loadingDms = false }
        }
    }

    fun selectDmChannel(dm: DmChannel) {
        loadMessages(dm.id)
    }

    fun openDmChannel(recipient: DmRecipient) {
        viewModelScope.launch(Dispatchers.IO) {
            val ch = DiscordApi.openDmChannel(token, recipient.id)
            if (ch != null) {
                withContext(Dispatchers.Main) {
                    val current = dmChannels.toMutableList()
                    if (current.none { it.id == ch.id }) current.add(0, ch)
                    dmChannels = current
                }
                loadMessages(ch.id)
            }
        }
    }

    fun openDmChannel(userId: String, onResult: (DmChannel?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ch = DiscordApi.openDmChannel(token, userId)
            withContext(Dispatchers.Main) { onResult(ch) }
        }
    }

    // ─── Friends ──────────────────────────────────────────────────────────────

    fun loadFriends() {
        viewModelScope.launch(Dispatchers.IO) { loadFriendsInternal() }
    }

    private suspend fun loadFriendsInternal() {
        withContext(Dispatchers.Main) { loadingFriends = true }
        try {
            val list = DiscordApi.fetchFriends(token)
            withContext(Dispatchers.Main) { friends = list }
        } finally {
            withContext(Dispatchers.Main) { loadingFriends = false }
        }
    }

    // ─── DM Call ──────────────────────────────────────────────────────────────

    fun startDmCall(dm: DmChannel) {
        activeDmCall = dm
        dmCallState  = DmCallState.RINGING_OUT
        viewModelScope.launch(Dispatchers.IO) {
            DiscordApi.startDmCall(token, dm.id)
        }
    }

    fun leaveDmCall() {
        val dm = activeDmCall
        if (dm != null) {
            viewModelScope.launch(Dispatchers.IO) {
                DiscordApi.declineCall(token, dm.id)
            }
        }
        activeDmCall  = null
        dmCallState   = DmCallState.IDLE
        dmVoiceStates = emptyList()
    }

    fun answerIncomingCall() {
        val incoming = incomingCall ?: return
        val dm = dmChannels.find { it.id == incoming.channelId }
        if (dm != null) {
            activeDmCall = dm
            dmCallState  = DmCallState.ACTIVE
        }
        incomingCall = null
    }

    fun declineIncomingCall() {
        val call = incomingCall ?: return
        viewModelScope.launch(Dispatchers.IO) {
            DiscordApi.declineCall(token, call.channelId)
            withContext(Dispatchers.Main) { incomingCall = null }
        }
    }
}
