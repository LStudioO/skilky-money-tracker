package com.vstorchevyi.skilky.routes

import com.vstorchevyi.skilky.api.ApiRoutes
import com.vstorchevyi.skilky.api.CategoryBreakdownItem
import com.vstorchevyi.skilky.api.CategoryTotal
import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.DefaultCategoryTranslations
import com.vstorchevyi.skilky.api.MonthlySummaryResponse
import com.vstorchevyi.skilky.api.TrendGranularity
import com.vstorchevyi.skilky.api.TrendPoint
import com.vstorchevyi.skilky.api.TrendResponse
import com.vstorchevyi.skilky.errors.ValidationException
import com.vstorchevyi.skilky.plugins.jwtAuthName
import com.vstorchevyi.skilky.repository.AnalyticsRepository
import com.vstorchevyi.skilky.repository.CategorySpend
import com.vstorchevyi.skilky.security.parseLocalDateOrThrow
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.ktor.ext.inject
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.time.Clock

/**
 * JWT-protected spending analytics under [com.vstorchevyi.skilky.api.ApiRoutes.Analytics].
 *
 * Every endpoint aggregates a single currency (`currency` query param, default
 * [DEFAULT_CURRENCY]) because the `amount` column stores major units with no
 * conversion. Category names are localized via the request `Accept-Language`,
 * matching the expense and category endpoints.
 */
fun Route.analyticsRoutes() {
    val analyticsRepository: AnalyticsRepository by inject()
    authenticate(jwtAuthName()) {
        route(ApiRoutes.Analytics.ROOT) {
            monthlySummary(analyticsRepository)
            categoryBreakdown(analyticsRepository)
            spendingTrend(analyticsRepository)
        }
    }
}

private fun Route.monthlySummary(analyticsRepository: AnalyticsRepository) {
    get("monthly") {
        val user = call.requireJwtPrincipal()
        val lang = call.requestLanguageTag()
        val today = currentDate()
        val year = intParamOrNull(call.request.queryParameters["year"], "year") ?: today.year
        val month = intParamOrNull(call.request.queryParameters["month"], "month") ?: today.monthNumber
        if (year !in MIN_YEAR..MAX_YEAR) {
            throw ValidationException("year must be between $MIN_YEAR and $MAX_YEAR")
        }
        if (month !in 1..MONTHS_PER_YEAR) {
            throw ValidationException("month must be between 1 and $MONTHS_PER_YEAR")
        }
        val currency = resolveCurrency(call.request.queryParameters["currency"])
        val (from, to) = monthBounds(year, month)
        val spend = analyticsRepository.spendByCategory(user.userId, currency, from, to)
        call.respond(
            MonthlySummaryResponse(
                year = year,
                month = month,
                currency = currency,
                grandTotal = spend.sumOf { it.total }.toMoneyDouble(),
                totalByCategory =
                    spend
                        .map { it.toCategoryTotal(lang) }
                        .sortedByDescending { it.amount },
            ),
        )
    }
}

private fun Route.categoryBreakdown(analyticsRepository: AnalyticsRepository) {
    get("breakdown") {
        val user = call.requireJwtPrincipal()
        val lang = call.requestLanguageTag()
        val today = currentDate()
        val from =
            parseLocalDateOrThrow(call.request.queryParameters["from"], "from")
                ?: LocalDate(today.year, today.month, 1)
        val to = parseLocalDateOrThrow(call.request.queryParameters["to"], "to") ?: today
        if (from > to) {
            throw ValidationException("from date must not be after to date")
        }
        val currency = resolveCurrency(call.request.queryParameters["currency"])
        val spend = analyticsRepository.spendByCategory(user.userId, currency, from, to)
        // Percentages are reconciled across the whole list so they sum to
        // exactly 100.0; see allocatePercentages.
        val percentages = allocatePercentages(spend.map { it.total }, spend.sumOf { it.total })
        call.respond(
            spend
                .mapIndexed { index, categorySpend ->
                    CategoryBreakdownItem(
                        category = categorySpend.displayName(lang),
                        amount = categorySpend.total.toMoneyDouble(),
                        percentage = percentages[index],
                        count = categorySpend.count.toInt(),
                    )
                }.sortedByDescending { it.amount },
        )
    }
}

private fun Route.spendingTrend(analyticsRepository: AnalyticsRepository) {
    get("trend") {
        val user = call.requireJwtPrincipal()
        val granularity = resolveGranularity(call.request.queryParameters["granularity"])
        val periods =
            intParamOrNull(call.request.queryParameters["periods"], "periods") ?: DEFAULT_TREND_PERIODS
        if (periods !in 1..MAX_TREND_PERIODS) {
            throw ValidationException("periods must be between 1 and $MAX_TREND_PERIODS")
        }
        val currency = resolveCurrency(call.request.queryParameters["currency"])
        val today = currentDate()
        val buckets =
            when (granularity) {
                TrendGranularity.MONTHLY -> monthlyTrendPeriods(today, periods)
                TrendGranularity.WEEKLY -> weeklyTrendPeriods(today, periods)
            }
        val totals =
            analyticsRepository.totalsForRanges(
                userId = user.userId,
                currency = currency,
                ranges = buckets.map { it.from to it.to },
            )
        call.respond(
            TrendResponse(
                granularity = granularity,
                points =
                    buckets.zip(totals) { bucket, total ->
                        TrendPoint(
                            year = bucket.year,
                            month = bucket.month,
                            weekStart = bucket.weekStart,
                            total = total.toMoneyDouble(),
                        )
                    },
            ),
        )
    }
}

private fun currentDate(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.UTC).date

private fun resolveCurrency(raw: String?): Currency {
    if (raw.isNullOrBlank()) return DEFAULT_CURRENCY
    return Currency.fromCode(raw) ?: throw ValidationException("Unknown currency '$raw'")
}

private fun resolveGranularity(raw: String?): TrendGranularity {
    if (raw.isNullOrBlank()) return TrendGranularity.MONTHLY
    return when (raw.lowercase()) {
        "weekly" -> TrendGranularity.WEEKLY
        "monthly" -> TrendGranularity.MONTHLY
        else -> throw ValidationException("granularity must be 'weekly' or 'monthly'")
    }
}

/**
 * Parses an integer query param. A blank or absent value returns null so the
 * caller can apply its default; a present but non-numeric value is a client
 * error and throws [ValidationException] for the `422` envelope.
 */
private fun intParamOrNull(
    raw: String?,
    paramName: String,
): Int? {
    if (raw.isNullOrBlank()) return null
    return raw.toIntOrNull() ?: throw ValidationException("$paramName must be an integer")
}

private fun CategorySpend.displayName(languageTag: String): String =
    DefaultCategoryTranslations.displayName(categoryNameKey, categoryName, languageTag)

private fun CategorySpend.toCategoryTotal(languageTag: String): CategoryTotal =
    CategoryTotal(category = displayName(languageTag), amount = total.toMoneyDouble())

private fun BigDecimal.toMoneyDouble(): Double = setScale(MONEY_SCALE, RoundingMode.HALF_UP).toDouble()

private const val DEFAULT_TREND_PERIODS = 6
private const val MAX_TREND_PERIODS = 24
private const val MONTHS_PER_YEAR = 12
private const val MIN_YEAR = 2000
private const val MAX_YEAR = 2100
private const val MONEY_SCALE = 2
private val DEFAULT_CURRENCY = Currency.UAH
