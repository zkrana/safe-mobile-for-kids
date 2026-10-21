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
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Parent control panel. Only reachable while the PIN unlock is active. Changes save instantly. */
class SettingsActivity : Activity() {

    private lateinit var policy: JSONObject
    private lateinit var statusLine: TextView
    private lateinit var gamesValue: TextView
    private lateinit var startValue: TextView
    private lateinit var endValue: TextView
    private lateinit var blocked: EditText
    private lateinit var allowed: EditText
    private lateinit var advanced: View
    private lateinit var accStep: (Boolean) -> Unit
    private lateinit var adminStep: (Boolean) -> Unit
    private val switches = LinkedHashMap<String, Switch>()
    private var start = 21 * 60
    private var end = 7 * 60
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Store.isUnlocked(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        lightSystemBars()
        policy = JSONObject(Store.policy(this))
        build()
    }

    override fun onResume() {
        super.onResume()
        if (!ready) return
        if (!Store.isUnlocked(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        refreshStatus()
    }

    override fun onPause() {
        super.onPause()
        if (ready) save()
    }

    private fun build() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pal.BG)
        }

        statusLine = text("", 14f, Pal.WARN, true)
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(8))
            addView(text(getString(R.string.app_name), 28f, Pal.TEXT, true))
            addView(statusLine)
        })

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(24))
        }
        content.addView(sectionLabel(getString(R.string.section_setup)))
        content.addView(setupGroup())
        content.addView(sectionLabel(getString(R.string.section_block)))
        content.addView(blockGroup())
        content.addView(sectionLabel(getString(R.string.section_youtube)))
        content.addView(youtubeGroup())
        content.addView(sectionLabel(getString(R.string.section_games)))
        content.addView(gamesGroup())
        content.addView(sectionLabel(getString(R.string.section_bedtime)))
        content.addView(bedtimeGroup())
        content.addView(sectionLabel(getString(R.string.section_advanced)))
        content.addView(advancedGroup())
        content.addView(sectionLabel(getString(R.string.section_protection)))
        content.addView(protectionGroup())
        root.addView(ScrollView(this).apply {
            addView(content)
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        })

        root.addView(LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), dp(16))
            addView(primaryButton(getString(R.string.lock_now)) {
                save()
                Store.lock(this@SettingsActivity)
                finish()
            })
        })
        setContentView(root)

        // Wire auto-save only now, so filling in the initial values does not trigger it.
        switches.values.forEach { it.setOnCheckedChangeListener { _, _ -> save() } }
        ready = true
    }

    private fun toggle(key: String, title: Int, subtitle: Int, default: Boolean = true): View {
        val (row, sw) = switchRow(getString(title), getString(subtitle), policy.optBoolean(key, default))
        switches[key] = sw
        return row
    }

    private fun setupGroup(): View {
        val (accRow, acc) = stepRow(getString(R.string.step_accessibility), getString(R.string.step_accessibility_sub)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        val (adminRow, admin) = stepRow(getString(R.string.step_admin), getString(R.string.step_admin_sub)) { requestAdmin() }
        val (autoRow, _) = navRow(getString(R.string.step_autostart), "") { openAutostart() }
        accStep = acc
        adminStep = admin
        return group(accRow, adminRow, autoRow)
    }

    private fun blockGroup(): View = group(
        toggle("block_games", R.string.block_games, R.string.block_games_sub),
        toggle("block_browsers", R.string.block_browsers, R.string.block_browsers_sub),
        toggle("block_play_store", R.string.block_play_store, R.string.block_play_store_sub),
        toggle("block_app_stores", R.string.block_app_stores, R.string.block_app_stores_sub),
        toggle("block_installers", R.string.block_installers, R.string.block_installers_sub),
    )

    private fun youtubeGroup(): View =
        group(toggle("youtube_redirect", R.string.youtube_redirect, R.string.youtube_redirect_sub))

    private fun gamesGroup(): View {
        val (row, value) = navRow(getString(R.string.uninstall_games), "") {
            save()
            startActivity(Intent(this, GamesActivity::class.java))
        }
        gamesValue = value
        return group(row)
    }

    private fun bedtimeGroup(): View {
        val bed = policy.optJSONObject("bedtime") ?: JSONObject()
        start = bed.optInt("start", start)
        end = bed.optInt("end", end)
        val (onRow, sw) = switchRow(getString(R.string.bedtime_on), getString(R.string.bedtime_on_sub), bed.optBoolean("enabled", false))
        switches["bedtime_enabled"] = sw
        val (startRow, sv) = navRow(getString(R.string.bedtime_start), fmt(start)) {
            pickTime(start) { start = it; showTimes(); save() }
        }
        val (endRow, ev) = navRow(getString(R.string.bedtime_end), fmt(end)) {
            pickTime(end) { end = it; showTimes(); save() }
        }
        startValue = sv
        endValue = ev
        return group(onRow, startRow, endRow)
    }

    private fun advancedGroup(): View {
        blocked = listEditor(policy.optJSONArray("blocked_packages"))
        allowed = listEditor(policy.optJSONArray("allowed_packages"))
        advanced = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, 0, 0, dp(14))
            addView(text(getString(R.string.blocked_list), 14f, Pal.TEXT, true))
            addView(caption(getString(R.string.package_hint)))
            addView(blocked)
            addView(text(getString(R.string.allowed_list), 14f, Pal.TEXT, true).apply { setPadding(0, dp(16), 0, 0) })
            addView(caption(getString(R.string.package_hint)))
            addView(allowed)
        }
        val (row, _) = navRow(getString(R.string.custom_lists), "") {
            advanced.visibility = if (advanced.visibility == View.GONE) View.VISIBLE else View.GONE
        }
        return group(row, advanced)
    }

    private fun protectionGroup(): View {
        val (pinRow, _) = navRow(getString(R.string.change_pin), "") { changePin() }
        return group(toggle("protect_settings", R.string.protect_settings, R.string.protect_settings_sub), pinRow)
    }

    private fun listEditor(items: JSONArray?): EditText = editField(lines = 4).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        typeface = android.graphics.Typeface.MONOSPACE
        setHorizontallyScrolling(false)
        setText((0 until (items?.length() ?: 0)).joinToString("\n") { items!!.getString(it) })
    }

    private fun lines(edit: EditText) = JSONArray(
        edit.text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    )

    private fun showTimes() {
        startValue.text = fmt(start)
        endValue.text = fmt(end)
    }

    private fun fmt(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

    private fun pickTime(current: Int, done: (Int) -> Unit) {
        TimePickerDialog(this, { _, h, m -> done(h * 60 + m) }, current / 60, current % 60, true).show()
    }

    private fun save() {
        switches.forEach { (key, sw) -> if (key != "bedtime_enabled") policy.put(key, sw.isChecked) }
        policy.put(
            "bedtime",
            JSONObject().put("enabled", switches["bedtime_enabled"]?.isChecked ?: false).put("start", start).put("end", end)
        )
        policy.put("blocked_packages", lines(blocked))
        policy.put("allowed_packages", lines(allowed))
        if (!Store.save(this, policy.toString())) {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun changePin() {
        val input = editField(getString(R.string.pin_hint)).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            gravity = Gravity.CENTER
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.change_pin)
            .setView(LinearLayout(this).apply {
                setPadding(dp(20), dp(8), dp(20), 0)
                addView(input)
            })
            .setPositiveButton(R.string.save) { _, _ ->
                val updated = Native.setPin(Store.policy(this), input.text.toString(), UUID.randomUUID().toString())
                if (updated.isEmpty() || !Store.save(this, updated)) {
                    Toast.makeText(this, R.string.pin_invalid, Toast.LENGTH_SHORT).show()
                } else {
                    // Keep the in-memory copy in step so a later save does not restore the old hash.
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
        accStep(accessibility)
        adminStep(admin)

        statusLine.text = getString(if (accessibility) R.string.protection_active else R.string.setup_needed)
        statusLine.setTextColor(if (accessibility) Pal.OK else Pal.WARN)

        val count = Games.installed(this).size
        gamesValue.text = if (count == 0) "" else count.toString()
    }
}
