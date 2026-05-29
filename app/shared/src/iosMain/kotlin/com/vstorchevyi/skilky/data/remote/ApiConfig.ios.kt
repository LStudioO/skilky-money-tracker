package com.vstorchevyi.skilky.data.remote

/**
 * iOS simulator shares the host network, so `localhost` reaches the dev
 * server directly. App Transport Security clears cleartext to `localhost`
 * when the Info.plist sets `NSAllowsLocalNetworking`.
 */
internal actual val SERVER_BASE_URL: String = "http://localhost:8080"
