package com.reach_android.util

import android.R
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.SparseArray
import android.view.View
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.reach_android.App
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex


internal fun <T> SharedFlow<T>.subscribe(
    scope: CoroutineScope,
    action: suspend CoroutineScope.(T) -> Unit
) = scope.launch { onEach { action(this, it) }.collect() }

internal fun <T> Flow<T>.subscribe(
    scope: CoroutineScope,
    action: suspend CoroutineScope.(T) -> Unit
) = scope.launch { onEach { action(this, it) }.collect() }

internal fun <T> MutableSharedFlow<T>.emitAll(
    scope: CoroutineScope,
    source: SharedFlow<T>
) = scope.launch { emitAll(source) }

internal fun <T> SharedFlow<T>.once(
    scope: CoroutineScope,
    action: suspend (T) -> Unit
) = scope.launch { onEach(action).first() }

internal fun <T> SharedFlow<T>.subscribe(
    owner: LifecycleOwner,
    action: suspend CoroutineScope.(T) -> Unit
) = owner.lifecycleScope.launch { onEach { action(this, it) }.collect() }

internal fun <T> MutableSharedFlow<T>.emitAll(
    owner: LifecycleOwner,
    source: SharedFlow<T>
) = owner.lifecycleScope.launch { emitAll(source) }

internal fun <T> SharedFlow<T>.once(
    owner: LifecycleOwner,
    action: suspend CoroutineScope.(T) -> Unit
) = owner.lifecycleScope.launch { onEach { action(this, it) }.first() }

internal fun <T> SharedFlow<T>.subscribe(
    owner: LifecycleOwner,
    state: Lifecycle.State,
    action: suspend CoroutineScope.(T) -> Unit
) = owner.launchOnLifecycle(state) { onEach { action(this, it) }.collect() }

internal fun <T> MutableSharedFlow<T>.emitAll(
    owner: LifecycleOwner,
    state: Lifecycle.State,
    source: SharedFlow<T>
) = owner.launchOnLifecycle(state) { emitAll(source) }

internal fun <T> SharedFlow<T>.once(
    owner: LifecycleOwner,
    state: Lifecycle.State,
    action: suspend CoroutineScope.(T) -> Unit
) = owner.launchOnLifecycle(state) { onEach { action(this, it) }.first() }

internal fun LifecycleOwner.launchOnLifecycle(
    state: Lifecycle.State,
    block: suspend CoroutineScope.() -> Unit
) = lifecycleScope.launch { lifecycle.repeatOnLifecycle(state, block) }

fun FragmentActivity?.hideActionBar() {
    (this as? AppCompatActivity)?.supportActionBar?.hide()
}

fun FragmentActivity?.showActionBar(title: String? = null) {
    (this as? AppCompatActivity)?.supportActionBar?.let {
        if (!title.isNullOrBlank())
            it.title = title
        it.show()

    }
}

fun FragmentActivity?.getActionBar() = (this as? AppCompatActivity)?.supportActionBar


fun View?.navigate(@IdRes id: Int) {
    this?.findNavController()?.navigate(id, null, getNavOptions())
}

fun View?.navigate(directions: NavDirections) {
    this?.findNavController()?.navigate(directions.actionId, directions.arguments, getNavOptions())
}

fun Fragment.navigate(@IdRes id: Int) {
    this.findNavController().navigate(id, null, getNavOptions())
}

fun Fragment.navigate(directions: NavDirections) {
    this.findNavController().navigate(directions.actionId, directions.arguments, getNavOptions())
}

fun getNavOptions(): NavOptions {
    return NavOptions.Builder()
        .setEnterAnim(R.anim.slide_in_left)
        .setExitAnim(-1)
        .setPopEnterAnim(R.anim.fade_in)
        .setPopExitAnim(-1)
        .build()
}

internal fun arePermissionsGranted(permissions: Array<String>): Boolean {
    for (p in permissions) {
        val rslt = ContextCompat.checkSelfPermission(App.app, p)
        if (rslt != PackageManager.PERMISSION_GRANTED) {
            return false
        }
    }
    return true
}

internal fun isPermissionGranted(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(
        App.app,
        permission
    ) == PackageManager.PERMISSION_GRANTED
}


internal fun filterGrantedPermissions(context: Context, vararg permissions: String) = flow {
    for (p in permissions) {
        if (ContextCompat.checkSelfPermission(context, p) != PackageManager.PERMISSION_GRANTED) {
            this.emit(p)
        }
    }
}

internal fun getOutstandingPermissions(
    context: Context,
    vararg permissions: String
): List<String> {
    return permissions.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }
}

class FragmentPermissionRequest(context: Fragment) {
    private val lock = Mutex()
    private var permissionsGranted: CompletableDeferred<Boolean> = CompletableDeferred()
    private val requestPermissionLauncher =
        context.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
        { isGranted -> permissionsGranted.complete(isGranted.values.all { it }) }

