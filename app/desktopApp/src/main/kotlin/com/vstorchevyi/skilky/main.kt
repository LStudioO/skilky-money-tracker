package com.vstorchevyi.skilky

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.vstorchevyi.skilky.di.desktopPlatformModule
import com.vstorchevyi.skilky.di.initializeKoin
import io.github.vinceglb.filekit.FileKit

fun main() {
    FileKit.init(appId = "com.vstorchevyi.skilky")
    initializeKoin(desktopPlatformModule)
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Skilky",
        ) {
            App()
        }
    }
}
