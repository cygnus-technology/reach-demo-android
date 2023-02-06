package com.reach_android.ui.views

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.button.MaterialButton
import com.reach_android.R

class SupportView(
    context: Context,
    attributes: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : ConstraintLayout(context, attributes, defStyleAttr, defStyleRes) {
    private val buttonSize: Int
    private val buttonIcon: Drawable?
    private val pageTitle: String?
    private val buttonText: String?
    private val alternateButtonText: String?
    private var clickListener: OnClickListener? = null

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
            R.styleable.SupportView,
            defStyleAttr, defStyleRes
        )
        try {
            buttonText = attrs.getString(R.styleable.SupportView_buttonText)
            alternateButtonText = attrs.getString(R.styleable.SupportView_alternateButtonText)
            buttonIcon = attrs.getDrawable(R.styleable.SupportView_buttonIcon)
            buttonSize = attrs.getDimensionPixelSize(
                R.styleable.SupportView_buttonIconSize,
                // https://developer.android.com/training/multiscreen/screendensities#dips-pels
                (24f * resources.displayMetrics.density + .5).toInt()
            )
            pageTitle = attrs.getString(R.styleable.SupportView_title)

        } finally {
            attrs.recycle()
        }
    }

    override fun onFinishInflate() {
        val children = Array(childCount, ::getChildAt)
        this.detachAllViewsFromParent()

        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val template = inflater.inflate(R.layout.support_view_layout, this, true)

        template.findViewById<TextView>(R.id.lbl_title).text = pageTitle
        template.findViewById<MaterialButton>(R.id.actionButton).apply {
            text = buttonText

            buttonIcon?.let {
                icon = it
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                iconSize = buttonSize
            }
            setOnClickListener {
                clickListener?.onClick(this)
            }
        }

        val container = template.findViewById<ConstraintLayout>(R.id.content_container)
        children.forEach { view -> container.addView(view) }
        super.onFinishInflate()
    }

    fun setOnButtonClickListener(listener: OnClickListener) {
        clickListener = listener
    }

    fun updateButtonText(alternate: Boolean = false) {
        findViewById<Button>(R.id.actionButton)?.let {
            it.text = when (alternate) {
                true -> alternateButtonText ?: buttonText ?: ""
                false -> buttonText ?: buttonText ?: ""
            }
        }
    }

    fun updateButtonBackground(alternate: Boolean = false) {
        findViewById<Button>(R.id.actionButton)?.let {
            val color = when (alternate) {
                true -> R.color.md_theme_dark_error
                false -> R.color.md_theme_dark_secondary
            }
            it.backgroundTintList = AppCompatResources.getColorStateList(context, color)
        }
    }

    fun setButtonText(text: String) {
        findViewById<Button>(R.id.actionButton)?.let {
            it.text = text
        }
    }

    fun setButtonText(@StringRes text: Int) {
        findViewById<Button>(R.id.actionButton)?.let {
            it.text = resources.getString(text)
        }
    }

    fun setButtonText(@StringRes text: Int, vararg formatArgs: Any) {
        findViewById<Button>(R.id.actionButton)?.let {
            it.text = resources.getString(text, *formatArgs)
        }
    }
}

