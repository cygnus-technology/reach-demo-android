package com.reach_android.ui

import android.bluetooth.BluetoothGatt
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.core.util.forEach
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cygnusreach.IClientProxy
import com.cygnusreach.RemoteSupport
import com.cygnusreach.RemoteSupportClientConnectionState
import com.cygnusreach.buffers.asInputStream
import com.cygnusreach.events.*
import com.cygnusreach.exceptions.CygnusReachException
import com.cygnusreach.media.*
import com.cygnusreach.messages.*
import com.google.gson.JsonParser
import com.reach_android.App
import com.reach_android.bluetooth.*
import com.reach_android.bluetooth.BleManager.canNotify
import com.reach_android.bluetooth.BleManager.canRead
import com.reach_android.bluetooth.BleManager.canWrite
import com.reach_android.bluetooth.BleManager.isConnected
import com.reach_android.model.BleDevice
import com.reach_android.model.ConnectionStatus
import com.reach_android.model.ObservableLogger
import com.reach_android.model.remotesupport.*
import com.reach_android.repository.ChatRepository
import com.reach_android.repository.DeviceRepository
import com.reach_android.tickerFlow
import com.reach_android.ui.support.CommandContextWrapper
import com.reach_android.util.*
import com.reach_android.util.subscribe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.SurfaceViewRenderer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.round
import kotlin.math.sqrt

data class DeviceConnectEventArgs(val device: String, val context: ICommandContext)
data class MediaShareEventArgs(val context: ICommandContext)

class RemoteSupportViewModel : ViewModel() {
    val hasVideo: Boolean
        get() = videoController.state.value?.isStarted ?: false

    val hasScreenShare: Boolean
        get() = screenController.state.value?.isStarted ?: false

    private val rs = ResettableLazyAsync(
        viewModelScope,
        { connectToRemoteSupport() },
        { it.close() })

    private val requestStartServiceFlow = MutableSharedFlow<Unit>()
    val requestStartService = requestStartServiceFlow.asSharedFlow()

    private val requestStopServiceFlow = MutableSharedFlow<Unit>()
    val requestStopService = requestStopServiceFlow.asSharedFlow()

    private val connStatus = MutableStateFlow(ConnectionStatus.Disconnected)
    val connectionStatus = connStatus.asStateFlow()

    private val peerDisconnectedFlow = MutableSharedFlow<Unit>()
    val peerDisconnected = peerDisconnectedFlow.asSharedFlow()

    private val requestDeviceConnectFlow = MutableSharedFlow<DeviceConnectEventArgs>()
    val requestDeviceConnect = requestDeviceConnectFlow.asSharedFlow()

    private val requestDeviceDisconnectFlow = MutableSharedFlow<Unit>()
    val requestDeviceDisconnect = requestDeviceDisconnectFlow.asSharedFlow()

    private val requestScreenShareFlow = MutableSharedFlow<MediaShareEventArgs>()
    val requestScreenShare = requestScreenShareFlow.asSharedFlow()

    private val requestVideoShareFlow = MutableSharedFlow<MediaShareEventArgs>()
    val requestVideoShare = requestVideoShareFlow.asSharedFlow()

    private val requestVideoShareStopFlow = MutableSharedFlow<Unit>()
    val requestVideoShareStop = requestVideoShareStopFlow.asSharedFlow()

    private val requestScreenShareStopFlow = MutableSharedFlow<Unit>()
    val requestScreenShareStop = requestScreenShareStopFlow.asSharedFlow()

    private val clientConnectionStateChangedFlow =
        MutableStateFlow(RemoteSupportClientConnectionState.NotConnected)
    val clientConnectionStateChanged = clientConnectionStateChangedFlow.asStateFlow()

    private val clientDisconnectedFlow = MutableSharedFlow<Unit>()
    val clientDisconnected = clientDisconnectedFlow.asSharedFlow()


    val requestResumeVideoShare = MutableSharedFlow<String>()

    val requestResumeScreenShare = MutableSharedFlow<String>()

