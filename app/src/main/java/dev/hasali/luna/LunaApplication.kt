package dev.hasali.luna

import android.app.Application
import logcat.AndroidLogcatLogger
import logcat.LogPriority

class LunaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidLogcatLogger.installOnDebuggableApp(this, minPriority = LogPriority.VERBOSE)
    }
}
