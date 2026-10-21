package com.gamecontrol.guard

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import org.json.JSONObject

data class AppEntry(val pkg: String, val label: String, val icon: Drawable)

object Games {
    /** User-installed games, plus any installed app on the blocked list. System apps cannot be removed. */
    @Suppress("DEPRECATION")
    fun installed(c: Context): List<AppEntry> {
        val pm = c.packageManager
        val listed = JSONObject(Store.policy(c)).optJSONArray("blocked_packages")
        val blocked = (0 until (listed?.length() ?: 0)).map { listed!!.getString(it) }.toSet()
        return pm.getInstalledApplications(0)
            .filter { it.packageName != c.packageName }
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .filter {
                it.category == ApplicationInfo.CATEGORY_GAME ||
                    (it.flags and ApplicationInfo.FLAG_IS_GAME) != 0 ||
                    it.packageName in blocked
            }
            .map { AppEntry(it.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .sortedBy { it.label.lowercase() }
    }
}
