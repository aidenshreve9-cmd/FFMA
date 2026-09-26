package com.focusfriend.app.model

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ff.settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = SettingsCodec.decode(prefs.getString(KEY, null))

    fun save(s: AppSettings): Boolean = prefs.edit().putString(KEY, SettingsCodec.encode(s)).commit()

    private companion object {
        const val KEY = "ff.settings.v1"
    }
}
