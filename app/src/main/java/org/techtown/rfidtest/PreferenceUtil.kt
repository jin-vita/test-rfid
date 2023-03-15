package org.techtown.rfidtest

import android.content.Context

class PreferenceUtil(context: Context) {

    private val prefs by lazy { context.getSharedPreferences("prefs_name", Context.MODE_PRIVATE) }

    fun getString(key: String, defValue: String): String = prefs.getString(key, defValue).toString()

    fun setString(key: String, str: String) = prefs.edit().putString(key, str).apply()
}