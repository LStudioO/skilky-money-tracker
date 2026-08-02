package com.vstorchevyi.skilky.data.remote

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.ParseTextRequest
import com.vstorchevyi.skilky.api.ParseTextResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/** The authenticated AI parsing endpoints used by the shared client. */
internal class ParseApi(
    private val httpClient: HttpClient,
) {
    suspend fun parseText(request: ParseTextRequest): ParseTextResponse =
        httpClient
            .post(ApiRoutes.Parse.TEXT) { setBody(request) }
            .body()
}
