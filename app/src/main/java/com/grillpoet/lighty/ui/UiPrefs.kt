package com.grillpoet.lighty.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局 UI 偏好。用户在当前会话内切换显示模式。
 * 不落盘——重启回到默认（列表）。
 */
object UiPrefs {

    /**
     * 显示模式：
     *  - List          单列列表
     *  - Grid(2/3/4)   网格
     */
    sealed interface DisplayMode {
        data object List : DisplayMode
        data class Grid(val columns: Int) : DisplayMode
    }

    private val cycle = listOf(
        DisplayMode.List,
        DisplayMode.Grid(2),
        DisplayMode.Grid(3),
        DisplayMode.Grid(4)
    )

    private val _displayMode = MutableStateFlow<DisplayMode>(DisplayMode.Grid(2))
    val displayMode: StateFlow<DisplayMode> = _displayMode.asStateFlow()

    /** 循环切换：列表 → 2列 → 3列 → 4列 → 列表 */
    fun toggleDisplayMode() {
        val current = _displayMode.value
        val idx = cycle.indexOfFirst {
            it::class == current::class &&
                    (it !is DisplayMode.Grid || it.columns == (current as? DisplayMode.Grid)?.columns)
        }
        val next = cycle[(idx + 1) % cycle.size]
        _displayMode.value = next
    }

    /** 直接设置 */
    fun setDisplayMode(mode: DisplayMode) {
        _displayMode.value = mode
    }
}