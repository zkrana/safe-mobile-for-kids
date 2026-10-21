package com.gamecontrol.guard

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Parent control panel. Only reachable while the PIN unlock is active. */
class SettingsActivity : Activity() {

    private lateinit var policy: JSONObject
    private lateinit var status: TextView
    private lateinit var games: Switch
    private lateinit var youtube: Switch
    private lateinit var protect: Switch
    private lateinit var bedtimeOn: Switch
    private lateinit var startBtn: Button
    private lateinit var endBtn: Button
    private lateinit var blocked: EditText
    private lateinit var allowed: EditText
    private var start = 21 * 60
    private var end = 7 * 60

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Store.isUnlocked(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        policy = JSONObject(Store.policy(this))
        build()
    }

    override fun onResume() {
        super.onResume()
        if (!::status.isInitialized) return
        if (!Store.isUnlocked(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        refreshStatus()
    }

    private fun build() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        setContentView(ScrollView(this).apply { addView(root) })

        root.addView(label(getString(R.string.app_name), 24f, bold = true))

        status = label("")
        root.addView(status)
        root.addView(button(getString(R.string.open_accessibility)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button(getString(R.string.enable_admin)) { requestAdmin() })
        root.addView(button(getString(R.string.open_autostart)) { openAutostart() })

        root.addView(label(getString(R.string.rules), 18f, bold = true))
        games = Switch(this).apply {
            text = getString(R.string.block_games)
            isChecked = policy.optBoolean("block_games", true)
        }
        youtube = Switch(this).apply {
            text = getString(R.string.youtube_redirect)
            isChecked = policy.optBoolean("youtube_redirect", true)
        }
        protect = Switch(this).apply {
            text = getString(R.string.protect_settings)
            isChecked = policy.optBoolean("protect_settings", true)
        }
        listOf(games, youtube, protect).forEach { root.addView(it) }

        root.addView(label(getString(R.string.bedtime), 18f, bold = true))
        val bed = policy.optJSONObject("bedtime") ?: JSONObject()
        start = bed.optInt("start", start)
        end = bed.optInt("end", end)
        bedtimeOn = Switch(this).apply {
            text = getString(R.string.bedtime_on)
            isChecked = bed.optBoolean("enabled", false)
        }
        startBtn = button("") { pickTime(start) { start = it; showTimes() } }
        endBtn = button("") { pickTime(end) { end = it; showTimes() } }
        showTimes()
        root.addView(bedtimeOn)
        root.addView(startBtn)
        root.addView(endBtn)

        root.addView(label(getString(R.string.blocked_list), 18f, bold = true))
        blocked = listEditor(policy.optJSONArray("blocked_packages"))
        root.addView(blocked)
        root.addView(label(getString(R.string.allowed_list), 18f, bold = true))
        allowed = listEditor(policy.optJSONArray("allowed_packages"))
        root.addView(allowed)

        root.addView(button(getString(R.string.save)) { save() })
        root.addView(button(getString(R.string.change_pin)) { changePin() })
        root.addView(button(getString(R.string.lock_now)) {
            Store.lock(this)
            finish()
        })
    }

    private fun listEditor(items: JSONArray?) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        minLines = 4
        setHorizontallyScrolling(false)
        setText((0 until (items?.length() ?: 0)).joinToString("\n") { items!!.getString(it) })
    }

    private fun lines(edit: EditText) = JSONArray(
        edit.text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    )

    private fun showTimes() {
        startBtn.text = getString(R.string.bedtime_start, fmt(start))
        endBtn.text = getString(R.string.bedtime_end, fmt(end))
    }

    private fun fmt(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

    private fun pickTime(current: Int, done: (Int) -> Unit) {
        TimePickerDialog(this, { _, h, m -> done(h * 60 + m) }, current / 60, current % 60, true).show()
    }

    private fun save() {
        policy.put("block_games", games.isChecked)
        policy.put("youtube_redirect", youtube.isChecked)
        policy.put("protect_settings", protect.isChecked)
        policy.put(
            "bedtime",
            JSONObject().put("enabled", bedtimeOn.isChecked).put("start", start).put("end", end)
        )
        policy.put("blocked_packages", lines(blocked))
        policy.put("allowed_packages", lines(allowed))
        val ok = Store.save(this, policy.toString())
        Toast.makeText(this, if (ok) R.string.saved else R.string.save_failed, Toast.LENGTH_SHORT).show()
    }

    private fun changePin() {
        val input = EditText(this).apply {
            hint = getString(R.string.pin_hint)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.change_pin)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val updated = Native.setPin(Store.policy(this), input.text.toString(), UUID.randomUUID().toString())
                if (updated.isEmpty() || !Store.save(this, updated)) {
                    Toast.makeText(this, R.string.pin_invalid, Toast.LENGTH_SHORT).show()
                } else {
                    // Keep the in-memory copy in step so a later Save does not restore the old hash.
                    policy = JSONObject(Store.policy(this))
                    Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private val adminComponent get() = ComponentName(this, AdminReceiver::class.java)

    private fun requestAdmin() {
        startActivity(
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_why))
        )
    }

    private fun openAutostart() {
        try {
            startActivity(
                Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                )
            )
        } catch (_: Exception) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
    }

    private fun refreshStatus() {
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val accessibility = enabledServices.contains("GuardService")
        val admin = getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent)
        fun mark(ok: Boolean) = if (ok) "✅" else "❌"
        status.text = getString(R.string.status, mark(accessibility), mark(admin))
    }
}
