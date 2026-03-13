package com.discordcall

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HomeTab { SERVERS, DMS }

sealed class UiState {
    object Loading : UiState()
    object Login   : UiState()
    object Home    : UiState()
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    // ─── Auth ─────────────────────────────────────────────────────────────────
    private var token: String = ""

    var loginError: String? by mutableStateOf(null)
        private set

    val isLoggedIn: Boolean get() = token.isNotEmpty() && _currentUser.value != null

    // ─── Navigation ───────────────────────────────────────────────────────────
    var homeTab: HomeTab by mutableStateOf(HomeTab.SERVERS)

    // ─── User ─────────────────────────────────────────────────────────────────
    private val _currentUser = MutableStateFlow<DiscordUser?>(null)
    val currentUser: StateFlow<DiscordUser?> = _currentUser

    // ─── Guilds ───────────────────────────────────────────────────────────────
    private val _guilds = MutableStateFlow<List<DiscordGuild>>(emptyList())
    val guilds: StateFlow<List<DiscordGuild>> = _guilds

    var loadingGuilds: Boolean by mutableStateOf(false)
        private set

    var favoriteGuildIds: Set<String> by mutableStateOf(emptySet())
        private set

    private val _selectedGuild = MutableStateFlow<DiscordGuild?>(null)
    val selectedGuild: StateFlow<DiscordGuild?> = _selectedGuild

    // ─── Channels ─────────────────────────────────────────────────────────────
    private val _categories = MutableStateFlow<List<ChannelCategory>>(emptyList())
    val categories: StateFlow<List<ChannelCategory>> = _categories

    var loadingChannels: Boolean by mutableStateOf(false)
        private set

    var selectedChannel: VoiceChannel? by mutableStateOf(null)
        private set

    // ─── Voice States ─────────────────────────────────────────────────────────
    private val _voiceStates = MutableStateFlow<List<VoiceState>>(emptyList())
    val voiceStates: StateFlow<List<VoiceState>> = _voiceStates

    // ─── Voice Call ───────────────────────────────────────────────────────────
    var isInCall: Boolean by mutableStateOf(false)
        private set

    var isMuted: Boolean by mutableStateOf(false)
        private set

    var isDeafened: Boolean by mutableStateOf(false)
        private set

    var isCameraOn: Boolean by mutableStateOf(false)
        private set

    var voiceSessionId: String by mutableStateOf("")
        private set

    var currentSpeakerId: String? by mutableStateOf(null)
        private set

    var callSettings: CallSettings by mutableStateOf(CallSettings())

    var videoQuality: VideoQuality by mutableStateOf(VideoQuality.AUTO)

    var showChat: Boolean by mutableStateOf(false)

    // ─── Messages ─────────────────────────────────────────────────────────────
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    var loadingMessages: Boolean by mutableStateOf(false)
        private set

    // ─── DM Channels ──────────────────────────────────────────────────────────
    private val _dmChannels = MutableStateFlow<List<DmChannel>>(emptyList())
    val dmChannels: StateFlow<List<DmChannel>> = _dmChannels

    var loadingDms: Boolean by mutableStateOf(false)
        private set

    // ─── Friends ──────────────────────────────────────────────────────────────
    private val _friends = MutableStateFlow<List<DmRecipient>>(emptyList())
    val friends: StateFlow<List<DmRecipient>> = _friends

    var loadingFriends: Boolean by mutableStateOf(false)
        private set

    // ─── DM Call ──────────────────────────────────────────────────────────────
    var activeDmCall: DmChannel? by mutableStateOf(null)
        private set

    var dmCallState: DmCallState by mutableStateOf(DmCallState.IDLE)
        private set

    private val _dmVoiceStates = MutableStateFlow<List<VoiceState>>(emptyList())
    val dmVoiceStates: StateFlow<List<VoiceState>> = _dmVoiceStates

    // ─── Incoming Call ────────────────────────────────────────────────────────
    private val _incomingCall = MutableStateFlow<IncomingCall?>(null)
    val incomingCall: StateFlow<IncomingCall?> = _incomingCall

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
                withContext(Dispatchers.Main) {
                    _currentUser.value = user
                }
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
        token = ""
        _currentUser.value  = null
        _guilds.value       = emptyList()
        _dmChannels.value   = emptyList()
        _friends.value      = emptyList()
        _messages.value     = emptyList()
        _categories.value   = emptyList()
        selectedChannel     = null
        activeDmCall        = null
        dmCallState         = DmCallState.IDLE
        isInCall            = false
        loginError          = null
    }

    // ─── Guilds ───────────────────────────────────────────────────────────────

    fun loadGuilds() {
        viewModelScope.launch(Dispatchers.IO) { loadGuildsInternal() }
    }

    private suspend fun loadGuildsInternal() {
        withContext(Dispatchers.Main) { loadingGuilds = true }
        try {
            val list = DiscordApi.fetchGuilds(token)
            withContext(Dispatchers.Main) { _guilds.value = list }
        } finally {
            withContext(Dispatchers.Main) { loadingGuilds = false }
        }
    }

    fun selectGuild(guild: DiscordGuild) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _selectedGuild.value = guild }
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
            val cats    = DiscordApi.fetchGuildCategories(token, guildId)
            val vstates = DiscordApi.fetchGuildVoiceStates(token, guildId)
            withContext(Dispatchers.Main) {
                _categories.value  = cats
                _voiceStates.value = vstates.values.flatten()
            }
        } finally {
            withContext(Dispatchers.Main) { loadingChannels = false }
        }
    }

    fun selectChannel(channel: VoiceChannel) {
        selectedChannel = channel
    }

    fun computeChannelPermissions(channel: VoiceChannel): Long {
        val user   = _currentUser.value ?: return 0L
        val guild  = _selectedGuild.value ?: return 0L
        // Simplified: return CONNECT | SPEAK | VIDEO | STREAM
        return DiscordPermissions.CONNECT or DiscordPermissions.SPEAK or DiscordPermissions.VIDEO or DiscordPermissions.STREAM
    }

    // ─── Voice Call ───────────────────────────────────────────────────────────

    fun joinVoiceChannel(channel: VoiceChannel) {
        selectedChannel = channel
        isInCall        = true
        Logger.i("ViewModel", "Joined voice channel: ${channel.name}")
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
                withContext(Dispatchers.Main) { _messages.value = msgs }
            } finally {
                withContext(Dispatchers.Main) { loadingMessages = false }
            }
        }
    }

    fun sendMessage(channelId: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DiscordApi.sendMessage(token, channelId, content)
            val msgs = DiscordApi.fetchMessages(token, channelId)
            withContext(Dispatchers.Main) { _messages.value = msgs }
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
            withContext(Dispatchers.Main) { _dmChannels.value = list }
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
                    val current = _dmChannels.value.toMutableList()
                    if (current.none { it.id == ch.id }) current.add(0, ch)
                    _dmChannels.value = current
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
            withContext(Dispatchers.Main) { _friends.value = list }
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
        activeDmCall = null
        dmCallState  = DmCallState.IDLE
        _dmVoiceStates.value = emptyList()
    }

    fun answerIncomingCall() {
        val incoming = _incomingCall.value ?: return
        val dm = _dmChannels.value.find { it.id == incoming.channelId }
        if (dm != null) {
            activeDmCall = dm
            dmCallState  = DmCallState.ACTIVE
        }
        _incomingCall.value = null
    }

    fun declineIncomingCall() {
        val call = _incomingCall.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            DiscordApi.declineCall(token, call.channelId)
            withContext(Dispatchers.Main) { _incomingCall.value = null }
        }
    }
}
