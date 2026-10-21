package com.gamecontrol.guard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import org.json.JSONObject
import java.util.Calendar

class GuardService : AccessibilityService() {

    /** Screens where the child could switch us off; scanned for our app name. */
    private val sensitive = setOf(
        "com.android.settings",
        "com.miui.securitycenter",
        "com.google.android.permissioncontroller",
    )
    private val kidsPackage = "com.google.android.apps.youtube.kids"

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val isWindowChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        // Content changes only matter on settings screens (the list loads after the window opens).
        if (!isWindowChange && pkg !in sensitive) return

        val ctx = JSONObject()
            .put("package", pkg)
            .put("is_game", isGame(pkg))
            .put("is_launcher", pkg == launcherPackage())
            .put("minute_of_day", minuteOfDay())
            .put("unlocked", Store.isUnlocked(this))
            .put("mentions_us", pkg in sensitive && screenMentionsUs())

        when (Native.decide(Store.policy(this), ctx.toString())) {
            Native.BLOCK -> block()
            Native.REDIRECT_YOUTUBE_KIDS -> openYoutubeKids()
        }
    }

    private fun block() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        Toast.makeText(this, R.string.blocked, Toast.LENGTH_SHORT).show()
    }

    private fun openYoutubeKids() {
        val intent = packageManager.getLaunchIntentForPackage(kidsPackage)
        if (intent == null) {
            // Play Store is blocked, so a parent has to install it.
            Toast.makeText(this, R.string.kids_missing, Toast.LENGTH_LONG).show()
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun isGame(pkg: String): Boolean = try {
        packageManager.getApplicationInfo(pkg, 0).category == ApplicationInfo.CATEGORY_GAME
    } catch (_: Exception) {
        false
    }

    private fun launcherPackage(): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    }

    private fun screenMentionsUs(): Boolean {
        val root = rootInActiveWindow ?: return false
        return root.findAccessibilityNodeInfosByText(getString(R.string.app_name)).isNotEmpty()
    }

    private fun minuteOfDay(): Int {
        val now = Calendar.getInstance()
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    }

    override fun onInterrupt() {}
}
