package com.vstorchevyi.skilky

import android.app.Application
import com.vstorchevyi.skilky.di.androidPlatformModule
import com.vstorchevyi.skilky.di.initializeKoin

/** Host `Application` that brings Koin up with the Android platform module. */
class SkilkyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeKoin(androidPlatformModule(applicationContext))
    }
}