    /**
     * Stores the logger for all remote support sessions. Broadcasts each logged message
     */
    val logger = ObservableLogger(viewModelScope)

    /**
     * Stores the selected image to send to the peer
     */
    var selectedImage: Uri? = null

    /**
     * Stores the selected video to send to the peer
     */
    var selectedVideo: Uri? = null

    private val selectedDevice: BleDevice? get() = DeviceRepository.selectedDevice.value
    private var deviceObservable: Job? = null
    private val diagnosticSupervisor = SupervisorJob()

    private val activeCommands = ConcurrentHashMap<String, CommandContextWrapper>()
    fun getCommandContext(id: String): ICommandContext? = activeCommands[id]
    fun saveCommandContext(context: ICommandContext): ICommandContext {
        val tmp = CommandContextWrapper(context, viewModelScope) {
            activeCommands.remove(context.commandId)
        }
        activeCommands[context.commandId] = tmp
        return tmp
    }

    init {
        DeviceRepository.selectedDevice.subscribe(viewModelScope) { device ->
            deviceObservable?.cancel()
            deviceObservable = device?.connectionStatusObservable?.subscribe(this) {
                if (it == ConnectionStatus.Connected) {
                    if (connStatus.value != ConnectionStatus.Disconnected) {
                        sendDeviceData()
                    }
                    tickerFlow(5000).subscribe(this + diagnosticSupervisor) {
                        if (connStatus.value != ConnectionStatus.Disconnected) {
                            sendDiagnosticData()
                        }
                    }
                } else {
                    diagnosticSupervisor.cancelChildren()
                }
            }
        }
    }

    suspend fun addScreenSharing(): Boolean {
        screenController.state.value?.let { controller ->
            controller.start()
            return true
        }

        return rs.get().let { client ->
            App.app.resources.displayMetrics.let { display ->
                client.addScreenSharing(
                    ScreenFormat(
                        RemoteSupport.getScreenCaptureInitializer(),
                        scaleDimensions(display.widthPixels, display.heightPixels)
                    )
                )
            }
        }
    }

    /** WebRTC video codecs are only tested on sizes aligned to 16x16 */
    private fun align(size: Int) = 16 * (size / 16)

    /**
     * Scale dimensions to fit in the same area as a Full HD image while
     * retaining original ratio
     */
    private fun scaleDimensions(widthPixels: Int, heightPixels: Int): FrameSize {
        val fullHD = 1920 * 1080
        val area = widthPixels * heightPixels
        if (area <= fullHD) {
            return FrameSize(align(widthPixels), align(heightPixels / 16))
        }

        val scale = sqrt(fullHD.toDouble() / area.toDouble())

        return FrameSize(
            align(round(widthPixels * scale).toInt()),
            align(round(heightPixels * scale).toInt())
        )
    }

    suspend fun addVideo(): Boolean {
        videoController.state.value?.let { controller ->
            return controller.start()
        }

        return rs.get().let { client ->
            val fullHD = 1920 * 1080

            val camera = CameraInfo
                .getCameras(App.app)
                .first { !it.isFrontFacing }
            val format = camera.formats
                .filter {
                    (it.frameSize.width % 16 == 0) && (it.frameSize.height % 16 == 0)
                            && it.frameSize.size <= fullHD
                }
                .maxByOrNull { it.frameSize.size }

            format?.let { f ->
                client.addVideoStream(camera.withFormat(f))
            } ?: false
        }
    }

    val lastVideoSink = ResettableDeferred<SurfaceViewRenderer>()

    suspend fun addVideoSink(control: SurfaceViewRenderer) {
        lastVideoSink.set(control)
        videoController.state.value?.addSink(control)
    }

    suspend fun removeVideoSink(control: SurfaceViewRenderer) {
        lastVideoSink.reset()
        videoController.state.value?.removeSink(control)
    }

    suspend fun removeVideo() {
        videoController.state.value?.let { c ->
            lastVideoSink.reset()?.let { c.removeSink(it) }
            c.stop()
        }
    }

