package com.reach_android.ui.devicelist

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.reach_android.*
import com.reach_android.bluetooth.BleManager
import com.reach_android.model.BleDevice
import com.reach_android.ui.MainViewModel
import com.reach_android.ui.objectmodel.Timer
import kotlinx.android.synthetic.main.fragment_device_list.*
import kotlinx.coroutines.launch
import com.reach_android.util.*

/**
 * Initial view. Displays the [BleDevice] list recycler view
 */
class DeviceListFragment : Fragment(R.layout.fragment_device_list) {
    private val viewModel: DeviceListViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val args: DeviceListFragmentArgs by navArgs()
    private val fromSupport get() = args.fromSupport
    private lateinit var adapter: DeviceListAdapter

    private lateinit var permissionRequest: FragmentPermissionRequest

    private lateinit var updateCountdown: Timer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionRequest = FragmentPermissionRequest(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity.showActionBar("Connect to a device")

        if (!fromSupport) {
            mainViewModel.disconnectDevice()
            mainViewModel.clearDevice()
        }

        adapter = DeviceListAdapter(::onRowClick)
        deviceList.adapter = adapter

        deviceRefresh.setOnRefreshListener {
            adapter.clear()
            lifecycleScope.launch {
                startScanning()
            }
        }

        updateCountdown = Timer(viewLifecycleOwner.lifecycle) {
            deviceRefresh?.isRefreshing = false
            BleManager.stopScanning()
            lifecycleScope.launch {
                viewModel.refresh()
            }
        }

        viewModel.displayedDevices.subscribe(viewLifecycleOwner, Lifecycle.State.STARTED) {
            adapter.submitList(it)
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                lifecycleScope.launch {
                    viewModel.updateSearchQuery(query ?: "")
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return true
            }
        })

        lifecycleScope.launch {
            startScanning()
        }
    }

    private suspend fun startScanning(duration: Long = 3000) {
        deviceRefresh?.isRefreshing = true
        if (!BleManager.startScanning()) {
            if (permissionRequest.requestPermissions(BleManager.requiredPermissions)) {
                BleManager.startScanning()
            }
        }
        updateCountdown.start(duration)
    }

    override fun onStop() {
        BleManager.stopScanning()
        super.onStop()
    }

    /**
     * Called when a device row is clicked
     */
    private fun onRowClick(device: BleDevice) {
        viewModel.selectDevice(device)

        // This can be called when using multiple fingers to tap which causes a crash due to
        // the nav controller trying to navigate twice. Check to make sure we haven't yet navigated
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.deviceListFragment) return
        nav.navigate(
            DeviceListFragmentDirections
                .actionDeviceListFragmentToSelectedDeviceFragment(fromSupport)
        )
    }
}