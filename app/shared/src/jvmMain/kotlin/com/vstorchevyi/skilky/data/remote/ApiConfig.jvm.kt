package com.vstorchevyi.skilky.data.remote

/**
 * Desktop runs Compose on the same host as Ktor, so `localhost` is fine.
 */
internal actual val SERVER_BASE_URL: String = "http://localhost:8080"
