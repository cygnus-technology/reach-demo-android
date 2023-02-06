package com.reach_android.ui.views

import android.content.Context
import android.os.Handler
import android.util.AttributeSet
import com.google.android.material.textview.MaterialTextView
import java.text.SimpleDateFormat
import java.util.*

class LogView(context: Context, attrs: AttributeSet) : MaterialTextView(context, attrs) {

    private val formatter by lazy {
        SimpleDateFormat("h:mm a", Locale.US)
    }

    var cachedLogs = ""

    fun logMessage(text: String) = Handler(context.mainLooper).post {
        val date = Calendar.getInstance().time
        val formattedDate = formatter.format(date)
        val text = "$formattedDate: $text\n"
        append(text)
        cachedLogs += text
        val top = layout?.getLineTop(lineCount) ?: return@post
        val scrollAmount: Int = top - height
        if (scrollAmount > 0) scrollTo(0, scrollAmount)
        else scrollTo(0, 0)
    }
}