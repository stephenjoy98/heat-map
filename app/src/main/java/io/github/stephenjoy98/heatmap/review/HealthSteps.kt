package io.github.stephenjoy98.heatmap.review

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId

class StepsSummary(val total: Long, val days: Int, val bestDay: LocalDate?, val bestDaySteps: Long) {
    val perDay: Long get() = if (days > 0) total / days else 0
}

/** Step counts from Android Health Connect (read-only). */
object HealthSteps {
    val permissions = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    enum class Availability { AVAILABLE, NEEDS_INSTALL, UNSUPPORTED }

    fun availability(context: Context): Availability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.NEEDS_INSTALL
        else -> Availability.UNSUPPORTED
    }

    suspend fun hasPermission(context: Context): Boolean {
        if (availability(context) != Availability.AVAILABLE) return false
        return HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(permissions)
    }

    suspend fun summary(context: Context, from: Long, to: Long): StepsSummary? {
        if (!hasPermission(context)) return null
        val client = HealthConnectClient.getOrCreate(context)
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(from)
        val end = Instant.ofEpochMilli(minOf(to, System.currentTimeMillis()))
        if (!end.isAfter(start)) return null

        val total = client.aggregate(
            AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, end))
        )[StepsRecord.COUNT_TOTAL] ?: 0L

        val daily = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(
                    LocalDateTime.ofInstant(start, zone),
                    LocalDateTime.ofInstant(end, zone),
                ),
                timeRangeSlicer = Period.ofDays(1),
            )
        )
        val withSteps = daily.mapNotNull { g -> g.result[StepsRecord.COUNT_TOTAL]?.let { g.startTime.toLocalDate() to it } }
            .filter { it.second > 0 }
        val best = withSteps.maxByOrNull { it.second }
        return StepsSummary(total, withSteps.size, best?.first, best?.second ?: 0L)
    }
}
