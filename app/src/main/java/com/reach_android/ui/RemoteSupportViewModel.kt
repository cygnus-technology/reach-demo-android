package com.reach_android.ui

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.core.util.forEach
import androidx.lifecycle.*
import com.cygnusreach.*
import com.cygnusreach.internal.chunking.MessageErrorEventArgs
import com.cygnusreach.internal.chunking.PartialMessageReceivedEventArgs
import com.cygnusreach.messages.*
import com.google.gson.Gson
import com.reach_android.App
import com.reach_android.bluetooth.BleManager
import com.reach_android.bluetooth.BleManager.canNotify
import com.reach_android.bluetooth.BleManager.canRead
import com.reach_android.bluetooth.BleManager.canWrite
import com.reach_android.bluetooth.BleManager.getCharacteristic
import com.reach_android.bluetooth.BleManager.isConnected
import com.reach_android.bluetooth.BleOperation
import com.reach_android.bluetooth.hexData
import com.reach_android.bluetooth.name
import com.reach_android.model.*
import com.reach_android.model.remotesupport.*
import com.reach_android.model.remotesupport.MessageCategory
import com.reach_android.repository.ChatRepository
import com.reach_android.repository.DeviceRepository
import kotlinx.coroutines.*
import java.io.*
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

class RemoteSupportViewModel : ViewModel() {

    private var rs: IRemoteSupportClient? = null
    private val connStatus = MutableLiveData(ConnectionStatus.Disconnected)
    val connectionStatus: LiveData<ConnectionStatus> = connStatus

    /**
     * Stores the logger for all remote support sessions. Broadcasts each logged message
     */
    val logger = ObservableLogger()

    /**
     * Stores the selected image to send to the peer
     */
    var selectedImage: Uri? = null

    /**
     * Stores the selected video to send to the peer
     */
    var selectedVideo: Uri? = null

    private val selectedDevice: BleDevice? get() = DeviceRepository.selectedDevice

    /**
     * Connects to a remote support session given a PIN. Emits an error message if applicable,
     * otherwise a null value will denote a successful connection
     */
    @ExperimentalCoroutinesApi
    fun connectToSupport(pin: String): LiveData<String?> {
        val data = MutableLiveData<String?>()
        val client = this.rs ?: RemoteSupportClient.createKotlin(
                App.app.baseContext,
                viewModelScope,
                App.app.remoteSupportUrl,
                "", // TODO: Your API key here
                logger)

        viewModelScope.launch(Dispatchers.Default) {
            client.onConnect.subscribe {
                data.postValue(null)
                handleConnect()
            }
            client.onDisconnect.subscribe(::handleDisconnect)
            client.onPartialMessage.subscribe(::handlePartialMessage)
            client.onNotification.subscribe(::handleNotification)
            client.onCommand.subscribe(::handleCommand)
            client.onQuery.subscribe(::handleQuery)
            client.onMessageError.subscribe(::handleMessageError)
            val result = runCatching { client.connectToSupportSession(pin) }

            if (result.isFailure) {
                data.postValue("Could not connect to session")
            }
        }

        rs = client
        return data
    }

    fun disconnect() {
        rs?.close()
        cleanup()
    }

    private fun cleanup() {
        rs = null
    }


    /***********************************/
    /******** Data Transmission ********/
    /***********************************/

    fun sendChat(text: String, drawable: Drawable?): LiveData<String?> {
        val data = MutableLiveData<String?>()

        viewModelScope.launch(Dispatchers.IO) {
            val imageUri = selectedImage
            val videoUri = selectedVideo
            if (imageUri != null) {
                // Send image
                val bitmap = drawable?.toBitmap()
                val message = ChatMessage(true, image = imageUri)
                val stream = ByteArrayOutputStream()
                bitmap?.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                val bytes = stream.toByteArray()
                // We don't have enough memory to store greater than a 30 MB image in the
                // remote support flatbuffer structs
                Log.d("RemoteSupportViewModel", "Sending ${bytes.size} byte image")
                rs?.sendBytes(ByteBuffer.wrap(bytes), MessageCategory.Image.raw, "image/jpeg")
                ChatRepository.newMessage(message)
            } else if (videoUri != null) {
                // Send video
                val result = runCatching {
                    val message = ChatMessage(true, video = videoUri)
                    App.app.applicationContext.contentResolver.openInputStream(videoUri)?.buffered()?.use {
                            val bytes = it.readBytes()
                            // We don't have enough memory to store greater than a 30 MB video in the
                            // remote support flatbuffer structs
                            Log.d("RemoteSupportViewModel", "Sending ${bytes.size} byte video")
                            rs?.sendBytes(ByteBuffer.wrap(bytes), MessageCategory.Video.raw, "video/mp4")
                            ChatRepository.newMessage(message)
                        } ?: run {
                        data.postValue("Not able to process selected video")
                        return@launch
                    }
                }

                if (result.isFailure) {
                    data.postValue(result.exceptionOrNull()?.localizedMessage?: "Failed to process selected video")
                    return@launch
                }
            }

            if (text.isNotEmpty()) {
                // Send text
                val message = ChatMessage(true, text = text)
                rs?.sendChat(text)
                ChatRepository.newMessage(message)
            }

            data.postValue(null)
        }

        return data
    }

