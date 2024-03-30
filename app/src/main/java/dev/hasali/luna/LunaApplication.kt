package dev.hasali.luna

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import fr.bipi.treessence.context.GlobalContext.startTimber
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.logcat

class LunaApplication : Application() {

    var isInBackground = true
        private set

    override fun onCreate() {
        super.onCreate()

        AndroidLogcatLogger.installOnDebuggableApp(this, minPriority = LogPriority.VERBOSE)

        startTimber {
            if (BuildConfig.DEBUG) {
                debugTree()
            }

            fileTree {
                level = Log.INFO
                dir = "${cacheDir.absolutePath}/logs"
            }
        }

        NotificationChannels.createAll(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isInBackground = false
                this@LunaApplication.logcat { "Entered foreground" }
            }

            override fun onStop(owner: LifecycleOwner) {
                isInBackground = true
                this@LunaApplication.logcat { "Entered background" }
            }
        })
    }
}
