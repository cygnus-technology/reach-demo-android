package com.reach_android.model.remotesupport

import com.cygnusreach.messages.IUserMessageTag


enum class ContentTag(override val tag: String) : IUserMessageTag {
    Image("image/jpeg"),
    Video("video/mp4")
}