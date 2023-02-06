package com.reach_android.ui.support

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.reach_android.NavGraphDirections
import com.reach_android.R
import com.reach_android.model.remotesupport.MediaSharingType
import com.reach_android.model.remotesupport.MessageErrors
import com.reach_android.util.*
import com.reach_android.ui.ConditionalBackFragment
import com.reach_android.ui.RemoteSupportViewModel
import com.reach_android.ui.views.SupportView
import kotlinx.android.synthetic.main.fragment_support_screen_share.*
import kotlinx.coroutines.launch

class SupportScreenFragment : Fragment(R.layout.fragment_support_screen_share),
    ConditionalBackFragment {
    private val rsViewModel: RemoteSupportViewModel by activityViewModels()
    private val supportViewModel: SupportViewModel by activityViewModels()
    private val args: SupportScreenFragmentArgs by navArgs()
    private val commandID get() = args.connectCommandID

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity.showActionBar("Screen Sharing")

        supportViewModel.screenViewState.subscribe(viewLifecycleOwner, Lifecycle.State.STARTED) {
            switchView(it)
        }

        if (rsViewModel.hasScreenShare) {
            supportViewModel.screenViewState.value = ScreenViewStates.SCREEN_VIEW
        } else {
            supportViewModel.screenViewState.value = ScreenViewStates.PLACE_HOLDER
        }

        (view as? SupportView)?.let {
            it.setOnButtonClickListener {
                when (supportViewModel.screenViewState.value) {
                    ScreenViewStates.PLACE_HOLDER -> {
                        lifecycleScope.launch {
                            val added = rsViewModel.addScreenSharing()
                            supportViewModel.screenViewState.value =
                                if (added) ScreenViewStates.SCREEN_VIEW
                                else ScreenViewStates.PLACE_HOLDER
                            if (added) {
                                rsViewModel.sendStartShare(MediaSharingType.SCREEN)
                            }
                        }
                    }
                    ScreenViewStates.SCREEN_VIEW -> {
                        lifecycleScope.launch {
                            rsViewModel.removeScreen()
                            rsViewModel.sendStopShare(MediaSharingType.SCREEN)
                            supportViewModel.screenViewState.value = ScreenViewStates.PLACE_HOLDER
                        }
                    }
                }
            }
        }

        rsViewModel.requestScreenShareStop.subscribe(viewLifecycleOwner, Lifecycle.State.STARTED) {
            supportViewModel.screenViewState.value = ScreenViewStates.PLACE_HOLDER
        }

        rsViewModel.requestResumeScreenShare.subscribe(
            viewLifecycleOwner,
            Lifecycle.State.STARTED
        ) {
            handleScreenRequest(it)
        }

        commandID?.let { commandID ->
            handleScreenRequest(commandID)
        }
    }

    override fun onBackPressed(): Boolean = false

    private fun handleScreenRequest(commandID: String) {
        rsViewModel.getCommandContext(commandID)?.let { ctx ->
            lifecycleScope.launch {
                supportViewModel.screenViewState.value =
                    if (rsViewModel.addScreenSharing()) {
                        if(ctx.acknowledge()) {
                            ScreenViewStates.SCREEN_VIEW
                        } else {
                            ScreenViewStates.PLACE_HOLDER
                        }
                    } else {
                        ctx.error(MessageErrors.MediaShareError)
                        ScreenViewStates.PLACE_HOLDER
                    }
            }
        }
    }

    private fun switchView(it: ScreenViewStates) {
        val supportView = view as? SupportView
        when (it) {
            ScreenViewStates.PLACE_HOLDER -> if (screenShareViewSwitcher.nextView.id == R.id.screenPlaceholder) {
                screenShareViewSwitcher.showNext()
                supportView?.updateButtonText()
                supportView?.updateButtonBackground()
            }
            ScreenViewStates.SCREEN_VIEW -> if (screenShareViewSwitcher.nextView.id == R.id.view_screen) {
                screenShareViewSwitcher.showNext()
                supportView?.updateButtonText(true)
                supportView?.updateButtonBackground(true)
            }
        }
    }
}