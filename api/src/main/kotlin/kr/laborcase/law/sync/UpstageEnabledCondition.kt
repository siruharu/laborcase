package kr.laborcase.law.sync

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * Activates the embedding beans only when both `EMBEDDING_ENABLED=true`
 * and a non-empty Upstage API key is configured. Keeps dev / test runs
 * (without an API key) free of accidental network calls and keeps the
 * Spring context resolvable even when the key hasn't been seeded yet.
 */
class UpstageEnabledCondition : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
        val env = context.environment
        val enabled = env.getProperty("embedding.enabled", "false").toBoolean()
        val key = env.getProperty("upstage.api-key", "")
        return enabled && key.isNotBlank()
    }
}
