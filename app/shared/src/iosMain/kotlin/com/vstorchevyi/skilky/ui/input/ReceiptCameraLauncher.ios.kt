package com.vstorchevyi.skilky.ui.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitCameraFacing
import io.github.vinceglb.filekit.dialogs.compose.rememberCameraPickerLauncher

@Composable
internal actual fun rememberReceiptCameraLauncher(onResult: (PlatformFile?) -> Unit): (() -> Unit)? {
    val launcher = rememberCameraPickerLauncher(onResult = onResult)
    return remember(launcher) {
        { launcher.launch(cameraFacing = FileKitCameraFacing.Back) }
    }
}
