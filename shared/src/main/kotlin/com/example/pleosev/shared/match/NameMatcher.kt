package com.example.pleosev.shared.match

import java.text.Normalizer

/**
 * Fuzzy comparison of charging-station names between the stock navi and KECO, e.g.
 * "워터 괴산휴게소 창원방향 전기차충전소" vs "괴산휴게소(창원방향)". Direction words are kept because
 * both carriageways of a service area usually have their own station.
 */
object NameMatcher {

    // Longest first so "전기차충전소" is removed before "충전소".
    private val NOISE = listOf(
        "전기자동차충전소", "전기차충전소", "전기충전소", "전기자동차", "전기차", "충전스테이션", "충전소", "충전기",
        "초급속", "급속", "완속", "방향", "방면",
    )
    private val PUNCTUATION = Regex("""[\s()\[\]{}<>·.,:;_\-/'"]+""")

    fun normalize(name: String): String {
        var s = Normalizer.normalize(name, Normalizer.Form.NFC).lowercase()
        s = PUNCTUATION.replace(s, "")
        for (word in NOISE) s = s.replace(word, "")
        return s
    }

    /** Dice coefficient over character bigrams of the normalized names, 0..1. */
    fun similarity(a: String, b: String): Double {
        val x = bigrams(normalize(a))
        val y = bigrams(normalize(b))
        if (x.isEmpty() || y.isEmpty()) return 0.0
        val remaining = y.groupingBy { it }.eachCount().toMutableMap()
        var common = 0
        for (gram in x) {
            val n = remaining[gram] ?: 0
            if (n > 0) {
                common++
                remaining[gram] = n - 1
            }
        }
        return 2.0 * common / (x.size + y.size)
    }

    private fun bigrams(s: String): List<String> = when {
        s.isEmpty() -> emptyList()
        s.length == 1 -> listOf(s)
        else -> s.windowed(2)
    }
}
