package dev.hasali.luna

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import logcat.logcat

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS, -123 // -1 is used by STATUS_PENDING_USER_ACTION
        )

        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)

        logcat { "Got status $status, packageName=$packageName, sessionId=$sessionId" }

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val application = context.applicationContext as LunaApplication
                if (application.isInBackground) {
                    PendingAppInstalls.notifyFailed(
                        sessionId,
                        PackageInstaller.STATUS_PENDING_USER_ACTION,
                        "User action required, but app is in background"
                    )
                } else {
                    val activityIntent =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(Intent.EXTRA_INTENT)
                        }

                    if (activityIntent != null) {
                        context.startActivity(activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                logcat { "Package installation succeeded for $packageName" }
                PendingAppInstalls.notifySucceeded(sessionId)
            }

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                logcat { "Package installation failed for $packageName, msg=$message" }
                PendingAppInstalls.notifyFailed(sessionId, status, message)
            }
        }
    }
}
