package com.reach_android.ui.support

import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.reach_android.App
import com.reach_android.R
import com.reach_android.model.remotesupport.ChatMessage
import kotlinx.android.synthetic.main.item_chat_received.view.*
import kotlinx.android.synthetic.main.item_chat_sent.view.*
import kotlinx.android.synthetic.main.item_image_received.view.*
import kotlinx.android.synthetic.main.item_image_sent.view.*
import kotlinx.android.synthetic.main.item_video_received.view.*
import kotlinx.android.synthetic.main.item_video_sent.view.*

class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.ViewHolder>(ChatListDiffCallback()) {

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return if (message.text != null) {
            if (message.sent) Row.TextSent.raw else Row.TextReceived.raw
        } else if (message.image != null) {
            if (message.sent) Row.ImageSent.raw else Row.ImageReceived.raw
        } else if (message.video != null) {
            if (message.sent) Row.VideoSent.raw else Row.VideoReceived.raw
        } else Row.Loading.raw
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        when (viewType) {
            Row.TextSent.raw -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_sent, parent, false)
                ViewHolder(view)
            }
            Row.TextReceived.raw -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_received, parent, false)
                ViewHolder(view)
            }
            Row.ImageSent.raw -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image_sent, parent, false)
                ViewHolder(view)
            }
            Row.ImageReceived.raw -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image_received, parent, false)
                ViewHolder(view)
            }
            Row.VideoSent.raw -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video_sent, parent, false)
                ViewHolder(view)
            }
            Row.VideoReceived.raw -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video_received, parent, false)
                ViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_loading, parent, false)
                ViewHolder(view)
            }
        }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = getItem(position)
        if (message.text != null) {
            if (message.sent) {
                holder.itemView.sentText.text = message.text
            } else {
                holder.itemView.receivedText.text = message.text
            }
        } else if (message.image != null) {
            if (message.sent) {
                Glide
                    .with(holder.itemView)
                    .load(message.image)
                    .into(holder.itemView.sentImageView)
            } else {
                Glide
                    .with(holder.itemView)
                    .load(message.image)
                    .into(holder.itemView.receivedImageView)
            }
        } else if (message.video != null) {
            val view = holder.itemView
            val retriever = MediaMetadataRetriever()
            val thumbnail = if (message.sent) view.sentVideoThumbnail else view.receivedVideoThumbnail
            val infoView = if (message.sent) view.sentVideoInfo else view.receivedVideoInfo
            val duration = if (message.sent) view.sentVideoDuration else view.receivedVideoDuration
            val videoView = if (message.sent) view.sentVideoView else view.receivedVideoView
            val tapTarget = if (message.sent) view.sentVideoTapTarget else view.receivedVideoTapTarget

            retriever.setDataSource(App.app.applicationContext, message.video)
            thumbnail.setImageBitmap(retriever.frameAtTime)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt()?.let { length ->
                val minutes = length / 1000 / 60 % 60
                val seconds = length / 1000 % 60
                val timeString = "${minutes}:${String.format("%02d", seconds)}"
                duration.text = timeString
            }

            tapTarget.setOnClickListener {
                when {
                    videoView.isPlaying -> {
                        infoView.visibility = View.VISIBLE
                        videoView.stopPlayback()
                    }
                    else -> {
                        infoView.visibility = View.GONE
                        thumbnail.visibility = View.INVISIBLE
                        videoView.setVideoURI(message.video)
                        videoView.start()
                    }
                }
            }

            videoView.setOnCompletionListener {
                thumbnail.visibility = View.VISIBLE
                infoView.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Cancel all currently playing media
     */
    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        val view = holder.itemView
        val videoView = view.sentVideoView?: view.receivedVideoView
        val thumbnail = view.sentVideoThumbnail?: view.receivedVideoThumbnail
        val infoView = view.sentVideoInfo?: view.receivedVideoInfo

        videoView?.stopPlayback()
        videoView?.suspend()
        thumbnail?.visibility = View.VISIBLE
        infoView?.visibility = View.VISIBLE
    }

    class ViewHolder(view: View): RecyclerView.ViewHolder(view)

    private enum class Row(val raw: Int) {
        TextSent(0),
        TextReceived(1),
        ImageSent(2),
        ImageReceived(3),
        VideoSent(4),
        VideoReceived(5),
        Loading(6)
    }
}

private class ChatListDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {

    override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem.id == newItem.id && areContentsTheSame(oldItem, newItem)
    }

    override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem.text == newItem.text
    }
}