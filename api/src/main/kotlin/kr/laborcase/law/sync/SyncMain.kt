package kr.laborcase.law.sync

import kr.laborcase.LaborcaseApiApplication
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import kotlin.system.exitProcess

/**
 * Cloud Run Job entry point. Launches the Spring context in NONE web mode,
 * dispatches to the selected sync job, and exits with the appropriate
 * status so Cloud Run can mark the execution as succeeded / failed.
 *
 * Usage:
 *   java -jar api.jar full
 *   java -jar api.jar delta
 */
object SyncMain {

    private val log = LoggerFactory.getLogger(SyncMain::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.firstOrNull()?.lowercase()
            ?: error("usage: SyncMain <full|delta>")

        val app = SpringApplication(LaborcaseApiApplication::class.java).apply {
            webApplicationType = WebApplicationType.NONE
        }
        val ctx = app.run(*args)
        val exit = try {
            when (mode) {
                "full" -> {
                    val r = ctx.getBean(FullSyncJob::class.java).run()
                    log.info("full-sync result={}", r)
                    if (r.lawsFailed > 0) 1 else 0
                }
                "delta" -> {
                    val r = ctx.getBean(DeltaSyncJob::class.java).run()
                    log.info("delta-sync result={}", r)
                    if (r.lawsFailed > 0) 1 else 0
                }
                else -> {
                    log.error("unknown mode: {}", mode)
                    2
                }
            }
        } catch (e: Throwable) {
            log.error("sync run failed fatally", e)
            3
        } finally {
            ctx.close()
        }
        exitProcess(exit)
    }
}
