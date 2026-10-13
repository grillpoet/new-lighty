package com.grillpoet.lighty.flow.impl

import com.grillpoet.lighty.flow.FlowConfigParser
import com.grillpoet.lighty.flow.FlowRunner
import com.grillpoet.lighty.flow.api.Fetcher
import com.grillpoet.lighty.flow.api.FlowEngine
import com.grillpoet.lighty.flow.api.FlowResult
import com.grillpoet.lighty.flow.api.JsRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class DefaultFlowEngine(
    private val fetcher: Fetcher,
    private val jsRuntime: JsRuntime? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
) : FlowEngine {

    private val runner = FlowRunner(fetcher, jsRuntime, json)

    override suspend fun run(config: JsonObject, args: Map<String, String>): FlowResult =
        withContext(Dispatchers.IO) {
            val flowConfig = FlowConfigParser.parse(config, json)
            runner.run(flowConfig, args)
        }
}