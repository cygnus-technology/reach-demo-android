package com.reach_android.ui.devicelist

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.reach_android.R
import com.reach_android.App.Companion.LOCATION_PERMISSION_REQUEST_CODE
import com.reach_android.bluetooth.BleManager
import com.reach_android.model.BleDevice
import com.reach_android.ui.MainViewModel
import kotlinx.android.synthetic.main.fragment_device_list.*

/**
 * Initial view. Displays the [BleDevice] list recycler view
 */
class DeviceListFragment : Fragment(R.layout.fragment_device_list) {

    private val viewModel: DeviceListViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_device_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = DeviceListAdapter(::onRowClick)
        deviceList.adapter = adapter
        subscribe(adapter)
        refreshDevices.setOnClickListener { refresh() }

        if (BleManager.startScanning()) {
            refresh()
        } else {
            promptForLocationPermission()
        }
    }

    override fun onStop() {
        super.onStop()
        BleManager.stopScanning()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    promptForLocationPermission()
                } else {
                    BleManager.startScanning()
                    refresh()
                }
            }
        }
    }

    /**
     * Listens to view model changes and applies those changes to the adapter
     */
    private fun subscribe(adapter: DeviceListAdapter) {
        viewModel.listenToSearchQuery(mainViewModel.searchQuery)
        viewModel.devices.observe(viewLifecycleOwner) { list ->
            adapter.currentList.forEach {
                it.rssiObservable.removeObservers(viewLifecycleOwner)
            }

            list.forEachIndexed { index, element ->
                element.rssiObservable.observe(viewLifecycleOwner) {
                    adapter.notifyItemChanged(index, element)
                }
            }

            adapter.submitList(list)
        }
    }

    private fun refresh() {
        progressBar.visibility = View.VISIBLE
        val refresh = viewModel.refreshDevices()
        refresh.observe(viewLifecycleOwner) {
            refresh.removeObservers(viewLifecycleOwner)
            progressBar?.visibility = View.GONE
        }
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
        nav.navigate(R.id.action_deviceListFragment_to_selectedDeviceFragment)
    }

    /**
     * Prompt user an explanation of why location services are needed and then perform the system
     * request
     */
    private fun promptForLocationPermission() {
        AlertDialog.Builder(requireContext())
                .setTitle(R.string.location_permission_title)
                .setMessage(R.string.location_permission_message)
                .setPositiveButton(R.string.ok) { dialog, i ->
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
                }
                .show()
    }
}