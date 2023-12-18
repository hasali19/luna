package dev.hasali.luna

import android.content.pm.PackageInstaller
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object PendingAppInstalls {

    private val lock = Unit
    private val installStatuses = mutableMapOf<Int, Pair<Int, String?>>()
    private val callbacks = mutableMapOf<Int, (Int, String?) -> Unit>()

    fun notifySucceeded(id: Int) {
        complete(id, PackageInstaller.STATUS_SUCCESS, null)
    }

    fun notifyFailed(id: Int, status: Int, message: String?) {
        complete(id, status, message)
    }

    private fun complete(id: Int, status: Int, message: String?) {
        val callback = synchronized(lock) {
            val callback = callbacks.remove(id)
            if (callback == null) {
                installStatuses.put(id, Pair(status, message))
                null
            } else {
                callback
            }
        }

        if (callback != null) {
            callback(status, message)
        }
    }

    suspend fun await(id: Int): Pair<Int, String?> {
        return suspendCoroutine { continuation ->
            val result = synchronized(lock) {
                val result = installStatuses.remove(id)
                if (result == null) {
                    callbacks[id] = { status, msg -> continuation.resume(Pair(status, msg)) }
                    null
                } else {
                    result
                }
            }

            if (result != null) {
                continuation.resume(result)
            }
        }
    }
}
