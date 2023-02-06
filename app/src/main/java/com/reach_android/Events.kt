package com.reach_android

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

object Events {
    val onProductKeyUpdated = MutableSharedFlow<String?>()
}

fun tickerFlow(period: Long, initialDelay: Long = 0L) = flow {
    delay(initialDelay)
    while (currentCoroutineContext().isActive) {
        emit(Unit)
        delay(period)
    }
}