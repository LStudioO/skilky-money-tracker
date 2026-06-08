package com.vstorchevyi.skilky.data.remote

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.CategoryDto
import com.vstorchevyi.skilky.api.CreateCategoryRequest
import com.vstorchevyi.skilky.api.UpdateCategoryRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

/**
 * The JWT-protected `/api/v1/categories` endpoints. The Bearer header is
 * attached upstream by the HTTP client's Auth plugin; a non-2xx response
 * throws (`expectSuccess = true`), and `CategoryRepositoryImpl` catches it.
 */
internal class CategoryApi(
    private val httpClient: HttpClient,
) {
    suspend fun list(): List<CategoryDto> = httpClient.get(ApiRoutes.Categories.ROOT).body()

    suspend fun create(request: CreateCategoryRequest): CategoryDto =
        httpClient
            .post(ApiRoutes.Categories.ROOT) { setBody(request) }
            .body()

    suspend fun update(
        id: Long,
        request: UpdateCategoryRequest,
    ): CategoryDto =
        httpClient
            .put(ApiRoutes.Categories.ROOT + "/" + id) { setBody(request) }
            .body()

    suspend fun delete(id: Long) {
        httpClient.delete(ApiRoutes.Categories.ROOT + "/" + id)
    }
}
