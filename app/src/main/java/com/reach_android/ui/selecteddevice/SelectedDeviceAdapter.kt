package com.reach_android.ui.selecteddevice

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.reach_android.R
import com.reach_android.bluetooth.BleManager.canWrite
import com.reach_android.bluetooth.formattedValue
import com.reach_android.bluetooth.name
import kotlinx.android.synthetic.main.item_device_data.view.*
import kotlinx.android.synthetic.main.item_device_header.view.*


class SelectedDeviceAdapter(
    val onClick: (BluetoothGattCharacteristic) -> Unit
) : ListAdapter<SelectedDeviceAdapter.Row, SelectedDeviceAdapter.ViewHolder>(
    SelectedDeviceListDiffCallback()
) {

    override fun getItemViewType(position: Int): Int {
        return getItem(position).type
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when (viewType) {
            Row.Service.type, Row.AdvertisementHeader.type -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device_header, parent, false)
                ViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device_data, parent, false)
                ViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val view = holder.itemView
        when (val row = getItem(position)) {
            is Row.Service, is Row.AdvertisementHeader -> {
                view.deviceHeaderLabel.text = row.title
                view.setOnClickListener(null)
            }
            is Row.Characteristic -> {
                view.dataNameLabel.text = row.title
                view.dataValueLabel.text = row.value.formattedValue?: ""
                view.setOnClickListener { onClick(row.value) }
                view.writableIndicator.visibility =
                        if (row.value.canWrite()) View.VISIBLE
                        else View.INVISIBLE
            }
            is Row.AdvertisementData -> {
                view.dataNameLabel.text = row.title
                view.dataValueLabel.text = row.value
                view.setOnClickListener(null)
            }
        }
    }

    sealed class Row(val title: String, val type: Int) {
        class Service(val value: BluetoothGattService) : Row(value.name, type) {
            companion object {
                const val type = 0
            }
        }
        class Characteristic(val value: BluetoothGattCharacteristic) : Row(value.name, type) {
            companion object {
                const val type = 1
            }
        }
        class AdvertisementHeader() : Row("Advertisement Data", type) {
            companion object {
                const val type = 2
            }
        }
        class AdvertisementData(title: String, val value: String) : Row(title, type) {
            companion object {
                const val type = 3
            }
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
}

private class SelectedDeviceListDiffCallback : DiffUtil.ItemCallback<SelectedDeviceAdapter.Row>() {

    override fun areItemsTheSame(oldItem: SelectedDeviceAdapter.Row, newItem: SelectedDeviceAdapter.Row): Boolean {
        return oldItem.title == newItem.title
    }

    override fun areContentsTheSame(oldItem: SelectedDeviceAdapter.Row, newItem: SelectedDeviceAdapter.Row): Boolean {
        return oldItem.title == newItem.title
    }
}