package com.discordcall

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    val logs     = mutableStateListOf<AppLog>()
    val enabled  = mutableStateOf(true)

    fun log(level: String, tag: String, message: String, detail: String? = null) {
        if (!enabled.value) return
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        logs.add(0, AppLog(ts, level, tag, message, detail))
        if (logs.size > 300) logs.removeAt(logs.size - 1)
    }

    fun i(tag: String, msg: String, detail: String? = null) = log("INFO",    tag, msg, detail)
    fun e(tag: String, msg: String, detail: String? = null) = log("ERROR",   tag, msg, detail)
    fun w(tag: String, msg: String, detail: String? = null) = log("WARN",    tag, msg, detail)
    fun s(tag: String, msg: String, detail: String? = null) = log("SUCCESS", tag, msg, detail)
    fun d(tag: String, msg: String, detail: String? = null) = log("DEBUG",   tag, msg, detail)
}
