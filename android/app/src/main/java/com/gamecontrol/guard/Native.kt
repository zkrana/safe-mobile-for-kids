package com.gamecontrol.guard

/** Bridge to the Rust engine (libguard_core.so). */
object Native {
    const val ALLOW = 0
    const val BLOCK = 1
    const val REDIRECT_YOUTUBE_KIDS = 2

    init { System.loadLibrary("guard_core") }

    @JvmStatic external fun defaultPolicy(): String
    @JvmStatic external fun normalize(policy: String): String
    @JvmStatic external fun setPin(policy: String, pin: String, salt: String): String
    @JvmStatic external fun checkPin(policy: String, pin: String): Boolean
    @JvmStatic external fun decide(policy: String, ctx: String): Int
}
