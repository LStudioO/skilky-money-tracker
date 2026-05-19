package com.vstorchevyi.skilky.plugins

import com.vstorchevyi.skilky.security.JwtUserPrincipal
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.util.AttributeKey
import kotlin.time.Duration.Companion.minutes

/** Single source of truth for the rate-limit name registered below. */
val ParseRateLimit: RateLimitName = RateLimitName("parse")

/**
 * Override the `/parse/...` rate-limit budget at test time. Ktor's
 * [RateLimit] config does not accept a clock or a per-test bucket, so
 * tests that want to verify the 429 path bring their own limit (e.g.
 * 2 requests) via `application { attributes.put(ParseRateLimitOverrideKey, 2) }`.
 * Production wiring never sets this attribute.
 */
val ParseRateLimitOverrideKey: AttributeKey<Int> =
    AttributeKey("skilky.parseRateLimitOverride")

private const val DEFAULT_PARSE_LIMIT_PER_MINUTE = 10

/**
 * Per-user budget on `/parse/...`. Keyed on the JWT user id, so two
 * users sharing a server don't starve each other. Anonymous requests
 * fall into a shared "0" bucket — they should not reach here anyway
 * because the routes are inside `authenticate`.
 *
 * The plugin's install must run before [configureRouting] so the named
 * limiter is visible when routes register themselves.
 */
fun Application.configureRateLimit() {
    val limit =
        if (attributes.contains(ParseRateLimitOverrideKey)) {
            attributes[ParseRateLimitOverrideKey]
        } else {
            DEFAULT_PARSE_LIMIT_PER_MINUTE
        }
    install(RateLimit) {
        register(ParseRateLimit) {
            rateLimiter(limit = limit, refillPeriod = 1.minutes)
            requestKey { call -> call.principal<JwtUserPrincipal>()?.userId ?: 0L }
        }
    }
}
