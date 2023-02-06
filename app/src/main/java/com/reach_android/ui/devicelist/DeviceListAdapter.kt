package com.reach_android.ui.devicelist

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.reach_android.R
import com.reach_android.databinding.ItemDeviceBinding
import com.reach_android.model.BleDevice
import com.reach_android.util.*
import kotlinx.android.synthetic.main.item_device.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren

/**
 * Adapter for the device recycler view in [DeviceListFragment]
 */
class DeviceListAdapter(
    private val onClick: (BleDevice) -> Unit
) : ListAdapter<BleDevice, RecyclerView.ViewHolder>(DeviceListDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return DeviceViewHolder(
            ItemDeviceBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val device = getItem(position)
        (holder as DeviceViewHolder).let {
            it.bind(device)
            device.rssiBucket.subscribe(it.updateScope) { rssi ->
                holder.itemView.signalStrengthIcon.setImageDrawable(getBucketIcon(rssi, holder))
            }
        }
        holder.itemView.setOnClickListener { onClick(device) }
        holder.itemView.signalStrengthIcon.setImageDrawable(getBucketIcon(device.rssiBucket.value, holder))
    }

    private fun getBucketIcon(rssi: Int, holder: RecyclerView.ViewHolder): Drawable? {
        return when (rssi) {
            3 -> ContextCompat.getDrawable(
                holder.itemView.context,
                R.drawable.ic_signal_3
            )
            2 -> ContextCompat.getDrawable(
                holder.itemView.context,
                R.drawable.ic_signal_2
            )
            1 -> ContextCompat.getDrawable(
                holder.itemView.context,
                R.drawable.ic_signal_1
            )
            else -> ContextCompat.getDrawable(
                holder.itemView.context,
                R.drawable.ic_signal_0
            )
        }
    }

    fun clear() {
        submitList(emptyList())
    }

    class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private val updateJob = SupervisorJob()
        val updateScope = CoroutineScope(updateJob)

        fun bind(item: BleDevice) {
            updateJob.cancelChildren()
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
        return oldItem.displayName == newItem.displayName
                && oldItem.rssiBucket == newItem.rssiBucket
    }
}

