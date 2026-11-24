package com.sgdys.gate

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.delay
import java.net.URLEncoder

/**
 * Streamtape resolver — device khud API call karta hai (dlticket -> dl -> direct mp4).
 * API download links IP-locked hote hain: jis IP se ticket bana, usi IP se video chalega.
 * Isliye ye code device pe chalna zaroori hai (worker se 403 milta hai).
 *
 * v15 fixes:
 *  - API ka wait_time honor hota hai (pehle hardcoded 6s tha — kabhi ticket ready
 *    nahi hota tha aur pehla dl call fail → extra 2x2s retries → slow playback)
 *  - Thread.sleep ki jagah coroutine delay (main thread block nahi hota)
 *  - Credentials ab GateCreds.kt (gitignored) me — repo me plain text nahi
 */
object GateStreamtape {
    private val ID_RE = Regex("/(?:e|v|embed)/([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class TicketResult(val ticket: String? = null, val wait_time: Int? = null)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class TicketResp(val status: Int? = null, val msg: String? = null, val result: TicketResult? = null)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class DlResult(val url: String? = null)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class DlResp(val status: Int? = null, val msg: String? = null, val result: DlResult? = null)

    suspend fun resolve(input: String): String? {
        val id = ID_RE.find(input)?.groupValues?.get(1) ?: return null

        val ticketResp = try {
            app.get("https://api.streamtape.com/file/dlticket?file=$id&login=${GateCreds.STREAMTAPE_LOGIN}&key=${GateCreds.STREAMTAPE_KEY}")
                .parsed<TicketResp>()
        } catch (_: Exception) { null }

        val ticket = ticketResp?.result?.ticket ?: return null

        // API jo wait_time bole wahi wait karo (4-12s sane range me) —
        // jaldi dl call karne par Streamtape 403/"_wait_" deta hai.
        val wait = (ticketResp.result?.wait_time ?: 6).coerceIn(4, 12)
        delay(wait * 1000L)

        for (i in 0 until 5) {
            val direct = try {
                app.get("https://api.streamtape.com/file/dl?file=$id&ticket=${URLEncoder.encode(ticket, "UTF-8")}")
                    .parsed<DlResp>().result?.url
            } catch (_: Exception) { null }
            if (!direct.isNullOrBlank() && direct.startsWith("http")) return direct
            delay(2500L)
        }
        return null
    }
}
