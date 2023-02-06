package com.reach_android.ui.connectprompt

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.reach_android.util.*
import com.reach_android.R
import com.reach_android.bluetooth.BleManager
import kotlinx.android.synthetic.main.fragment_connect_device_system_prompt.*
import kotlinx.coroutines.launch

class ConnectPromptFragment : Fragment(R.layout.fragment_connect_device_system_prompt) {
    private lateinit var permissionRequest: FragmentPermissionRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionRequest = FragmentPermissionRequest(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btn_continue.setOnClickListener {
            lifecycleScope.launch {
                if (permissionRequest.requestPermissions(BleManager.requiredPermissions)) {
                    view.findNavController()
                        .navigate(ConnectPromptFragmentDirections.actionConnectPromptFragmentToDeviceListFragment())
                } else {
                    view.findNavController()
                        .navigate(ConnectPromptFragmentDirections.actionConnectPromptFragmentToHelloFragment())
                }
            }
        }
    }
}