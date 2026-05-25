package com.vstorchevyi.skilky.di

import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.mp.KoinPlatformTools

/**
 * Starts Koin with [appModules] plus the caller's [platformModule], which
 * supplies the per-platform pieces (currently just `DataStore<Preferences>`).
 *
 * Safe to call more than once: on Android, configuration changes can recreate
 * the process state in ways that re-invoke the host `Application`, so this
 * short-circuits when Koin has already been started.
 */
fun initializeKoin(platformModule: Module) {
    if (KoinPlatformTools.defaultContext().getOrNull() != null) return
    startKoin {
        modules(appModules + platformModule)
    }
}
