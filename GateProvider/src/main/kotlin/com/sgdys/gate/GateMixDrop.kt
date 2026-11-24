package com.sgdys.gate

import com.lagradost.cloudstream3.app

/**
 * MixDrop device-side resolver — packed JS (eval(function(p,a,c,k,e...) se
 * MDCore.wurl nikalta hai. Cloudflare IP block hai, isliye device se fetch.
 */
object GateMixDrop {
    private val ID_RE = Regex("/(?:e|f|v)/([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)
    private val HOSTS = listOf(
        "mxdrop.click", "mixdrop.co", "mixdrop.ag", "mixdrop.to",
        // worker EMBED_RE jitne mirror hosts cover karta hai, device resolver bhi —
        // MixDrop embed kisi bhi mirror pe ho sakta hai, fail-fast na ho.
        "mixdrop.top", "mixdrop.ws", "mixdrop.fm", "mixdrop.nu",
        "mixdrop.ch", "mixdrop.sx", "mixdrop.io", "mixdrop.net", "mixdrop.com",
    )
    private val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private fun encodeBase(value: Int, base: Int): String {
        if (base <= 36) return value.toString(base)
        var n = value
        val out = StringBuilder()
        do {
            out.insert(0, ALPHABET[n % base])
            n /= base
        } while (n > 0)
        return out.toString()
    }

    private fun unpack(input: String): String? {
        val raw = input.trim()
        if (!raw.contains("eval(function(p,a,c,k,e")) return null
        // Node se verified exact pattern
        val m = Regex("""eval\(function\(p,a,c,k,e,d\)\{[\s\S]*?\}\('((?:[^'\\]|\\.)*)',(\d+),(\d+),'([^']*)'""").find(raw) ?: return null
        val p = m.groupValues[1].replace("\\'", "'").replace("\\\\", "\\")
        val base = m.groupValues[2].toIntOrNull() ?: 62
        val count = m.groupValues[3].toIntOrNull() ?: 0
        val words = m.groupValues[4].split("|")
        val dict = HashMap<String, String>()
        for (i in 0 until count) dict[encodeBase(i, base)] = words.getOrNull(i) ?: encodeBase(i, base)
        // single-pass token replace (proven approach)
        return Regex("""\b\w+\b""").replace(p) { match -> dict[match.value] ?: match.value }
    }

    suspend fun resolve(input: String): String? {
        val id = ID_RE.find(input)?.groupValues?.get(1) ?: return null
        for (host in HOSTS) {
            try {
                val html = app.get("https://$host/e/$id", headers = mapOf(
                    "Referer" to "https://mixdrop.ag/",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
                )).text

                // 1) unpacked wurl
                val scripts = Regex("""<script[^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE).findAll(html)
                for (sc in scripts) {
                    val body = sc.groupValues[1]
                    if (!body.contains("wurl", true) && !body.contains("MDCore", true)) continue
                    val unpacked = unpack(body)
                    val text = unpacked ?: body
                    val wm = Regex("""(?:MDCore\.wurl|wurl)\s*=\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE).find(text)
                    if (wm != null) {
                        var u = wm.groupValues[1].replace("\\/", "/")
                        if (u.startsWith("//")) u = "https:$u"
                        if (u.startsWith("http")) return u
                    }
                }
                // 2) plain wurl in HTML
                val plain = Regex("""(?:MDCore\.wurl|wurl)\s*=\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE).find(html)
                if (plain != null) {
                    var u = plain.groupValues[1].replace("\\/", "/")
                    if (u.startsWith("//")) u = "https:$u"
                    if (u.startsWith("http")) return u
                }
            } catch (_: Exception) { }
        }
        return null
    }
}