    suspend fun requestPermissions(permissions: Array<String>): Boolean {
        val outstanding = getOutstandingPermissions(App.app, *permissions)
        if (outstanding.isEmpty()) return true

        lock.lock()
        try {
            requestPermissionLauncher.launch(outstanding.toTypedArray())
            return permissionsGranted.await()
        } finally {
            permissionsGranted = CompletableDeferred()
            lock.unlock()
        }
    }

    suspend fun requestPermission(permission: String): Boolean {
        if (isPermissionGranted(permission)) return true

        lock.lock()
        try {
            requestPermissionLauncher.launch(arrayOf(permission))
            return permissionsGranted.await()
        } finally {
            permissionsGranted = CompletableDeferred()
            lock.unlock()
        }
    }
}

class ActivityPermissionRequest(context: AppCompatActivity) {
    private val lock = Mutex()
    private var permissionsGranted: CompletableDeferred<Boolean> = CompletableDeferred()
    private val requestPermissionLauncher =
        context.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
        { isGranted -> permissionsGranted.complete(isGranted.values.all { it }) }

    suspend fun requestPermissions(permissions: Array<String>): Boolean {
        lock.lock()

        try {
            requestPermissionLauncher.launch(permissions)
            return permissionsGranted.await()
        } finally {
            permissionsGranted = CompletableDeferred()
            lock.unlock()
        }
    }
}

class PhotoRequest(context: Fragment) {
    private val lock = Mutex()
    private var results = CompletableDeferred<Boolean>()
    private val requestMediaLauncher =
        context.registerForActivityResult(ActivityResultContracts.TakePicture()) {
            results.complete(it)
        }

    suspend fun requestPhoto(uri: Uri): Boolean {
        lock.lock()
        try {
            requestMediaLauncher.launch(uri)
            return results.await()
        } finally {
            results = CompletableDeferred()
            lock.unlock()
        }
    }
}

class VideoRequest(context: Fragment) {
    private val lock = Mutex()
    private var results = CompletableDeferred<Bitmap?>()
    private val requestMediaLauncher =
        context.registerForActivityResult(ActivityResultContracts.TakeVideo()) { preview: Bitmap? ->
            results.complete(preview)
        }

    suspend fun requestVideo(uri: Uri): Bitmap? {
        lock.lock()
        try {
            requestMediaLauncher.launch(uri)
            return results.await()
        } finally {
            results = CompletableDeferred()
            lock.unlock()
        }
    }
}

class ImageGalleryRequest(context: Fragment) {
    private val lock = Mutex()
    private var results = CompletableDeferred<Uri?>()
    private val requestMediaLauncher =
        context.registerForActivityResult(object : ActivityResultContract<Unit, Uri?>() {
            override fun createIntent(context: Context, input: Unit): Intent {
                return Intent(Intent.ACTION_PICK)
                    .setDataAndType(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        "image/*")
            }

            override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
                return if (intent == null || resultCode != Activity.RESULT_OK) null else intent.data
            }
        }) {
            results.complete(it)
        }

    suspend fun requestImage(): Uri? {
        lock.lock()
        try {
            requestMediaLauncher.launch()
            return results.await()
        } finally {
            results = CompletableDeferred()
            lock.unlock()
        }
    }
}

class ResettableDeferred<T> {
    private val stateFlow = MutableStateFlow<T?>(null)
    val state = stateFlow.asStateFlow()


    val isSet get() = stateFlow.value != null

    fun reset(): T? {
        val prev = stateFlow.value
        stateFlow.value = null
        return prev
    }

    fun set(value: T) {
        stateFlow.value = value
    }

    fun getOrDefault(default: T): T = state.value ?: default

    suspend fun await() = stateFlow.value
        ?.let { return it }
        ?: stateFlow.first { it != null }!!
}

class ResettableLazyAsync<T>(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val factory: suspend () -> T,
    private val cleanup: (T) -> Unit
) {
    var v = scope.async(start = CoroutineStart.LAZY) { factory() }

    suspend fun get() = v.await()

    suspend fun reset() {
        if(v.isCompleted) cleanup(v.await())
        v = scope.async(start = CoroutineStart.LAZY) { factory() }
    }
}

inline fun <T, U> SparseArray<T>.map(action: (key: Int, value: T) -> U): List<U> {
    val result = mutableListOf<U>()
    for (index in 0 until size()) {
        result.add(action(keyAt(index), valueAt(index)))
    }
    return result
}

inline fun <T, U> SparseArray<T>.mapCompact(action: (key: Int, value: T) -> U?): List<U> {
    val result = mutableListOf<U>()
    for (index in 0 until size()) {
        action(keyAt(index), valueAt(index))?.apply(result::add)
    }
    return result
}
