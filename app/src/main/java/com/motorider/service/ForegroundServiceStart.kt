package com.motorider.service

import android.app.Notification
import android.app.Service
import android.os.Build

/**
 * On API 34+ (including Android 16), [Service.startForeground] must declare the manifest
 * foreground service type. Using the two-argument overload can throw [SecurityException].
 */
fun Service.startForegroundTyped(
    id: Int,
    notification: Notification,
    foregroundServiceType: Int
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startForeground(id, notification, foregroundServiceType)
    } else {
        startForeground(id, notification)
    }
}
