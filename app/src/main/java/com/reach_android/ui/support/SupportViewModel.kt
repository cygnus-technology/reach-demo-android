package com.reach_android.ui.support

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlin.concurrent.fixedRateTimer

class SupportViewModel : ViewModel() {

    private val timerData = MutableLiveData(Unit)
    private val timer = fixedRateTimer("diagnosticTimer", true, 0, 5000) {
        timerData.postValue(Unit)
    }

    /**
     * Set to true if a user has selected to take a photo, false if video. We need to track
     * this for when a user is prompted to accept camera usage permissions
     */
    var capturePhoto = true

    /**
     * Emits every time the timer fires off. Used to tell when we should send diagnostic data
     * to the peer
     */
    val diagnosticTimer: LiveData<Unit> = timerData

    /**
     * File path used when capturing a photo/video from the camera activity
     */
    var capturedMediaFilePath: String? = null

    override fun onCleared() {
        super.onCleared()
        timer.cancel()
    }
}