package com.grillpoet.lighty.core.expr

import java.util.concurrent.ConcurrentHashMap

/**
 * 表达式求值对外 API。内部缓存 AST，同一表达式只解析一次。
 *
 * 用法：
 *   ExpressionEngine.eval("page + 1") { name -> scopeMap[name] }
 *   返回 "6"（如果 page="5"）
 *   返回 null（解析失败或求值失败）
 */
object ExpressionEngine {

    private val cache = ConcurrentHashMap<String, Node>(256)

    fun eval(expr: String, scope: (String) -> String?): String? {
        val node = cache[expr] ?: run {
            val parsed = runCatching {
                Parser(Lexer(expr).tokenize()).parse()
            }.getOrNull() ?: return null
            if (cache.size < 2000) cache[expr] = parsed
            parsed
        }
        return runCatching {
            val v = Evaluator(scope).eval(node)
            when (v) {
                null -> ""
                is Double -> if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
                else -> v.toString()
            }
        }.getOrNull()
    }
}