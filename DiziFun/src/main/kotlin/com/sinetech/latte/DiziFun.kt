package com.sinetech.latte

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.net.URI
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.LoadResponse

class DiziFun : MainAPI() {
    override var mainUrl = "https://dizifun3.com"
    override var name = "DiziFun"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        // === ANA SAYFA BÖLÜMLERİ ===
        "yeni_diziler" to "Yeni Eklenen Diziler",
        "yeni_filmler" to "Yeni Eklenen Filmler",
        // ================================
        "$mainUrl/filmler"                       to "Filmler",
        "$mainUrl/netflix-dizileri"              to "Netflix Dizileri",
        "$mainUrl/exxen-dizileri"                to "Exxen Dizileri",
        "$mainUrl/disney-plus-dizileri"          to "Disney+ Dizileri",
        "$mainUrl/tabii-dizileri"                to "Tabii Dizileri",
        "$mainUrl/blutv-dizileri"                to "BluTV Dizileri",
        "$mainUrl/todtv-dizileri"                to "TodTV Dizileri",
        "$mainUrl/gain-dizileri"                 to "Gain Dizileri",
        "$mainUrl/hulu-dizileri"                 to "Hulu Dizileri",
        "$mainUrl/primevideo"                    to "PrimeVideo Dizileri",
        "$mainUrl/hbomax"                        to "HboMax Dizileri",
        "$mainUrl/paramount-plus-dizileri"       to "Paramount+ Dizileri",
        "$mainUrl/unutulmaz-diziler"             to "Unutulmaz Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {

