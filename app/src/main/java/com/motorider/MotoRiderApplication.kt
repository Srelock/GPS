package com.motorider

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for MotoRider.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class MotoRiderApplication : Application()
