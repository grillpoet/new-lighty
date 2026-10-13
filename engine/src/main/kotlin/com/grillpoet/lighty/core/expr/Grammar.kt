package com.grillpoet.lighty.core.expr

// ==================== Token ====================

sealed interface Token {
    data class Ident(val name: String) : Token
    data class Num(val value: Double) : Token
    data class Str(val value: String) : Token
    data object LParen : Token
    data object RParen : Token
    data object Comma : Token
    data object Question : Token
    data object Colon : Token
    data object Plus : Token
    data object Minus : Token
    data object Star : Token
    data object Slash : Token
    data object Percent : Token
    data object Eq : Token
    data object Neq : Token
    data object Lt : Token
    data object Le : Token
    data object Gt : Token
    data object Ge : Token
    data object And : Token
    data object Or : Token
    data object Not : Token
    data object Eof : Token
}

// ==================== Lexer ====================

class Lexer(private val src: String) {

    private var pos = 0

    fun tokenize(): List<Token> {
        val out = ArrayList<Token>()
        while (true) {
            val t = next() ?: break
            out += t
        }
        out += Token.Eof
        return out
    }

    private fun next(): Token? {
        skipWs()
        if (pos >= src.length) return null
        val c = src[pos]

        // 数字
        if (c.isDigit() || (c == '.' && pos + 1 < src.length && src[pos + 1].isDigit())) {
            val start = pos
            while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) pos++
            return Token.Num(src.substring(start, pos).toDoubleOrNull() ?: 0.0)
        }

        // 标识符
        if (c.isLetter() || c == '_') {
            val start = pos
            while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '_')) pos++
            return Token.Ident(src.substring(start, pos))
        }

        // 字符串
        if (c == '\'' || c == '"') {
            pos++
            val sb = StringBuilder()
            while (pos < src.length && src[pos] != c) {
                if (src[pos] == '\\' && pos + 1 < src.length) {
                    when (val e = src[pos + 1]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        '\\' -> sb.append('\\')
                        '\'' -> sb.append('\'')
                        '"' -> sb.append('"')
                        else -> sb.append(e)
                    }
                    pos += 2
                } else {
                    sb.append(src[pos]); pos++
                }
            }
            if (pos < src.length) pos++
            return Token.Str(sb.toString())
        }

        return when {
            match("==") -> Token.Eq
            match("!=") -> Token.Neq
            match("<=") -> Token.Le
            match(">=") -> Token.Ge
            match("&&") -> Token.And
            match("||") -> Token.Or
            match("<") -> Token.Lt
            match(">") -> Token.Gt
            match("+") -> Token.Plus
            match("-") -> Token.Minus
            match("*") -> Token.Star
            match("/") -> Token.Slash
            match("%") -> Token.Percent
            match("!") -> Token.Not
            match("(") -> Token.LParen
            match(")") -> Token.RParen
            match(",") -> Token.Comma
            match("?") -> Token.Question
            match(":") -> Token.Colon
            else -> { pos++; null }
        }
    }

    private fun match(s: String): Boolean {
        if (src.startsWith(s, pos)) { pos += s.length; return true }
        return false
    }

    private fun skipWs() {
        while (pos < src.length && src[pos].isWhitespace()) pos++
    }
}

// ==================== Ast ====================

sealed interface Node {
    data class Lit(val v: Any?) : Node
    data class Var(val name: String) : Node
    data class Unary(val op: String, val x: Node) : Node
    data class Bin(val op: String, val l: Node, val r: Node) : Node
    data class Ternary(val c: Node, val a: Node, val b: Node) : Node
    data class Call(val name: String, val args: List<Node>) : Node
}

// ==================== Parser ====================

class Parser(private val tokens: List<Token>) {

    private var pos = 0
    private var depth = 0

    fun parse(): Node {
        val n = expr()
        if (peek() !is Token.Eof) error("expected EOF, got ${peek()}")
        return n
    }

    private fun expr(): Node {
        if (++depth > 50) error("expression too deep")
        try {
            val c = or()
            return if (peek() is Token.Question) {
                advance()
                val a = expr()
                if (peek() !is Token.Colon) error("expected ':'")
                advance()
                val b = expr()
                Node.Ternary(c, a, b)
            } else c
        } finally { depth-- }
    }

    private fun or(): Node {
        var l = and()
        while (peek() is Token.Or) { advance(); l = Node.Bin("||", l, and()) }
        return l
    }

    private fun and(): Node {
        var l = eq()
        while (peek() is Token.And) { advance(); l = Node.Bin("&&", l, eq()) }
        return l
    }

    private fun eq(): Node {
        var l = cmp()
        while (peek() is Token.Eq || peek() is Token.Neq) {
            val op = if (peek() is Token.Eq) "==" else "!="
            advance(); l = Node.Bin(op, l, cmp())
        }
        return l
    }

    private fun cmp(): Node {
        var l = add()
        while (peek() is Token.Lt || peek() is Token.Le ||
            peek() is Token.Gt || peek() is Token.Ge) {
            val op = when (peek()) {
                is Token.Lt -> "<"
                is Token.Le -> "<="
                is Token.Gt -> ">"
                else -> ">="
            }
            advance(); l = Node.Bin(op, l, add())
        }
        return l
    }

    private fun add(): Node {
        var l = mul()
        while (peek() is Token.Plus || peek() is Token.Minus) {
            val op = if (peek() is Token.Plus) "+" else "-"
            advance(); l = Node.Bin(op, l, mul())
        }
        return l
    }

    private fun mul(): Node {
        var l = unary()
        while (peek() is Token.Star || peek() is Token.Slash || peek() is Token.Percent) {
            val op = when (peek()) {
                is Token.Star -> "*"
                is Token.Slash -> "/"
                else -> "%"
            }
            advance(); l = Node.Bin(op, l, unary())
        }
        return l
    }

    private fun unary(): Node {
        if (peek() is Token.Not) { advance(); return Node.Unary("!", unary()) }
        if (peek() is Token.Minus) { advance(); return Node.Unary("-", unary()) }
        return primary()
    }

    private fun primary(): Node {
        val t = advance()
        return when (t) {
            is Token.Num -> Node.Lit(t.value)
            is Token.Str -> Node.Lit(t.value)
            is Token.Ident -> {
                if (peek() is Token.LParen) {
                    advance()
                    val args = ArrayList<Node>()
                    if (peek() !is Token.RParen) {
                        args += expr()
                        while (peek() is Token.Comma) { advance(); args += expr() }
                    }
                    if (peek() !is Token.RParen) error("expected ')'")
                    advance()
                    Node.Call(t.name, args)
                } else Node.Var(t.name)
            }
            is Token.LParen -> {
                val n = expr()
                if (peek() !is Token.RParen) error("expected ')'")
                advance()
                n
            }
            else -> error("unexpected $t")
        }
    }

    private fun peek(): Token = tokens[pos]
    private fun advance(): Token = tokens[pos++]
    private fun error(msg: String): Nothing = throw RuntimeException(msg)
}