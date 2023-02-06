package com.reach_android.ui

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.cygnusreach.*
import com.cygnusreach.messages.ICommandContext
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.reach_android.*
import com.reach_android.R
import com.reach_android.bluetooth.BleOperation
import com.reach_android.model.ConnectionStatus
import com.reach_android.model.remotesupport.MessageErrors
import com.reach_android.ui.support.CommandContextWrapper
import com.reach_android.ui.support.SupportScreenFragment
import com.reach_android.ui.support.SupportVideoFragment
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.reach_android.util.*


class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val rsViewModel: RemoteSupportViewModel by viewModels()
    private lateinit var navController: NavController
    lateinit var appBarConfiguration: AppBarConfiguration
        private set

    fun navigate(directions: NavDirections) {
        navController.navigate(directions.actionId, directions.arguments, getNavOptions())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController
        this.navController = navController

        initializeRemoteSupport()
        initializeToolbar(navController)
        initializeBottomNav(navController)
    }

    override fun onDestroy() {
        runBlocking {
            RemoteSupport.stopService(applicationContext)
        }
        super.onDestroy()
    }

    private fun initializeBottomNav(navController: NavController) {
        bottomNav.setupWithNavController(navController)
        bottomNav.setOnItemSelectedListener { item ->
            when (rsViewModel.connectionStatus.value) {
                ConnectionStatus.Connected, ConnectionStatus.Connecting -> {
                    when (item.itemId) {
                        R.id.nav_device -> navigate(NavGraphDirections.actionGlobalSupportDeviceFragment())
                        R.id.nav_messaging -> navigate(NavGraphDirections.actionGlobalSupportMessagesFragment())
                        R.id.nav_video_share -> navigate(NavGraphDirections.actionGlobalSupportVideoFragment())
                        R.id.nav_screen_share -> navigate(NavGraphDirections.actionGlobalSupportScreenFragment())
                    }
                    return@setOnItemSelectedListener true
                }
                else -> if (item.itemId == R.id.nav_device) {
                    navigate(NavGraphDirections.actionGlobalDeviceListFragment())
                    return@setOnItemSelectedListener true
                } else {
                    return@setOnItemSelectedListener false
                }
            }
        }
        bottomNav.setOnItemReselectedListener {
            // do nothing
        }
    }

    private fun initializeToolbar(navController: NavController) {
        // Setup toolbar
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.helloFragment,
                R.id.supportMessagesFragment,
                R.id.supportDeviceFragment,
                R.id.supportScreenFragment,
                R.id.supportVideoFragment
            )
        )
        setSupportActionBar(toolbar)
        setupActionBarWithNavController(navController, appBarConfiguration)

        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_disconnect_from_device -> {
                    if (rsViewModel.connectionStatus.value == ConnectionStatus.Connected) {
                        handleDeviceDisconnect(false)
                    }
                }
                R.id.action_end_support_session -> confirmSessionDisconnect()
            }
            true
        }

        rsViewModel.connectionStatus.subscribe(this, Lifecycle.State.STARTED) {
            bottomNav.visibility = when (it) {
                ConnectionStatus.Connected, ConnectionStatus.Connecting -> View.VISIBLE
                ConnectionStatus.Disconnected -> View.GONE
            }

            if (it == ConnectionStatus.Disconnected) {
                val mi = toolbar.menu.findItem(R.id.action_end_support_session)
                mi?.isVisible = false
                navController.popBackStack(R.id.pinFragment, false)
            }
            // Send device data if the support session just connected and there's a connected device
            else if (it == ConnectionStatus.Connected) {
                val mi = toolbar.menu.findItem(R.id.action_end_support_session)
                mi?.isVisible = true
                launch { rsViewModel.sendDeviceData() }
            }
        }

        viewModel.selectedDevice.subscribe(this, Lifecycle.State.STARTED) {
            val mi = toolbar.menu.findItem(R.id.action_disconnect_from_device)
            mi?.isVisible = it != null
        }

        navController.addOnDestinationChangedListener { controller, destination, arguments ->
            // Show / hide action bar menu items
            when (destination.id) {
                R.id.selectedDeviceFragment -> {
                    supportActionBar?.title = viewModel.selectedDevice.value?.displayName ?: "Unknown"
                    val mi = toolbar.menu.findItem(R.id.action_disconnect_from_device)
                    mi?.isVisible = true
                }
                else -> {}
            }

            // Show / hide action bar
            when (destination.id) {
                R.id.helloFragment -> supportActionBar?.hide()
                else -> supportActionBar?.show()
            }

            // Show / hide bottom nav visibility
            bottomNav.visibility = when (rsViewModel.connectionStatus.value) {
                ConnectionStatus.Connected, ConnectionStatus.Connecting -> View.VISIBLE
                ConnectionStatus.Disconnected -> View.GONE
            }

            // Set keyboard resize mode
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED)
        }
    }

    /**
     * Displays an alert dialog when user clicks the disconnect button
     */
    private fun confirmSessionDisconnect() {
        AlertDialog.Builder(this)
            .setTitle(R.string.session_disconnect_confirm)
            .setMessage(R.string.session_disconnect_request)
            .setNeutralButton(R.string.cancel) { _, _ -> }
            .setPositiveButton(R.string.session_end) { _, _ ->
                runBlocking {
                    rsViewModel.disconnect()
                }

            }
            .show()
    }

    private fun initializeRemoteSupport() {
        RemoteSupport.setScreenCaptureInitializer(AppCompatScreenCaptureInitializer(this))
        RemoteSupport.configureRemoteSupport(
            App.app.remoteSupportUrl,
            App.app.remoteSupportApiKey)


        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                rsViewModel.requestDeviceConnect.subscribe(this) {
                    handleDeviceConnect(it.device, it.context)
                }
                rsViewModel.requestDeviceDisconnect.subscribe(this) {
                    handleDeviceDisconnect()
                }
                rsViewModel.requestScreenShare.subscribe(this) {
                    handleScreenShare(it.context)
                }
                rsViewModel.requestVideoShare.subscribe(this) {
                    handleVideoShare(it.context)
                }

                rsViewModel.requestStartService.subscribe(this){
                    RemoteSupport.startService(applicationContext)
                }

                rsViewModel.requestStopService.subscribe(this){
                    RemoteSupport.stopService(applicationContext)
                }

                Events.onProductKeyUpdated.subscribe(this) { key ->
                    RemoteSupport.stopService(applicationContext)
                    RemoteSupport.configureRemoteSupport(App.app.remoteSupportUrl, key ?: "")
                }
            }
        }
    }

    private fun handleVideoShare(context: ICommandContext) {
        val isVideoFragment = supportFragmentManager.currentNavigationFragment is SupportVideoFragment
        MaterialAlertDialogBuilder(this)
            .setCancelable(false)
            .setMessage("Agent is requesting you to share your video.")
            .setNegativeButton("DENY") { dialogInterface: DialogInterface, i: Int ->
                lifecycleScope.launch { context.error(MessageErrors.MediaShareError) }
                dialogInterface.dismiss()
            }
            .setPositiveButton("ALLOW") { dialogInterface: DialogInterface, i: Int ->
                if (isVideoFragment) {
                    lifecycleScope.launch {
                        rsViewModel.requestResumeVideoShare.emit(context.commandId)
                    }
                } else {
                    bottomNav.selectedItemId = R.id.nav_video_share
                    navigate(NavGraphDirections.actionGlobalSupportVideoFragment(context.commandId))
                }
                dialogInterface.dismiss()
            }.show()
    }

    private fun handleScreenShare(context: ICommandContext) {
        val isScreenFragment =
            supportFragmentManager.currentNavigationFragment is SupportScreenFragment
        MaterialAlertDialogBuilder(this)
            .setCancelable(false)
            .setMessage("Agent is requesting you to share your screen.")
            .setNegativeButton("DENY") { dialogInterface: DialogInterface, i: Int ->
                lifecycleScope.launch { context.error(MessageErrors.MediaShareError) }
                dialogInterface.dismiss()
            }
            .setPositiveButton("ALLOW") { dialogInterface: DialogInterface, i: Int ->
                if (isScreenFragment) {
                    lifecycleScope.launch {
                        rsViewModel.requestResumeScreenShare.emit(context.commandId)
                    }
                } else {
                    bottomNav.selectedItemId = R.id.nav_screen_share
                    navigate(NavGraphDirections.actionGlobalSupportScreenFragment(context.commandId))
                }
                dialogInterface.dismiss()
            }.show()
    }

    private suspend fun handleDeviceConnect(device: String, context: ICommandContext) {
        if (viewModel.deviceConnectionStatus.value == DeviceConnectionState.connected) {
            context.error(MessageErrors.InvalidState)
            return
        }

        (context as? CommandContextWrapper)?.reset()

        MaterialAlertDialogBuilder(this)
            .setMessage("Agent is requesting to use Bluetooth to connect to a device.")
            .setNegativeButton("DENY") { dialogInterface: DialogInterface, i: Int ->
                lifecycleScope.launch { context.error(MessageErrors.DeviceConnectionError) }
                dialogInterface.dismiss()
            }
            .setPositiveButton("ALLOW") { dialogInterface: DialogInterface, i: Int ->
                lifecycleScope.launch {
                    if (!viewModel.selectDevice(device)) {
                        context.error(
                            MessageErrors.DeviceConnectionError.value,
                            "Unable to select device"
                        )
                        return@launch
                    }

                    (context as? CommandContextWrapper)?.reset()
                    when (val err = viewModel.connectDevice()) {
                        is BleOperation.Result.Success -> {
                            navigate(NavGraphDirections.actionGlobalSupportDeviceFragment())
                            context.acknowledge()
                        }
                        is BleOperation.Result.Failure -> {
                            context.error(
                                MessageErrors.DeviceConnectionError.value,
                                err.error
                            )
                        }
                    }
                }
                dialogInterface.dismiss()
            }.show()
    }

    private fun handleDeviceDisconnect(fromPeer: Boolean = true) {
        viewModel.disconnectDevice()
        viewModel.clearDevice()
        if (fromPeer) return
        lifecycleScope.launch {
            rsViewModel.sendDeviceDisconnect()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        // Fragments that should conditionally navigate up
        when (val fragment = supportFragmentManager.currentNavigationFragment) {
            is ConditionalBackFragment -> if (!fragment.onUpPressed()) return false
        }

        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onBackPressed() {
        // Fragments that should conditionally go back
        when (val fragment = supportFragmentManager.currentNavigationFragment) {
            is ConditionalBackFragment -> if (!fragment.onBackPressed()) return
        }

        super.onBackPressed()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.top_app_bar, menu)
        return super.onCreateOptionsMenu(menu)
    }

}

/**
 * The currently displayed fragment in the navigation stack
 */
val FragmentManager.currentNavigationFragment: Fragment?
    get() = primaryNavigationFragment?.childFragmentManager?.fragments?.firstOrNull()