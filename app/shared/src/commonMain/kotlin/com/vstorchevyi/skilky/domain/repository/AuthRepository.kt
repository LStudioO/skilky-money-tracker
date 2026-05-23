package com.vstorchevyi.skilky.domain.repository

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.AuthSession
import com.vstorchevyi.skilky.domain.model.Either

/**
 * Authentication operations the domain depends on. The data layer implements
 * this (see `data/repository/AuthRepositoryImpl`); the domain never sees Ktor,
 * DataStore, or the wire DTOs.
 */
interface AuthRepository {
    suspend fun register(
        email: String,
        password: String,
        displayName: String,
    ): Either<AppError, AuthSession>

    suspend fun login(
        email: String,
        password: String,
    ): Either<AppError, AuthSession>

    /** The session persisted from a previous run, or null when signed out. */
    suspend fun currentSession(): AuthSession?

    /** Forget the persisted session. */
    suspend fun logout()
}
