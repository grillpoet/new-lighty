package com.grillpoet.lighty.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Source(
    val id: String,
    val name: String = "",
    val kind: MediaKind = MediaKind.MUSIC,
    val qualities: List<Map<String, String>> = emptyList(),
    val showQuality: Boolean = true,
    val resultLayout: ResultConfig = ResultConfig(),
    val buttons: List<Map<String, String>> = emptyList(),
    val imageReferer: Map<String, String> = emptyMap(),
    /** 每页条数。搜索分页判断 hasMore 时使用。0 或负值视为默认 20 */
    val pageSize: Int = 20,
    val flow: Map<String, JsonObject> = emptyMap()
) {
    fun actionConfig(name: String): JsonObject? = flow[name]
    fun hasAction(name: String): Boolean = flow.containsKey(name)

    fun buttonsFor(scope: String): List<Map<String, String>> =
        buttons.filter { b ->
            val scopes = b["scope"].orEmpty().split(",").map { it.trim() }
            scope in scopes
        }
}