    fun removeScreen() {
        screenController.state.value?.stop()
    }

    private val screenController = ResettableDeferred<IScreenCaptureController>()
    private val videoController = ResettableDeferred<ICameraStreamController>()

    private suspend fun connectToRemoteSupport(): IClientProxy {
        requestStartServiceFlow.emit(Unit)
        return RemoteSupport.getProxy(viewModelScope).also { client ->
            client.updateLogger(logger)
            client.onDisconnect.subscribe(viewModelScope) { handleDisconnect(it) }
            client.onPartialMessage.subscribe(viewModelScope) { handlePartialMessage(it) }
            client.onNotification.subscribe(viewModelScope) { handleNotification(it) }
            client.onCommand.subscribe(viewModelScope) { handleCommand(it) }
            client.onQuery.subscribe(viewModelScope) { handleQuery(it) }
            client.onMessageError.subscribe(viewModelScope) { handleMessageError(it) }
            client.onScreenCapture.subscribe(viewModelScope) {
                screenController.set(it.controller)
                it.controller.start()
            }
            client.onVideoCapture.subscribe(viewModelScope) {
                videoController.set(it.controller)
                lastVideoSink.state.value?.let { addVideoSink(it) }
                it.controller.start()
            }

            connStatus.value = when (client.connectionState.value) {
                RemoteSupportClientConnectionState.Connected -> ConnectionStatus.Connected
                RemoteSupportClientConnectionState.Connecting -> ConnectionStatus.Connecting
                else -> ConnectionStatus.Disconnected
            }
        }
    }

    /**
     * Connects to a remote support session given a PIN. Emits an error message if applicable,
     * otherwise a null value will denote a successful connection
     */
    suspend fun connectToSupport(pin: String) = suspendCoroutine<String?> { promise ->
        viewModelScope.launch {
            val client = rs.get()
            val job = client.onConnect.once(viewModelScope) {
                promise.resume(null)
                handleConnect()
            }
            try {
                client.connectToSupportSession(pin)
            } catch (err: CygnusReachException) {
                job.cancel()
                promise.resume(err.localizedMessage)
            }
        }
    }

    suspend fun disconnect() {
        cleanup()
        requestStopServiceFlow.emit(Unit)
        connStatus.value = ConnectionStatus.Disconnected
    }

    private suspend fun cleanup() {
        removeScreen()
        removeVideo()
        videoController.reset()
        screenController.reset()
        rs.reset()
    }


    /***********************************/
    /******** Data Transmission ********/
    /***********************************/

