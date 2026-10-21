package com.gamecontrol.guard

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** Being an active device admin makes Android refuse to uninstall the app. */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Turning this off lets anyone uninstall Game Control."
}
