package com.sgdys.gate

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder

@JsonIgnoreProperties(ignoreUnknown = true)
data class UfoMetas(val metas: List<UfoMeta> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
data class UfoMeta(
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val description: String? = null,
)

/** Alag CloudStream source: sirf UFOload wale videos (The Gate worker se). */
class UfoLoadProvider : MainAPI() {
    override var mainUrl = "https://my-anime-gate.unityads9954.workers.dev"
    override var name = "UFOload"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val catalogUrl = "$mainUrl/catalog/ufoload.json"

    private fun toSearch(m: UfoMeta): SearchResponse? {
        val id = m.id ?: return null
        val nm = m.name ?: return null
        return newMovieSearchResponse(nm, "$mainUrl/meta/${m.type ?: "movie"}/$id.json", TvType.Movie) {
            this.posterUrl = m.poster
        }
    }

    override val mainPage = mainPageOf(catalogUrl to "UFOload Movies")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(emptyList())
        val metas = try {
            app.get(catalogUrl).parsed<UfoMetas>().metas
        } catch (_: Exception) { emptyList() }
        return newHomePageResponse(request.name, metas.mapNotNull { toSearch(it) }, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            app.get(catalogUrl).parsed<UfoMetas>().metas
                .filter { it.name?.contains(query, ignoreCase = true) == true }
                .mapNotNull { toSearch(it) }
        } catch (_: Exception) { emptyList() }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/").substringBefore(".json").removePrefix("tmdb:")
        val res = try {
            app.get(url).parsed<GateMeta>()
        } catch (_: Exception) { GateMeta() }
        val title = res.name?.takeIf { it.isNotBlank() } ?: "UFOload"
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
            if (!link.contains("ufoload", true) && !link.contains("andromedans", true)) return@forEach
            try {
                val r = app.get("$mainUrl/resolve?url=${URLEncoder.encode(link, "UTF-8")}").parsed<GateResolve>()
                if (!r.ok || r.url.isNullOrBlank()) return@forEach
                callback(
                    ExtractorLink(
                        source = name,
                        name = "UFOload · The Gate",
                        url = r.url,
                        referer = r.headers["Referer"] ?: "",
                        quality = Qualities.Unknown.value,
                        headers = r.headers,
                        type = if (r.type == "m3u8") ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                    )
                )
                found = true
            } catch (_: Exception) { }
        }
        return found
    }
}
