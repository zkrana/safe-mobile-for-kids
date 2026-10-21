package com.gamecontrol.guard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
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

        lightSystemBars()
        val hasPin = JSONObject(Store.policy(this)).optString("pin_hash").isNotEmpty()
        val pin = pinField(getString(R.string.pin_hint))
        val confirm = pinField(getString(R.string.confirm_pin_hint))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Pal.BG)
            setPadding(dp(32), dp(24), dp(32), dp(24))

            addView(text("🛡️", 34f).apply {
                gravity = Gravity.CENTER
                background = rounded(Pal.SURFACE, 36)
                layoutParams = LinearLayout.LayoutParams(dp(72), dp(72))
            })
            addView(text(getString(if (hasPin) R.string.enter_pin else R.string.create_pin), 22f, Pal.TEXT, true)
                .apply { setPadding(0, dp(20), 0, dp(4)) })
            addView(caption(getString(if (hasPin) R.string.enter_pin_hint else R.string.create_pin_hint))
                .apply { gravity = Gravity.CENTER })
            addView(pin)
            if (!hasPin) addView(confirm)
            addView(primaryButton(getString(if (hasPin) R.string.unlock else R.string.set_pin)) {
                if (hasPin) tryUnlock(pin.text.toString()) else createPin(pin.text.toString(), confirm.text.toString())
            }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(20) })
        }
        setContentView(root)
    }

    private fun pinField(hint: String): EditText = editField(hint).apply {
        background = rounded(Pal.SURFACE, 12)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        filters = arrayOf(InputFilter.LengthFilter(12))
        gravity = Gravity.CENTER
        textSize = 22f
        letterSpacing = 0.3f
        (layoutParams as LinearLayout.LayoutParams).topMargin = dp(16)
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
