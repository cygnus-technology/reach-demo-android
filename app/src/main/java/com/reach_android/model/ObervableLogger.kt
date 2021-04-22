package com.reach_android.model

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cygnusreach.Logger

/**
 * Broadcasts remote support log messages
 */
class ObservableLogger : Logger() {

    private val mutableText = MutableLiveData<String>()
    val logs: LiveData<String> = mutableText

    override fun info(TAG: String, msg: String) {
        super.info(TAG, msg)
        log(msg)
    }

    override fun warn(TAG: String, msg: String) {
        super.warn(TAG, msg)
        log(msg)
    }

    override fun error(TAG: String, msg: String) {
        log(msg)
        super.error(TAG, msg)
    }

    override fun debug(TAG: String, msg: String) {
        super.debug(TAG, msg)
        log(msg)
    }

    override fun trace(TAG: String, msg: String) {
        super.trace(TAG, msg)
        log( msg)
    }

    private fun log(msg: String) {
        mutableText.postValue(msg)
    }
}