package com.gamecontrol.guard

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.TextView

fun Context.dp(n: Int) = (n * resources.displayMetrics.density).toInt()

fun Context.label(text: String, size: Float = 15f, bold: Boolean = false) = TextView(this).apply {
    this.text = text
    textSize = size
    if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    setPadding(0, dp(8), 0, dp(4))
}

fun Context.button(text: String, onClick: (View) -> Unit) = Button(this).apply {
    this.text = text
    setOnClickListener(onClick)
}
