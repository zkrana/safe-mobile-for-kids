package com.gamecontrol.guard

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView

/** Pick games and remove them in one go. Android asks for a confirmation per app. */
class GamesActivity : Activity() {

    private companion object { const val REQ_UNINSTALL = 7 }

    private lateinit var list: LinearLayout
    private lateinit var uninstallBtn: Button
    private val checks = LinkedHashMap<String, CheckBox>()
    private val queue = ArrayDeque<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Store.isUnlocked(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        lightSystemBars()
        build()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (!Store.isUnlocked(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun build() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pal.BG)
        }

        root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(12), dp(24), dp(4))
            addView(text("‹", 32f).apply {
                setPadding(dp(12), 0, dp(12), dp(4))
                setOnClickListener { finish() }
            })
            addView(text(getString(R.string.games_screen_title), 24f, Pal.TEXT, true))
        })

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(16))
            addView(caption(getString(R.string.games_info)).apply { setPadding(dp(4), 0, 0, dp(12)) })
        }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list)
        root.addView(ScrollView(this).apply {
            addView(content)
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        })

        uninstallBtn = dangerButton("") { startUninstall() }
            .apply { layoutParams = LinearLayout.LayoutParams(0, dp(48), 1.6f) }
        val selectAll = quietButton(getString(R.string.select_all)) { checks.values.forEach { it.isChecked = true } }
            .apply { layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(10) } }
        root.addView(LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), dp(16))
            addView(selectAll)
            addView(uninstallBtn)
        })
        setContentView(root)
    }

    private fun refresh() {
        list.removeAllViews()
        checks.clear()
        val apps = Games.installed(this)
        if (apps.isEmpty()) {
            list.addView(text(getString(R.string.games_none), 16f, Pal.MUTED).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(48), 0, 0)
            })
        } else {
            val rows = apps.map { app ->
                val box = CheckBox(this).apply { setOnCheckedChangeListener { _, _ -> updateButton() } }
                checks[app.pkg] = box
                LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(10), 0, dp(10))
                    addView(box)
                    addView(ImageView(context).apply {
                        setImageDrawable(app.icon)
                        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(12) }
                    })
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(text(app.label, 15.5f))
                        addView(caption(app.pkg))
                    })
                    setOnClickListener { box.toggle() }
                }
            }
            list.addView(group(*rows.toTypedArray<View>()))
        }
        updateButton()
    }

    private fun updateButton() {
        val n = checks.values.count { it.isChecked }
        uninstallBtn.text = getString(R.string.uninstall_selected, n)
        uninstallBtn.isEnabled = n > 0
        uninstallBtn.alpha = if (n > 0) 1f else 0.4f
    }

    private fun startUninstall() {
        queue.clear()
        queue.addAll(checks.filter { it.value.isChecked }.keys)
        uninstallNext()
    }

    private fun uninstallNext() {
        val pkg = queue.removeFirstOrNull() ?: return refresh()
        Store.unlock(this) // keep the installer unblocked while the dialogs are open
        startActivityForResult(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")), REQ_UNINSTALL)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_UNINSTALL) uninstallNext()
    }
}
