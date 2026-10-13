package com.grillpoet.lighty.data

import com.grillpoet.lighty.core.SourceDirectoryProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File

class SourceStore(
    private val dirProvider: SourceDirectoryProvider,
    private val json: Json
) {
    private val dir: File get() = dirProvider.sourcesDir()

    private val _sources = MutableStateFlow<List<Source>>(emptyList())
    val sources: StateFlow<List<Source>> = _sources.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        val list = dir.listFiles { f -> f.isFile && f.extension == "json" }
            .orEmpty()
            .mapNotNull { f ->
                runCatching { json.decodeFromString<Source>(f.readText()) }
                    .getOrNull()
                    ?.takeIf { it.id.isNotBlank() }
            }
        _sources.value = list
    }

    fun get(id: String): Source? = _sources.value.firstOrNull { it.id == id }

    /** 保存单个源（覆盖同名文件） */
    fun delete(id: String): Boolean {
        val ok = File(dir, "$id.json").delete()
        if (ok) reload()
        return ok
    }

    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
        reload()
    }

    /** 批量删除。返回成功删除的数量 */
    fun deleteIds(ids: Collection<String>): Int {
        var count = 0
        ids.forEach { id ->
            if (File(dir, "$id.json").delete()) count++
        }
        if (count > 0) reload()
        return count
    }

    // ==================== JSON 导入导出 ====================

    /**
     * 导出为 JSON 文本。
     * @param ids 为 null 或空时导出全部
     */
    /**
     * 导出单个源为独立 JSON 字符串（纯 Source，不含包裹）。
     * 返回 null 表示源不存在。
     *
     * 产出格式与 sources/itunes.json 一致，可直接导入。
     */
    fun exportJsonOne(sourceId: String): String? {
        val source = _sources.value.firstOrNull { it.id == sourceId } ?: return null
        return json.encodeToString(Source.serializer(), source)
    }

    /**
     * 从 JSON 文本导入。支持三种格式：
     *   1. { "version": 1, "sources": [ ... ] }   包裹对象
     *   2. [ ... ]                                 裸数组
     *   3. { "id": "...", ... }                    单个源
     *
     * 返回成功导入的数量。同名 id 覆盖。
     */
    suspend fun importJson(text: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = text.trim()
            require(trimmed.isNotEmpty()) { "空文件" }

            val sources: List<Source> = when {
                trimmed.startsWith("[") -> {
                    json.decodeFromString(ListSerializer(Source.serializer()), trimmed)
                }
                else -> {
                    val root = json.parseToJsonElement(trimmed).jsonObject
                    val elem = root["sources"]
                    if (elem != null) {
                        json.decodeFromJsonElement(
                            ListSerializer(Source.serializer()),
                            elem
                        )
                    } else {
                        // 当成单个源
                        listOf(json.decodeFromString(Source.serializer(), trimmed))
                    }
                }
            }

            var count = 0
            sources.forEach { src ->
                if (src.id.isNotBlank()) {
                    val f = File(dir, "${src.id}.json")
                    f.writeText(json.encodeToString(Source.serializer(), src))
                    count++
                }
            }
            reload()
            count
        }
    }
}
