package com.reach_android.ui.support

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.reach_android.*
import com.reach_android.model.ConnectionStatus
import com.reach_android.util.*
import com.reach_android.ui.ConditionalBackFragment
import com.reach_android.ui.RemoteSupportViewModel
import com.reach_android.ui.views.SupportView
import kotlinx.android.synthetic.main.fragment_support_device_logs.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive

class SupportDeviceFragment : Fragment(R.layout.fragment_support_device_logs), ConditionalBackFragment {
    private val rsViewModel: RemoteSupportViewModel by activityViewModels()
    private val supportViewModel: SupportViewModel by activityViewModels()
    private var connectionJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Listen to logs
        logView.text = logView.cachedLogs
        rsViewModel.logger.logs.subscribe(viewLifecycleOwner) {
            logView.logMessage(it)
        }

        supportViewModel.deviceViewState.subscribe(this, Lifecycle.State.STARTED) {
            switchView(it)
        }
        switchView(supportViewModel.deviceViewState.value)

        supportViewModel.selectedDevice.subscribe(this, Lifecycle.State.STARTED) {
            if (it != null) {
                supportViewModel.deviceViewState.value = DeviceViewState.DEVICE_SELECTED
            } else {
                supportViewModel.deviceViewState.value = DeviceViewState.NO_DEVICE
            }

            observeDevice()
        }
        observeDevice()


        (view as? SupportView)?.setOnButtonClickListener {
            when (supportViewModel.deviceViewState.value) {
                DeviceViewState.NO_DEVICE -> {
                    findNavController().navigate(
                        SupportDeviceFragmentDirections
                            .actionSupportDeviceFragmentToDeviceListFragment(true)
                    )
                }
                DeviceViewState.DEVICE_SELECTED -> {
                    findNavController().navigate(
                        SupportDeviceFragmentDirections
                            .actionSupportDeviceFragmentToSelectedDeviceFragment(
                                fromSupport = true,
                                viewParameters = true
                            )
                    )
                }
            }
        }
    }

    override fun onBackPressed(): Boolean = false

    private fun switchView(state: DeviceViewState) {
        when (state) {
            DeviceViewState.NO_DEVICE -> if (deviceViewSwitcher.nextView.id == R.id.devicePlaceholder) {
                deviceViewSwitcher.showNext()
                (view as? SupportView)?.updateButtonText(false)
            }
            DeviceViewState.DEVICE_SELECTED -> if (deviceViewSwitcher.nextView.id == R.id.logView) {
                deviceViewSwitcher.showNext()
                (view as? SupportView)?.updateButtonText(true)
            }
        }
    }

    private fun observeDevice() {
        connectionJob?.cancel()
        val status = supportViewModel.selectedDevice.value?.connectionStatusObservable
        val title = supportViewModel.selectedDevice.value?.name ?: "No Device Connected"
        activity.showActionBar(title)
        connectionJob = status?.subscribe(this, Lifecycle.State.STARTED) {
            if (!isActive) return@subscribe
            when (it) {
                ConnectionStatus.Disconnected -> {
                    supportViewModel.deviceViewState.value = DeviceViewState.NO_DEVICE
                }
                ConnectionStatus.Connecting -> {
                    supportViewModel.deviceViewState.value = DeviceViewState.DEVICE_SELECTED
                }
                ConnectionStatus.Connected -> {
                    supportViewModel.deviceViewState.value = DeviceViewState.DEVICE_SELECTED
                }
            }
        }
    }
}