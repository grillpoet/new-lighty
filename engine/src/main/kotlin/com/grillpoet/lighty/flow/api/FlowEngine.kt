package com.grillpoet.lighty.flow.api

import kotlinx.serialization.json.JsonObject

/**
 * Flow 引擎对外契约。
 *
 * 输入：源 JSON 配置（flow 节点下的某个 action）+ 运行时参数
 * 输出：结构化结果 FlowResult
 *
 * 不依赖任何业务模型（Source / MediaKind / Episode）。
 * 业务信息通过 args 里的字符串 tag 传入（"_type" / "_kind" 等）。
 */
interface FlowEngine {
    suspend fun run(config: JsonObject, args: Map<String, String>): FlowResult
}