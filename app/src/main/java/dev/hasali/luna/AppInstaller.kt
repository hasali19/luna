package dev.hasali.luna

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.logcat
import java.net.HttpURLConnection
import java.net.URL

class AppInstaller(private val context: Context) {

    sealed interface InstallationResult {
        data object Success : InstallationResult
        data object NoCompatiblePackage : InstallationResult
        data object UserCanceled : InstallationResult
        data class Failure(val message: String?) : InstallationResult
    }

    suspend fun install(
        manifest: AppManifest,
        onProgress: (Float) -> Unit
    ): InstallationResult {
        val packages = manifest.packages.associateBy { it.abi ?: "any" }
        val abi = Build.SUPPORTED_ABIS.find { packages.containsKey(it) } ?: "any"
        val pkg = packages[abi] ?: return InstallationResult.NoCompatiblePackage
        return install(pkg.name, pkg.uri, manifest.info.packageName, onProgress)
    }

    private suspend fun install(
        name: String,
        url: String,
        packageName: String,
        onProgress: (Float) -> Unit
    ): InstallationResult {
        logcat { "Beginning download of '$name' from '$url'" }

        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val packageInstaller = context.packageManager.packageInstaller

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        withContext(Dispatchers.IO) {
            session.openWrite(name, 0, -1).use { output ->
                val connection = (URL(url).openConnection() as HttpURLConnection)
                    .apply { instanceFollowRedirects = true }
                logcat { "Download request returned status ${connection.responseMessage}" }
                val input = connection.inputStream
                val size = connection.contentLength
                val buffer = ByteArray(4096)
                var totalBytesRead = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    totalBytesRead += read
                    onProgress(
                        totalBytesRead.toFloat() / maxOf(
                            totalBytesRead.toFloat(),
                            size.toFloat()
                        )
                    )
                }
                session.fsync(output)
                logcat { "Finished downloading '$name'" }
            }
        }

        logcat { "Starting install for '$name'" }

        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }

        val intent = Intent(context, InstallReceiver::class.java)
        val pendingIntent =
            PendingIntent.getBroadcast(context, 3439, intent, flags)
        val receiver = pendingIntent.intentSender

        logcat { "Committing install session for '$name'" }

        session.commit(receiver)
        session.close()

        val (status, message) = PendingAppInstalls.await(sessionId)

        return when (status) {
            PackageInstaller.STATUS_SUCCESS -> InstallationResult.Success
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallationResult.UserCanceled
            else -> InstallationResult.Failure(message)
        }
    }
}
