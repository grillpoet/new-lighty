package com.grillpoet.lighty.core.expr

import java.net.URLDecoder
import java.net.URLEncoder

class Evaluator(private val scope: (String) -> String?) {

    fun eval(n: Node): Any? = when (n) {
        is Node.Lit -> n.v
        is Node.Var -> scope(n.name)
        is Node.Unary -> evalUnary(n)
        is Node.Bin -> evalBin(n)
        is Node.Ternary -> if (truthy(eval(n.c))) eval(n.a) else eval(n.b)
        is Node.Call -> evalCall(n)
    }

    private fun evalUnary(n: Node.Unary): Any? {
        val v = eval(n.x)
        return when (n.op) {
            "!" -> !truthy(v)
            "-" -> -toNum(v)
            else -> null
        }
    }

    private fun evalBin(n: Node.Bin): Any? {
        // 短路
        if (n.op == "&&") return truthy(eval(n.l)) && truthy(eval(n.r))
        if (n.op == "||") return truthy(eval(n.l)) || truthy(eval(n.r))

        val l = eval(n.l)
        val r = eval(n.r)
        return when (n.op) {
            "+" -> {
                if (l is Double && r is Double) l + r
                else toStr(l) + toStr(r)
            }
            "-" -> toNum(l) - toNum(r)
            "*" -> toNum(l) * toNum(r)
            "/" -> { val d = toNum(r); if (d == 0.0) 0.0 else toNum(l) / d }
            "%" -> { val d = toNum(r); if (d == 0.0) 0.0 else toNum(l) % d }
            "==" -> toStr(l) == toStr(r)
            "!=" -> toStr(l) != toStr(r)
            "<"  -> cmp(l, r) < 0
            "<=" -> cmp(l, r) <= 0
            ">"  -> cmp(l, r) > 0
            ">=" -> cmp(l, r) >= 0
            else -> null
        }
    }

    private fun cmp(l: Any?, r: Any?): Int {
        val ld = l as? Double
        val rd = r as? Double
        if (ld != null && rd != null) return ld.compareTo(rd)
        return toStr(l).compareTo(toStr(r))
    }

    private fun truthy(v: Any?): Boolean = when (v) {
        null -> false
        is Boolean -> v
        is Double -> v != 0.0
        is String -> v.isNotEmpty() && v != "0" && v != "false"
        else -> true
    }

    private fun toNum(v: Any?): Double = when (v) {
        null -> 0.0
        is Double -> v
        is Boolean -> if (v) 1.0 else 0.0
        is String -> v.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    private fun toStr(v: Any?): String = when (v) {
        null -> ""
        is Double -> if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
        else -> v.toString()
    }

    private fun evalCall(n: Node.Call): Any? {
        val a = n.args.map { eval(it) }
        return when (n.name) {
            // 字符串
            "len" -> toStr(a.getOrNull(0)).length.toDouble()
            "upper" -> toStr(a.getOrNull(0)).uppercase()
            "lower" -> toStr(a.getOrNull(0)).lowercase()
            "trim" -> toStr(a.getOrNull(0)).trim()
            "sub" -> {
                val s = toStr(a.getOrNull(0))
                val f = toNum(a.getOrNull(1)).toInt().coerceIn(0, s.length)
                val t = if (a.size >= 3) toNum(a[2]).toInt().coerceIn(f, s.length) else s.length
                s.substring(f, t)
            }
            "replace" -> toStr(a.getOrNull(0))
                .replace(toStr(a.getOrNull(1)), toStr(a.getOrNull(2)))
            "split" -> {
                val parts = toStr(a.getOrNull(0)).split(toStr(a.getOrNull(1)))
                parts.getOrNull(toNum(a.getOrNull(2)).toInt()) ?: ""
            }
            "contains" -> toStr(a.getOrNull(0)).contains(toStr(a.getOrNull(1)))
            "startsWith" -> toStr(a.getOrNull(0)).startsWith(toStr(a.getOrNull(1)))
            "endsWith" -> toStr(a.getOrNull(0)).endsWith(toStr(a.getOrNull(1)))
            "indexOf" -> toStr(a.getOrNull(0)).indexOf(toStr(a.getOrNull(1))).toDouble()
            // 数字
            "int" -> toNum(a.getOrNull(0)).toLong().toDouble()
            "abs" -> kotlin.math.abs(toNum(a.getOrNull(0)))
            "min" -> minOf(toNum(a.getOrNull(0)), toNum(a.getOrNull(1)))
            "max" -> maxOf(toNum(a.getOrNull(0)), toNum(a.getOrNull(1)))
            "floor" -> kotlin.math.floor(toNum(a.getOrNull(0)))
            "ceil" -> kotlin.math.ceil(toNum(a.getOrNull(0)))
            "round" -> kotlin.math.round(toNum(a.getOrNull(0)))
            // 工具
            "str" -> toStr(a.getOrNull(0))
            "default" -> {
                val v = a.getOrNull(0)
                if (v == null || toStr(v).isEmpty()) a.getOrNull(1) else v
            }
            "if" -> if (truthy(a.getOrNull(0))) a.getOrNull(1) else a.getOrNull(2)
            "urlenc" -> runCatching {
                URLEncoder.encode(toStr(a.getOrNull(0)), "UTF-8")
            }.getOrDefault("")
            "urldec" -> runCatching {
                URLDecoder.decode(toStr(a.getOrNull(0)), "UTF-8")
            }.getOrDefault("")
            "now" -> System.currentTimeMillis().toDouble()
            else -> null
        }
    }
}