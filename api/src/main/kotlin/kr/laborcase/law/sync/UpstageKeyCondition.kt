package kr.laborcase.law.sync

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * Activates beans that need an Upstage API key but not the full embedding
 * pipeline (e.g. the runtime search endpoint). Decoupled from
 * [UpstageEnabledCondition] so the API service can serve search even when
 * `embedding.enabled` is false (it would still write through the embedder
 * only on the sync jobs).
 */
class UpstageKeyCondition : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
        return context.environment.getProperty("upstage.api-key", "").isNotBlank()
    }
}
