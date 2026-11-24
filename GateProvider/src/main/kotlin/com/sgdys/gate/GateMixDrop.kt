package com.sgdys.gate

import com.lagradost.cloudstream3.app

/**
 * MixDrop device-side resolver — packed JS (eval(function(p,a,c,k,e...) se
 * MDCore.wurl nikalta hai. Cloudflare IP block hai, isliye device se fetch.
 */
object GateMixDrop {
    private val ID_RE = Regex("/(?:e|f|v)/([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)
    private val HOSTS = listOf("mxdrop.click", "mixdrop.co", "mixdrop.ag", "mixdrop.to")
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
        val m = Regex("""}\('((?:[^'\\]|\\.)*)',(\d+),(\d+),'([^']*)'""").find(raw) ?: return null
        val p = m.groupValues[1].replace("\\'", "'").replace("\\\\", "\\")
        val base = m.groupValues[2].toIntOrNull() ?: 62
        val count = m.groupValues[3].toIntOrNull() ?: 0
        val words = m.groupValues[4].split("|")
        var decoded = p
        for (i in count - 1 downTo 0) {
            val token = encodeBase(i, base)
            val rep = words.getOrNull(i) ?: token
            if (rep != token) decoded = decoded.replace(Regex("\\b${Regex.escape(token)}\\b"), rep)
        }
        return decoded
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
                    val wurl = Regex("""(?:MDCore\.wurl|wurl)\s*=\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE).find(text)
                        ?: Regex("""https?:[^'"\s]+\.(?:m3u8|mp4)[^'"\s]*""", RegexOption.IGNORE_CASE).find(text)
                    if (wurl != null) {
                        var u = wurl.groupValues[1].replace("\\/", "/")
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