    suspend fun sendChat(text: String, drawable: Drawable?): String? {
        rs.get().let { rs ->
            when (connectionStatus.value) {
                ConnectionStatus.Disconnected -> return "Remote support client is not connected"
                ConnectionStatus.Connecting -> return "Remote support client is connecting"
                else -> {}
            }

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

                withContext(Dispatchers.IO) {
                    rs.sendBytes(
                        ByteBuffer.wrap(bytes),
                        ContentTag.Image,
                        MessageCategory.Image
                    )
                }
                ChatRepository.newMessage(message)
            } else if (videoUri != null) {
                // Send video
                val result = runCatching {
                    val message = ChatMessage(true, video = videoUri)
                    App.app.applicationContext.contentResolver.openInputStream(videoUri)
                        ?.buffered()
                        ?.use {
                            val bytes = it.readBytes()
                            // We don't have enough memory to store greater than a 30 MB video in the
                            // remote support flatbuffer structs
                            Log.d("RemoteSupportViewModel", "Sending ${bytes.size} byte video")
                            withContext(Dispatchers.IO) {
                                rs.sendBytes(
                                    ByteBuffer.wrap(bytes),
                                    ContentTag.Video,
                                    MessageCategory.Video
                                )
                            }
                            ChatRepository.newMessage(message)
                        } ?: run {
                        return "Not able to process selected video"
                    }
                }

                if (result.isFailure) {
                    return result.exceptionOrNull()?.localizedMessage
                        ?: "Failed to process selected video"
                }
            }

            if (text.isNotEmpty()) {
                // Send text
                val message = ChatMessage(true, text = text)
                rs.sendChat(text)
                ChatRepository.newMessage(message)
            }

            return null
        }
    }

    suspend fun sendDiagnosticData() {
        rs.get().let { rs ->
            val device = selectedDevice ?: return
            val status = device.connectionStatusObservable.value.description
            val json = App.serializer.toJson(DiagnosticHeartbeat(device.rssi.value, status))

            val notification = MessageBuilder().buildNotification(
                WellKnownTagEncoder.Json.encode(json),
                MessageCategory.DiagnosticHeartbeat
            )

            viewModelScope.launch(Dispatchers.IO) {
                if (rs.isConnected()) {
                    rs.sendNotification(notification)
                    Log.d("RemoteSupportViewModel", "Connected")
                } else {
                    Log.d("RemoteSupportViewModel", "Not connected")
                }
            }
        }
    }

    suspend fun sendDeviceDisconnect() {
        rs.get().let { rsc ->
            val notification = MessageBuilder().buildNotification(
                WellKnownTagEncoder.Empty.encode(Unit),
                MessageCategory.DisconnectFromDevice
            )
            viewModelScope.launch(Dispatchers.IO) {
                if (rsc.isConnected()) {
                    rsc.sendNotification(notification)
                }
            }
        }
    }

    /**
     * Notify the peer that video/stream sharing has started. The peer can wait for a stream
     * to be added via the SDK's callbacks initially, but a stream is never actually torn down.
     * If either side stops video/screen sharing and then the app initiates it again, the app
     * needs to let the peer know
     */
    suspend fun sendStartShare(type: MediaSharingType) {
        rs.get().let { rsc ->
            val notification = MessageBuilder().buildNotification(
                WellKnownTagEncoder.Int.encode(type.value),
                MessageCategory.StartSharing
            )
            viewModelScope.launch(Dispatchers.IO) {
                if (rsc.isConnected()) {
                    rsc.sendNotification(notification)
                }
            }
        }
    }

    /**
     * Notify the peer that video/stream sharing has stopped
     */
    suspend fun sendStopShare(type: MediaSharingType) {
        rs.get().let { rsc ->
            val notification = MessageBuilder().buildNotification(
                WellKnownTagEncoder.Int.encode(type.value),
                MessageCategory.StopSharing
            )
            viewModelScope.launch(Dispatchers.IO) {
                if (rsc.isConnected()) {
                    rsc.sendNotification(notification)
                }
            }
        }
    }

    private suspend fun sendDeviceList(query: IQueryContext) {

        if (BleManager.startScanning()) {
            delay(5000)
        }

        val devices = DeviceList(
            BleManager.devices
                .filter { it.isValid }
                .map { device ->
//                    val services = viewModelScope.async {
//                        BleManager.gattMap[device.uuid]?.let {
//                            readServices(it)
//                        }
//                    }

                    val advertisementInfo = hashMapOf<String, String>()
                    device.advertisedName?.let { name ->
                        advertisementInfo["Advertised name"] = name
                    }

                    device.manufacturerData?.forEach { key, value ->
                        val hex = value.joinToString("") { String.format("%02X", it) }
                        val companyName = BleManager.knownCompanyIDs[key]
                        val manVal =
                            "${companyName ?: "Unknown Company"} (${
                                String.format(
                                    "0x%04X",
                                    key
                                )
                            }):\n0x$hex"
                        advertisementInfo["Manufacturer specific data"] = manVal
                    }

                    device.scanBytes?.let { bytes ->
                        val hex = bytes.joinToString("") { String.format("%02X", it) }
                        advertisementInfo["Raw advertisement packet"] = "0x$hex"
                    }

                    DeviceData(
                        device.name,
                        device.uuid, device.rssi.value,
                        device.rssiBucket.value, advertisementInfo,
                         emptyList()
                    )
                }
                .sortedByDescending { it.signalStrength })

        rs.get().let {
            val response = WellKnownTagEncoder.Json.encode(App.serializer.toJson(devices))
            withContext(Dispatchers.IO) {
                query.respond(response)
            }
        }
    }

    suspend fun sendDeviceData() {
        val device = selectedDevice ?: return
        val gatt = BleManager.gattMap[device.uuid] ?: return
        val advertisementInfo = hashMapOf<String, String>()
        val services = viewModelScope.async { readServices(gatt) }

        // Add advertisement data
        device.advertisedName?.let { name ->
            advertisementInfo["Advertised name"] = name
        }

        device.manufacturerData?.forEach { key, value ->
            val hex = value.joinToString("") { String.format("%02X", it) }
            val companyName = BleManager.knownCompanyIDs[key]
            val manVal =
                "${companyName ?: "Unknown Company"} (${String.format("0x%04X", key)}):\n0x$hex"
            advertisementInfo["Manufacturer specific data"] = manVal
        }

        device.scanBytes?.let { bytes ->
            val hex = bytes.joinToString("") { String.format("%02X", it) }
            advertisementInfo["Raw advertisement packet"] = "0x$hex"
        }

        val deviceInfo = DeviceData(
            device.name,
            device.uuid, device.rssi.value,
            device.rssiBucket.value, advertisementInfo, services.await()
        )


        rs.get().let { rsc ->
            val json = App.serializer.toJson(deviceInfo)
            val notification = MessageBuilder().buildNotification(
                WellKnownTagEncoder.Json.encode(json),
                MessageCategory.DeviceData
            )
            viewModelScope.launch(Dispatchers.IO) {
                if (rsc.isConnected()) {
                    rsc.sendNotification(notification)
                }
            }
        }
    }

    private suspend fun readServices(
        gatt: BluetoothGatt
    ) = gatt.services.map { service ->
        ServiceInfo(
            service.uuid.toString(),
            service.characteristics.mapNotNull {
                val value = BleManager.readCachedCharacteristic(gatt.device, it.uuid)
                if (value is BleOperation.Result.Success && value.result != null) {
                    CharacteristicInfo(
                        it.uuid.toString(),
                        it.canRead(),
                        it.canWrite(),
                        it.canNotify(),
                        it.name,
                        value.result.formatted,
                        value.result.value, "utf-8"
                    )
                } else {
                    null
                }
            })
    }


    /********************************/
    /******** Event Handlers ********/
    /********************************/

    private fun handleConnect() {
        Log.d("SupportConnection", "Connected to support")
        connStatus.value = ConnectionStatus.Connected
    }

    private suspend fun handleDisconnect(args: DisconnectEventArgs) {
        Log.d("SupportConnection", "Disconnected from support, Expected: ${args.expected}")
        if (args.expected) {
            peerDisconnectedFlow.emit(Unit)
            disconnect()
        } else {
            connStatus.value = ConnectionStatus.Connecting
        }
    }

    private suspend fun handlePartialMessage(args: PartialMessageReceivedEventArgs) {
        if (args.category == DefaultCategory.Chat.value
            || args.category == MessageCategory.Image.value
            || args.category == MessageCategory.Video.value
        ) {
            if (ChatRepository.getMessage(args.msgid) != null) return
            val message = ChatMessage(sent = false, loading = true, args.msgid)
            ChatRepository.newMessage(message)
        }
    }

    private suspend fun handleNotification(args: NotificationEventArgs) {
        val notification = args.notification
        val data = notification.data
        when (notification.category) {
            DefaultCategory.Chat.value -> {
                data.use {
                    val text = WellKnownTagEncoder.Text.decode(it)
                    val message = ChatMessage(false, text = text)
                    ChatRepository.replaceMessage(args.msgid, message)
                }
            }
            MessageCategory.Image.value -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val result = data.use { data ->
                        runCatching {
                            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                                .format(Date())
                            val storageDir = App.app.applicationContext
                                .getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                            val file = File.createTempFile(
                                "received_${timeStamp}",
                                ".jpg",
                                storageDir
                            )
                            FileOutputStream(file).channel.use {
                                data.write(it)
                                val message = ChatMessage(false, image = file.toUri())
                                ChatRepository.replaceMessage(args.msgid, message)
                            }
                        }
                    }
                    if (result.isFailure) {
                        ChatRepository.deleteMessage(args.msgid)
                        Log.e(
                            "SupportFragment",
                            "Failed to save received image to file",
                            result.exceptionOrNull()
                        )
                    }
                }
            }
            MessageCategory.Video.value -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val result = data.use { data ->
                        runCatching {
                            val timeStamp =
                                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            val storageDir =
                                App.app.applicationContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                            val file =
                                File.createTempFile("capture_${timeStamp}", ".mp4", storageDir)
                            FileOutputStream(file).channel.use { data.write(it) }
                            val uri = FileProvider.getUriForFile(
                                App.app.applicationContext,
                                App.FILE_PROVIDER, file
                            )
                            val message = ChatMessage(false, video = uri)
                            ChatRepository.replaceMessage(args.msgid, message)
                        }
                    }

                    if (result.isFailure) {
                        ChatRepository.deleteMessage(args.msgid)
                        Log.e(
                            "SupportFragment",
                            "Failed to save received video to file",
                            result.exceptionOrNull()
                        )
                    }
                }
            }
            MessageCategory.DiagnosticHeartbeat.value,
            MessageCategory.DeviceData.value -> {
            }
            else -> {
                Log.e(
                    "RemoteSupportViewModel",
                    "Received unknown notification category: ${args.notification.category}"
                )
            }
        }
    }

    private fun handleCommand(args: CommandEventArgs) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = saveCommandContext(args.context)
            when (args.command.category) {
                MessageCategory.BluetoothWriteRequest.value -> {
                    val device = selectedDevice ?: return@launch

                    val request = args.command.data.use {
                        try {
                            App.serializer.fromJson(
                                InputStreamReader(it.asInputStream()),
                                BluetoothWriteRequest::class.java
                            )
                        } catch (ex: Exception) {
                            context.error(500, "Could not parse command data")
                            return@launch
                        }
                    }
                    val value =
                        if (request.encoding == BluetoothWriteRequest.Encoding.Utf8) request.value.toByteArray()
                        else request.value.hexData

                    if (device.device.isConnected() && value != null) {
                        val uuid = UUID.fromString(request.uuid)
                        when (val result =
                            BleManager.writeCharacteristic(device.device, uuid, value)) {
                            is BleOperation.Result.Success -> context.acknowledge()
                            is BleOperation.Result.Failure -> context.error(
                                500,
                                result.error
                            )
                        }
                    } else {
                        val message =
                            if (value == null) "Invalid value, cannot write to characteristic"
                            else "Not connected to device"

                        context.error(500, message)
                    }
                }
                MessageCategory.BluetoothNotifyRequest.value -> {
                    val device = selectedDevice ?: return@launch

                    val request = args.command.data.use {
                        try {
                            App.serializer.fromJson(
                                InputStreamReader(it.asInputStream()),
                                BluetoothNotifyRequest::class.java
                            )
                        } catch (ex: Exception) {
                            context.error(500, "Could not parse command data")
                            return@launch
                        }
                    }

                    if (device.device.isConnected()) {
                        val uuid = UUID.fromString(request.uuid)
                        when (val result =
                            BleManager.setNotify(device.device, uuid, request.setNotify)) {
                            is BleOperation.Result.Success -> context.acknowledge()
                            is BleOperation.Result.Failure -> context.error(
                                500,
                                result.error
                            )
                        }
                    } else {
                        context.error(500, "Not connected to device")
                    }
                }
                MessageCategory.ConnectToDevice.value -> {
                    val address = args.command.data.use { data ->
                        try {
                            val raw = WellKnownTagEncoder.Json.decode(data)
                            val tree = JsonParser.parseString(raw)
                            tree.asJsonObject["macAddress"].asString
                        } catch (ex: Throwable) {
                            context.error(MessageErrors.JsonParseError)
                            return@launch
                        }
                    }

                    requestDeviceConnectFlow.emit(DeviceConnectEventArgs(address, context))
                }
                MessageCategory.DisconnectFromDevice.value -> {
                    requestDeviceDisconnectFlow.emit(Unit)
                    context.acknowledge()
                }
                MessageCategory.StartSharing.value -> {
                    val type = args.command.data.use { data ->
                        try {
                            MediaSharingType.decode(data)
                        } catch (ex: Throwable) {
                            context.error(
                                MessageErrors.MediaShareError.value,
                                ex.message ?: MessageErrors.MediaShareError.message
                            )
                            return@launch
                        }
                    }
                    when (type) {
                        MediaSharingType.VIDEO ->
                            requestVideoShareFlow.emit(MediaShareEventArgs(context))
                        MediaSharingType.SCREEN ->
                            requestScreenShareFlow.emit(MediaShareEventArgs(context))
                        else -> {}
                    }
                }
                MessageCategory.StopSharing.value -> {
                    val type = args.command.data.use { data ->
                        try {
                            MediaSharingType.decode(data)
                        } catch (ex: Throwable) {
                            context.error(
                                MessageErrors.MediaShareError.value,
                                ex.message ?: MessageErrors.MediaShareError.message
                            )
                            return@launch
                        }
                    }
                    when (type) {
                        MediaSharingType.VIDEO -> {
                            requestVideoShareStopFlow.emit(Unit)
                            removeVideo()
                            context.acknowledge()
                        }
                        MediaSharingType.SCREEN -> {
                            requestScreenShareStopFlow.emit(Unit)
                            removeScreen()
                            context.acknowledge()
                        }
                        else -> {}
                    }
                }
                else -> {
                    Log.e(
                        "RemoteSupportViewModel",
                        "Received unknown command category: ${args.command.category}"
                    )
                    context.error(500, "Unknown command category")
                }
            }
        }
    }


    private fun handleQuery(args: QueryEventArgs) {
        viewModelScope.launch(Dispatchers.IO) {
            when (args.query.category) {
                MessageCategory.BluetoothReadRequest.value -> handleCharacteristicReadRequest(args)
                MessageCategory.RequestDeviceList.value -> sendDeviceList(args.context)
                else -> {
                    Log.e(
                        "RemoteSupportViewModel",
                        "Received unknown query category: ${args.query.category}"
                    )
                    args.context.error(500, "Unknown query category")
                }
            }
        }
    }

    private suspend fun handleCharacteristicReadRequest(args: QueryEventArgs) {
        val device = selectedDevice ?: return

        val request = args.query.data.use {
            try {
                App.serializer.fromJson(
                    InputStreamReader(it.asInputStream()),
                    BluetoothReadRequest::class.java
                )
            } catch (ex: Exception) {
                args.context.error(500, "Could not parse command data")
                return
            }
        }

        if (device.device.isConnected()) {
            val uuid = UUID.fromString(request.uuid)
            when (val result = BleManager.readCharacteristic(device.device, uuid)) {
                is BleOperation.Result.Failure -> args.context.error(500, result.error)
                is BleOperation.Result.Success<ReadCharacteristicResults> -> {
                    result.result?.let {
                        try {
                            args.context.respond(
                                WellKnownTagEncoder.Json.encode(
                                    App.serializer.toJson(
                                        BluetoothReadResponse(it.formatted, it.value)
                                    )
                                ),
                                true
                            )
                        } catch (e: CygnusReachException) {
                            Log.e("RemoteSupportViewModel", e.message, e)
                        }
                    } ?: run {
                        args.context.error(
                            500,
                            "Failed to read value from characteristic"
                        )
                    }
                }
            }
        } else {
            args.context.error(500, "Not connected to device")
        }
    }

    private suspend fun handleMessageError(args: MessageErrorEventArgs) {
        Log.e("Reach", "Message error")
        ChatRepository.deleteMessage(args.messageId)
    }
}