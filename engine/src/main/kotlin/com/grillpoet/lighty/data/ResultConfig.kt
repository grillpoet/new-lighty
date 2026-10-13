package com.grillpoet.lighty.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 搜索结果布局配置。
 *
 * 配置里可写字符串（简写）：
 *   "resultLayout": "grid"
 *
 * 也可写对象（完整）：
 *   "resultLayout": {
 *     "mode": "grid",
 *     "columns": 3,
 *     "coverRatio": 0.75,
 *     "showSubtitle": true
 *   }
 */
@Serializable(with = ResultConfigSerializer::class)
data class ResultConfig(
    /** 布局模式：auto（按 kind 判断）/ list / grid */
    val mode: String = "auto",
    /** grid 模式下的列数，1..6 */
    val columns: Int = 2,
    /** grid 模式下封面宽高比（width / height）。0.667 = 竖版海报；1.0 = 正方；1.5 = 横版 */
    val coverRatio: Float = 0.667f,
    /** 是否显示副标题（作者/集数等） */
    val showSubtitle: Boolean = true
)

object ResultConfigSerializer : KSerializer<ResultConfig> {

    override val descriptor: SerialDescriptor =
        JsonObject.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ResultConfig) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("ResultConfig 只支持 JSON 序列化")
        jsonEncoder.encodeJsonElement(buildJsonObject {
            put("mode", value.mode)
            put("columns", value.columns)
            put("coverRatio", value.coverRatio)
            put("showSubtitle", value.showSubtitle)
        })
    }

    override fun deserialize(decoder: Decoder): ResultConfig {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("ResultConfig 只支持 JSON 反序列化")
        return when (val elem = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> ResultConfig(mode = elem.content)
            is JsonObject -> ResultConfig(
                mode = elem["mode"]?.jsonPrimitive?.content ?: "auto",
                columns = elem["columns"]?.jsonPrimitive?.intOrNull
                    ?.coerceIn(1, 6) ?: 2,
                coverRatio = elem["coverRatio"]?.jsonPrimitive?.floatOrNull
                    ?: 0.667f,
                showSubtitle = elem["showSubtitle"]?.jsonPrimitive?.booleanOrNull
                    ?: true
            )
            else -> ResultConfig()
        }
    }
}