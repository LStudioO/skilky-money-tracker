package com.vstorchevyi.skilky.data.remote

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.AuthResponse
import com.vstorchevyi.skilky.api.LoginRequest
import com.vstorchevyi.skilky.api.RegisterRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * The unauthenticated `/auth/...` endpoints. A non-2xx response throws (the
 * client is built with `expectSuccess = true`); `AuthRepositoryImpl` catches it.
 */
internal class AuthApi(
    private val httpClient: HttpClient,
) {
    suspend fun register(request: RegisterRequest): AuthResponse {
        val response = httpClient.post(ApiRoutes.Auth.REGISTER) { setBody(request) }
        return response.body()
    }

    suspend fun login(request: LoginRequest): AuthResponse {
        val response = httpClient.post(ApiRoutes.Auth.LOGIN) { setBody(request) }
        return response.body()
    }
}
