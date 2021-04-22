package com.reach_android.ui.views

import android.content.Context
import android.os.Handler
import android.text.method.ScrollingMovementMethod
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.reach_android.R
import java.text.SimpleDateFormat
import java.util.*

class LogView(context: Context, attrs: AttributeSet) : AppCompatTextView(context, attrs) {

    private val formatter by lazy {
        SimpleDateFormat("h:mm a", Locale.US)
    }

    init {
        setBackgroundResource(R.drawable.edit_text_background)
        isVerticalScrollBarEnabled = true
        movementMethod = ScrollingMovementMethod()
        textSize = 12f
        setPadding(15, 15, 15, 15)
        requestLayout()
    }

    fun logMessage(text: String) = Handler(context.mainLooper).post {
        val date = Calendar.getInstance().time
        val formattedDate = formatter.format(date)
        append("$formattedDate: $text\n")
        val scrollAmount: Int = layout.getLineTop(lineCount) - height
        if (scrollAmount > 0) scrollTo(0, scrollAmount)
        else scrollTo(0, 0)
    }
}