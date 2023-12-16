package dev.hasali.luna

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import logcat.logcat

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS, -123 // -1 is used by STATUS_PENDING_USER_ACTION
        )

        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)

        logcat { "Got status $status, packageName=$packageName" }

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val activityIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }

                if (activityIntent != null) {
                    context.startActivity(activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(
                    context, "Package installed successfully: $packageName", Toast.LENGTH_SHORT
                ).show()
            }

            else -> {
                logcat {
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    "Package installation failed for $packageName, msg=$message"
                }
            }
        }
    }
}
