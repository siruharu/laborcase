package kr.laborcase.law.sync

import kr.laborcase.law.client.LawOpenApiClient
import kr.laborcase.law.client.LawSearchHit
import kr.laborcase.law.storage.RawXmlStore
import kr.laborcase.law.xml.LawXmlParser
import kr.laborcase.law.xml.ParsedLawBody
import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionTemplate

/**
 * Walks every law in [LawSeed], fetches the current version from 법제처,
 * persists an immutable copy in GCS, parses + inserts rows, and marks the
 * prior is_current row as superseded.
 *
 * Transaction boundary is **one law per transaction**: if 근기법 fails to
 * parse we still try 최저임금법; sync_log records the overall run's status
 * after the last per-law attempt.
 *
 * Idempotency: if the DB already has a law_version row with the same
 * (law, lsiSeq), the job skips the whole pipeline for that law. This lets
 * us re-run the job safely after a partial outage — and lets DeltaSyncJob
 * reuse the same implementation for the "expected no-op" case.
 */
class FullSyncJob(
    private val client: LawOpenApiClient,
    private val rawXmlStore: RawXmlStore,
    private val parser: LawXmlParser,
    private val repo: LawSyncRepository,
    private val syncLog: SyncLogRepository,
    private val tx: TransactionTemplate,
    private val seed: LawSeed,
) {

    private val log = LoggerFactory.getLogger(FullSyncJob::class.java)

    data class Result(
        val versionsChanged: Int,
        val lawsSkippedIdempotent: Int,
        val lawsFailed: Int,
    )

    /**
     * Runs the full seed walk. Accepts a [jobName] so DeltaSyncJob can reuse
     * the implementation while distinguishing itself in sync_log. Defaults to
     * [JOB_NAME] = "full-sync".
     */
    fun run(jobName: String = JOB_NAME): Result {
        val logId = syncLog.start(jobName)
        var changed = 0
        var skipped = 0
        var failed = 0
        try {
            for (entry in seed.laws) {
                val outcome = processSafely(entry)
                when (outcome) {
                    LawOutcome.IMPORTED -> changed++
                    LawOutcome.SKIPPED_IDEMPOTENT -> skipped++
                    LawOutcome.FAILED -> failed++
                }
                log.info("[{}] law {} (lsId={}) → {}", jobName, entry.shortName, entry.lsId, outcome)
            }
            syncLog.success(logId, changed)
        } catch (e: Throwable) {
            // processSafely catches per-law errors, so reaching here implies a
            // programming bug (e.g., bad seed shape). Record and rethrow.
            syncLog.failed(logId, e.message ?: e::class.simpleName.orEmpty())
            throw e
        }
        return Result(versionsChanged = changed, lawsSkippedIdempotent = skipped, lawsFailed = failed)
    }

    private enum class LawOutcome { IMPORTED, SKIPPED_IDEMPOTENT, FAILED }

    private fun processSafely(entry: LawSeedEntry): LawOutcome {
        return try {
            tx.execute { processOneLaw(entry) } ?: LawOutcome.FAILED
        } catch (e: Exception) {
            log.error("failed to sync ${entry.shortName} (lsId=${entry.lsId})", e)
            LawOutcome.FAILED
        }
    }

    private fun processOneLaw(entry: LawSeedEntry): LawOutcome {
        val hit = findCurrentHit(entry)
            ?: run {
                log.warn("no 현행 hit for ${entry.shortName} (lsId=${entry.lsId})")
                return LawOutcome.FAILED
            }

        val parsed = parsedBodyOrSkipIfCached(entry, hit) ?: return LawOutcome.SKIPPED_IDEMPOTENT

        val lawId = repo.upsertLaw(parsed.body.law)
        repo.demoteCurrentVersions(lawId)
        val versionId = repo.insertLawVersion(
            lawId = lawId,
            parsed = parsed.body.version,
            rawXmlGcsUri = parsed.rawXmlGcsUri,
            isCurrent = true,
        )
        repo.insertArticles(versionId, parsed.body.articles)
        return LawOutcome.IMPORTED
    }

    private fun findCurrentHit(entry: LawSeedEntry): LawSearchHit? {
        val hits = client.searchLaws(entry.searchQuery, display = 5)
        return hits.firstOrNull { it.lsId == entry.lsId && it.isCurrent }
            ?: hits.firstOrNull { it.lsId == entry.lsId }
    }

    /** Holds the raw uri alongside the parsed body so the caller has both. */
    private data class ParsedWithUri(val body: ParsedLawBody, val rawXmlGcsUri: String)

    private fun parsedBodyOrSkipIfCached(entry: LawSeedEntry, hit: LawSearchHit): ParsedWithUri? {
        // Cheap idempotency: if the DB already has this exact version, skip.
        // A law row may not exist yet; in that case we still need to proceed
        // (upsert will create it below).
        val existingLawId = repo.upsertLaw(
            kr.laborcase.law.xml.ParsedLaw(
                lsId = entry.lsId,
                nameKr = entry.searchQuery, // placeholder; real name is filled after parse
                shortName = entry.shortName,
                department = null,
            ),
        )
        if (repo.findLawVersionId(existingLawId, hit.lsiSeq) != null) return null

        val response = client.fetchLawByLsId(hit.lsId)
        val uri = rawXmlStore.put(entry.lsId, hit.lsiSeq, response.xml).toString()
        val body = parser.parse(response.xml, hit.lsiSeq)
        return ParsedWithUri(body, uri)
    }

    companion object {
        const val JOB_NAME = "full-sync"
    }
}
