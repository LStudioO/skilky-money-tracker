package com.vstorchevyi.skilky.ui.input

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

@Composable
internal actual fun rememberReceiptCameraLauncher(onResult: (PlatformFile?) -> Unit): (() -> Unit)? = null
