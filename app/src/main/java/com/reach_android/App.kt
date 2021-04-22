package com.reach_android

import android.app.Application

class App : Application() {

    val remoteSupportUrl by lazy {
        getString(R.string.prod_cygnus_uri)
    }

    override fun onCreate() {
        reachApp = this
        super.onCreate()
    }

    companion object {
        /** The running [App] **/
        private lateinit var reachApp: App

        /** The running [App] **/
        val app: App get() = reachApp

        /** Code received on location permissions callback */
        const val LOCATION_PERMISSION_REQUEST_CODE = 1

        /** Code received on camera permissions callback */
        const val CAMERA_PERMISSION_REQUEST_CODE = 2

        /** UUID for the characteristic descriptor containing the name/description */
        const val NAME_DESCRIPTOR_UUID = "00002901-0000-1000-8000-00805f9b34fb"

        /** String representation of fileprovider location */
        const val FILE_PROVIDER = "com.reach_android.fileprovider"
    }
}