package com.sgdys.gate

import com.lagradost.cloudstream3.app
import java.net.URLEncoder

/**
 * Streamtape direct resolver — device ke apne IP se embed page fetch karke
 * get_video link nikalta hai. (Cloudflare worker IP se 403 — isliye yahan.)
 */
object GateStreamtape {
    private val ID_RE = Regex("/(?:e|v|embed)/([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)
    private val GET_VIDEO_RE = Regex("""(/streamtape\.com/get_video\?[^"'<>\s]+)""", RegexOption.IGNORE_CASE)

    suspend fun resolve(input: String): String? {
        val id = ID_RE.find(input)?.groupValues?.get(1) ?: return null
        val page = "https://streamtape.com/e/$id"
        val html = try {
            app.get(page, headers = mapOf(
                "Referer" to "https://streamtape.com/",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
            )).text
        } catch (_: Exception) { return null }

        val clean = html.replace("\\u002F", "/").replace("\\/", "/").replace("&amp;", "&")
        val m = GET_VIDEO_RE.find(clean) ?: return null
        return "https:" + m.groupValues[1]
    }
}
