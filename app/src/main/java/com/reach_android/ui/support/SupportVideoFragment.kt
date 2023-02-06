package com.reach_android.ui.support

import android.Manifest
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.reach_android.*
import com.reach_android.model.remotesupport.MediaSharingType
import com.reach_android.model.remotesupport.MessageErrors
import com.reach_android.ui.ConditionalBackFragment
import com.reach_android.ui.RemoteSupportViewModel
import com.reach_android.ui.views.SupportView
import kotlinx.android.synthetic.main.fragment_support_video_share.*
import kotlinx.coroutines.launch
import com.reach_android.util.*

class SupportVideoFragment : Fragment(R.layout.fragment_support_video_share),
    ConditionalBackFragment {
    private val rsViewModel: RemoteSupportViewModel by activityViewModels()
    private val supportViewModel: SupportViewModel by activityViewModels()
    private val videoCaptureRequest = FragmentPermissionRequest(this)
    private val args: SupportVideoFragmentArgs by navArgs()
    private val commandID get() = args.connectCommandID

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity.showActionBar("Video Sharing")

        supportViewModel.cameraViewState.subscribe(viewLifecycleOwner, Lifecycle.State.STARTED) {
            switchView(it)
        }

        if (rsViewModel.hasVideo) {
            supportViewModel.cameraViewState.value = CameraViewStates.CAMERA_VIEW
        } else {
            supportViewModel.cameraViewState.value = CameraViewStates.PLACE_HOLDER
        }

        (view as? SupportView)?.let { frame ->
            frame.setOnButtonClickListener {
                when (supportViewModel.cameraViewState.value) {
                    CameraViewStates.PLACE_HOLDER -> {
                        lifecycleScope.launch {
                            if (checkPermissions()) {
                                val added = rsViewModel.addVideo()
                                supportViewModel.cameraViewState.value =
                                    if (added) CameraViewStates.CAMERA_VIEW
                                    else CameraViewStates.PLACE_HOLDER
                                if (added) {
                                    rsViewModel.sendStartShare(MediaSharingType.VIDEO)
                                }
                            } else {
                                supportViewModel.cameraViewState.value =
                                    CameraViewStates.PLACE_HOLDER
                            }
                        }
                    }

                    CameraViewStates.CAMERA_VIEW -> {
                        lifecycleScope.launch {
                            rsViewModel.removeVideo()
                            rsViewModel.sendStopShare(MediaSharingType.VIDEO)
                            supportViewModel.cameraViewState.value = CameraViewStates.PLACE_HOLDER
                        }
                    }
                }

            }
        }

        rsViewModel.requestVideoShareStop.subscribe(viewLifecycleOwner, Lifecycle.State.STARTED) {
            supportViewModel.cameraViewState.value = CameraViewStates.PLACE_HOLDER
        }

        rsViewModel.requestResumeVideoShare.subscribe(viewLifecycleOwner, Lifecycle.State.STARTED) {
            handleVideoRequest(it)
        }

        commandID?.let { commandID ->
            handleVideoRequest(commandID)
        }
    }

    override fun onDestroyView() {
        lifecycleScope.launch { rsViewModel.removeVideoSink(view_camera) }
        super.onDestroyView()
    }

    override fun onBackPressed(): Boolean = false

    private fun handleVideoRequest(commandID: String) {
        rsViewModel.getCommandContext(commandID)?.let { ctx ->
            lifecycleScope.launch {
                if (checkPermissions()) {
                    supportViewModel.cameraViewState.value =
                        when (rsViewModel.addVideo()) {
                            true -> {
                                if(ctx.acknowledge()) {
                                    CameraViewStates.CAMERA_VIEW
                                } else {
                                    CameraViewStates.PLACE_HOLDER
                                }
                            }
                            false -> {
                                ctx.error(MessageErrors.MediaShareError)
                                CameraViewStates.PLACE_HOLDER
                            }
                        }
                } else {
                    ctx.error(MessageErrors.MediaShareError)
                    supportViewModel.cameraViewState.value = CameraViewStates.PLACE_HOLDER
                }
            }
        }
    }

    private fun switchView(it: CameraViewStates) {
        val supportView = view as? SupportView
        when (it) {
            CameraViewStates.PLACE_HOLDER -> {
                lifecycleScope.launch { rsViewModel.removeVideoSink(view_camera) }
                if (videoShareViewSwitcher.nextView.id == R.id.videoPlaceholder) {
                    videoShareViewSwitcher.showNext()
                    supportView?.updateButtonText()
                    supportView?.updateButtonBackground()

                }
            }
            CameraViewStates.CAMERA_VIEW -> {
                lifecycleScope.launch { rsViewModel.addVideoSink(view_camera) }
                if (videoShareViewSwitcher.nextView.id == R.id.view_camera) {
                    videoShareViewSwitcher.showNext()
                    supportView?.updateButtonText(true)
                    supportView?.updateButtonBackground(true)

                }
            }
        }
    }

    private suspend fun checkPermissions() =
        videoCaptureRequest.requestPermission(Manifest.permission.CAMERA)

}