    fun sendDiagnosticData() {
        val device = selectedDevice ?: return
        val status = device.connectionStatusObservable.value?.description ?: return
        val json = Gson().toJson(DiagnosticHeartbeat(device.rssi, status))
        val buffer = ByteBuffer.wrap(json.toByteArray())
        val notification = Notification(buffer, DefaultTag.Object.get(), MessageCategory.DiagnosticHeartbeat.raw)
        viewModelScope.launch(Dispatchers.IO) {
            if (rs?.isConnected == true) {
                rs?.sendNotification(notification)
            }
        }
    }

    fun sendDeviceData() {
        val device = selectedDevice ?: return
        val gatt = BleManager.gattMap[device.uuid] ?: return
        val advertisementInfo = hashMapOf<String, String>()
        val services = gatt.services.map { service ->
            ServiceInfo(
                service.uuid.toString(),
                service.characteristics.map {
                    CharacteristicInfo(
                        it.uuid.toString(),
                        it.canRead(),
                        it.canWrite(),
                        it.canNotify(),
                        it.name,
                        it.value?.joinToString("") { value ->
                            String.format("%02x", value)
                        })
                })
        }

        // Add advertisement data
        device.advertisedName?.let { name ->
            advertisementInfo["Advertised name"] = name
        }

        device.manufacturerData?.forEach { key, value ->
            val hex = value.joinToString("") { String.format("%02X", it) }
            val companyName = BleManager.knownCompanyIDs[key]
            val manVal = "${companyName ?: "Unknown Company"} (${String.format("0x%04X", key)}):\n0x$hex"
            advertisementInfo["Manufacturer specific data"] = manVal
        }

        device.scanBytes?.let { bytes ->
            val hex = bytes.joinToString("") { String.format("%02X", it) }
            advertisementInfo["Raw advertisement packet"] = "0x$hex"
        }

        val deviceInfo = DeviceData(device.name ?: "Unknown", advertisementInfo, services)
        val json = Gson().toJson(deviceInfo)
        val buffer = ByteBuffer.wrap(json.toByteArray())
        val notification = Notification(buffer, DefaultTag.Object.get(), MessageCategory.DeviceData.raw)

        viewModelScope.launch(Dispatchers.IO) {
            if (rs?.isConnected == true) {
                rs?.sendNotification(notification)
            }
        }
    }


    /********************************/
    /******** Event Handlers ********/
    /********************************/

    private fun handleConnect() {
        Log.d("SupportConnection", "Connected to support")
        connStatus.postValue(ConnectionStatus.Connected)
    }

    private fun handleDisconnect(args: DisconnectEventArgs) {
        Log.d("SupportConnection", "Disconnected from support, Expected: ${args.expected}")
        if (args.expected) {
            connStatus.postValue(ConnectionStatus.Disconnected)
            cleanup()
        } else {
            connStatus.postValue(ConnectionStatus.Connecting)
        }
    }

    private suspend fun handlePartialMessage(args: PartialMessageReceivedEventArgs) {
        if(args.category == DefaultCategory.Chat.get()
            || args.category == MessageCategory.Image.raw
            || args.category == MessageCategory.Video.raw) {

            if (ChatRepository.getMessage(args.msgid) != null) return
            val message = ChatMessage(sent = false, loading = true, args.msgid)
            ChatRepository.newMessage(message)
        }
    }

    private suspend fun handleNotification(args: NotificationEventArgs) {
        when (args.notification.category) {
            DefaultCategory.Chat.get() -> {
                val text = Charsets.UTF_8.decode(args.notification.data).toString()
                val message = ChatMessage(false, text = text)
                ChatRepository.replaceMessage(args.msgid, message)
            }
            MessageCategory.Image.raw -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val result = runCatching {
                        val buffer = args.notification.data
                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        val storageDir = App.app.applicationContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                        val file = File.createTempFile(
                            "received_${timeStamp}",
                            ".jpg",
                            storageDir)
                        FileOutputStream(file).channel.use {
                            it.write(buffer)
                            val message = ChatMessage(false, image = file.toUri())
                            ChatRepository.replaceMessage(args.msgid, message)
                        }
                    }

                    if (result.isFailure) {
                        ChatRepository.deleteMessage(args.msgid)
                        Log.e("SupportFragment",
                              "Failed to save received image to file",
                              result.exceptionOrNull())
                    }
                }
            }
            MessageCategory.Video.raw -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val result = runCatching {
                        val buffer = args.notification.data
                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        val storageDir = App.app.applicationContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                        val file = File.createTempFile("capture_${timeStamp}", ".jpg", storageDir)
                        FileOutputStream(file).channel.use { it.write(buffer) }
                        val uri = FileProvider.getUriForFile(App.app.applicationContext,
                            App.FILE_PROVIDER, file)
                        val message = ChatMessage(false, video = uri)
                        ChatRepository.replaceMessage(args.msgid, message)
                    }

