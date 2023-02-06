package com.reach_android.ui.support

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import androidx.core.content.FileProvider
import androidx.databinding.ObservableList
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.reach_android.*
import com.reach_android.model.ConnectionStatus
import com.reach_android.model.remotesupport.ChatMessage
import com.reach_android.repository.ChatRepository
import com.reach_android.ui.ConditionalBackFragment
import com.reach_android.ui.MainViewModel
import com.reach_android.ui.RemoteSupportViewModel
import com.reach_android.util.*
import kotlinx.android.synthetic.main.bottom_sheet_camera.view.*
import kotlinx.android.synthetic.main.fragment_support_messages.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


class SupportMessagesFragment : Fragment(R.layout.fragment_support_messages),
    ConditionalBackFragment {
    private val rsViewModel: RemoteSupportViewModel by activityViewModels()

    private val imageGalleryRequest = ImageGalleryRequest(this)
    private val videoCaptureRequest = VideoRequest(this)
    private val imageCaptureRequest = PhotoRequest(this)
    private val permissionRequest = FragmentPermissionRequest(this)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity.showActionBar("Messages")
        val adapter = ChatAdapter()
        chatList.adapter = adapter
        subscribe(adapter)
    }

    override fun onBackPressed(): Boolean = false

    private fun showChatMessages() {
        chatList?.let { cl ->
            if (cl.visibility != View.VISIBLE) {
                layout_placeholder.visibility = View.GONE
                cl.visibility = View.VISIBLE
            }
        }

    }

    private fun subscribe(adapter: ChatAdapter) {
        adapter.submitList(ChatRepository.messageList)
        if (ChatRepository.messageList.size > 0)
            showChatMessages()

        ChatRepository.messageList.addOnListChangedCallback(object :
            ObservableList.OnListChangedCallback<ObservableList<ChatMessage>>() {
            override fun onChanged(sender: ObservableList<ChatMessage>?) {
                sender?.let {
                    if (it.isNotEmpty()) {
                        showChatMessages()
                        adapter.submitList(sender)
                        chatList?.smoothScrollToPosition(it.size - 1)
                    }
                }
            }

            override fun onItemRangeChanged(
                sender: ObservableList<ChatMessage>?,
                positionStart: Int,
                itemCount: Int
            ) {
                showChatMessages()
                adapter.notifyItemRangeChanged(positionStart, itemCount)
                sender.ifNotEmpty { chatList?.smoothScrollToPosition(it.size - 1) }
            }

            override fun onItemRangeInserted(
                sender: ObservableList<ChatMessage>?,
                positionStart: Int,
                itemCount: Int
            ) {
                showChatMessages()
                adapter.notifyItemRangeInserted(positionStart, itemCount)
                sender.ifNotEmpty { chatList?.smoothScrollToPosition(it.size - 1) }
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun onItemRangeMoved(
                sender: ObservableList<ChatMessage>?,
                fromPosition: Int,
                toPosition: Int,
                itemCount: Int
            ) {
                showChatMessages()
                adapter.notifyDataSetChanged()
                sender.ifNotEmpty { chatList?.smoothScrollToPosition(it.size - 1) }
            }

            override fun onItemRangeRemoved(
                sender: ObservableList<ChatMessage>?,
                positionStart: Int,
                itemCount: Int
            ) {
                showChatMessages()
                adapter.notifyItemRangeRemoved(positionStart, itemCount)
                sender.ifNotEmpty { chatList?.smoothScrollToPosition(it.size - 1) }
            }
        })

        // Scroll chat when keyboard opens
        chatList.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom) {
                chatList.scrollBy(0, oldBottom - bottom)
            }
        }

        // Listen to session connection
        rsViewModel.connectionStatus.subscribe(viewLifecycleOwner) {
//            sessionStatusIcon.connectionStatus = it
            when (it) {
                ConnectionStatus.Connected -> {
//                    sessionStatusLabel.text = getString(R.string.session_agent_connected)
                }
                ConnectionStatus.Connecting -> {
//                    sessionStatusLabel.text = getString(R.string.session_agent_reconnecting)
                }
                ConnectionStatus.Disconnected -> {
//                    sessionStatusLabel.text = getString(R.string.session_agent_disconnected)
                    showSessionDisconnect()
                }
            }
        }

