package kr.laborcase.law.sync

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.InputStream

/** One row from laborcase.law.seed.yaml — the canonical list of laws to sync. */
data class LawSeedEntry(
    val shortName: String,
    val lsId: String,
    val searchQuery: String,
)

data class LawSeed(val laws: List<LawSeedEntry>)

object LawSeedLoader {
    private val mapper = YAMLMapper().registerKotlinModule()

    fun load(input: InputStream): LawSeed = mapper.readValue(input, LawSeed::class.java)

    fun loadFromClasspath(path: String = "laborcase.law.seed.yaml"): LawSeed {
        val stream = LawSeedLoader::class.java.classLoader.getResourceAsStream(path)
            ?: error("$path not found on classpath")
        return stream.use { load(it) }
    }
}
