package com.sgdys.gate

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URLEncoder

/**
 * Catch-all extractor: CloudStream kisi bhi provider se jo bhi video/embed link deta hai,
 * wo pehle The Gate worker ke /resolve pe bheja jata hai. The Gate direct stream + headers
 * return karta hai to "The Gate" source se playback hota hai. Resolve fail pe false —
 * CloudStream apne baaki extractors ko try karta hai (baaki providers na toot jaayein).
 */
class GateUniversalExtractor : ExtractorApi() {
    override val name = "The Gate · Resolve"
    override val mainUrl = "http" // har http/https URL match
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val resolved = try {
            app.get("https://my-anime-gate.unityads9954.workers.dev/resolve?url=${URLEncoder.encode(url, "UTF-8")}")
                .parsed<GateResolve>().takeIf { it.ok && !it.url.isNullOrBlank() }
        } catch (_: Exception) {
            null
        } ?: return

        try {
            callback(
                ExtractorLink(
                    source = "The Gate",
                    name = "The Gate · Resolve",
                    url = resolved.url!!,
                    referer = resolved.headers["Referer"] ?: referer ?: "",
                    quality = Qualities.Unknown.value,
                    headers = resolved.headers,
                    type = if (resolved.type == "m3u8") ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                )
            )
        } catch (_: Exception) {
            // resolve mila par callback fail — CloudStream baaki extractors try karega
        }
    }
}
