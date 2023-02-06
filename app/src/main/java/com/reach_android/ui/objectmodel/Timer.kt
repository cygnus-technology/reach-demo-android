package com.reach_android.ui.objectmodel

import android.os.CountDownTimer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class Timer(
    private val lifecycle: Lifecycle,
    private val callback: () -> Unit
) : LifecycleEventObserver, Closeable {
    private var eta: Long = 0
    private var expireTime: Long = 0
    private var timer: CountDownTimer? = null

    init {
        lifecycle.addObserver(this)
    }

    fun start(eta: Long) {
        timer?.cancel()
        this.eta = eta
        expireTime = System.currentTimeMillis() + eta
        timer = object : CountDownTimer(eta, eta) {
            override fun onTick(millisUntilFinished: Long) {}

            override fun onFinish() {
                callback()
                isRunning = false
            }
        }.start()
        isRunning = true
    }

    @Volatile
    var isRunning: Boolean = false
        private set

    fun restart() {
        start(eta)
    }

    fun stop() {
        timer?.cancel()
        isRunning = false
    }

    override fun close() {
        stop()
        lifecycle.removeObserver(this)
    }

    private fun onResume() {
        val remaining = expireTime - System.currentTimeMillis()
        if (remaining <= 0) {
            callback()
        } else {
            start(remaining)
        }
    }

    private fun onPause() {
        timer?.cancel()
    }

    private fun onDestroy() {
        close()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_RESUME -> onResume()
            Lifecycle.Event.ON_PAUSE -> onPause()
            Lifecycle.Event.ON_DESTROY -> onDestroy()
            else -> {}
        }
    }
}