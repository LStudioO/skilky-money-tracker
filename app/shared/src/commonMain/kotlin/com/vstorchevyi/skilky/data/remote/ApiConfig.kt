package com.vstorchevyi.skilky.data.remote

/**
 * Where the client looks for the Skilky server.
 *
 * Placeholder for the foundation: a single per-platform base URL pointing at
 * the dev server on the host machine. An in-app override and a real
 * production value land with the settings screen later.
 *
 * The Android emulator reaches the host at `10.0.2.2`; iOS simulator and the
 * desktop app share the host network and use `localhost`. Hence the
 * expect/actual split.
 */
internal expect val SERVER_BASE_URL: String

internal object ApiConfig {
    val BASE_URL: String get() = SERVER_BASE_URL
}
