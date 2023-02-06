package com.reach_android

import android.app.Application
import android.util.Base64
import android.util.Log
import androidx.annotation.RawRes
import com.amplifyframework.AmplifyException
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.core.Amplify
import com.amplifyframework.core.AmplifyConfiguration
import com.google.gson.*
import java.lang.reflect.Type
import java.util.*

class App : Application() {

    val remoteSupportUrl by lazy {
        getString(R.string.cygnus_uri)
    }

    val remoteSupportApiKey by lazy {
        getString(R.string.cygnus_key)
    }

    @RawRes val configId: Int = R.raw.release_config

    override fun onCreate() {
        reachApp = this
        com.cygnusreach.RemoteSupportClient.initialize(applicationContext)
        super.onCreate()

        // Initialize Cognito
        try {
            val config = AmplifyConfiguration
                .builder(applicationContext, configId)
                .devMenuEnabled(false)
                .build()
            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.configure(config, applicationContext)
        } catch (error: AmplifyException) {
            Log.e("Reach", "Could not initialize Amplify", error)
        }
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
        val NAME_DESCRIPTOR_ID = UUID.fromString(App.NAME_DESCRIPTOR_UUID)

        /** String representation of fileprovider location */
        const val FILE_PROVIDER = "com.reach_android.fileprovider"

        val serializer by lazy {
            GsonBuilder().registerTypeHierarchyAdapter(
                ByteArray::class.java,
                object : JsonSerializer<ByteArray>, JsonDeserializer<ByteArray> {
                    override fun serialize(
                        src: ByteArray?,
                        typeOfSrc: Type?,
                        context: JsonSerializationContext?
                    ): JsonElement {
                        return JsonPrimitive(Base64.encodeToString(src, Base64.NO_WRAP))
                    }

                    override fun deserialize(
                        json: JsonElement?,
                        typeOfT: Type?,
                        context: JsonDeserializationContext?
                    ): ByteArray {
                        return Base64.decode(json?.asString, Base64.NO_WRAP)
                    }

                }).create()
        }
    }
}