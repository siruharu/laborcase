package kr.laborcase.law.web

import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Exposes a single projection of the most recent successful sync so the API
 * layer can decorate each response with "how stale is this data right now?"
 *
 * The frontend uses this to decide whether to show a "데이터가 오래되었습니다"
 * banner — see ADR-0002.
 */
class SyncFreshnessService(
    private val jdbc: JdbcClient,
    private val staleThreshold: Duration,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun current(): Freshness {
        val lastSuccess = jdbc.sql(
            """
            SELECT finished_at FROM sync_log
             WHERE status = 'SUCCESS' AND finished_at IS NOT NULL
             ORDER BY finished_at DESC
             LIMIT 1
            """.trimIndent(),
        ).query(java.sql.Timestamp::class.java).optional().orElse(null)?.toInstant()

        val now = Instant.now(clock)
        val isStale = lastSuccess == null || Duration.between(lastSuccess, now) > staleThreshold
        return Freshness(
            lastSyncedAt = lastSuccess,
            stale = isStale,
            staleThresholdHours = staleThreshold.toHours().toInt(),
        )
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Freshness(
    val lastSyncedAt: Instant?,
    val stale: Boolean,
    val staleThresholdHours: Int,
)

/**
 * Spring Boot configuration helper that wires the freshness service with the
 * externalized `laborcase.freshness.stale-threshold-hours` property.
 */
class SyncFreshnessProperties(
    @Value("\${laborcase.freshness.stale-threshold-hours:48}") val staleHours: Int,
)
