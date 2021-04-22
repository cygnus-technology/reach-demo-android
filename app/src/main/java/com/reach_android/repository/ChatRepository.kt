package com.reach_android.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.reach_android.model.remotesupport.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ChatRepository {

    val messageList: LiveData<List<ChatMessage>> = MutableLiveData(ArrayList())

    fun getMessage(id: Int): ChatMessage? =
        (messageList.value as? ArrayList<ChatMessage>)?.firstOrNull { it.id == id }

    suspend fun newMessage(message: ChatMessage) {
        withContext(Dispatchers.Default) {
            val list = messageList.value as? ArrayList<ChatMessage>?: return@withContext
            list.add(message)
            (messageList as MutableLiveData<List<ChatMessage>>).postValue(list)
        }
    }

    /**
     * Replaces message with given ID in the list of messages if it exists, otherwise just adds it
     */
    suspend fun replaceMessage(id: Int, message: ChatMessage) {
        withContext(Dispatchers.Default) {
            val list = messageList.value as? ArrayList<ChatMessage> ?: return@withContext
            val index = list.indexOfFirst { it.id == id }
            if (index >= 0) {
                list[index] = message
            } else {
                list.add(message)
            }
            (messageList as MutableLiveData<List<ChatMessage>>).postValue(list)
        }
    }

    suspend fun deleteMessage(id: Int) {
        withContext(Dispatchers.Default) {
            val list = messageList.value as? ArrayList<ChatMessage> ?: return@withContext
            list.removeAll { it.id == id }
            (messageList as MutableLiveData<List<ChatMessage>>).postValue(list)
        }
    }
}