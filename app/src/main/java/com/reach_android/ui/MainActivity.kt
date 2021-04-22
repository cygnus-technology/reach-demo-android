package com.reach_android.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.reach_android.R
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val rsViewModel: RemoteSupportViewModel by viewModels()

    private val navController: NavController
        get() = findNavController(R.id.nav_host)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup toolbar
        val appBarConfig = AppBarConfiguration(setOf(R.id.deviceListFragment))
        setSupportActionBar(toolbar)
        toolbar.setupWithNavController(navController, appBarConfig)
        searchView.setOnQueryTextListener(viewModel)
        navController.addOnDestinationChangedListener { controller, destination, arguments ->
            toolbar.visibility =
                    if (destination.id == R.id.selectedDeviceFragment) View.VISIBLE
                    else View.GONE
            searchView.visibility =
                    if (destination.id == R.id.deviceListFragment) View.VISIBLE
                    else View.GONE
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED)

            when (destination.id) {
                R.id.deviceListFragment -> {
                    viewModel.disconnectDevice()
                }
                R.id.selectedDeviceFragment -> {
                    toolbar.title = viewModel.selectedDevice?.name?: "Unknown"
                    rsViewModel.disconnect()
                }
            }
        }
    }

    override fun onSupportNavigateUp() = navController.navigateUp()

    override fun onBackPressed() {
        when (navController.currentDestination?.id) {
            R.id.supportFragment -> {}
            else -> super.onBackPressed()
        }
    }
}