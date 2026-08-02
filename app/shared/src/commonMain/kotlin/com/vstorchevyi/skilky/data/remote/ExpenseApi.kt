package com.vstorchevyi.skilky.data.remote

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.ExpenseBatchRequest
import com.vstorchevyi.skilky.api.ExpenseListResponse
import com.vstorchevyi.skilky.api.ExpenseRequest
import com.vstorchevyi.skilky.api.ExpenseResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.datetime.LocalDate

/**
 * The JWT-protected `/api/v1/expenses` endpoints. The Bearer header is
 * attached upstream by the HTTP client's Auth plugin; a non-2xx response
 * throws (`expectSuccess = true`), and `ExpenseRepositoryImpl` catches it.
 *
 * The list endpoint accepts optional `from` / `to` / `categoryId` filters
 * and `page` / `size` paging. This slice always asks for page 0 with the
 * default size; filters and paging surface as the screen grows.
 *
 * POST is the batch endpoint (idempotent on `clientId`). A single-item
 * create still wraps in a one-element batch.
 */
internal class ExpenseApi(
    private val httpClient: HttpClient,
) {
    suspend fun list(
        from: LocalDate? = null,
        to: LocalDate? = null,
        categoryId: Long? = null,
        page: Int = 0,
        size: Int = DEFAULT_PAGE_SIZE,
    ): ExpenseListResponse =
        httpClient
            .get(ApiRoutes.Expenses.ROOT) {
                from?.let { parameter("from", it.toString()) }
                to?.let { parameter("to", it.toString()) }
                categoryId?.let { parameter("categoryId", it) }
                parameter("page", page)
                parameter("size", size)
            }
            .body()

    suspend fun createBatch(request: ExpenseBatchRequest): ExpenseListResponse =
        httpClient
            .post(ApiRoutes.Expenses.ROOT) { setBody(request) }
            .body()

    suspend fun update(
        id: Long,
        request: ExpenseRequest,
    ): ExpenseResponse =
        httpClient
            .put(ApiRoutes.Expenses.ROOT + "/" + id) { setBody(request) }
            .body()

    suspend fun delete(id: Long) {
        httpClient.delete(ApiRoutes.Expenses.ROOT + "/" + id)
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 50
    }
}
