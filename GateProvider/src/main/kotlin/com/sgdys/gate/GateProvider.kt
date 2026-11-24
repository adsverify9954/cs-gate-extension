package com.sgdys.gate

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder

// ===== The Gate worker ke Stremio responses ke models =====

@JsonIgnoreProperties(ignoreUnknown = true)
data class GateMetas(val metas: List<GateMeta> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
data class GateMeta(
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,       // "movie" | "series"
    val poster: String? = null,
    val background: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val videos: List<GateVideo> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GateVideo(
    val id: String? = null,          // e.g. "tmdb:83095:S1E1"
    val title: String? = null,
    val season: Int? = null,
    val episode: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GateStreams(val streams: List<GateStream> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
data class GateStream(
    val name: String? = null,        // e.g. "The Gate · Streamtape"
    val title: String? = null,
    val host: String? = null,        // streamtape, mixdrop, ...
    val url: String? = null,         // embed URL
    val behaviorHints: GateBehaviorHints? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GateBehaviorHints(val proxyHeaders: GateProxyHeaders? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GateProxyHeaders(val request: Map<String, String> = emptyMap())

// The Gate /resolve response — CloudStream ka koi bhi video link worker ko bhejne par
@JsonIgnoreProperties(ignoreUnknown = true)
data class GateResolve(
    val ok: Boolean = false,
    val url: String? = null,
    val type: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

class GateProvider : MainAPI() {
    override var mainUrl = "https://my-anime-gate.unityads9954.workers.dev"
    override var name = "The Gate"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    private data class GateCatalog(
        val stremioType: String,     // manifest.json ka type
        val catalogId: String,       // manifest.json ka catalog id
        val title: String,
        val tvType: TvType
    )

    // The Gate manifest ke 4 catalogs — naya catalog add ho to yahan ek line daal do
    private val gateCatalogs = listOf(
        GateCatalog("series", "anime", "The Gate Anime", TvType.Anime),
        GateCatalog("movie", "movies", "The Gate Movies", TvType.Movie),
        GateCatalog("series", "series", "The Gate Series", TvType.TvSeries),
        GateCatalog("series", "tv", "The Gate TV", TvType.TvSeries),
    )

    private fun catalogUrl(c: GateCatalog) = "$mainUrl/catalog/${c.stremioType}/${c.catalogId}.json"

    override val mainPage = mainPageOf(
        *gateCatalogs.map { catalogUrl(it) to it.title }.toTypedArray()
    )

    private fun GateMeta.toSearchResponse(hint: TvType? = null): SearchResponse? {
        val mid = id ?: return null
        val nm = name ?: return null
        val mtype = type ?: return null
        val type = when {
            mtype == "movie" -> TvType.Movie
            hint == TvType.Anime -> TvType.Anime
            else -> TvType.TvSeries
        }
        // anime hint ko load() tak le jaane ke liye query param
        val metaUrl = "$mainUrl/meta/$mtype/$mid.json" + if (hint == TvType.Anime) "?gate=anime" else ""
        return newMovieSearchResponse(nm, metaUrl, type) {
            this.posterUrl = poster
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // The Gate catalogs paginated nahi hain — poora list ek hi response me aata hai
        if (page > 1) return newHomePageResponse(emptyList())

        val hint = gateCatalogs.firstOrNull { request.data.endsWith("/${it.stremioType}/${it.catalogId}.json") }?.tvType
        val metas = try {
            app.get(request.data).parsed<GateMetas>().metas
        } catch (_: Exception) {
            emptyList()
        }
        val items = metas.mapNotNull { it.toSearchResponse(hint) }
        return newHomePageResponse(request.name, items, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        val results = mutableListOf<SearchResponse>()
        gateCatalogs.forEach { c ->
            try {
                // The Gate worker search= ko query param maangta hai, Stremio path-convention nahi
                val metas = app.get("$mainUrl/catalog/${c.stremioType}/${c.catalogId}.json?search=$encoded")
                    .parsed<GateMetas>().metas
                results.addAll(metas.mapNotNull { it.toSearchResponse(c.tvType) })
            } catch (_: Exception) {
                // ek catalog fail ho to baaki catalogs na rukein
            }
        }
        return results.distinctBy { it.url }
    }

    private fun LoadResponse.applyCommon(res: GateMeta) {
        this.posterUrl = res.poster
        this.backgroundPosterUrl = res.background
        this.plot = res.description
        this.year = res.releaseInfo?.take(4)?.toIntOrNull()
    }

    override suspend fun load(url: String): LoadResponse {
        val res = app.get(url).parsed<GateMeta>()
        val mid = res.id ?: throw ErrorLoadingException("Meta me id nahi mila: $url")
        val nm = res.name ?: throw ErrorLoadingException("Meta me name nahi mila: $url")
        val isMovie = res.type == "movie"
        val isAnime = url.contains("gate=anime")
        val type = when {
            isMovie -> TvType.Movie
            isAnime -> TvType.Anime
            else -> TvType.TvSeries
        }

        if (isMovie) {
            return newMovieLoadResponse(nm, url, type, "movie|$mid") {
                applyCommon(res)
            }
        }

        val episodes = res.videos.mapNotNull { v ->
            val season = v.season ?: 1
            val episode = v.episode ?: return@mapNotNull null
            newEpisode(
                url = "series|$mid|$season|$episode",
                fix = false,
                initializer = {
                    this.name = v.title ?: "$nm S${season}E${episode}"
                    this.season = season
                    this.episode = episode
                    this.posterUrl = res.poster
                }
            )
        }

        return newTvSeriesLoadResponse(nm, url, type, episodes) {
            applyCommon(res)
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
        val streamType = parts[0]
        val mid = parts[1]

        val streamUrl = if (streamType == "movie") {
            "$mainUrl/stream/movie/$mid.json"
        } else {
            val season = parts.getOrNull(2) ?: return false
            val episode = parts.getOrNull(3) ?: return false
            "$mainUrl/stream/series/$mid:$season:$episode.json"
        }

        val streams = try {
            app.get(streamUrl).parsed<GateStreams>().streams
        } catch (_: Exception) {
            emptyList()
        }
        if (streams.isEmpty()) return false

        // Best video server choose karo — worker wali ranking ka exact mirror.
        // Sirf top-ranked stream The Gate /resolve pe bhejein (baaki ko ignore).
        val ranked = streams
            .mapNotNull { stream -> stream.url?.let { it to GateBestServer.score(it) } }
            .sortedByDescending { it.second }
        if (ranked.isEmpty()) return false

        for ((link, _) in ranked) {
            // Streamtape IP-locked hai — device ke apne IP se khud resolve karo
            // (CloudStream built-in extractor reliable nahi — isliye custom resolver).
            if (link.contains("streamtape", ignoreCase = true)) {
                val direct = GateStreamtape.resolve(link)
                if (!direct.isNullOrBlank()) {
                    try {
                        callback(
                            ExtractorLink(
                                source = name,
                                name = "The Gate · Streamtape",
                                url = direct,
                                referer = "https://streamtape.com/",
                                quality = Qualities.Unknown.value,
                                headers = mapOf(
                                    "Referer" to "https://streamtape.com/",
                                    "Origin" to "https://streamtape.com",
                                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
                                ),
                                type = ExtractorLinkType.VIDEO,
                            )
                        )
                        return true
                    } catch (_: Exception) { }
                }
                continue
            }

            // Baaki sab hosts: link The Gate /resolve se guzarta hai (direct m3u8/mp4 + headers).
            val resolved = try {
                app.get("$mainUrl/resolve?url=${URLEncoder.encode(link, "UTF-8")}")
                    .parsed<GateResolve>().takeIf { it.ok && !it.url.isNullOrBlank() }
            } catch (_: Exception) {
                null
            }
            if (resolved == null) continue
            try {
                callback(
                    ExtractorLink(
                        source = name,
                        name = "The Gate · Resolve",
                        url = resolved.url!!,
                        referer = resolved.headers["Referer"] ?: "",
                        quality = Qualities.Unknown.value,
                        headers = resolved.headers,
                        type = if (resolved.type == "m3u8") ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                    )
                )
                return true
            } catch (_: Exception) {
                // agle ranked stream pe chalein
            }
        }
        return false
    }
}
