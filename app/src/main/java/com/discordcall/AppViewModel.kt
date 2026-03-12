package com.discordcall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    private val _currentUser = MutableStateFlow<DiscordUser?>(null)
    val currentUser: StateFlow<DiscordUser?> = _currentUser

    private val _guilds = MutableStateFlow<List<DiscordGuild>>(emptyList())
    val guilds: StateFlow<List<DiscordGuild>> = _guilds

    private val _selectedGuild = MutableStateFlow<DiscordGuild?>(null)
    val selectedGuild: StateFlow<DiscordGuild?> = _selectedGuild

    private val _categories = MutableStateFlow<List<ChannelCategory>>(emptyList())
    val categories: StateFlow<List<ChannelCategory>> = _categories

    private val _voiceStates = MutableStateFlow<Map<String, List<VoiceState>>>(emptyMap())
    val voiceStates: StateFlow<Map<String, List<VoiceState>>> = _voiceStates

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _dmChannels = MutableStateFlow<List<DmChannel>>(emptyList())
    val dmChannels: StateFlow<List<DmChannel>> = _dmChannels

    private val _friends = MutableStateFlow<List<DmRecipient>>(emptyList())
    val friends: StateFlow<List<DmRecipient>> = _friends

    private val _incomingCall = MutableStateFlow<IncomingCall?>(null)
    val incomingCall: StateFlow<IncomingCall?> = _incomingCall

    private var token: String = ""

    init {
        val savedToken = TokenStore.getToken()
        if (savedToken != null) {
            token = savedToken
            viewModelScope.launch(Dispatchers.IO) {
                loginWithToken(savedToken)
            }
        } else {
            _uiState.value = UiState.Login
        }
    }

    fun loginWithToken(t: String) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _uiState.value = UiState.Loading }
            try {
                val user = DiscordApi.fetchMe(t)
                token = t
                TokenStore.saveToken(t)
                withContext(Dispatchers.Main) {
                    _currentUser.value = user
                    _uiState.value = UiState.Home
                }
                loadGuildsInternal()
                loadDmChannelsInternal()
                loadFriendsInternal()
            } catch (e: Exception) {
                Logger.e("ViewModel", "Login error: ${e.message}")
                withContext(Dispatchers.Main) {
                    _uiState.value = UiState.Login
                }
            }
        }
    }

    fun loadGuilds() {
        viewModelScope.launch(Dispatchers.IO) { loadGuildsInternal() }
    }

    private suspend fun loadGuildsInternal() {
        val list = DiscordApi.fetchGuilds(token)
        withContext(Dispatchers.Main) { _guilds.value = list }
    }

    fun selectGuild(guild: DiscordGuild) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _selectedGuild.value = guild }
            val cats   = DiscordApi.fetchGuildCategories(token, guild.id)
            val vstates = DiscordApi.fetchGuildVoiceStates(token, guild.id)
            withContext(Dispatchers.Main) {
                _categories.value  = cats
                _voiceStates.value = vstates
            }
        }
    }

    fun loadChannels(guildId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cats    = DiscordApi.fetchGuildCategories(token, guildId)
            val vstates = DiscordApi.fetchGuildVoiceStates(token, guildId)
            withContext(Dispatchers.Main) {
                _categories.value  = cats
                _voiceStates.value = vstates
            }
        }
    }

    fun loadMessages(channelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val msgs = DiscordApi.fetchMessages(token, channelId)
            withContext(Dispatchers.Main) { _messages.value = msgs }
        }
    }

    fun sendMessage(channelId: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DiscordApi.sendMessage(token, channelId, content)
            val msgs = DiscordApi.fetchMessages(token, channelId)
            withContext(Dispatchers.Main) { _messages.value = msgs }
        }
    }

    fun loadDmChannels() {
        viewModelScope.launch(Dispatchers.IO) { loadDmChannelsInternal() }
    }

    private suspend fun loadDmChannelsInternal() {
        val list = DiscordApi.fetchDmChannels(token)
        withContext(Dispatchers.Main) { _dmChannels.value = list }
    }

    fun loadFriends() {
        viewModelScope.launch(Dispatchers.IO) { loadFriendsInternal() }
    }

    private suspend fun loadFriendsInternal() {
        val list = DiscordApi.fetchFriends(token)
        withContext(Dispatchers.Main) { _friends.value = list }
    }

    fun openDmChannel(userId: String, onResult: (DmChannel?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ch = DiscordApi.openDmChannel(token, userId)
            withContext(Dispatchers.Main) { onResult(ch) }
        }
    }

    fun startDmCall(channelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DiscordApi.startDmCall(token, channelId)
        }
    }

    fun declineIncomingCall() {
        val call = _incomingCall.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            DiscordApi.declineCall(token, call.channelId)
            withContext(Dispatchers.Main) { _incomingCall.value = null }
        }
    }

    fun logout() {
        TokenStore.clearToken()
        token = ""
        _currentUser.value  = null
        _guilds.value       = emptyList()
        _dmChannels.value   = emptyList()
        _friends.value      = emptyList()
        _uiState.value      = UiState.Login
    }
}
