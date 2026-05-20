package com.vstorchevyi.skilky.ai

import com.sun.management.OperatingSystemMXBean
import com.vstorchevyi.skilky.config.AppConfig
import io.ktor.server.application.Application
import org.slf4j.Logger
import java.lang.management.ManagementFactory
import java.util.Locale

/**
 * Best-effort boot warning when the host has less RAM than gemma4:e4b needs
 * to serve comfortably. The model sits around 6-7 GiB resident while loaded;
 * 8 GiB total is the practical floor (matches docs/deployment.md).
 *
 * Warn-only. We never auto-downgrade the model: the operator may have
 * already configured a smaller variant via `AI_MODEL`, and changing model
 * choice at boot time would silently override that.
 */
internal fun Application.warnIfLowMemory(
    ai: AppConfig.AiConfig?,
    detectTotalMemoryBytes: () -> Long? = ::probeHostMemory,
) {
    warnIfLowMemory(ai, detectTotalMemoryBytes(), environment.log)
}

internal fun warnIfLowMemory(
    ai: AppConfig.AiConfig?,
    totalMemoryBytes: Long?,
    log: Logger,
) {
    if (ai == null || totalMemoryBytes == null) return
    val totalGiB = totalMemoryBytes.toDouble() / BYTES_PER_GIB
    if (totalGiB < LOW_MEMORY_THRESHOLD_GIB) {
        val formatted = String.format(Locale.ROOT, "%.1f", totalGiB)
        log.warn(
            "Host has $formatted GiB total memory; below the " +
                "$LOW_MEMORY_THRESHOLD_GIB GiB recommended for ${ai.model}. " +
                "Consider a smaller chat model via AI_MODEL (see docs/deployment.md).",
        )
    }
}

private fun probeHostMemory(): Long? =
    (ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean)
        ?.totalMemorySize

private const val BYTES_PER_GIB: Double = 1024.0 * 1024.0 * 1024.0
private const val LOW_MEMORY_THRESHOLD_GIB: Int = 8
