package com.vstorchevyi.skilky.di

import com.vstorchevyi.skilky.data.local.DataStoreTokenStorage
import com.vstorchevyi.skilky.data.local.TokenStorage
import com.vstorchevyi.skilky.data.remote.AuthApi
import com.vstorchevyi.skilky.data.remote.CategoryApi
import com.vstorchevyi.skilky.data.remote.ExpenseApi
import com.vstorchevyi.skilky.data.remote.SessionEvents
import com.vstorchevyi.skilky.data.remote.createHttpClient
import com.vstorchevyi.skilky.data.repository.AuthRepositoryImpl
import com.vstorchevyi.skilky.data.repository.CategoryRepositoryImpl
import com.vstorchevyi.skilky.data.repository.ExpenseRepositoryImpl
import com.vstorchevyi.skilky.domain.repository.AuthRepository
import com.vstorchevyi.skilky.domain.repository.CategoryRepository
import com.vstorchevyi.skilky.domain.repository.ExpenseRepository
import com.vstorchevyi.skilky.domain.usecase.CreateCategoryUseCase
import com.vstorchevyi.skilky.domain.usecase.DeleteCategoryUseCase
import com.vstorchevyi.skilky.domain.usecase.GetCategoriesUseCase
import com.vstorchevyi.skilky.domain.usecase.GetCurrentSessionUseCase
import com.vstorchevyi.skilky.domain.usecase.GetExpensesUseCase
import com.vstorchevyi.skilky.domain.usecase.LoginUseCase
import com.vstorchevyi.skilky.domain.usecase.LogoutUseCase
import com.vstorchevyi.skilky.domain.usecase.RefreshCategoriesUseCase
import com.vstorchevyi.skilky.domain.usecase.RefreshExpensesUseCase
import com.vstorchevyi.skilky.domain.usecase.RegisterUseCase
import com.vstorchevyi.skilky.domain.usecase.UpdateCategoryUseCase
import com.vstorchevyi.skilky.ui.auth.LoginViewModel
import com.vstorchevyi.skilky.ui.auth.RegisterViewModel
import com.vstorchevyi.skilky.ui.categories.CategoriesViewModel
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
 * `SkilkyDatabase` and `CategoryDao` are platform-supplied: each
 * platform module builds the database for its target and provides the DAO.
 *
 * Entry points (Android, iOS, desktop) start Koin with [appModules].
 */
internal val networkModule: Module =
    module {
        singleOf(::SessionEvents)
        single { createHttpClient(tokenStorage = get(), sessionEvents = get()) }
        singleOf(::AuthApi)
        singleOf(::CategoryApi)
        singleOf(::ExpenseApi)
    }

internal val dataModule: Module =
    module {
        singleOf(::DataStoreTokenStorage) bind TokenStorage::class
        singleOf(::AuthRepositoryImpl) bind AuthRepository::class
        single<CategoryRepository> { CategoryRepositoryImpl(dao = get(), api = get()) }
        single<ExpenseRepository> { ExpenseRepositoryImpl(dao = get(), api = get()) }
    }

internal val domainModule: Module =
    module {
        factoryOf(::RegisterUseCase)
        factoryOf(::LoginUseCase)
        factoryOf(::LogoutUseCase)
        factoryOf(::GetCurrentSessionUseCase)
        factoryOf(::GetCategoriesUseCase)
        factoryOf(::RefreshCategoriesUseCase)
        factoryOf(::CreateCategoryUseCase)
        factoryOf(::UpdateCategoryUseCase)
        factoryOf(::DeleteCategoryUseCase)
        factoryOf(::GetExpensesUseCase)
        factoryOf(::RefreshExpensesUseCase)
    }

internal val presentationModule: Module =
    module {
        viewModelOf(::LoginViewModel)
        viewModelOf(::RegisterViewModel)
        viewModelOf(::HomeViewModel)
        viewModelOf(::CategoriesViewModel)
    }

val appModules: List<Module> =
    listOf(networkModule, dataModule, domainModule, presentationModule)
