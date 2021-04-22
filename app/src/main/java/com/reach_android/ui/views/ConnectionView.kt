package com.reach_android.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.reach_android.R
import com.reach_android.model.ConnectionStatus

class ConnectionView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    var connectionStatus = ConnectionStatus.Connected
        set(value) {
            field = value
            setBackgroundResource(value.background)
            invalidate()
            requestLayout()
        }

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.ConnectionView,
            0, 0).apply {

            try {
                val raw = getInteger(R.styleable.ConnectionView_connectionStatus, 0)
                val status = ConnectionStatus.from(raw)?: return@apply
                connectionStatus = status
            } finally {
                recycle()
            }
        }
    }
}