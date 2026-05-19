package com.vstorchevyi.skilky.routes

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.ExpenseBatchRequest
import com.vstorchevyi.skilky.api.ExpenseListResponse
import com.vstorchevyi.skilky.api.ExpenseRequest
import com.vstorchevyi.skilky.plugins.jwtAuthName
import com.vstorchevyi.skilky.repository.ExpenseRepository
import com.vstorchevyi.skilky.security.parseLocalDateOrThrow
import com.vstorchevyi.skilky.security.validateExpenseBatch
import com.vstorchevyi.skilky.security.validateExpenseRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/**
 * JWT-protected expense list, batch create, update, and delete under
 * [com.vstorchevyi.skilky.api.ApiRoutes.Expenses].
 *
 * **Query dates:** `from` / `to` are optional `YYYY-MM-DD` filters; invalid strings yield **422**.
 * **Batch POST:** idempotent per [com.vstorchevyi.skilky.api.ExpenseRequest.clientId] — safe for
 * offline sync retries (see repository KDoc).
 */
fun Route.expenseRoutes(expenseRepository: ExpenseRepository) {
    authenticate(jwtAuthName()) {
        route(ApiRoutes.Expenses.ROOT) {
            expenseGetList(expenseRepository)
            expensePostBatch(expenseRepository)
            expensePutOne(expenseRepository)
            expenseDeleteOne(expenseRepository)
        }
    }
}

private fun Route.expenseGetList(expenseRepository: ExpenseRepository) {
    get {
        val user = call.requireJwtPrincipal()
        val lang = call.requestLanguageTag()
        val from = parseLocalDateOrThrow(call.request.queryParameters["from"], "from")
        val to = parseLocalDateOrThrow(call.request.queryParameters["to"], "to")
        val categoryId = call.request.queryParameters["categoryId"]?.toLongOrNull()
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
        val pageSize = (call.request.queryParameters["size"]?.toIntOrNull() ?: 50).coerceIn(1, 100)
        val (items, total) =
            expenseRepository.list(
                userId = user.userId,
                fromDate = from,
                toDate = to,
                categoryIdFilter = categoryId,
                page = page,
                size = pageSize,
            )
        call.respond(
            ExpenseListResponse(
                items = items.map { it.toResponse(lang) },
                total = total,
                page = page,
                size = pageSize,
            ),
        )
    }
}

private fun Route.expensePostBatch(expenseRepository: ExpenseRepository) {
    post {
        val user = call.requireJwtPrincipal()
        val body = call.receive<ExpenseBatchRequest>()
        validateExpenseBatch(body)
        val lang = call.requestLanguageTag()
        val created = expenseRepository.createBatch(user.userId, body.items)
        call.respond(
            HttpStatusCode.Created,
            ExpenseListResponse(
                items = created.map { it.toResponse(lang) },
                total = created.size,
                page = 0,
                size = created.size,
            ),
        )
    }
}

private fun Route.expensePutOne(expenseRepository: ExpenseRepository) {
    put("{id}") {
        val user = call.requireJwtPrincipal()
        val id =
            call.parameters["id"]?.toLongOrNull()
                ?: throw NotFoundException("Invalid expense id")
        val body = call.receive<ExpenseRequest>()
        validateExpenseRequest(body)
        val updated =
            expenseRepository.update(user.userId, id, body)
                ?: throw NotFoundException("Expense not found")
        call.respond(updated.toResponse(call.requestLanguageTag()))
    }
}

private fun Route.expenseDeleteOne(expenseRepository: ExpenseRepository) {
    delete("{id}") {
        val user = call.requireJwtPrincipal()
        val id =
            call.parameters["id"]?.toLongOrNull()
                ?: throw NotFoundException("Invalid expense id")
        if (!expenseRepository.delete(user.userId, id)) {
            throw NotFoundException("Expense not found")
        }
        call.respond(HttpStatusCode.NoContent)
    }
}