                    if (result.isFailure) {
                        ChatRepository.deleteMessage(args.msgid)
                        Log.e("SupportFragment",
                              "Failed to save received video to file",
                              result.exceptionOrNull())
                    }
                }
            }
            MessageCategory.DiagnosticHeartbeat.raw,
            MessageCategory.DeviceData.raw -> {}
            else -> {
                Log.e("RemoteSupportViewModel", "Received unknown notification category: ${args.notification.category}")
            }
        }
    }

    private fun handleCommand(args: CommandEventArgs) {
        viewModelScope.launch(Dispatchers.IO) {
            when (args.command.category) {
                MessageCategory.BluetoothWriteRequest.raw -> {
                    val device = selectedDevice ?: return@launch
                    val inputStream = ByteBufferInputStream(args.command.data)
                    val request = try {
                        Gson().fromJson( InputStreamReader(inputStream), BluetoothWriteRequest::class.java)
                    } catch (ex: Exception) {
                        args.context.error(500, "Could not parse command data")
                        return@launch
                    }
                    val value =
                        if (request.encoding == BluetoothWriteRequest.Encoding.Utf8) request.value.toByteArray()
                        else request.value.hexData

                    if (device.device.isConnected() && value != null) {
                        val uuid = UUID.fromString(request.uuid)
                        when (val result = BleManager.writeCharacteristic(device.device, uuid, value)) {
                            is BleOperation.Result.Success -> args.context.acknowledge()
                            is BleOperation.Result.Failure -> args.context.error(500, result.error)
                        }
                    } else {
                        val message =
                            if (value == null) "Invalid value, cannot write to characteristic"
                            else "Not connected to device"

                        args.context.error(500, message)
                    }
                }
                MessageCategory.BluetoothNotifyRequest.raw -> {
                    val device = selectedDevice ?: return@launch
                    val inputStream = ByteBufferInputStream(args.command.data)
                    val request = try {
                        Gson().fromJson(InputStreamReader(inputStream), BluetoothNotifyRequest::class.java)
                    } catch (ex: Exception) {
                        args.context.error(500, "Could not parse command data")
                        return@launch
                    }

                    if (device.device.isConnected()) {
                        val uuid = UUID.fromString(request.uuid)
                        when (val result = BleManager.setNotify(device.device, uuid, request.setNotify)) {
                            is BleOperation.Result.Success -> args.context.acknowledge()
                            is BleOperation.Result.Failure -> args.context.error(500, result.error)
                        }
                    } else {
                        args.context.error(500, "Not connected to device")
                    }
                }
                else -> {
                    Log.e("RemoteSupportViewModel", "Received unknown command category: ${args.command.category}")
                    args.context.error(500, "Unknown command category")
                }
            }
        }
    }

    private fun handleQuery(args: QueryEventArgs) {
        viewModelScope.launch(Dispatchers.IO) {
            when (args.query.category) {
                MessageCategory.BluetoothReadRequest.raw -> {
                    val device = selectedDevice ?: return@launch
                    val inputStream = ByteBufferInputStream(args.query.data)
                    val request = try {
                        Gson().fromJson(InputStreamReader(inputStream), BluetoothReadRequest::class.java)
                    } catch (ex: Exception) {
                        args.context.error(500, "Could not parse command data")
                        return@launch
                    }

                    if (device.device.isConnected()) {
                        val uuid = UUID.fromString(request.uuid)
                        when (val result = BleManager.readCharacteristic(device.device, uuid)) {
                            is BleOperation.Result.Success -> {
                                BleManager.gattMap[device.uuid]
                                    ?.getCharacteristic(uuid)?.value
                                    ?.joinToString("") { value ->
                                        String.format("%02x", value)
                                    }?.let {
                                        val response = BluetoothReadResponse(it)
                                        val json = Gson().toJson(response)
                                        val buffer = ByteBuffer.wrap(json.toByteArray())
                                        args.context.respond(DefaultTag.Object.get(), buffer, true)
                                    } ?: run {
                                    args.context.error(500, "Failed to read value from characteristic")
                                }
                            }
                            is BleOperation.Result.Failure -> args.context.error(500, result.error)
                        }
                    } else {
                        args.context.error(500, "Not connected to device")
                    }
                }
                else -> {
                    Log.e("RemoteSupportViewModel", "Received unknown query category: ${args.query.category}")
                    args.context.error(500, "Unknown query category")
                }
            }
        }
    }

    private suspend fun handleMessageError(args: MessageErrorEventArgs) {
        Log.e("Reach", "Message error")
        ChatRepository.deleteMessage(args.messageId)
    }
}