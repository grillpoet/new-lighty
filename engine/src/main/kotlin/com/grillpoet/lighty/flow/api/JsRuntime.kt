package com.grillpoet.lighty.flow.api

/**
 * JS 执行引擎抽象。默认 null（不启用）。
 * app 层可注入 Rhino 实现。
 */
interface JsRuntime {
    fun eval(code: String, bindings: Map<String, String>): String
}