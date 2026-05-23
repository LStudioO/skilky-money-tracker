package com.vstorchevyi.skilky.data.remote

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

internal actual fun httpClientEngine(): HttpClientEngineFactory<*> = CIO
