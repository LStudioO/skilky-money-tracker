package com.vstorchevyi.skilky.di

import com.vstorchevyi.skilky.data.local.DataStoreTokenStorage
import com.vstorchevyi.skilky.data.local.TokenStorage
import com.vstorchevyi.skilky.data.remote.AuthApi
import com.vstorchevyi.skilky.data.remote.SessionEvents
import com.vstorchevyi.skilky.data.remote.createHttpClient
import com.vstorchevyi.skilky.data.repository.AuthRepositoryImpl
import com.vstorchevyi.skilky.domain.repository.AuthRepository
import com.vstorchevyi.skilky.domain.usecase.GetCurrentSessionUseCase
import com.vstorchevyi.skilky.domain.usecase.LoginUseCase
import com.vstorchevyi.skilky.domain.usecase.LogoutUseCase
import com.vstorchevyi.skilky.domain.usecase.RegisterUseCase
import com.vstorchevyi.skilky.ui.auth.LoginViewModel
import com.vstorchevyi.skilky.ui.auth.RegisterViewModel
import com.vstorchevyi.skilky.ui.home.HomeViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin modules for the KMP client, layered to match the Clean Architecture
 * split:
 * - [networkModule] — the shared HTTP client, transport-level events, and the
 *   remote APIs that ride the client.
 * - [dataModule] — repository implementations and local storage, bound to the
 *   domain-layer interfaces so the domain never sees the concrete types.
 * - [domainModule] — the use cases exposed to the presentation layer.
 * - [presentationModule] — the ViewModels backing each screen.
 *
 * Entry points (Android, iOS, desktop) start Koin with [appModules].
 */
internal val networkModule: Module =
    module {
        singleOf(::SessionEvents)
        single { createHttpClient(tokenStorage = get(), sessionEvents = get()) }
        singleOf(::AuthApi)
    }

internal val dataModule: Module =
    module {
        singleOf(::DataStoreTokenStorage) bind TokenStorage::class
        singleOf(::AuthRepositoryImpl) bind AuthRepository::class
    }

internal val domainModule: Module =
    module {
        factoryOf(::RegisterUseCase)
        factoryOf(::LoginUseCase)
        factoryOf(::LogoutUseCase)
        factoryOf(::GetCurrentSessionUseCase)
    }

internal val presentationModule: Module =
    module {
        viewModelOf(::LoginViewModel)
        viewModelOf(::RegisterViewModel)
        viewModelOf(::HomeViewModel)
    }

val appModules: List<Module> =
    listOf(networkModule, dataModule, domainModule, presentationModule)
