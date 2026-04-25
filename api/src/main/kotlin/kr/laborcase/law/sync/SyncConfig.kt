package kr.laborcase.law.sync

import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import kr.laborcase.law.client.LawOpenApiClient
import kr.laborcase.law.client.LawOpenApiUrlBuilder
import kr.laborcase.law.embed.ArticleEmbedder
import kr.laborcase.law.embed.UpstageEmbeddingClient
import kr.laborcase.law.storage.GcsRawXmlStore
import kr.laborcase.law.storage.RawXmlStore
import kr.laborcase.law.web.LawReadRepository
import kr.laborcase.law.web.SourceMetaFactory
import kr.laborcase.law.web.SyncFreshnessService
import kr.laborcase.law.xml.LawXmlParser
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import javax.sql.DataSource

/**
 * Wires the sync layer into a Spring context. Each bean is defined here so
 * Task 10's Cloud Run Job entry point can reuse the same wiring.
 *
 * Keeping the definitions explicit (rather than @Component scanning) makes
 * it trivial to swap a single dependency in tests — FullSyncJobIntegrationTest
 * constructs these manually rather than going through Spring.
 */
@Configuration
class SyncConfig {

    @Bean
    fun storage(): Storage = StorageOptions.getDefaultInstance().service

    @Bean
    fun rawXmlStore(
        storage: Storage,
        @Value("\${laborcase.gcs.raw-bucket}") bucket: String,
    ): RawXmlStore = GcsRawXmlStore(storage, bucket)

    @Bean
    fun lawOpenApiClient(@Value("\${law.oc}") oc: String): LawOpenApiClient =
        LawOpenApiClient(LawOpenApiUrlBuilder(oc = oc))

    @Bean
    fun lawXmlParser(): LawXmlParser = LawXmlParser()

    @Bean
    fun jdbcClient(dataSource: DataSource): JdbcClient = JdbcClient.create(dataSource)

    @Bean
    fun lawSyncRepository(jdbcClient: JdbcClient): LawSyncRepository = LawSyncRepository(jdbcClient)

    @Bean
    fun syncLogRepository(jdbcClient: JdbcClient): SyncLogRepository = SyncLogRepository(jdbcClient)

    @Bean
    fun transactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate =
        TransactionTemplate(transactionManager)

    @Bean
    fun lawSeed(): LawSeed = LawSeedLoader.loadFromClasspath()

    @Bean(name = ["upstageEmbeddingClient"])
    @org.springframework.context.annotation.Conditional(UpstageEnabledCondition::class)
    fun upstageEmbeddingClient(
        @Value("\${upstage.api-key:}") apiKey: String,
    ): UpstageEmbeddingClient = UpstageEmbeddingClient(apiKey = apiKey)

    @Bean(name = ["articleEmbedder"])
    @org.springframework.context.annotation.Conditional(UpstageEnabledCondition::class)
    fun articleEmbedder(
        jdbcClient: JdbcClient,
        upstageEmbeddingClient: UpstageEmbeddingClient,
    ): ArticleEmbedder = ArticleEmbedder(jdbcClient, upstageEmbeddingClient)

    @Bean
    fun fullSyncJob(
        client: LawOpenApiClient,
        rawXmlStore: RawXmlStore,
        parser: LawXmlParser,
        repo: LawSyncRepository,
        syncLog: SyncLogRepository,
        tx: TransactionTemplate,
        seed: LawSeed,
        embedder: ArticleEmbedder?,
    ): FullSyncJob = FullSyncJob(client, rawXmlStore, parser, repo, syncLog, tx, seed, embedder)

    @Bean
    fun deltaSyncJob(fullSyncJob: FullSyncJob): DeltaSyncJob = DeltaSyncJob(fullSyncJob)

    @Bean
    fun lawReadRepository(jdbcClient: JdbcClient): LawReadRepository = LawReadRepository(jdbcClient)

    @Bean
    fun sourceMetaFactory(): SourceMetaFactory = SourceMetaFactory()

    @Bean
    fun syncFreshnessService(
        jdbcClient: JdbcClient,
        @Value("\${laborcase.freshness.stale-threshold-hours:48}") staleHours: Int,
    ): SyncFreshnessService = SyncFreshnessService(
        jdbcClient,
        staleThreshold = Duration.ofHours(staleHours.toLong()),
    )
}
