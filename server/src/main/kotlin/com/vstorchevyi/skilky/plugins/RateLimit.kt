package com.vstorchevyi.skilky.plugins

import com.vstorchevyi.skilky.security.JwtUserPrincipal
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.util.AttributeKey
import kotlin.time.Duration.Companion.minutes

/** Name of the per-user `/parse/...` rate limiter. */
val ParseRateLimit: RateLimitName = RateLimitName("parse")

/** Name of the per-IP limiter guarding the unauthenticated `/auth/...` endpoints. */
val AuthRateLimit: RateLimitName = RateLimitName("auth")

/**
 * Override the `/parse/...` rate-limit budget at test time. Ktor's
 * [RateLimit] config does not accept a clock or a per-test bucket, so
 * tests that want to verify the 429 path bring their own limit (e.g.
 * 2 requests) via `application { attributes.put(ParseRateLimitOverrideKey, 2) }`.
 * Production wiring never sets this attribute.
 */
val ParseRateLimitOverrideKey: AttributeKey<Int> =
    AttributeKey("skilky.parseRateLimitOverride")

/** Test-time override for the `/auth/...` budget; see [ParseRateLimitOverrideKey]. */
val AuthRateLimitOverrideKey: AttributeKey<Int> =
    AttributeKey("skilky.authRateLimitOverride")

private const val DEFAULT_PARSE_LIMIT_PER_MINUTE = 10
private const val DEFAULT_AUTH_LIMIT_PER_MINUTE = 10

/**
 * Installs the rate limiters.
 *
 * **parse** — per-user budget on `/parse/...`, keyed on the JWT user id so
 * two users sharing a server don't starve each other. Anonymous requests
 * fall into a shared "0" bucket; they should not reach here anyway because
 * the routes sit inside `authenticate`.
 *
 * **auth** — per-IP budget on `/auth/register|login|refresh`. These run
 * before authentication, so there is no user id to key on; the client
 * address is the only signal. Caps password brute-force and signup spam.
 *
 * The install must run before [configureRouting] so the named limiters are
 * visible when routes register themselves.
 */
fun Application.configureRateLimit() {
    val parseLimit = overrideOr(ParseRateLimitOverrideKey, DEFAULT_PARSE_LIMIT_PER_MINUTE)
    val authLimit = overrideOr(AuthRateLimitOverrideKey, DEFAULT_AUTH_LIMIT_PER_MINUTE)
    install(RateLimit) {
        register(ParseRateLimit) {
            rateLimiter(limit = parseLimit, refillPeriod = 1.minutes)
            requestKey { call -> call.principal<JwtUserPrincipal>()?.userId ?: 0L }
        }
        register(AuthRateLimit) {
            rateLimiter(limit = authLimit, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }
}

private fun Application.overrideOr(
    key: AttributeKey<Int>,
    default: Int,
): Int = if (attributes.contains(key)) attributes[key] else default
