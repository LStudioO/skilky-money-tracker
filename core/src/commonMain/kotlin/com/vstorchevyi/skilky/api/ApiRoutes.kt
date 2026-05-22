package com.vstorchevyi.skilky.api

/**
 * Wire paths shared by `:server` and KMP clients via `:core` so literals stay aligned with the
 * REST contract in `docs/api-spec.md` at the repository root.
 */
object ApiRoutes {
    const val BASE = "/api/v1"

    const val HEALTH = "$BASE/health"
    const val HEALTH_DB = "$BASE/health/db"

    object Auth {
        const val REGISTER = "$BASE/auth/register"
        const val LOGIN = "$BASE/auth/login"
        const val REFRESH = "$BASE/auth/refresh"
    }

    object Expenses {
        const val ROOT = "$BASE/expenses"
        const val BY_ID = "$ROOT/{id}"
    }

    object Categories {
        const val ROOT = "$BASE/categories"
        const val BY_ID = "$ROOT/{id}"
    }

    object Parse {
        const val TEXT = "$BASE/parse/text"
        const val AUDIO = "$BASE/parse/audio"
        const val RECEIPT = "$BASE/parse/receipt"
        const val CORRECTIONS = "$BASE/parse/corrections"
    }

    object Analytics {
        const val ROOT = "$BASE/analytics"
        const val MONTHLY = "$ROOT/monthly"
        const val BREAKDOWN = "$ROOT/breakdown"
        const val TREND = "$ROOT/trend"
    }
}
