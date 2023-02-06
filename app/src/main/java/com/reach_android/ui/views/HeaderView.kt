package com.reach_android.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.constraintlayout.widget.ConstraintLayout
import com.reach_android.R

class HeaderView(
    context: Context,
    attributes: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : ConstraintLayout(context, attributes, defStyleAttr, defStyleRes) {

    constructor(context: Context) : this(context, null, 0, 0)
    constructor(context: Context, attributes: AttributeSet?) : this(context, attributes, 0, 0)
    constructor(context: Context, attributes: AttributeSet?, @AttrRes defStyleAttr: Int) : this(
        context,
        attributes,
        defStyleAttr,
        0
    )

    init {
        val attrs = context.theme.obtainStyledAttributes(
            attributes,
            R.styleable.HeaderView,
            defStyleAttr, defStyleRes
        )
        attrs.recycle()
    }

    override fun onFinishInflate() {
        val children = Array(childCount, ::getChildAt)
        this.detachAllViewsFromParent()

        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val template = inflater.inflate(R.layout.header_layout, this, true)

        val container = template.findViewById<ConstraintLayout>(R.id.content_container)
        children.forEach { view -> container.addView(view) }

        super.onFinishInflate()
    }


}