package com.grillpoet.lighty.data

import com.grillpoet.lighty.core.DebugLog
import com.grillpoet.lighty.core.LoggedException
import com.grillpoet.lighty.flow.api.FlowEngine
import com.grillpoet.lighty.flow.api.FlowResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class EngineConfig(
    val maxConcurrent: Int = 6,
    val throttleMs: Long = 200L
)

class Engine(
    private val sourceStore: SourceStore,
    private val flowEngine: FlowEngine,
    private val config: EngineConfig = EngineConfig()
) {
    private val semaphore = Semaphore(config.maxConcurrent)
    private val throttle = Mutex()
    private var lastAt = 0L

    fun sources(): List<Source> = sourceStore.sources.value
    fun reload() = sourceStore.reload()

    suspend fun invoke(
        source: Source,
        action: String,
        args: Record
    ): Result<FlowResult> = withContext(Dispatchers.IO) {
        runCatching {
            val flowConfig = source.actionConfig(action)
                ?: error("源 ${source.id} 无 action: $action")

            val enrichedArgs = args + mapOf(
                "_type" to source.kind.typeTag,
                "_kind" to source.kind.name
            )

            semaphore.withPermit {
                throttle.withLock {
                    val elapsed = System.currentTimeMillis() - lastAt
                    if (elapsed < config.throttleMs) delay(config.throttleMs - elapsed)
                    lastAt = System.currentTimeMillis()
                }
                flowEngine.run(flowConfig, enrichedArgs)
            }
        }.onFailure { e ->
            if (e !is LoggedException) {
                DebugLog.error(
                    "flow/${source.id}/$action\n" +
                            "${e.javaClass.simpleName}: ${e.message ?: "未知错误"}"
                )
            }
        }
    }
}