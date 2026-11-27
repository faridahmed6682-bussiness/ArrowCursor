package com.farid.arrowcursor

object JsonUtil {

    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.isAtEnd()) {
            throw IllegalArgumentException("Malformed JSON: unexpected trailing characters at position ${parser.pos}")
        }
        return value
    }

    fun stringify(value: Any?): String {
        val sb = StringBuilder()
        writeValue(sb, value)
        return sb.toString()
    }

    fun escape(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append("\\u")
                        sb.append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun writeValue(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> {
                sb.append('"')
                sb.append(escape(value))
                sb.append('"')
            }
            is Number -> sb.append(value.toString())
            is Boolean -> sb.append(value.toString())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((key, entryValue) in value) {
                    if (!first) sb.append(',')
                    first = false
                    sb.append('"')
                    sb.append(escape(key.toString()))
                    sb.append('"')
                    sb.append(':')
                    writeValue(sb, entryValue)
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                var first = true
                for (item in value) {
                    if (!first) sb.append(',')
                    first = false
                    writeValue(sb, item)
                }
                sb.append(']')
            }
            else -> {
                sb.append('"')
                sb.append(escape(value.toString()))
                sb.append('"')
            }
        }
    }

    private class Parser(private val text: String) {
        var pos: Int = 0

        fun isAtEnd(): Boolean = pos >= text.length

        fun skipWhitespace() {
            while (pos < text.length) {
                val c = text[pos]
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++
                } else {
                    break
                }
            }
        }

        fun parseValue(): Any? {
            skipWhitespace()
            if (isAtEnd()) {
                throw IllegalArgumentException("Malformed JSON: unexpected end of input")
            }
            return when (val c = text[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Malformed JSON: unexpected character '$c' at position $pos")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            // text[pos] == '{'
            pos++
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (!isAtEnd() && text[pos] == '}') {
                pos++
                return result
            }
            while (true) {
                skipWhitespace()
                if (isAtEnd() || text[pos] != '"') {
                    throw IllegalArgumentException("Malformed JSON: expected string key at position $pos")
                }
                val key = parseString()
                skipWhitespace()
                if (isAtEnd() || text[pos] != ':') {
                    throw IllegalArgumentException("Malformed JSON: expected ':' after object key at position $pos")
                }
                pos++
                val value = parseValue()
                result[key] = value
                skipWhitespace()
                if (isAtEnd()) {
                    throw IllegalArgumentException("Malformed JSON: unterminated object")
                }
                when (text[pos]) {
                    ',' -> {
                        pos++
                    }
                    '}' -> {
                        pos++
                        return result
                    }
                    else -> throw IllegalArgumentException("Malformed JSON: expected ',' or '}' in object at position $pos")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            // text[pos] == '['
            pos++
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (!isAtEnd() && text[pos] == ']') {
                pos++
                return result
            }
            while (true) {
                val value = parseValue()
                result.add(value)
                skipWhitespace()
                if (isAtEnd()) {
                    throw IllegalArgumentException("Malformed JSON: unterminated array")
                }
                when (text[pos]) {
                    ',' -> {
                        pos++
                    }
                    ']' -> {
                        pos++
                        return result
                    }
                    else -> throw IllegalArgumentException("Malformed JSON: expected ',' or ']' in array at position $pos")
                }
            }
        }

        private fun parseString(): String {
            // text[pos] == '"'
            pos++
            val sb = StringBuilder()
            while (pos < text.length) {
                val c = text[pos]
                pos++
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= text.length) {
                            throw IllegalArgumentException("Malformed JSON: unterminated escape sequence in string")
                        }
                        val esc = text[pos]
                        pos++
                        when (esc) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > text.length) {
                                    throw IllegalArgumentException("Malformed JSON: incomplete unicode escape in string")
                                }
                                val hex = text.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16)
                                    ?: throw IllegalArgumentException("Malformed JSON: invalid unicode escape '\\u$hex'")
                                pos += 4
                                sb.append(code.toChar())
                            }
                            else -> throw IllegalArgumentException("Malformed JSON: invalid escape '\\$esc' in string")
                        }
                    }
                    else -> sb.append(c)
                }
            }
            throw IllegalArgumentException("Malformed JSON: unterminated string")
        }

        private fun parseNumber(): Double {
            val start = pos
            if (pos < text.length && text[pos] == '-') pos++
            while (pos < text.length && text[pos] in '0'..'9') pos++
            if (pos < text.length && text[pos] == '.') {
                pos++
                while (pos < text.length && text[pos] in '0'..'9') pos++
            }
            if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
                pos++
                if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) pos++
                while (pos < text.length && text[pos] in '0'..'9') pos++
            }
            val numberText = text.substring(start, pos)
            return numberText.toDoubleOrNull()
                ?: throw IllegalArgumentException("Malformed JSON: invalid number '$numberText'")
        }

        private fun parseLiteral(literal: String, value: Any?): Any? {
            if (pos + literal.length > text.length || text.substring(pos, pos + literal.length) != literal) {
                throw IllegalArgumentException("Malformed JSON: invalid literal at position $pos")
            }
            pos += literal.length
            return value
        }
    }
}
