package dev.hasali.luna

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.logcat

class LunaApplication : Application() {

    var isInBackground = true
        private set

    override fun onCreate() {
        super.onCreate()
        AndroidLogcatLogger.installOnDebuggableApp(this, minPriority = LogPriority.VERBOSE)
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
