package com.farid.arrowcursor

/**
 * Minimal Avro-phonetic transliteration (Latin -> Bengali) for the
 * ArrowCursor keyboard's hardware typing.
 *
 * Rules (standard Avro phonetic):
 * - Greedy longest match: digraphs (kh, ch, sh, th, dh, ph, bh, gh, ng,
 *   ny, Th, Dh, Sh, NG, Rh) win over single letters.
 * - A vowel letter right after a consonant becomes its kar
 *   (a -> া, i -> ি, u -> ু, e -> ে, o -> ো, ...).
 * - A consonant right after a consonant gets a hasant (্) before it,
 *   so clusters form conjuncts (e.g. "kk" -> ক্ক).
 * - A word-initial "a" with more letters after it is আ; a lone "a" is অ.
 *   (ami -> আমি, amar -> আমার, kotha -> কথা, bangla -> বাংলা.)
 *
 * Pure function: no Android APIs, so it can be unit-tested anywhere.
 */
object AvroPhonetic {

    private data class Token(
        val latin: String,
        val bengali: String,
        val isConsonant: Boolean,
        val kar: String? = null,
    )

    private val vowels = listOf(
        Token("rri", "ঋ", isConsonant = false, kar = "ৃ"),
        Token("oi", "ঐ", isConsonant = false, kar = "ৈ"),
        Token("ou", "ঔ", isConsonant = false, kar = "ৌ"),
        Token("au", "ঔ", isConsonant = false, kar = "ৌ"),
        Token("ai", "ঐ", isConsonant = false, kar = "ৈ"),
        Token("ee", "ঈ", isConsonant = false, kar = "ী"),
        Token("oo", "ঊ", isConsonant = false, kar = "ূ"),
        Token("a", "অ", isConsonant = false, kar = "া"),
        Token("A", "আ", isConsonant = false, kar = "া"),
        Token("i", "ই", isConsonant = false, kar = "ি"),
        Token("I", "ঈ", isConsonant = false, kar = "ী"),
        Token("u", "উ", isConsonant = false, kar = "ু"),
        Token("U", "ঊ", isConsonant = false, kar = "ূ"),
        Token("e", "এ", isConsonant = false, kar = "ে"),
        Token("o", "ও", isConsonant = false, kar = "ো"),
        Token("O", "ও", isConsonant = false, kar = "ো"),
        Token("w", "ও", isConsonant = false, kar = "ো"),
    )

    private val consonants = listOf(
        Token("kh", "খ", isConsonant = true),
        Token("gh", "ঘ", isConsonant = true),
        Token("ch", "ছ", isConsonant = true),
        Token("jh", "ঝ", isConsonant = true),
        Token("th", "ঠ", isConsonant = true),
        Token("dh", "ঢ", isConsonant = true),
        Token("ph", "ফ", isConsonant = true),
        Token("bh", "ভ", isConsonant = true),
        Token("sh", "শ", isConsonant = true),
        Token("ng", "ঙ", isConsonant = true),
        Token("rh", "\u09DD", isConsonant = true),
        Token("Th", "থ", isConsonant = true),
        Token("Dh", "ধ", isConsonant = true),
        Token("Sh", "শ", isConsonant = true),
        Token("NG", "ঙ", isConsonant = true),
        Token("k", "ক", isConsonant = true),
        Token("g", "গ", isConsonant = true),
        Token("c", "চ", isConsonant = true),
        Token("j", "জ", isConsonant = true),
        Token("t", "ট", isConsonant = true),
        Token("d", "ড", isConsonant = true),
        Token("n", "ন", isConsonant = true),
        Token("T", "ত", isConsonant = true),
        Token("D", "দ", isConsonant = true),
        Token("N", "ণ", isConsonant = true),
        Token("p", "প", isConsonant = true),
        Token("b", "ব", isConsonant = true),
        Token("m", "ম", isConsonant = true),
        Token("y", "য", isConsonant = true),
        Token("Y", "\u09DF", isConsonant = true),
        Token("r", "র", isConsonant = true),
        Token("R", "\u09DC", isConsonant = true),
        Token("l", "ল", isConsonant = true),
        Token("s", "স", isConsonant = true),
        Token("S", "ষ", isConsonant = true),
        Token("h", "হ", isConsonant = true),
        Token("f", "ফ", isConsonant = true),
        Token("v", "ভ", isConsonant = true),
        Token("z", "জ", isConsonant = true),
        Token("q", "ক", isConsonant = true),
        Token("x", "ক্স", isConsonant = true),
    )