//        // Listen to disconnect request
//        sessionDisconnectButton.setOnClickListener {
//            confirmSessionDisconnect()
//        }

        // Listen to text changes
        chatEditText.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(text: CharSequence, p1: Int, p2: Int, p3: Int) {
                sendButton.isEnabled = text.isNotEmpty()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(p0: Editable?) {}
        })

        // Send text
        sendButton.setOnClickListener {
            sendButton.visibility = View.INVISIBLE
            chatProgress.visibility = View.VISIBLE
            lifecycleScope.launch {
                val message = rsViewModel.sendChat(
                    chatEditText.text.toString(),
                    selectedMedia.drawable
                )
                chatProgress.visibility = View.GONE
                sendButton.visibility = View.VISIBLE

                if (message != null) {
                    showError(message)
                } else {
                    chatEditText.setText("")
                    cancelMedia()
                }
            }
        }

        // Handle camera button
        cameraButton.setOnClickListener {
            showPhotoOptions()
        }

        // Handle cancel media button
        cancelMedia.setOnClickListener {
            cancelMedia()
        }
    }

    /**
     * Displays a bottom sheet with options to either take or choose media
     */
    private fun showPhotoOptions() {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = layoutInflater.inflate(R.layout.bottom_sheet_camera, null)

        sheet.photoCameraButton.setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                if (permissionRequest.requestPermission(Manifest.permission.CAMERA)) {
                    capturePhoto()
                }

            }
        }
        sheet.videoCameraButton.setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                if (permissionRequest.requestPermission(Manifest.permission.CAMERA)) {
                    captureVideo()
                }
            }
        }
        sheet.photoLibraryButton.setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                openGallery()
            }
        }

        dialog.setContentView(sheet)
        dialog.show()
    }

    private suspend fun capturePhoto() {
        // Create a file for the camera intent to save a captured image to
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val directory = Environment.DIRECTORY_PICTURES
        val storageDir = requireContext().getExternalFilesDir(directory)
        val file = kotlin.runCatching {
            File.createTempFile("capture_${timeStamp}", ".jpg", storageDir)
        }
        if (file.isSuccess) {
            val uri =
                FileProvider.getUriForFile(requireContext(), App.FILE_PROVIDER, file.getOrThrow())
            if (imageCaptureRequest.requestPhoto(uri)) {
                rsViewModel.selectedImage = uri
                Glide.with(this).load(uri).into(selectedMedia)
                mediaView.visibility = View.VISIBLE
                sendButton.isEnabled = true
            }
        } else {
            Log.e("SupportFragment", "Failed to create temp file", file.exceptionOrNull())
        }
    }

    private suspend fun captureVideo() {
        // Create a file for the camera intent to save a captured image to
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val directory = Environment.DIRECTORY_MOVIES
        val storageDir = requireContext().getExternalFilesDir(directory)
        val file = kotlin.runCatching {
            File.createTempFile("capture_${timeStamp}", ".mp4", storageDir)
        }
        if (file.isSuccess) {
            val uri = FileProvider.getUriForFile(
                requireContext(), App.FILE_PROVIDER, file.getOrThrow()
            )
            videoCaptureRequest.requestVideo(uri)?.let { preview ->
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toInt()
                    ?.let { length ->
                        val minutes = length / 1000 / 60 % 60
                        val seconds = length / 1000 % 60
                        val timeString = "${minutes}:${String.format("%02d", seconds)}"
                        videoDuration.text = timeString
                        videoDuration.visibility = View.VISIBLE
                    }

                rsViewModel.selectedImage = uri
                Glide.with(this).load(preview).into(selectedMedia)
                mediaView.visibility = View.VISIBLE
                sendButton.isEnabled = true
            }
        } else {
            Log.e("SupportFragment", "Failed to create temp file", file.exceptionOrNull())
        }
    }

    private suspend fun openGallery() {
        imageGalleryRequest.requestImage()?.let { uri ->
            rsViewModel.selectedImage = uri
            Glide.with(this).load(uri).into(selectedMedia)
            mediaView.visibility = View.VISIBLE
            sendButton.isEnabled = true
        }
    }

    /**
     * Gets rid of the selected media and the asoociated views
     */
    private fun cancelMedia() {
        rsViewModel.selectedImage = null
        rsViewModel.selectedVideo = null
        mediaView.visibility = View.GONE
        videoDuration.visibility = View.INVISIBLE
        sendButton.isEnabled = chatEditText.text.toString().isNotEmpty()
    }


    /**
     * Displays an error telling the user that the peer disconnected
     */
    private fun showSessionDisconnect() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.session_disconnect_message_peer)
            .setPositiveButton(R.string.ok) { _, _ ->
                findNavController().navigateUp()
            }
            .setOnCancelListener {
                findNavController().navigateUp()
            }
            .show()
    }

    /**
     * Displays an error message
     */
    private fun showError(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.error)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { _, _ -> }
            .show()
    }
}

//fun <T> List<T>?.ifNotEmpty(): Boolean = this?.let { it.isNotEmpty() } ?: false
fun <T> List<T>?.ifNotEmpty(action: (List<T>) -> Unit): Unit =
    this?.let { if (it.isNotEmpty()) action(it) } ?: Unit