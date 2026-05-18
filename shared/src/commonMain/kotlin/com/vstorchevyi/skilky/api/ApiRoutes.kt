package com.vstorchevyi.skilky.api

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
}
