package com.vstorchevyi.skilky.ui.input

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

/** Returns a camera action on mobile and null on targets without camera picker support. */
@Composable
internal expect fun rememberReceiptCameraLauncher(onResult: (PlatformFile?) -> Unit): (() -> Unit)?
