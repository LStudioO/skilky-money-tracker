package com.vstorchevyi.skilky.routes

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.CreateCategoryRequest
import com.vstorchevyi.skilky.api.UpdateCategoryRequest
import com.vstorchevyi.skilky.domain.model.CategoryRecord
import com.vstorchevyi.skilky.errors.ForbiddenException
import com.vstorchevyi.skilky.plugins.jwtAuthName
import com.vstorchevyi.skilky.repository.CategoryRepository
import com.vstorchevyi.skilky.security.validateCreateCategory
import io.ktor.http.HttpStatusCode
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
import org.koin.ktor.ext.inject

/**
 * JWT-protected category CRUD under [com.vstorchevyi.skilky.api.ApiRoutes.Categories].
 *
 * **404 vs 403:** mutating another user's category returns **404** (resource not found in *your*
 * tenant) to avoid leaking whether an id exists globally; touching a system default returns **403**
 * with a clear message because the id is visible to everyone in `GET /categories`.
 */
fun Route.categoryRoutes() {
    val categoryRepository: CategoryRepository by inject()
    authenticate(jwtAuthName()) {
        route(ApiRoutes.Categories.ROOT) {
            categoryGetList(categoryRepository)
            categoryPost(categoryRepository)
            categoryPut(categoryRepository)
            categoryDelete(categoryRepository)
        }
    }
}

private fun Route.categoryGetList(categoryRepository: CategoryRepository) {
    get {
        val user = call.requireJwtPrincipal()
        val lang = call.requestLanguageTag()
        val rows = categoryRepository.listVisible(user.userId)
        call.respond(rows.map { it.toDto(lang) })
    }
}

private fun Route.categoryPost(categoryRepository: CategoryRepository) {
    post {
        val user = call.requireJwtPrincipal()
        val body = call.receive<CreateCategoryRequest>()
        validateCreateCategory(body.name, body.icon, body.color)
        val created =
            categoryRepository.create(
                userId = user.userId,
                name = body.name.trim(),
                icon = body.icon.trim(),
                color = body.color.trim(),
            )
        call.respond(HttpStatusCode.Created, created.toDto(call.requestLanguageTag()))
    }
}

private fun Route.categoryPut(categoryRepository: CategoryRepository) {
    put("{id}") {
        val user = call.requireJwtPrincipal()
        val id =
            call.parameters["id"]?.toLongOrNull()
                ?: throw NotFoundException("Invalid category id")
        categoryRepository.requireOwnedCustomCategory(user.userId, id, action = "modify")
        val body = call.receive<UpdateCategoryRequest>()
        validateCreateCategory(body.name, body.icon, body.color)
        val updated =
            categoryRepository.updateCustom(
                userId = user.userId,
                categoryId = id,
                name = body.name.trim(),
                icon = body.icon.trim(),
                color = body.color.trim(),
            ) ?: throw NotFoundException("Category not found")
        call.respond(updated.toDto(call.requestLanguageTag()))
    }
}

private fun Route.categoryDelete(categoryRepository: CategoryRepository) {
    delete("{id}") {
        val user = call.requireJwtPrincipal()
        val id =
            call.parameters["id"]?.toLongOrNull()
                ?: throw NotFoundException("Invalid category id")
        categoryRepository.requireOwnedCustomCategory(user.userId, id, action = "delete")
        if (!categoryRepository.deleteCustomIfOwned(user.userId, id)) {
            throw NotFoundException("Category not found")
        }
        call.respond(HttpStatusCode.NoContent)
    }
}

/**
 * Confirms the category exists, is custom, and belongs to [userId].
 * Throws [ForbiddenException] for system defaults so the API surfaces a
 * clear reason (the id is visible to every user), and [NotFoundException]
 * for foreign rows so cross-tenant existence isn't leaked.
 */
private suspend fun CategoryRepository.requireOwnedCustomCategory(
    userId: Long,
    categoryId: Long,
    action: String,
): CategoryRecord {
    val existing = findById(categoryId) ?: throw NotFoundException("Category not found")
    if (existing.ownerUserId == null) {
        throw ForbiddenException("Cannot $action system default category")
    }
    if (existing.ownerUserId != userId) {
        throw NotFoundException("Category not found")
    }
    return existing
}
