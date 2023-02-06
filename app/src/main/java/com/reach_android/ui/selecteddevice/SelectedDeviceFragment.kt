package com.reach_android.ui.selecteddevice

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothGattCharacteristic
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.cygnusreach.RemoteSupport
import com.reach_android.App
import com.reach_android.R
import com.reach_android.bluetooth.BleOperation
import com.reach_android.model.ConnectionStatus
import com.reach_android.util.*
import com.reach_android.repository.DeviceRepository
import com.reach_android.ui.ConditionalBackFragment
import com.reach_android.ui.MainViewModel
import kotlinx.android.synthetic.main.fragment_selected_device.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.util.concurrent.atomic.AtomicBoolean

class SelectedDeviceFragment : Fragment(), ConditionalBackFragment {

    private val viewModel: SelectedDeviceViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val args: SelectedDeviceFragmentArgs by navArgs()
    private val fromSupport get() = args.fromSupport
    private val viewParameters get() = args.viewParameters
    private var exiting = AtomicBoolean(false)

    var deviceScope: CoroutineScope? = null

    private var deviceAdapter
        get() = deviceDataList?.adapter?.let { it as? SelectedDeviceAdapter }
        set(value) {
            deviceDataList.adapter = value
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_selected_device, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity.showActionBar()

        refreshLayout.visibility = View.GONE
        deviceAdapter = SelectedDeviceAdapter(::onCharacteristicClick)
        lifecycleScope.launch { connect() }

        observeDevice()

        refreshLayout.setOnRefreshListener {
            readCharacteristics()
            Toast.makeText(requireContext(), R.string.selected_device_refresh, Toast.LENGTH_SHORT)
                .show()
        }
    }

    override fun onStop() {
        deviceScope?.cancel()
        super.onStop()
    }

    override fun onBackPressed(): Boolean = onBack()

    override fun onUpPressed(): Boolean = onBack()

    private fun onBack(): Boolean {
        exiting.set(true)
        return true
    }

    private suspend fun connect() {
        val connect = mainViewModel.connectDevice()
        loading?.visibility = View.GONE
        when (connect) {
            is BleOperation.Result.Success<*> -> {
                if (fromSupport && !viewParameters) {
                    navigate(R.id.supportDeviceFragment)
                } else {
                    refreshLayout?.visibility = View.VISIBLE
                    deviceAdapter?.submitList(viewModel.displayData)

                    if(fromSupport || RemoteSupport.isClientConnected()){
                        remoteSupportButton?.visibility = View.GONE
                    } else {
                        remoteSupportButton?.visibility = View.VISIBLE
                        remoteSupportButton?.setOnClickListener {
                            navigate(
                                SelectedDeviceFragmentDirections
                                    .actionSelectedDeviceFragmentToPinFragment()
                            )
                        }
                    }
                }
            }
            is BleOperation.Result.Failure<*> -> {
                if (exiting.get()) return
                showConnectError()
            }
        }
    }

    private fun observeDevice() {
        // Observe connection status
        DeviceRepository.selectedDevice.subscribe(viewLifecycleOwner, Lifecycle.State.STARTED) {
            deviceScope?.cancel()
            it?.let { device ->
                deviceScope = (this + SupervisorJob()).apply {
                    device.connectionStatusObservable.subscribe(this) { status ->
                        when (status) {
                            ConnectionStatus.Connected -> {
                                readCharacteristics()
                            }
                            ConnectionStatus.Disconnected -> {
                                if (!fromSupport && exiting.get())
                                deviceAdapter?.submitList(viewModel.displayData)

                                // Attempt to reconnect
                                when (mainViewModel.connectDevice(true)) {
                                    is BleOperation.Result.Success<*> -> deviceAdapter?.submitList(
                                        viewModel.displayData
                                    )
                                    is BleOperation.Result.Failure<*> -> {
                                        showConnectError(true)
                                        this.cancel()
                                    }
                                }
                            }
                            else -> {}
                        }
                    }

                    device.descriptorObservable.subscribe(this) {
                        if(it.uuid == App.NAME_DESCRIPTOR_ID) {
                            deviceAdapter?.submitList(viewModel.displayData)
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun readCharacteristics() {
        deviceAdapter?.let { adapter ->
            lifecycleScope.launch {
                viewModel.readCharacteristics().collect {
                    adapter.notifyDataSetChanged()
                    refreshLayout?.isRefreshing = false
                }
            }
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
            characteristicWriteLoading.visibility = View.VISIBLE
            characteristicWriteButtons.visibility = View.GONE

            lifecycleScope.launch {
                val rslt = viewModel.writeCharacteristic(characteristic)
                characteristicWriteLoading.visibility = View.GONE
                characteristicWriteButtons.visibility = View.VISIBLE
                characteristicWriteDialog.visibility = View.GONE

                when (rslt) {
                    is BleOperation.Result.Success<*> -> {
                        Toast.makeText(requireActivity(), "Wrote value", Toast.LENGTH_SHORT).show()
                    }
                    is BleOperation.Result.Failure<*> -> {
                        AlertDialog.Builder(requireContext())
                            .setTitle(R.string.selected_device_write_failed)
                            .setMessage(rslt.error)
                            .setPositiveButton(R.string.ok) { _: DialogInterface, _: Int -> }
                            .show()
                    }
                }
            }
        }
    }

    private fun showConnectError(unexpected: Boolean = false) {
        val message =
            if (unexpected) R.string.selected_device_disconnected else R.string.selected_device_error

        context?.let {
            AlertDialog.Builder(requireContext())
                .setMessage(message)
                .setPositiveButton(R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                    if (!unexpected) {
                        if (isAdded) {
                            findNavController().navigateUp()
                        } else {
                            Log.w(javaClass.name, "Could not connect to device")
                        }
                    }
                }
                .setOnCancelListener {
                    if (isAdded && !unexpected) findNavController().navigateUp()
                }
                .show()
        } ?: Log.w(javaClass.name, "Connection error")

    }
}