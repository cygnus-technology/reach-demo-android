package com.reach_android.ui.support

import android.Manifest
import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isNotEmpty
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.reach_android.App
import com.reach_android.R
import com.reach_android.bluetooth.BleManager.isConnected
import com.reach_android.model.ConnectionStatus
import com.reach_android.repository.ChatRepository
import com.reach_android.ui.MainViewModel
import com.reach_android.ui.RemoteSupportViewModel
import kotlinx.android.synthetic.main.bottom_sheet_camera.view.*
import kotlinx.android.synthetic.main.fragment_support.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class SupportFragment : Fragment() {

    private val mainViewModel: MainViewModel by activityViewModels()
    private val rsViewModel: RemoteSupportViewModel by activityViewModels()
    private val supportViewModel: SupportViewModel by viewModels()
    private val galleryRequestCode = 100
    private val captureImageRequestCode = 101
    private val captureVideoRequestCode = 102

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_support, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = ChatAdapter()
        chatList.adapter = adapter
        subscribe(adapter)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return

        val uri = if (requestCode == galleryRequestCode) {
            // Video/image selected
            data?.data?: return
        } else {
            // Video/image captured
            val filePath = supportViewModel.capturedMediaFilePath?: return
            FileProvider.getUriForFile(requireContext(), App.FILE_PROVIDER, File(filePath))
        }

        val isImage = if (requestCode == galleryRequestCode) {
            val columns = arrayOf(MediaStore.Images.Media.MIME_TYPE)
            requireContext().contentResolver.query(uri, columns, null, null, null)?.use {
                it.moveToFirst()
                val type = it.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val mimeType = it.getString(type).startsWith("image")
                mimeType
            }?: return
        } else {
            requestCode == captureImageRequestCode
        }

        if (isImage) {
            rsViewModel.selectedImage = uri
            Glide
                .with(this)
                .load(uri)
                .into(selectedMedia)
        } else {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt()?.let { length ->
                val minutes = length / 1000 / 60 % 60
                val seconds = length / 1000 % 60
                val timeString = "${minutes}:${String.format("%02d", seconds)}"
                videoDuration.text = timeString
                videoDuration.visibility = View.VISIBLE
            }

            rsViewModel.selectedVideo = uri
            Glide
                .with(this)
                .load(retriever.frameAtTime)
                .into(selectedMedia)
        }

        mediaView.visibility = View.VISIBLE
        sendButton.isEnabled = true
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            App.CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() != PackageManager.PERMISSION_DENIED) {
                    startCameraActivity(supportViewModel.capturePhoto)
                }
            }
        }
    }

    private fun subscribe(adapter: ChatAdapter) {
        // Listen to chat messages
        ChatRepository.messageList.observe(viewLifecycleOwner) {
            adapter.submitList(it)
            adapter.notifyDataSetChanged()
            if (it.isNotEmpty()) chatList.smoothScrollToPosition(it.size - 1)
        }

        // Scroll chat when keyboard opens
        chatList.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom) {
                chatList.scrollBy(0, oldBottom - bottom)
            }
        }

        // Listen to device connection
        mainViewModel.selectedDevice?.let { device ->
            device.connectionStatusObservable.observe(viewLifecycleOwner) {
                deviceStatusIcon.connectionStatus = it
                rsViewModel.sendDiagnosticData()

                when (it!!) {
                    ConnectionStatus.Connected -> {
                        deviceConnectionLabel.text = getString(R.string.session_device_connected, device.name
                                ?: "Unknown")
                        rsViewModel.sendDeviceData()
                    }
                    ConnectionStatus.Connecting -> {
                        deviceConnectionLabel.text = getString(R.string.session_device_reconnecting, device.name
                                ?: "Unknown")
                    }
                    ConnectionStatus.Disconnected -> {
                        deviceConnectionLabel.text = getString(R.string.session_device_disconnected)
                        mainViewModel.connectDevice(true)
                    }
                }
            }
        }

        // Set up diagnostic heartbeat
        supportViewModel.diagnosticTimer.observe(viewLifecycleOwner) {
            val device = mainViewModel.selectedDevice
            if (device?.device == null ||
                    !device.device.isConnected() ||
                    rsViewModel.connectionStatus.value != ConnectionStatus.Connected) return@observe
            rsViewModel.sendDiagnosticData()
        }

        // Listen to logs
        rsViewModel.logger.logs.observe(viewLifecycleOwner) {
            loggerTextView.logMessage(it)
        }

        // Listen to session connection
        rsViewModel.connectionStatus.observe(viewLifecycleOwner) {
            sessionStatusIcon.connectionStatus = it
            when (it!!) {
                ConnectionStatus.Connected -> {
                    sessionStatusLabel.text = getString(R.string.session_agent_connected)
                }
                ConnectionStatus.Connecting -> {
                    sessionStatusLabel.text = getString(R.string.session_agent_reconnecting)
                }
                ConnectionStatus.Disconnected -> {
                    sessionStatusLabel.text = getString(R.string.session_agent_disconnected)
                    showSessionDisconnect()
                }
            }
        }

        // Listen to disconnect request
        sessionDisconnectButton.setOnClickListener {
            confirmSessionDisconnect()
        }

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
            val data = rsViewModel.sendChat(chatEditText.text.toString(), selectedMedia.drawable)
            sendButton.visibility = View.INVISIBLE
            chatProgress.visibility = View.VISIBLE
            data.observe(viewLifecycleOwner) {
                data.removeObservers(viewLifecycleOwner)
                chatProgress.visibility = View.GONE
                sendButton.visibility = View.VISIBLE

                if (it != null) {
                    showError(it)
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

        fun onCaptureClick(photo: Boolean) {
            supportViewModel.capturePhoto = photo
            dialog.dismiss()
            if (ContextCompat.checkSelfPermission(App.app, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), App.CAMERA_PERMISSION_REQUEST_CODE)
            } else {
                startCameraActivity(photo)
            }
        }

        sheet.photoCameraButton.setOnClickListener { onCaptureClick(true) }
        sheet.videoCameraButton.setOnClickListener { onCaptureClick(false) }
        sheet.photoLibraryButton.setOnClickListener {
            dialog.dismiss()
            val gallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
            startActivityForResult(gallery, galleryRequestCode)
        }

        dialog.setContentView(sheet)
        dialog.show()
    }

    /**
     * Prepares a file for the camera activity and starts it up
     * @param photo Set to true to capture a photo, false to capture a video
     */
    private fun startCameraActivity(photo: Boolean) {
        // Create a file for the camera intent to save a captured image to
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val directory = if (photo) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
        val storageDir = requireContext().getExternalFilesDir(directory)
        try {
            val suffix = if (photo) ".jpg" else ".mp4"
            val file = File.createTempFile(
                    "capture_${timeStamp}",
                    suffix,
                    storageDir)
            supportViewModel.capturedMediaFilePath = file.absolutePath
            val intentDescription =
                    if (photo) MediaStore.ACTION_IMAGE_CAPTURE
                    else MediaStore.ACTION_VIDEO_CAPTURE
            val intent = Intent(intentDescription)
            val uri = FileProvider.getUriForFile(
                    requireContext(),
                    App.FILE_PROVIDER,
                    file)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            val code = if (photo) captureImageRequestCode else captureVideoRequestCode
            startActivityForResult(intent, code)
        } catch (ex: IOException) {
            Log.e("SupportFragment", "Failed to create temp file", ex)
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
     * Displays an alert dialog when user clicks the disconnect button
     */
    private fun confirmSessionDisconnect() {
        AlertDialog.Builder(requireContext())
                .setTitle(R.string.session_disconnect_confirm)
                .setMessage(R.string.session_disconnect_request)
                .setNeutralButton(R.string.cancel) { _, _ -> }
                .setPositiveButton(R.string.session_disconnect) { _, _ ->
                    rsViewModel.disconnect()
                    findNavController().navigateUp()
                }
                .show()
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