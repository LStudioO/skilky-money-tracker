package com.vstorchevyi.skilky.di

import com.vstorchevyi.skilky.data.local.DataStoreTokenStorage
import com.vstorchevyi.skilky.data.local.TokenStorage
import com.vstorchevyi.skilky.data.remote.AuthApi
import com.vstorchevyi.skilky.data.remote.CategoryApi
import com.vstorchevyi.skilky.data.remote.ExpenseApi
import com.vstorchevyi.skilky.data.remote.ParseApi
import com.vstorchevyi.skilky.data.remote.SessionEvents
import com.vstorchevyi.skilky.data.remote.createHttpClient
import com.vstorchevyi.skilky.data.repository.AuthRepositoryImpl
import com.vstorchevyi.skilky.data.repository.CategoryRepositoryImpl
import com.vstorchevyi.skilky.data.repository.ExpenseRepositoryImpl
import com.vstorchevyi.skilky.data.repository.ParseRepositoryImpl
import com.vstorchevyi.skilky.data.sync.ExpenseSyncManager
import com.vstorchevyi.skilky.domain.repository.AuthRepository
import com.vstorchevyi.skilky.domain.repository.CategoryRepository
import com.vstorchevyi.skilky.domain.repository.ExpenseRepository
import com.vstorchevyi.skilky.domain.repository.ParseRepository
import com.vstorchevyi.skilky.domain.usecase.CreateCategoryUseCase
import com.vstorchevyi.skilky.domain.usecase.CreateExpenseUseCase
import com.vstorchevyi.skilky.domain.usecase.CreateExpensesUseCase
import com.vstorchevyi.skilky.domain.usecase.DeleteCategoryUseCase
import com.vstorchevyi.skilky.domain.usecase.DeleteExpenseUseCase
import com.vstorchevyi.skilky.domain.usecase.DeletePendingExpenseUseCase
import com.vstorchevyi.skilky.domain.usecase.GetCategoriesUseCase
import com.vstorchevyi.skilky.domain.usecase.GetCurrentSessionUseCase
import com.vstorchevyi.skilky.domain.usecase.GetExpenseUseCase
import com.vstorchevyi.skilky.domain.usecase.GetExpensesUseCase
import com.vstorchevyi.skilky.domain.usecase.LoginUseCase
import com.vstorchevyi.skilky.domain.usecase.LogoutUseCase
import com.vstorchevyi.skilky.domain.usecase.ParseAudioUseCase
import com.vstorchevyi.skilky.domain.usecase.ParseReceiptUseCase
import com.vstorchevyi.skilky.domain.usecase.ParseTextUseCase
import com.vstorchevyi.skilky.domain.usecase.RefreshCategoriesUseCase
import com.vstorchevyi.skilky.domain.usecase.RefreshExpensesUseCase
import com.vstorchevyi.skilky.domain.usecase.RegisterUseCase
import com.vstorchevyi.skilky.domain.usecase.RetryPendingExpenseUseCase
import com.vstorchevyi.skilky.domain.usecase.UpdateCategoryUseCase
import com.vstorchevyi.skilky.domain.usecase.UpdateExpenseUseCase
import com.vstorchevyi.skilky.ui.auth.LoginViewModel
import com.vstorchevyi.skilky.ui.auth.RegisterViewModel
import com.vstorchevyi.skilky.ui.categories.CategoriesViewModel
import com.vstorchevyi.skilky.ui.expense.ExpenseFormViewModel
import com.vstorchevyi.skilky.ui.home.HomeViewModel
import com.vstorchevyi.skilky.ui.input.InputViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.TimeZone
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock

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
 * `SkilkyDatabase` and its DAOs are platform-supplied. Construction failures
 * are startup-fatal; repository operations map runtime Room and DataStore
 * failures to `AppError.Storage`.
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
        singleOf(::ParseApi)
    }

internal val dataModule: Module =
    module {
        singleOf(::DataStoreTokenStorage) bind TokenStorage::class
        singleOf(::AuthRepositoryImpl) bind AuthRepository::class
        single<CategoryRepository> { CategoryRepositoryImpl(dao = get(), api = get()) }
        single<ExpenseRepository> {
            ExpenseRepositoryImpl(
                dao = get(),
                categoryDao = get(),
                syncQueueDao = get(),
                api = get(),
            )
        }
        singleOf(::ParseRepositoryImpl) bind ParseRepository::class
        single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        single {
            ExpenseSyncManager(networkMonitor = get(), repository = get(), scope = get())
        }
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
        factoryOf(::GetExpenseUseCase)
        factoryOf(::RefreshExpensesUseCase)
        factoryOf(::CreateExpenseUseCase)
        factoryOf(::CreateExpensesUseCase)
        factoryOf(::UpdateExpenseUseCase)
        factoryOf(::DeleteExpenseUseCase)
        factoryOf(::RetryPendingExpenseUseCase)
        factoryOf(::DeletePendingExpenseUseCase)
        factoryOf(::ParseTextUseCase)
        factoryOf(::ParseAudioUseCase)
        factoryOf(::ParseReceiptUseCase)
    }

internal val presentationModule: Module =
    module {
        single<Clock> { Clock.System }
        single<TimeZone> { TimeZone.currentSystemDefault() }
        viewModelOf(::LoginViewModel)
        viewModelOf(::RegisterViewModel)
        viewModelOf(::HomeViewModel)
        viewModelOf(::InputViewModel)
        viewModelOf(::CategoriesViewModel)
        viewModel { params ->
            ExpenseFormViewModel(
                expenseId = params.getOrNull(),
                getExpense = get(),
                getCategories = get(),
                refreshCategories = get(),
                createExpense = get(),
                updateExpense = get(),
                deleteExpense = get(),
            )
        }
    }

val appModules: List<Module> =
    listOf(networkModule, dataModule, domainModule, presentationModule)
