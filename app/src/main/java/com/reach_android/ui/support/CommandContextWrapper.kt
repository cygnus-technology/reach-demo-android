package com.reach_android.ui.support

import com.cygnusreach.messages.ICommandContext
import com.reach_android.model.remotesupport.MessageErrors
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class CommandContextWrapper(
    val context: ICommandContext,
    private val scope: CoroutineScope,
    private val onComplete: () -> Unit,
) : ICommandContext {
    override val hasExecuted: Boolean
        get() = context.hasExecuted

    private var job: Job = scope.launch {
        delay(timeout)
        if(isActive) {
            error(MessageErrors.UserTimeout)
        }
    }

    override val commandId: String
        get() = context.commandId

    override suspend fun acknowledge(): Boolean {
        job.cancel()
        if(!context.hasExecuted) {
            context.acknowledge()
            onComplete()
            return true
        }
        return false
    }

    override suspend fun error(status: Long, message: String): Boolean {
        job.cancel()
        if(!context.hasExecuted) {
            context.error(status, message)
            onComplete()
            return true
        }
        return false
    }

    fun reset() {
        job.cancel()
        job = scope.launch {
            delay(timeout)
            if(isActive) {
                error(MessageErrors.UserTimeout)
            }
        }
    }

    companion object {
        val timeout = TimeUnit.SECONDS.toMillis(60)
    }
}