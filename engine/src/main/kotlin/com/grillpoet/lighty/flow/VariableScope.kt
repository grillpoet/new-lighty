package com.grillpoet.lighty.flow

import com.grillpoet.lighty.core.expr.ExpressionEngine

/**
 * Flow 执行期间的变量作用域。
 *
 * 变量用 {xxx} 语法引用，替换规则：
 *   - 内容是纯标识符（字母/数字/下划线/点）→ 当作变量名直接查表
 *   - 内容是其他任何东西 → 交给 ExpressionEngine 求值
 *
 * 示例：
 *   {keyword}           → 变量 keyword
 *   {page + 1}          → 表达式
 *   {upper(name)}       → 函数调用
 *   {hasNext ? "y":"n"} → 三元
 *   {user.name}         → 变量 user.name（含点也是变量名）
 *   {}                  → 原样输出 {}
 *   \{                  → 转义，输出 {
 */
class VariableScope(initial: Map<String, String> = emptyMap()) {

    private val map = LinkedHashMap<String, String>()

    init {
        map.putAll(initial)
    }

    fun put(key: String, value: String) {
        map[key] = value
    }

    fun get(key: String): String? = map[key]

    fun all(): Map<String, String> = map.toMap()

    fun interpolate(template: String): String {
        if (template.indexOf('{') < 0) return template

        val out = StringBuilder(template.length)
        var i = 0
        while (i < template.length) {
            val c = template[i]

            // 转义 \{ → 输出 {
            if (c == '\\' && i + 1 < template.length && template[i + 1] == '{') {
                out.append('{')
                i += 2
                continue
            }

            if (c == '{') {
                val end = findClosing(template, i + 1)
                if (end < 0) {
                    // 未闭合，剩余原样输出
                    out.append(template, i, template.length)
                    break
                }
                val body = template.substring(i + 1, end)
                out.append(resolve(body))
                i = end + 1
                continue
            }

            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun resolve(body: String): String {
        if (body.isEmpty()) return "{}"
        if (isPlainIdent(body)) {
            return map[body] ?: ""
        }
        // 表达式；解析/求值失败时原样保留 {xxx}，避免误伤
        return ExpressionEngine.eval(body) { map[it] } ?: "{$body}"
    }

    /**
     * 找到与起始 { 匹配的 }。
     * 跳过字符串字面量（'...' / "..."）里的花括号。
     * 支持嵌套 {}（表达式里虽然少见，但三元里可能出现）。
     */
    private fun findClosing(s: String, start: Int): Int {
        var depth = 1
        var i = start
        while (i < s.length) {
            when (s[i]) {
                '\'' -> i = skipString(s, i, '\'')
                '"' -> i = skipString(s, i, '"')
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    /** 返回字符串结束引号的位置。未闭合时返回字符串末尾。 */
    private fun skipString(s: String, start: Int, quote: Char): Int {
        var i = start + 1
        while (i < s.length) {
            when (s[i]) {
                '\\' -> i += 2
                quote -> return i
                else -> i++
            }
        }
        return s.length
    }

    private fun isPlainIdent(s: String): Boolean {
        if (s.isEmpty()) return false
        val first = s[0]
        if (!first.isLetter() && first != '_') return false
        for (i in 1 until s.length) {
            val c = s[i]
            if (!c.isLetterOrDigit() && c != '_' && c != '.') return false
        }
        return true
    }
}