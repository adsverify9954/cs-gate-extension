// Use an integer for version numbers — har release pe bump karo
version = 1

cloudstream {
    description = "The Gate — tumhara personal OTT addon ab CloudStream pe. Anime, Movies, Series aur TV ka catalog + streams seedha The Gate worker se."
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