        when (request.data) {
            "yeni_diziler", "yeni_filmler" -> {
                if (page > 1) return newHomePageResponse(request.name, emptyList())

                val mainDocument = try { app.get(mainUrl).document } catch (e: Exception) {
                    return newHomePageResponse(request.name, emptyList())
                }

                val items = when (request.data) {
                    "yeni_diziler" -> {
                        mainDocument.select("h4.title")
                            .find { it.text().contains("Yeni Eklenen Diziler", ignoreCase = true) }
                            ?.nextElementSibling()
                            ?.select(".movies_recent-item")
                            ?.mapNotNull { it.toRecentSearchResult() }
                            ?: emptyList<SearchResponse>().also { Log.w("DiziFun", "Yeni Eklenen Diziler bölümü veya öğeleri bulunamadı.") } // Bulamazsa boş liste
                    }
                    "yeni_filmler" -> {
                         mainDocument.select("h4.title")
                            .find { it.text().contains("Yeni Eklenen Filmler", ignoreCase = true) }
                            ?.nextElementSibling()
                            ?.select(".movies_recent-item")
                            ?.mapNotNull { it.toRecentSearchResult() }
                            ?: emptyList<SearchResponse>().also { Log.w("DiziFun", "Yeni Eklenen Filmler bölümü veya öğeleri bulunamadı.") }
                    }
                    else -> emptyList()
                }

                return newHomePageResponse(
                    list = HomePageList(request.name, items, isHorizontalImages = false),
                    hasNext = false
                )
            }

            else -> {
                val url = if (page > 1) { "${request.data}?p=$page" } else { request.data }
                val document = try { app.get(url).document } catch (e: Exception) { /*...*/ return newHomePageResponse(request.name, emptyList()) }
                val items = document.select(".uk-grid .uk-width-large-1-6").mapNotNull { element -> element.toSearchResult() }
                if (items.isEmpty() && page == 1) { /*...*/ }
                val nextPageComponent = document.selectFirst(".uk-pagination > li.uk-active + li:not(.uk-disabled) > a[href*='?p=']")
                val hasNextPage = nextPageComponent != null
                return newHomePageResponse(
                list = HomePageList(request.name, items, isHorizontalImages = false),
                hasNext = hasNextPage
                )
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/arama?query=$query"
        val document = try {
            app.get(searchUrl).document
        } catch (e: Exception) {
            return emptyList()
        }

        val results = document.select(".uk-grid .uk-width-large-1-5, .uk-grid .uk-width-large-1-6")
            .mapNotNull { element ->
                element.toSearchResult() ?: element.toRecentSearchResult()
            }

        if (results.isEmpty()) {
        }

        return results
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = this.selectFirst("a.uk-position-cover")
        val href = fixUrlNull(anchor?.attr("href") ?: this.selectFirst("a")?.attr("href")) ?: return null
        val title = this.selectFirst(".uk-panel-title, h5")?.text()?.trim() ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val year = this.selectFirst(".uk-text-muted")?.text()?.trim()?.toIntOrNull()
        val type = if (href.contains("/film/")) TvType.Movie else TvType.TvSeries

        return if (type == TvType.Movie) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.year = year
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.year = year
            }
        }
    }

    private fun Element.toRecentSearchResult(): SearchResponse? {
        val linkElement = this.selectFirst("a.movies_recent-link")
       val href = fixUrlNull(linkElement?.attr("href")) ?: return null
       val title = this.selectFirst(".movies_recent-title")?.text()?.trim() ?: return null
       val posterUrl = fixUrlNull(this.selectFirst("img.movies_recent-image")?.attr("src"))
       val year = this.selectFirst(".movies_recent-date")?.text()?.trim()?.toIntOrNull()
       val type = if (href.contains("/film/")) TvType.Movie else TvType.TvSeries

       Log.d("DiziFun", "toRecentSearchResult: title=$title, href=$href, type=$type, year=$year")

       return if (type == TvType.Movie) {
           newMovieSearchResponse(title, href, TvType.Movie) {
               this.posterUrl = posterUrl
               this.year = year
           }
       } else {
           newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
               this.posterUrl = posterUrl
               this.year = year
           }
       }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst(".text-bold")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst(".media-cover img")?.attr("src"))
        val plot = document.selectFirst(".text-muted, .summary")?.text()?.trim()

        val genreElement = document.select(".series-info").find { it.text().contains("Türü:") }
        val genreText = genreElement?.text()?.substringAfter("Türü:")?.trim()
        val tags = genreText?.split(",")?.mapNotNull { it.trim().takeIf { tag -> tag.isNotEmpty() } }

        val actors = document.select(".actors-container .actor-card").mapNotNull { actorElement ->
            val name = actorElement.selectFirst(".actor-name")?.text()?.trim() ?: return@mapNotNull null
            val image = fixUrlNull(actorElement.selectFirst("img")?.attr("src"))
            val actor = Actor(name, image)
            ActorData(actor = actor, role = null)
        }

        val type = if (url.contains("/film/")) TvType.Movie else TvType.TvSeries
        val trailerUrl = fixUrlNull(document.selectFirst(".plyr__video-wrapper .plyr__video-embed iframe")?.attr("src"))

        val recommendations = document.select(".related-series .item, .benzer-yapimlar .item").mapNotNull {

            it.toSearchResult() ?: it.toRecentSearchResult()
        }

        val subNavItems = document.select(".subnav li")
            val yearElement = subNavItems.find { it.text().contains("Yılı:") }
            val year = yearElement?.text()?.substringAfter("Yılı:")?.trim()?.take(4)?.toIntOrNull()

        if (type == TvType.Movie) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                this.actors = actors
                this.recommendations = recommendations
                if (trailerUrl != null) { this.addTrailer(trailerUrl) }
            }
        } else { 
            val episodes = mutableListOf<Episode>()
            val seasonButtons = document.select(".season-menu .season-btn")
             if (seasonButtons.isNotEmpty()) {
                seasonButtons.forEach { seasonButton ->
                    val seasonText = seasonButton.text().trim()
                    val seasonNum = seasonText.filter { it.isDigit() }.toIntOrNull()
                        ?: seasonButton.id().substringAfter("season-btn-").toIntOrNull()
                        ?: return@forEach 

                    val seasonDetailId = "season-$seasonNum"
                    val seasonDetailDiv = document.getElementById(seasonDetailId)

                    seasonDetailDiv?.select(".uk-width-large-1-5 a")?.forEach { episodeAnchor ->
                        val relativeHref = episodeAnchor.attr("href")
                        val epLink = if (relativeHref.startsWith("?")) {
                            "$url$relativeHref" 
                        } else {
                            fixUrl(relativeHref) 
                        }

                        if (epLink.isBlank() || epLink == url) {
                             return@forEach 
                        }

                        val episodeDiv = episodeAnchor.selectFirst(".episode-button")
                        val epName = episodeDiv?.text()?.trim() ?: "Bölüm"

                        val queryParamsMap = queryParams(epLink)
                        val epNum = epName.split(".").firstOrNull()?.filter { it.isDigit() }?.toIntOrNull()
                             ?: queryParamsMap["episode"]?.toIntOrNull()

                        episodes.add(
                            newEpisode(epLink) {
                                this.name = epName
                                this.season = seasonNum
                                this.episode = epNum
                                this.posterUrl = poster
                            }
                        )
                        // ========================================
                    }
                }
            } else {
                 Log.i("DiziFun", "Sezon butonu yok, fallback seçiciler kullanılıyor: $url")
                 document.select(".bolumler .bolumtitle a, .episodes-list .episode a, .episode-item a, #season1 .uk-width-large-1-5 a").forEach { episodeAnchor ->
                    val relativeHref = episodeAnchor.attr("href")
                    val epLink = if (relativeHref.startsWith("?")) {
                         "$url$relativeHref"
                    } else {
                         fixUrl(relativeHref)
                    }

                    if (epLink.isBlank() || epLink == url) {
                         return@forEach
                    }

                    val epName = episodeAnchor.text().trim().ifEmpty {
                        episodeAnchor.selectFirst(".episode-button")?.text()?.trim() ?: "Bölüm"
                    }

                    val queryParamsMap = queryParams(epLink)
                    val epNum = queryParamsMap["episode"]?.toIntOrNull()
                        ?: queryParamsMap["bolum"]?.toIntOrNull()
                        ?: epName.split(".").firstOrNull()?.filter { it.isDigit() }?.toIntOrNull()
                    val seasonNum = queryParamsMap["sezon"]?.toIntOrNull() ?: 1

                    episodes.add(
                        newEpisode(epLink) { 
                            this.name = epName
                            this.season = seasonNum
                            this.episode = epNum
                            this.posterUrl = poster
                        }
                    )
                }
                 if (episodes.isEmpty()) {
                      Log.w("DiziFun", "Fallback ile de bölüm bulunamadı: $url")
                 }
            }

            episodes.sortWith(compareBy({ it.season }, { it.episode }))

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                this.actors = actors
                this.recommendations = recommendations
                if (trailerUrl != null) { this.addTrailer(trailerUrl) }
            }
        }
    }

    private fun queryParams(url: String): Map<String, String> {
        return try {
            val query = url.substringAfter('?', "")
            if (query.isEmpty()) emptyMap() else {
                query.split('&').mapNotNull { param ->
                    val parts = param.split('=', limit = 2)
                    if (parts.size == 2 && parts[0].isNotEmpty()) parts[0] to parts[1] else null
                }.toMap()
            }
        } catch (e: Exception) { emptyMap() }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val mainPageDocument = try { app.get(data).document } catch (e: Exception) { return false }
        var foundLinks = false
        Log.d("DiziFun", "loadLinks çağrıldı (extractor kullanacak): $data")
        val scriptContent = mainPageDocument.select("script").html()
        val hexPattern = Regex("""hexToString\w*\("([a-fA-F0-9]+)"\)""")
        val hexUrls = hexPattern.findAll(scriptContent).mapNotNull { it.groups[1]?.value }.toList().distinct()

        if (hexUrls.isEmpty()) {
             Log.w("DiziFun", "Script içinde hexToString...() çağrısı bulunamadı. Fallback iframe aranıyor...")
             // === apmap yerine forEach ===
             mainPageDocument.select("iframe#londonIframe[src], ... , iframe[data-src*=premiumvideo]")
                 .forEach { iframe -> // apmap yerine forEach
                     val iframeSrc = iframe.attr("src").ifEmpty { iframe.attr("data-src") }
                     if (iframeSrc.isNotBlank() && !iframeSrc.startsWith("about:blank")) {
                         val embedUrl = fixUrl(iframeSrc)
                         Log.d("DiziFun", "Fallback iframe bulundu: $embedUrl")
                         // suspendSafeApiCall içinde loadExtractor hala askıya alınabilir
                         suspendSafeApiCall {
                             if (loadExtractor(embedUrl, data, subtitleCallback, callback)) {
                                 Log.i("DiziFun", "Fallback loadExtractor başarılı: $embedUrl")
                                 // foundLinks'i burada güncellemek senkronizasyon sorunu yaratabilir,
                                 // bunun yerine tüm linkleri toplayıp sonda kontrol etmek daha iyi olabilir
                                 // Şimdilik basit bırakalım, ama çok fazla link varsa performansı etkileyebilir.
                                 foundLinks = true
                             } else {
                                 Log.w("DiziFun", "Fallback loadExtractor başarısız: $embedUrl")
                             }
                         }
                     }
                 }
             // ==========================
        } else {
            Log.d("DiziFun", "Bulunan Hex Embed URL'ler: $hexUrls")
             // === apmap yerine forEach ===
            hexUrls.forEach { hexUrl -> // apmap yerine forEach
                val decodedRelativeUrl = hexToString(hexUrl)
                if (decodedRelativeUrl.isNotBlank()) {
                    val embedUrl = fixUrl(decodedRelativeUrl)
                    Log.d("DiziFun", "loadExtractor çağrılıyor (Hex'ten): $embedUrl")
                    suspendSafeApiCall {
                        if (loadExtractor(embedUrl, data, subtitleCallback, callback)) {
                             Log.i("DiziFun", "loadExtractor başarılı (Hex'ten): $embedUrl")
                            foundLinks = true
                        } else {
                             Log.w("DiziFun", "loadExtractor başarısız (Hex'ten): $embedUrl")
                        }
                    }
                }
            }
             // ==========================
        }
         if (!foundLinks) { Log.e("DiziFun", "loadExtractor hiçbir yöntemle link bulamadı: $data") }
        return foundLinks
    }

    private fun hexToString(hex: String): String {
        return try {
            hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray().toString(Charsets.UTF_8)
        } catch (e: Exception) { Log.e("DiziFun", "hexToString hatası: $hex", e); "" }
    }
}