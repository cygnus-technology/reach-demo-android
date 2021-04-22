package com.reach_android.ui.selecteddevice

import android.app.AlertDialog
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.reach_android.R
import com.reach_android.bluetooth.BleOperation
import com.reach_android.model.ConnectionStatus
import com.reach_android.ui.MainViewModel
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_selected_device.*

class SelectedDeviceFragment : Fragment() {

    private val viewModel: SelectedDeviceViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewModel.selectedDevice = mainViewModel.selectedDevice
        return inflater.inflate(R.layout.fragment_selected_device, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        deviceInfoLayout.visibility = View.GONE
        deviceDataList.adapter = SelectedDeviceAdapter(::onCharacteristicClick)
        connect()
        remoteSupportButton.setOnClickListener {
            findNavController().navigate(R.id.action_selectedDeviceFragment_to_pinFragment)
        }
        activity?.refreshButton?.setOnClickListener {
            readCharacteristics()
            Toast.makeText(requireContext(), R.string.selected_device_refresh, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStop() {
        super.onStop()
        activity?.refreshButton?.setOnClickListener(null)
    }

    private fun connect() {
        val adapter = deviceDataList.adapter as? SelectedDeviceAdapter
        activity?.reconnectingView?.visibility = View.GONE
        activity?.refreshButton?.visibility = View.GONE
        val connect = mainViewModel.connectDevice()
        connect.observe(viewLifecycleOwner) { result ->
            connect.removeObservers(viewLifecycleOwner)
            loading.visibility = View.GONE
            when (result) {
                is BleOperation.Result.Success -> {
                    deviceInfoLayout.visibility = View.VISIBLE
                    adapter?.submitList(viewModel.displayData)
                    observeDevice()
                }
                is BleOperation.Result.Failure -> {
                    showConnectError()
                }
            }
        }
    }

    private fun observeDevice() {
        // Observe connection status
        val status = mainViewModel.selectedDevice?.connectionStatusObservable
        status?.observe(viewLifecycleOwner) {
            when (it) {
                ConnectionStatus.Connected -> {
                    activity?.refreshButton?.visibility = View.VISIBLE
                    readCharacteristics()
                }
                ConnectionStatus.Disconnected -> {
                    activity?.reconnectingView?.visibility = View.VISIBLE
                    activity?.refreshButton?.visibility = View.GONE
                    val adapter = deviceDataList.adapter as? SelectedDeviceAdapter
                    adapter?.submitList(viewModel.displayData)

                    // Attempt to reconnect
                    val reconnect = mainViewModel.connectDevice(true)
                    reconnect.observe(viewLifecycleOwner) { result ->
                        reconnect.removeObservers(viewLifecycleOwner)
                        activity?.reconnectingView?.visibility = View.GONE
                        if (result != null) {
                            adapter?.submitList(viewModel.displayData)
                        } else {
                            showConnectError(true)
                            status.removeObservers(viewLifecycleOwner)
                        }
                    }
                }
                else -> {}
            }
        }

        // Observe characteristic descriptors to display readable names
        mainViewModel.selectedDevice?.descriptorObservable?.observe(viewLifecycleOwner) {
            val adapter = deviceDataList.adapter as? SelectedDeviceAdapter?: return@observe
            adapter.submitList(viewModel.displayData)
        }
    }

    private fun readCharacteristics() {
        val adapter = deviceDataList.adapter as? SelectedDeviceAdapter
        viewModel.readCharacteristics().observe(viewLifecycleOwner) {
            adapter?.notifyDataSetChanged()
        }
    }

    private fun onCharacteristicClick(characteristic: BluetoothGattCharacteristic) {
        characteristicWriteDialog.visibility = View.VISIBLE
        characteristicHexValue.addTextChangedListener { viewModel.hexValue = it!!.toString() }
        writeCancelButton.setOnClickListener {
            characteristicWriteDialog.visibility = View.GONE
            viewModel.hexValue = ""
        }
        writeButton.setOnClickListener {
            val write = viewModel.writeCharacteristic(characteristic)
            characteristicWriteLoading.visibility = View.VISIBLE
            characteristicWriteButtons.visibility = View.GONE

            write.observe(viewLifecycleOwner) {
                write.removeObservers(viewLifecycleOwner)
                characteristicWriteLoading.visibility = View.GONE
                characteristicWriteButtons.visibility = View.VISIBLE
                characteristicWriteDialog.visibility = View.GONE

                when (it) {
                    is BleOperation.Result.Success -> {
                        Toast.makeText(requireActivity(), "Wrote value", Toast.LENGTH_SHORT).show()
                    }
                    is BleOperation.Result.Failure -> {
                        AlertDialog.Builder(requireContext())
                                .setTitle(R.string.selected_device_write_failed)
                                .setMessage(it.error)
                                .setPositiveButton(R.string.ok) { _, _ -> }
                                .show()
                    }
                }
            }
        }
    }

    private fun showConnectError(unexpected: Boolean = false) {
        val message = if (unexpected) R.string.selected_device_disconnected else R.string.selected_device_error
        AlertDialog.Builder(requireContext())
                .setMessage(message)
                .setPositiveButton(R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                    if (!unexpected) findNavController().navigateUp()
                }
                .setOnCancelListener {
                    if (!unexpected) findNavController().navigateUp()
                }
                .show()
    }
}