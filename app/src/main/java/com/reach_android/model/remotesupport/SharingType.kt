package com.reach_android.model.remotesupport

import com.cygnusreach.messages.Command
import com.cygnusreach.messages.WellKnownTagEncoder
import com.cygnusreach.messages.WellKnownTags

enum class SharingType(val value: Int) {
    Video(0), Screen(1);

    companion object {
        fun decode(data: Command) =
            if ((data.category != MessageCategory.StartSharing.value
                        && data.category != MessageCategory.StopSharing.value)
                || data.tag != WellKnownTags.Int.tag
            ) {
                null
            } else when (WellKnownTagEncoder.Int.decode(data.data)) {
                0 -> Video
                1 -> Screen
                else -> null
            }
    }
}