package com.discordcall

import android.content.Context
import android.content.SharedPreferences

object TokenStore {
    private const val PREFS_NAME = "discord_prefs"
    private const val KEY_TOKEN  = "token"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getToken(): String? =
        prefs?.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun saveToken(token: String) {
        prefs?.edit()?.putString(KEY_TOKEN, token)?.apply()
    }

    fun clearToken() {
        prefs?.edit()?.remove(KEY_TOKEN)?.apply()
    }
}
