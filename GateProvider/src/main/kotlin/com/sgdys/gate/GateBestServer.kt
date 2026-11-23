package com.sgdys.gate

/**
 * The Gate worker ke /resolve wali ranking ka exact mirror.
 * Jitna zyada score, utna better video server.
 */
object GateBestServer {
    private data class HostRule(val regex: Regex, val points: Int)

    private val hostRules = listOf(
        HostRule(Regex("stream\\.andromedans\\.live", RegexOption.IGNORE_CASE), 100),
        HostRule(Regex("(^|\\.)ufoload\\.", RegexOption.IGNORE_CASE), 90),
        HostRule(Regex("(^|\\.)streamtape\\.", RegexOption.IGNORE_CASE), 85),
        HostRule(Regex("(^|\\.)(mixdrop|mxdrop)\\.", RegexOption.IGNORE_CASE), 80),
        HostRule(Regex("(^|\\.)vidmoly\\.", RegexOption.IGNORE_CASE), 75),
        HostRule(Regex("(^|\\.)(doodstream|dood)\\.", RegexOption.IGNORE_CASE), 70),
    )

    fun score(url: String): Int {
        var points = if (Regex("\\.m3u8(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(url)) 40 else 10
        if (url.startsWith("https", ignoreCase = true)) points += 15
        val host = runCatching { java.net.URL(url).host.lowercase() }.getOrNull() ?: return points
        for (rule in hostRules) {
            if (rule.regex.containsMatchIn(host)) {
                points += rule.points
                break
            }
        }
        if (Regex("(1080|720|hdr)", RegexOption.IGNORE_CASE).containsMatchIn(url)) points += 5
        return points
    }
}
