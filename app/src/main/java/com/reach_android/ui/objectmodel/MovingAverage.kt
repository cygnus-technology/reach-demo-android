package com.reach_android.ui.objectmodel

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MovingAverage(window: Int, private val transform: (Int)->Int = { it }) {
    private var first = true
    private val lock = Mutex()
    private var ix = 0
    private val buffer = IntArray(window) { 0 }
    private val flow = MutableStateFlow(0)

    suspend fun emit(value: Int) {
        lock.withLock {
            update(value)
        }
    }

    private fun update(value: Int) {
        if (first) {
            first = false
            for (i in buffer.indices) {
                buffer[i] = value
            }
            flow.value = value
        } else {
            if(ix == Int.MAX_VALUE) ix = 0
            buffer[ix++.mod(buffer.size)] = value
            flow.value = transform(buffer.average().toInt())
        }
    }

    fun tryEmit(value: Int): Boolean {
        if (!lock.tryLock())
            return false

        try {
            update(value)
            return true
        } finally {
            lock.unlock()
        }
    }

    fun asStateFlow() = flow.asStateFlow()
}