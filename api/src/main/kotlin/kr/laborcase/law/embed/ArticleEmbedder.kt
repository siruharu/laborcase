package kr.laborcase.law.embed

import com.pgvector.PGvector
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID

/**
 * Finds articles that don't yet have an embedding and embeds them via Upstage,
 * then inserts the resulting vectors into article_embedding.
 *
 * Batched to cap the per-request token cost and to spread network I/O.
 *
 * Only the article's body is embedded, prefixed with the 법령 약칭·조문 번호
 * for context ("근기법 제23조 ① …"). This helps the passage model ground
 * each vector in its 조문 location without blowing up the token cost.
 */
class ArticleEmbedder(
    private val jdbc: JdbcClient,
    private val client: UpstageEmbeddingClient,
    private val batchSize: Int = 20,
) {
    private val log = LoggerFactory.getLogger(ArticleEmbedder::class.java)

    data class Result(val embedded: Int, val skipped: Int, val failed: Int)

    fun embedPending(limit: Int = Int.MAX_VALUE): Result {
        var embedded = 0
        var skipped = 0
        var failed = 0

        while (embedded + skipped + failed < limit) {
            val rows = fetchNextBatch(batchSize)
            if (rows.isEmpty()) break

            try {
                val vectors = client.embedPassages(rows.map { it.textForEmbedding })
                require(vectors.size == rows.size) {
                    "Upstage returned ${vectors.size} vectors for ${rows.size} inputs"
                }
                insertEmbeddings(rows, vectors)
                embedded += rows.size
            } catch (e: Exception) {
                log.error("batch embed failed (batch of ${rows.size})", e)
                failed += rows.size
                // Move on so a single bad article doesn't block the rest; the
                // skipped rows stay pending and will be retried next run.
                break
            }
        }
        log.info("embed run: embedded={} failed={}", embedded, failed)
        return Result(embedded = embedded, skipped = skipped, failed = failed)
    }

    private data class PendingRow(
        val articleId: UUID,
        val textForEmbedding: String,
    )

    private fun fetchNextBatch(n: Int): List<PendingRow> =
        jdbc.sql(
            """
            SELECT a.id AS article_id,
                   COALESCE(l.short_name, l.name_kr) AS law_tag,
                   a.jo,
                   a.title,
                   a.body
              FROM article a
              JOIN law_version lv ON lv.id = a.law_version_id AND lv.is_current = TRUE
              JOIN law l ON l.id = lv.law_id
         LEFT JOIN article_embedding ae ON ae.article_id = a.id
             WHERE ae.article_id IS NULL
               AND a.body IS NOT NULL AND char_length(a.body) > 0
             ORDER BY a.id
             LIMIT :n
            """.trimIndent(),
        )
            .param("n", n)
            .query { rs, _ ->
                val lawTag = rs.getString("law_tag")
                val jo = rs.getString("jo").trim().toInt()
                val title = rs.getString("title")
                val body = rs.getString("body")
                val header = buildString {
                    append(lawTag).append(" 제").append(jo).append("조")
                    if (!title.isNullOrBlank()) append("(").append(title).append(")")
                }
                PendingRow(
                    articleId = rs.getObject("article_id", UUID::class.java),
                    textForEmbedding = "$header\n$body",
                )
            }.list()

    private fun insertEmbeddings(rows: List<PendingRow>, vectors: List<FloatArray>) {
        // Use a single INSERT per row inside a transaction boundary set by
        // the caller (FullSyncJob wraps this). For our volumes the round-trips
        // are fine; switch to copy-binary if it becomes hot.
        for ((row, vec) in rows.zip(vectors)) {
            jdbc.sql(
                """
                INSERT INTO article_embedding (article_id, vector, model)
                VALUES (:articleId, :vec, :model)
                ON CONFLICT (article_id) DO UPDATE
                    SET vector = EXCLUDED.vector,
                        model  = EXCLUDED.model,
                        embedded_at = now()
                """.trimIndent(),
            )
                .param("articleId", row.articleId)
                .param("vec", PGvector(vec))
                .param("model", UpstageEmbeddingClient.MODEL_PASSAGE)
                .update()
        }
    }
}
