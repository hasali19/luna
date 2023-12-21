package dev.hasali.luna

import android.content.pm.PackageInfo
import android.os.Build

val PackageInfo.longVersionCodeCompat
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }
