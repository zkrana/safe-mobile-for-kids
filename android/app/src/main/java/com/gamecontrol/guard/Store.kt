package com.gamecontrol.guard

import android.content.Context

/** Policy JSON (owned by Rust) and the parent-unlock window. */
object Store {
    private const val UNLOCK_MINUTES = 10L

    private fun prefs(c: Context) = c.getSharedPreferences("guard", Context.MODE_PRIVATE)

    fun policy(c: Context): String =
        prefs(c).getString("policy", null) ?: Native.defaultPolicy().also {
            prefs(c).edit().putString("policy", it).apply()
        }

    /** Returns false if Rust rejected the JSON. */
    fun save(c: Context, json: String): Boolean {
        val clean = Native.normalize(json)
        if (clean.isEmpty()) return false
        prefs(c).edit().putString("policy", clean).apply()
        return true
    }

    fun unlock(c: Context) {
        prefs(c).edit().putLong("unlocked_until", System.currentTimeMillis() + UNLOCK_MINUTES * 60_000).apply()
    }

    fun lock(c: Context) {
        prefs(c).edit().putLong("unlocked_until", 0).apply()
    }

    fun isUnlocked(c: Context) = System.currentTimeMillis() < prefs(c).getLong("unlocked_until", 0)
}
