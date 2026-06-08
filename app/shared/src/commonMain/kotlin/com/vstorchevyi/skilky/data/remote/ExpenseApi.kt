package com.vstorchevyi.skilky.data.remote

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.ExpenseListResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.datetime.LocalDate

/**
 * The JWT-protected `/api/v1/expenses` endpoints. The Bearer header is
 * attached upstream by the HTTP client's Auth plugin; a non-2xx response
 * throws (`expectSuccess = true`), and `ExpenseRepositoryImpl` catches it.
 *
 * The list endpoint accepts optional `from` / `to` / `categoryId` filters
 * and `page` / `size` paging. This slice always asks for page 0 with the
 * default size; filters and paging surface as the screen grows.
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

    companion object {
        const val DEFAULT_PAGE_SIZE = 50
    }
}
