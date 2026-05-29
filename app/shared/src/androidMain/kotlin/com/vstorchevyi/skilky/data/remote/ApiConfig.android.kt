package com.vstorchevyi.skilky.data.remote

/**
 * Android emulator routes `10.0.2.2` to the host machine's loopback, where
 * the dev server is bound to `localhost:8080`.
 */
internal actual val SERVER_BASE_URL: String = "http://10.0.2.2:8080"
