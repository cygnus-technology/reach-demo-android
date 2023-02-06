package com.reach_android.repository

import androidx.databinding.ObservableArrayList
import androidx.databinding.ObservableList
import com.reach_android.model.remotesupport.ChatMessage

object ChatRepository {

    private val messageListPrivate = ObservableArrayList<ChatMessage>()
    val messageList: ObservableList<ChatMessage> = messageListPrivate

    fun getMessage(id: Int): ChatMessage? = messageList.firstOrNull { it.id == id }

    fun newMessage(message: ChatMessage) {
        messageListPrivate.add(message)
    }

    fun reset() {
        messageListPrivate.clear()
    }

    /**
     * Replaces message with given ID in the list of messages if it exists, otherwise just adds it
     */
    fun replaceMessage(id: Int, message: ChatMessage) {
        val index = messageListPrivate.indexOfFirst { it.id == id }
        if (index >= 0) {
            messageListPrivate[index] = message
        } else {
            messageListPrivate.add(message)
        }
    }

    fun deleteMessage(id: Int) {
        messageListPrivate.removeAll { it.id == id }
    }
}