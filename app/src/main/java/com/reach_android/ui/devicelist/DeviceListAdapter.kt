package com.reach_android.ui.devicelist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.reach_android.R
import com.reach_android.databinding.ItemDeviceBinding
import com.reach_android.model.BleDevice
import kotlinx.android.synthetic.main.item_device.view.*

/**
 * Adapter for the device recycler view in [DeviceListFragment]
 */
class DeviceListAdapter(
    private val onClick: (BleDevice) -> Unit
) : ListAdapter<BleDevice, RecyclerView.ViewHolder>(DeviceListDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return DeviceViewHolder(ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        ))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val device = getItem(position)
        (holder as DeviceViewHolder).bind(device)
        holder.itemView.setOnClickListener { onClick(device) }
        when (device.rssi) {
            in 0 downTo -39 -> {
                val icon = ContextCompat.getDrawable(holder.itemView.context, R.drawable.ic_signal_3)
                holder.itemView.signalStrengthIcon.setImageDrawable(icon)
            }
            in -40 downTo -59 -> {
                val icon = ContextCompat.getDrawable(holder.itemView.context, R.drawable.ic_signal_2)
                holder.itemView.signalStrengthIcon.setImageDrawable(icon)
            }
            in -60 downTo -84 -> {
                val icon = ContextCompat.getDrawable(holder.itemView.context, R.drawable.ic_signal_1)
                holder.itemView.signalStrengthIcon.setImageDrawable(icon)
            }
            else -> {
                val icon = ContextCompat.getDrawable(holder.itemView.context, R.drawable.ic_signal_0)
                holder.itemView.signalStrengthIcon.setImageDrawable(icon)
            }
        }
    }

    class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BleDevice) {
            binding.apply {
                device = item
                executePendingBindings()
            }
        }
    }
}

private class DeviceListDiffCallback : DiffUtil.ItemCallback<BleDevice>() {

    override fun areItemsTheSame(oldItem: BleDevice, newItem: BleDevice): Boolean {
        return oldItem.uuid == newItem.uuid
    }

    override fun areContentsTheSame(oldItem: BleDevice, newItem: BleDevice): Boolean {
        return oldItem.rssi == newItem.rssi
    }
}