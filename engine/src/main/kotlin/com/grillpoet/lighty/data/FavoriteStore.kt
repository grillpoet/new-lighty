package com.grillpoet.lighty.data

import com.grillpoet.lighty.core.SourceDirectoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * 收藏存储。
 *
 * 收藏单位是 (sourceId, record)。播放时把 record 喂回 engine.invoke("play", ...)，
 * 每次拿到新鲜直链，不存 URL。
 *
 * 文件格式：
 * {
 *   "version": 1,
 *   "items": {
 *     "kuwo:123456": { "sourceId": "kuwo", "title": "...", "rid": "123456", ... }
 *   }
 * }
 */
class FavoriteStore(
    private val dirProvider: SourceDirectoryProvider,
    private val json: Json
) {
    private val file: File
        get() = File(dirProvider.sourcesDir().parentFile, "favorites.json")

    private val items = LinkedHashMap<String, JsonObject>()

    private val _state = MutableStateFlow<List<Pair<String, JsonObject>>>(emptyList())
    val state: StateFlow<List<Pair<String, JsonObject>>> = _state.asStateFlow()

    init {
        load()
    }

    // ==================== 查询 ====================

    fun keyOf(sourceId: String, record: Record): String? {
        val id = record["rid"]
            ?: record["playUrl"]
            ?: record["_key"]
            ?: return null
        return "$sourceId:$id"
    }

    fun contains(key: String): Boolean = items.containsKey(key)

    fun contains(sourceId: String, record: Record): Boolean {
        val key = keyOf(sourceId, record) ?: return false
        return contains(key)
    }

    // ==================== 变更 ====================

    /** 收藏/取消。返回 true 表示已收藏，false 表示已取消或失败 */
    fun toggle(sourceId: String, record: Record): Boolean {
        val key = keyOf(sourceId, record) ?: return false
        return if (items.containsKey(key)) {
            items.remove(key)
            persist()
            false
        } else {
            items[key] = recordToJson(sourceId, record)
            persist()
            true
        }
    }

    fun remove(key: String): Boolean {
        if (items.remove(key) == null) return false
        persist()
        return true
    }

    // ==================== 导入导出 ====================

    fun exportText(): String = json.encodeToString(
        JsonObject.serializer(),
        buildRoot()
    )

    /** 导入（合并，同 key 覆盖）。返回处理的条数 */
    fun importText(text: String): Result<Int> = runCatching {
        val root = json.parseToJsonElement(text.trim()).jsonObject
        require(validate(root)) { "格式不符：需要 { version, items }" }
        val incoming = root["items"]!!.jsonObject
        var count = 0
        incoming.forEach { (k, v) ->
            items[k] = v.jsonObject
            count++
        }
        persist()
        count
    }

    // ==================== 内部 ====================

    private fun load() {
        items.clear()
        if (file.exists()) {
            runCatching {
                val root = json.parseToJsonElement(file.readText()).jsonObject
                if (validate(root)) {
                    root["items"]!!.jsonObject.forEach { (k, v) ->
                        items[k] = v.jsonObject
                    }
                }
            }
        }
        emit()
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(
                json.encodeToString(JsonObject.serializer(), buildRoot())
            )
        }
        emit()
    }

    private fun emit() {
        _state.value = items.toList()
    }

    private fun buildRoot(): JsonObject = buildJsonObject {
        put("version", 1)
        put("items", buildJsonObject {
            items.forEach { (k, v) -> put(k, v) }
        })
    }

    private fun validate(root: JsonObject): Boolean {
        root["version"]?.jsonPrimitive?.intOrNull ?: return false
        root["items"] as? JsonObject ?: return false
        return true
    }

    private fun recordToJson(sourceId: String, record: Record): JsonObject =
        buildJsonObject {
            put("sourceId", sourceId)
            record.forEach { (k, v) -> put(k, v) }
            put("addedAt", System.currentTimeMillis())
        }
}