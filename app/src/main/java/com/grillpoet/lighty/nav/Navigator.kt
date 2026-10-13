package com.grillpoet.lighty.nav

import androidx.compose.runtime.mutableStateListOf

/**
 * 类型安全的导航栈。无字符串路由、无参数序列化。
 *
 * 与 libi 一致：
 *  - 栈底固定为 Screen.Main
 *  - push 有上限保护，防止无限嵌套
 *  - 相邻重复不入栈
 */
class Navigator {

    private companion object {
        const val MAX_STACK = 20
    }

    private val _stack = mutableStateListOf<Screen>(Screen.Main)

    val current: Screen get() = _stack.last()

    fun push(screen: Screen) {
        if (_stack.size >= MAX_STACK) return
        if (_stack.lastOrNull() == screen) return
        _stack.add(screen)
    }

    fun pop(): Boolean {
        if (_stack.size <= 1) return false
        _stack.removeAt(_stack.lastIndex)
        return true
    }
}