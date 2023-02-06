package com.reach_android.ui.hello

import android.view.View
import androidx.lifecycle.ViewModel
import com.reach_android.util.*
import com.reach_android.bluetooth.BleManager

class HelloViewModel : ViewModel() {

    fun startSupportSession(source: View) {
        source.navigate(HelloFragmentDirections.actionHelloFragmentToPinFragment())
    }

    fun connectToDevice(source: View) {
        if (arePermissionsGranted(BleManager.requiredPermissions)) {
            source.navigate(HelloFragmentDirections.actionHelloFragmentToDeviceListFragment())
        } else {
            source.navigate(HelloFragmentDirections.actionHelloFragmentToConnectPromptFragment())
        }
    }
}