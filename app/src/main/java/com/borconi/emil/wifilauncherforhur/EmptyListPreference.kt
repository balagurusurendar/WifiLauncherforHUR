package com.borconi.emil.wifilauncherforhur

import android.content.Context
import android.util.AttributeSet
import androidx.preference.MultiSelectListPreference

class EmptyListPreference : MultiSelectListPreference {
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun onClick() {
        if (entries == null) return

        super.onClick()
    }
}
