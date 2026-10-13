package com.grillpoet.lighty.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 运行时调试记录。只有两类：
 *  - ERROR：网络错误、HTTP 非 2xx、播放失败、未捕获异常
 *  - PRINT：源配置里 print op 主动打印
 *
 * 全局单例，纯内存，环形缓冲 300 条。
 */
object DebugLog {

    enum class Kind { ERROR, PRINT }

    data class Entry(
        val id: Long,
        val timestamp: Long,
        val kind: Kind,
        val text: String
    )

    private const val MAX_ENTRIES = 300
    private const val MAX_TEXT_LEN = 4000

    private var nextId = 0L

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun error(text: String) = write(Kind.ERROR, text)

    fun print(text: String) = write(Kind.PRINT, text)

    private fun write(kind: Kind, text: String) {
        if (text.isBlank()) return
        val entry = Entry(
            id = nextId++,
            timestamp = System.currentTimeMillis(),
            kind = kind,
            text = if (text.length > MAX_TEXT_LEN) text.take(MAX_TEXT_LEN) else text
        )
        _entries.update { prev ->
            val list = prev + entry
            if (list.size > MAX_ENTRIES) list.takeLast(MAX_ENTRIES) else list
        }
    }

    fun show() { _visible.value = true }
    fun hide() { _visible.value = false }
    fun toggle() { _visible.value = !_visible.value }
    fun clear() { _entries.value = emptyList() }
}