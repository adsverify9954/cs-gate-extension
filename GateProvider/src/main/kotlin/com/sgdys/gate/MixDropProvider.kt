package com.sgdys.gate

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.app

@JsonIgnoreProperties(ignoreUnknown = true)
data class MxMetas(val metas: List<MxMeta> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
data class MxMeta(
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val description: String? = null,
)

/** Alag CloudStream source: sirf MixDrop wale videos (The Gate worker se). */
class MixDropProvider : MainAPI() {
    override var mainUrl = "https://my-anime-gate.unityads9954.workers.dev"
    override var name = "MixDrop"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val catalogUrl = "$mainUrl/catalog/mixdrop.json"

    private fun toSearch(m: MxMeta): SearchResponse? {
        val id = m.id ?: return null
        val nm = m.name ?: return null
        return newMovieSearchResponse(nm, "$mainUrl/meta/${m.type ?: "movie"}/$id.json", TvType.Movie) {
            this.posterUrl = m.poster
        }
    }

    override val mainPage = mainPageOf(catalogUrl to "MixDrop Movies")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(emptyList())
        val metas = try {
            app.get(catalogUrl).parsed<MxMetas>().metas
        } catch (_: Exception) { emptyList() }
        return newHomePageResponse(request.name, metas.mapNotNull { toSearch(it) }, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            app.get(catalogUrl).parsed<MxMetas>().metas
                .filter { it.name?.contains(query, ignoreCase = true) == true }
                .mapNotNull { toSearch(it) }
        } catch (_: Exception) { emptyList() }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/").substringBefore(".json").removePrefix("tmdb:")
        val res = try {
            app.get(url).parsed<GateMeta>()
        } catch (_: Exception) { GateMeta() }
        val title = res.name?.takeIf { it.isNotBlank() } ?: "MixDrop"
        return newMovieLoadResponse(title, url, TvType.Movie, "movie|$id") {
            this.posterUrl = res.poster
            this.backgroundPosterUrl = res.background
            this.plot = res.description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parts = data.split("|")
        if (parts.size < 2) return false
        val streamUrl = "$mainUrl/stream/movie/${parts[1]}.json"
        val streams = try {
            app.get(streamUrl).parsed<GateStreams>().streams
        } catch (_: Exception) { emptyList() }
        var found = false
        streams.forEach { st ->
            val link = st.url ?: return@forEach
            if (!link.contains("mixdrop", true) && !link.contains("mxdrop", true)) return@forEach
            // Device-side custom resolver (Cloudflare worker IP se MixDrop block hai)
            val direct = GateMixDrop.resolve(link)
            if (!direct.isNullOrBlank()) {
                try {
                    callback(
                        ExtractorLink(
                            source = name,
                            name = "MixDrop · The Gate",
                            url = direct,
                            referer = "https://mixdrop.ag/",
                            quality = Qualities.Unknown.value,
                            headers = mapOf(
                                "Referer" to "https://mixdrop.ag/",
                                "Origin" to "https://mixdrop.ag",
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
                            ),
                            type = if (direct.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                        )
                    )
                    found = true
                    return@forEach
                } catch (_: Exception) { }
            }
        }
        return found
    }
}
