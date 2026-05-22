package com.vstorchevyi.skilky.di

import com.vstorchevyi.skilky.ai.CachedCategoryLoader
import com.vstorchevyi.skilky.ai.CategoryHint
import com.vstorchevyi.skilky.ai.OllamaClient
import com.vstorchevyi.skilky.ai.TextParsingService
import com.vstorchevyi.skilky.config.AppConfig
import com.vstorchevyi.skilky.db.DatabaseFactory
import com.vstorchevyi.skilky.repository.AnalyticsRepository
import com.vstorchevyi.skilky.repository.CategoryRepository
import com.vstorchevyi.skilky.repository.ExpenseRepository
import com.vstorchevyi.skilky.repository.ParseCorrectionsRepository
import com.vstorchevyi.skilky.repository.RefreshTokenRepository
import com.vstorchevyi.skilky.repository.UserRepository
import com.vstorchevyi.skilky.security.JwtTokenProvider
import com.vstorchevyi.skilky.security.PasswordHasher
import com.vstorchevyi.skilky.security.TokenHasher
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * Assembles the Koin module list for a given [AppConfig]. The
 * persistence and AI modules are conditional: when the operator has not
 * configured a DB or AI block, the corresponding services are simply
 * absent from the container — DI-aware routes guard their registration
 * against this, matching the previous `?.let` wiring in `module()`.
 */
internal fun appModules(appConfig: AppConfig): List<Module> {
    val modules = mutableListOf<Module>()
    modules += appConfigModule(appConfig)
    modules += securityModule()
    if (appConfig.database != null) modules += persistenceModule()
    if (appConfig.ai != null) modules += aiModule()
    return modules
}

private fun appConfigModule(config: AppConfig): Module =
    module {
        single { config }
        single { config.api }
        single { config.cors }
        single { config.jwt }
        single { config.security }
        config.database?.let { dbConfig -> single { dbConfig } }
        config.ai?.let { aiConfig -> single { aiConfig } }
    }

private fun securityModule(): Module =
    module {
        // PasswordHasher and TokenHasher both consume a single primitive
        // field out of SecurityConfig. singleOf can't bind raw Int/String
        // without a wrapper, so we unwrap inline — same pattern, both
        // services. The cost and pepper live in SecurityConfig as proper
        // tunable knobs (HOCON + env).
        single { PasswordHasher(get<AppConfig.SecurityConfig>().bcryptCost) }
        single { TokenHasher(get<AppConfig.SecurityConfig>().refreshTokenPepper) }
        singleOf(::JwtTokenProvider)
    }

private fun persistenceModule(): Module =
    module {
        single(createdAtStart = true) {
            DatabaseFactory(get()).also { it.init() }
        } onClose { it?.close() }
        singleOf(::UserRepository)
        singleOf(::RefreshTokenRepository)
        singleOf(::CategoryRepository)
        singleOf(::ExpenseRepository)
        singleOf(::ParseCorrectionsRepository)
        singleOf(::AnalyticsRepository)
    }

private fun aiModule(): Module =
    module {
        // OllamaClient takes an HttpClient default that we don't expose to
        // Koin (it's constructed from the AiConfig), so singleOf would
        // ask the container for an HttpClient and fail. Go explicit.
        single { OllamaClient(get()) } onClose { it?.close() }
        // CachedCategoryLoader takes a lambda over CategoryRepository, so
        // construction logic doesn't fit a constructor-reference binding.
        single {
            val categoryRepository = get<CategoryRepository>()
            CachedCategoryLoader(
                source = { userId ->
                    categoryRepository
                        .listVisible(userId)
                        .map { CategoryHint(id = it.id, name = it.name) }
                },
            )
        }
        // TextParsingService's `loadCategories` parameter is a function type
        // (suspend (Long) -> List<CategoryHint>), which Koin can't resolve by
        // constructor reference. CachedCategoryLoader satisfies it via subtyping.
        single {
            TextParsingService(
                ollamaClient = get(),
                loadCategories = get<CachedCategoryLoader>(),
            )
        }
    }
