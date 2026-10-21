package com.gamecontrol.guard

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Light, flat palette: white page, pale grey groups, one blue accent. */
object Pal {
    val BG = Color.WHITE
    val SURFACE = 0xFFF5F6F8.toInt()
    val LINE = 0xFFE6E8EC.toInt()
    val TEXT = 0xFF111827.toInt()
    val MUTED = 0xFF6B7280.toInt()
    val PRIMARY = 0xFF2563EB.toInt()
    val OK = 0xFF16A34A.toInt()
    val WARN = 0xFFD97706.toInt()
    val DANGER = 0xFFDC2626.toInt()
}

fun Context.dp(n: Int) = (n * resources.displayMetrics.density).toInt()

@Suppress("DEPRECATION")
fun Activity.lightSystemBars() {
    window.statusBarColor = Pal.BG
    window.navigationBarColor = Pal.BG
    window.decorView.systemUiVisibility =
        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
}

fun Context.rounded(fill: Int, radiusDp: Int, stroke: Int? = null) = GradientDrawable().apply {
    setColor(fill)
    cornerRadius = dp(radiusDp).toFloat()
    if (stroke != null) setStroke(dp(1), stroke)
}

fun Context.text(value: String, sp: Float = 15f, color: Int = Pal.TEXT, bold: Boolean = false) =
    TextView(this).apply {
        text = value
        textSize = sp
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

fun Context.caption(value: String) = text(value, 12.5f, Pal.MUTED)

fun Context.sectionLabel(value: String) = text(value.uppercase(), 12f, Pal.MUTED, true).apply {
    letterSpacing = 0.08f
    setPadding(dp(4), dp(24), 0, dp(8))
}

/** Rows on a pale rounded surface, separated by hairlines. */
fun Context.group(vararg rows: View): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = rounded(Pal.SURFACE, 14)
    setPadding(dp(16), 0, dp(16), 0)
    rows.forEachIndexed { i, row ->
        if (i > 0) {
            addView(View(context).apply {
                setBackgroundColor(Pal.LINE)
                layoutParams = LinearLayout.LayoutParams(MATCH, dp(1))
            })
        }
        addView(row)
    }
}

private fun Context.textColumn(title: String, subtitle: String?) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(12) }
    addView(text(title, 15.5f))
    if (subtitle != null) addView(caption(subtitle))
}

private fun Context.row(): LinearLayout = LinearLayout(this).apply {
    gravity = Gravity.CENTER_VERTICAL
    setPadding(0, dp(13), 0, dp(13))
}

fun Context.switchRow(title: String, subtitle: String, checked: Boolean): Pair<View, Switch> {
    val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
    val sw = Switch(this).apply {
        isChecked = checked
        thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Color.WHITE))
        trackTintList = ColorStateList(states, intArrayOf(Pal.PRIMARY, 0xFFD1D5DB.toInt()))
    }
    val r = row().apply {
        addView(textColumn(title, subtitle))
        addView(sw)
        setOnClickListener { sw.toggle() }
    }
    return r to sw
}

/** Title on the left, a value and a chevron on the right. */
fun Context.navRow(title: String, value: String, onClick: () -> Unit): Pair<View, TextView> {
    val valueView = text(value, 15f, Pal.MUTED)
    val r = row().apply {
        addView(textColumn(title, null))
        addView(valueView)
        addView(text("  ›", 20f, Pal.MUTED))
        setOnClickListener { onClick() }
    }
    return r to valueView
}

/** A setup step. Call the returned function to flip it between "On" and "Turn on". */
fun Context.stepRow(title: String, subtitle: String, onClick: () -> Unit): Pair<View, (Boolean) -> Unit> {
    val status = text("", 14f, Pal.WARN, true)
    val r = row().apply {
        addView(textColumn(title, subtitle))
        addView(status)
        addView(text("  ›", 20f, Pal.MUTED))
        setOnClickListener { onClick() }
    }
    val update: (Boolean) -> Unit = { ok ->
        status.text = getString(if (ok) R.string.status_on else R.string.status_off)
        status.setTextColor(if (ok) Pal.OK else Pal.WARN)
    }
    update(false)
    return r to update
}

private fun Context.styledButton(label: String, fill: Int, textColor: Int, onClick: (View) -> Unit) =
    Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(textColor)
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(fill, 12)
        stateListAnimator = null
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(48))
        setOnClickListener(onClick)
    }

fun Context.primaryButton(label: String, onClick: (View) -> Unit) =
    styledButton(label, Pal.PRIMARY, Color.WHITE, onClick)

fun Context.dangerButton(label: String, onClick: (View) -> Unit) =
    styledButton(label, Pal.DANGER, Color.WHITE, onClick)

fun Context.quietButton(label: String, onClick: (View) -> Unit) =
    styledButton(label, Pal.SURFACE, Pal.TEXT, onClick)

fun Context.editField(hint: String? = null, lines: Int = 1) = EditText(this).apply {
    if (hint != null) this.hint = hint
    background = rounded(Pal.BG, 10, Pal.LINE)
    setPadding(dp(12), dp(10), dp(12), dp(10))
    setTextColor(Pal.TEXT)
    textSize = 14f
    minLines = lines
    gravity = if (lines > 1) Gravity.TOP else Gravity.CENTER_VERTICAL
    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) }
}