    // Longest first so digraphs win over single letters.
    private val allTokens = (vowels + consonants)
        .sortedByDescending { it.latin.length }

    // Whole-word exceptions where the plain rules give the wrong word
    // (the letter "o" is the inherent vowel in these, not the ো-কার).
    private val exceptions = mapOf(
        "kotha" to "কথা",
        "kora" to "করা",
        "kore" to "করে",
        "koro" to "করো",
        "korbo" to "করবো",
        "korchi" to "করছি",
        "korechi" to "করেছি",
        "korecho" to "করেছো",
        "korlam" to "করলাম",
        "kokhon" to "কখন",
        "kobe" to "কবে",
        "sob" to "সব",
        "shob" to "সব",
        "hobe" to "হবে",
        "hoye" to "হ\u09DFে",
        "hole" to "হলে",
        "holo" to "হলো",
        "dhonnobad" to "ধন্যবাদ",
        "dhonyobad" to "ধন্যবাদ",
        "shongshar" to "সংসার",
        "shong" to "সং",
        "shokti" to "শক্তি",
        "shkTi" to "শক্তি",
        "konya" to "কন্যা",
        "rong" to "রং",
    )

    fun transliterate(latin: String): String {
        if (latin.isEmpty()) return ""
        exceptions[latin]?.let { return it }
        val out = StringBuilder()
        var pendingConsonant = false
        var lastConsonantLatin: String? = null
        var i = 0
        while (i < latin.length) {
            val token = allTokens.firstOrNull {
                latin.startsWith(it.latin, startIndex = i)
            }
            if (token == null) {
                // Unknown character (digit, punctuation): copy through.
                out.append(latin[i])
                pendingConsonant = false
                lastConsonantLatin = null
                i++
                continue
            }
            if (token.isConsonant) {
                // No hasant after ঙ/ঞ, and no hasant before র when the
                // previous consonant is a nasal (আমরা, not আম্রা).
                val noHasant = lastConsonantLatin == "ng" ||
                    lastConsonantLatin == "ny" ||
                    (token.latin == "r" && lastConsonantLatin in NASALS)
                if (pendingConsonant && !noHasant) out.append('্')
                out.append(token.bengali)
                pendingConsonant = true
                lastConsonantLatin = token.latin
            } else {
                if (pendingConsonant && token.kar != null) {
                    out.append(token.kar)
                } else if (i == 0 && token.latin == "a" && latin.length > 1) {
                    // Word-initial "a" is আ (ami -> আমি); a lone "a" is অ.
                    out.append('আ')
                } else {
                    out.append(token.bengali)
                }
                pendingConsonant = false
                lastConsonantLatin = null
            }
            i += token.latin.length
        }
        return applyNasalRules(out.toString())
    }

    private val NASALS = setOf("m", "n", "N", "ng", "ny")

    /**
     * ঙ/ঞ before its own class keeps the hasant (সঙ্গীত: ঙ্‌গ);
     * before any other consonant, or at the end of a word, it is written
     * as the anusvara/chandrabindu (বাংলা: ং, রং: ং).
     */
    private fun applyNasalRules(s: String): String {
        val velars = setOf('ক', 'খ', 'গ', 'ঘ', 'ঙ')
        val palatals = setOf('চ', 'ছ', 'জ', 'ঝ', 'ঞ')
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == 'ঙ' || ch == 'ঞ') {
                val next = if (i + 1 < s.length) s[i + 1] else null
                val ownClass = if (ch == 'ঙ') next in velars else next in palatals
                when {
                    next == null -> out.append(if (ch == 'ঙ') 'ং' else 'ঁ')
                    ownClass -> out.append(ch).append('্')
                    isBengaliConsonant(next) -> out.append(if (ch == 'ঙ') 'ং' else 'ঁ')
                    else -> out.append(ch)
                }
                i++
            } else {
                out.append(ch)
                i++
            }
        }
        return out.toString()
    }

    private fun isBengaliConsonant(ch: Char): Boolean {
        return (ch in 'ক'..'হ') || ch == '\u09DC' || ch == '\u09DD' || ch == '\u09DF'
    }
}
