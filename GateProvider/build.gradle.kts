// Use an integer for version numbers — har release pe bump karo
version = 8

cloudstream {
    description = "The Gate — sirf The Gate. Har video link worker ke /resolve se, direct m3u8 + headers. Koi doosra source nahi."
    authors = listOf("sgdys")

    /**
    * Status int as one of the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta-only
    **/
    status = 1

    tvTypes = listOf("Anime", "Movie", "TvSeries")

    requiresResources = false
    language = "en"
}
