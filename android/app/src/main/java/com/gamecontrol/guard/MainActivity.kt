package com.gamecontrol.guard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import org.json.JSONObject
import java.util.UUID

/** PIN gate. First launch sets the PIN; later launches ask for it. */
class MainActivity : Activity() {

    companion object {
        private var fails = 0
        private var lockedUntil = 0L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Store.isUnlocked(this)) return openSettings()

        val hasPin = JSONObject(Store.policy(this)).optString("pin_hash").isNotEmpty()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
        }
        root.addView(label(getString(R.string.app_name), 26f, bold = true))
        root.addView(label(getString(if (hasPin) R.string.enter_pin else R.string.create_pin)))

        val pin = pinField(getString(R.string.pin_hint)).also { root.addView(it) }
        val confirm = pinField(getString(R.string.confirm_pin_hint))
        if (!hasPin) root.addView(confirm)

        root.addView(button(getString(if (hasPin) R.string.unlock else R.string.set_pin)) {
            if (hasPin) tryUnlock(pin.text.toString()) else createPin(pin.text.toString(), confirm.text.toString())
        })
        setContentView(root)
    }

    private fun pinField(hint: String) = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun createPin(pin: String, confirm: String) {
        if (pin != confirm) return toast(R.string.pin_mismatch)
        val updated = Native.setPin(Store.policy(this), pin, UUID.randomUUID().toString())
        if (updated.isEmpty() || !Store.save(this, updated)) return toast(R.string.pin_invalid)
        Store.unlock(this)
        openSettings()
    }

    private fun tryUnlock(pin: String) {
        if (System.currentTimeMillis() < lockedUntil) return toast(R.string.too_many_tries)
        if (Native.checkPin(Store.policy(this), pin)) {
            fails = 0
            Store.unlock(this)
            openSettings()
        } else {
            if (++fails >= 5) {
                fails = 0
                lockedUntil = System.currentTimeMillis() + 60_000
            }
            toast(R.string.wrong_pin)
        }
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
        finish()
    }
}
