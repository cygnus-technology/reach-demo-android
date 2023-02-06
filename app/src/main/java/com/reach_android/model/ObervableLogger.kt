package com.reach_android.model

import com.cygnusreach.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Broadcasts remote support log messages
 */
class ObservableLogger(
    private val scope: CoroutineScope
) : Logger() {

    private val mutableText = MutableSharedFlow<String>()
    val logs: SharedFlow<String> = mutableText.asSharedFlow()

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
        scope.launch {
            mutableText.emit(msg)
        }
    }
}