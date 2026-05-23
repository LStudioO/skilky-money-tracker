package com.vstorchevyi.skilky.data.remote

/**
 * Where the client looks for the Skilky server.
 *
 * Placeholder for the foundation: a single hard-coded base URL. The Android
 * emulator reaches the host machine at `10.0.2.2`; an iOS simulator or the
 * desktop app reach it at `localhost`. A real per-platform value, and an
 * in-app override, come with the settings screen later.
 */
internal object ApiConfig {
    const val BASE_URL: String = "http://10.0.2.2:8080"
}
