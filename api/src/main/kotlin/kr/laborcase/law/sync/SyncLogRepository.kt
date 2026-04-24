package kr.laborcase.law.sync

import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID

class SyncLogRepository(private val jdbc: JdbcClient) {

    fun start(jobName: String): UUID =
        jdbc.sql(
            """
            INSERT INTO sync_log (job_name, status)
            VALUES (:job, 'RUNNING')
            RETURNING id
            """.trimIndent(),
        )
            .param("job", jobName)
            .query(UUID::class.java).single()

    fun success(id: UUID, versionsChanged: Int) {
        jdbc.sql(
            """
            UPDATE sync_log
               SET finished_at      = now(),
                   status           = 'SUCCESS',
                   versions_changed = :changed
             WHERE id = :id
            """.trimIndent(),
        )
            .param("id", id)
            .param("changed", versionsChanged)
            .update()
    }

    fun failed(id: UUID, errorMessage: String) {
        jdbc.sql(
            """
            UPDATE sync_log
               SET finished_at   = now(),
                   status        = 'FAILED',
                   error_message = :err
             WHERE id = :id
            """.trimIndent(),
        )
            .param("id", id)
            .param("err", errorMessage.take(2000))
            .update()
    }
}
