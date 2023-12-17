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
    suspend fun install(name: String, url: String, onProgress: (Float) -> Unit) {
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            logcat { "Requesting install with GENTLE_UPDATE for '$name'" }
            packageInstaller.commitSessionAfterInstallConstraintsAreMet(
                sessionId,
                receiver,
                PackageInstaller.InstallConstraints.GENTLE_UPDATE,
                1000 * 60 * 60 * 24 * 7 // 1 week
            )
        } else {
            logcat { "Committing install session for '$name'" }
            session.commit(receiver)
            session.close()
        }
    }
}
