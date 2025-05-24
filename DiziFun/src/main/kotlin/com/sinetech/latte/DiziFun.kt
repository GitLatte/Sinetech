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
import org.jsoup.Jsoup

class DiziFun : MainAPI() {
    override var mainUrl = "https://dizifun3.com"
    override var name = "DiziFun"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "yeni_diziler" to "Yeni Eklenen Diziler",
        "yeni_filmler" to "Yeni Eklenen Filmler",
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
                val mainDocument = try { app.get(mainUrl).document } catch (e: Exception) { return newHomePageResponse(request.name, emptyList()) }
                val items = when (request.data) {
                    "yeni_diziler" -> {
                        mainDocument.select("h4.title")
                            .find { it.text().contains("Yeni Eklenen Diziler", ignoreCase = true) }
                            ?.nextElementSibling()
                            ?.select(".movies_recent-item")
                            ?.mapNotNull { it.toRecentSearchResult() }
                            ?: emptyList()
                    }
                    "yeni_filmler" -> {
                         mainDocument.select("h4.title")
                            .find { it.text().contains("Yeni Eklenen Filmler", ignoreCase = true) }
                            ?.nextElementSibling()
                            ?.select(".movies_recent-item")
                            ?.mapNotNull { it.toRecentSearchResult() }
                            ?: emptyList()
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
                val document = try { app.get(url).document } catch (e: Exception) { return newHomePageResponse(request.name, emptyList()) }
                val items = document.select(".uk-grid .uk-width-large-1-6").mapNotNull { element -> element.toSearchResult() }
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
        val document = try { app.get(searchUrl).document } catch (e: Exception) { return emptyList() }
        val results = document.select(".uk-grid .uk-width-large-1-5, .uk-grid .uk-width-large-1-6").mapNotNull { element -> element.toSearchResult() ?: element.toRecentSearchResult() }
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
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl; this.year = year }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl; this.year = year }
        }
    }

    private fun Element.toRecentSearchResult(): SearchResponse? {
       val linkElement = this.selectFirst("a.movies_recent-link")
       val href = fixUrlNull(linkElement?.attr("href")) ?: return null
       val title = this.selectFirst(".movies_recent-title")?.text()?.trim() ?: return null
       val posterUrl = fixUrlNull(this.selectFirst("img.movies_recent-image")?.attr("src"))
       val year = this.selectFirst(".movies_recent-date")?.text()?.trim()?.toIntOrNull()
       val type = if (href.contains("/film/")) TvType.Movie else TvType.TvSeries
       return if (type == TvType.Movie) {
           newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl; this.year = year }
       } else {
           newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl; this.year = year }
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
        val recommendations = document.select(".related-series .item, .benzer-yapimlar .item").mapNotNull { it.toSearchResult() ?: it.toRecentSearchResult() }
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
                        val epLink = if (relativeHref.startsWith("?")) { "$url$relativeHref" } else { fixUrl(relativeHref) }
                        if (epLink.isBlank() || epLink == url) { return@forEach }
                        val episodeDiv = episodeAnchor.selectFirst(".episode-button")
                        val epName = episodeDiv?.text()?.trim() ?: "Bölüm"
                        val queryParamsMap = queryParams(epLink)
                        val epNum = epName.split(".").firstOrNull()?.filter { it.isDigit() }?.toIntOrNull() ?: queryParamsMap["episode"]?.toIntOrNull()
                        episodes.add(
                            newEpisode(epLink) {
                                this.name = epName
                                this.season = seasonNum
                                this.episode = epNum
                                this.posterUrl = poster
                            }
                        )
                    }
                }
            } else {
                 document.select(".bolumler .bolumtitle a, .episodes-list .episode a, .episode-item a, #season1 .uk-width-large-1-5 a").forEach { episodeAnchor ->
                    val relativeHref = episodeAnchor.attr("href")
                    val epLink = if (relativeHref.startsWith("?")) { "$url$relativeHref" } else { fixUrl(relativeHref) }
                    if (epLink.isBlank() || epLink == url) { return@forEach }
                    val epName = episodeAnchor.text().trim().ifEmpty { episodeAnchor.selectFirst(".episode-button")?.text()?.trim() ?: "Bölüm" }
                    val queryParamsMap = queryParams(epLink)
                    val epNum = queryParamsMap["episode"]?.toIntOrNull() ?: queryParamsMap["bolum"]?.toIntOrNull() ?: epName.split(".").firstOrNull()?.filter { it.isDigit() }?.toIntOrNull()
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
        val mainPageSourceText = try { app.get(data).text } catch (e: Exception) { return false }
        val mainPageDocument = try { Jsoup.parse(mainPageSourceText) } catch (e: Exception) { return false }
        var foundLinks = false

        val hexPattern = Regex("""hexToString\w*\("([a-fA-F0-9]+)"\)""")
        val hexUrls = hexPattern.findAll(mainPageSourceText).mapNotNull { it.groups[1]?.value }.toList().distinct()

        hexUrls.forEach { hexUrl ->
            val decodedRelativeUrl = hexToString(hexUrl)
            if (decodedRelativeUrl.isNotBlank()) {
                val embedUrl = fixUrl(decodedRelativeUrl)
                val embedUrlHost = try { URI(embedUrl).host } catch (e: Exception) { null }

                val iframeSourceText = try { app.get(embedUrl, referer = data).text } catch (e: Exception) { null }

                if (iframeSourceText != null) {
                    val baseUri = try { URI(embedUrl) } catch (e: Exception) { null }

                     if (embedUrlHost != null && embedUrlHost.contains("gujan.premiumvideo.click")) {
                         // Gujan durumu - iframe içinden M3U8 ara
                         val iframeM3u8Pattern = Regex("""(https?:\/\/gujan\.premiumvideo\.click\/hls\/.*?\/playlist\.m3u8)""")
                         iframeM3u8Pattern.findAll(iframeSourceText).forEach { match ->
                            val iframeM3u8Url = match.groups[1]?.value
                             if (!iframeM3u8Url.isNullOrBlank()) {
                                 val cleanIframeM3u8Url = iframeM3u8Url.substringBefore('?')
                                  callback.invoke(
                                     newExtractorLink(
                                         source = this.name,
                                         name = "Gujan",
                                         url = cleanIframeM3u8Url,
                                         type = ExtractorLinkType.M3U8
                                     ) {
                                         this.quality = Qualities.Unknown.value
                                         this.referer = embedUrl
                                     }
                                 )
                                 foundLinks = true
                             }
                         }
                     } else if (embedUrlHost != null && embedUrlHost.contains("playhouse.premiumvideo.click")) {
                         // Playhouse/PlayAmony durumu - iframe içinden M3U8 ara ve doğru alt domaini bul
                         val iframeSourceDocument = try { Jsoup.parse(iframeSourceText) } catch (e: Exception) { null }
                         if (iframeSourceDocument != null) {
                             val relativeM3u8Path = iframeSourceDocument.selectFirst("video#my-video_html5_api > source[type='application/x-mpegURL'], video > source[src*=.m3u8]")?.attr("src")
                                 ?: iframeSourceDocument.select("script").html().let { script ->
                                     Regex("""file:\s*['"]([^'"]+\.m3u8)['"]""").find(script)?.groups?.get(1)?.value
                                 }

                             if (!relativeM3u8Path.isNullOrBlank()) {
                                 // Iframe kaynak kodunda d1.premiumvideo.click veya d2.premiumvideo.click adreslerini ara
                                 val domainPattern = Regex("""https?:\/\/(d\d+\.premiumvideo\.click)""")
                                 val correctBaseDomain = domainPattern.find(iframeSourceText)?.groups?.get(1)?.value

                                 if (correctBaseDomain != null) {
                                     val finalM3u8Url = "https://$correctBaseDomain$relativeM3u8Path"
                                     Log.i(name, "Playhouse/PlayAmony Nihai Oynatma URL: $finalM3u8Url")

                                     callback.invoke(
                                        newExtractorLink(
                                            source = this.name,
                                            name = "Playhouse/PlayAmony",
                                            url = finalM3u8Url,
                                            type = ExtractorLinkType.M3U8
                                        ) {
                                            this.quality = Qualities.Unknown.value
                                            this.referer = embedUrl
                                        }
                                    )
                                     foundLinks = true
                                 } else {
                                     Log.w(name, "Playhouse/PlayAmony embed sayfasında doğru alt domain bulunamadı: $embedUrl")
                                 }

                             } else {
                                 Log.w(name, "Playhouse/PlayAmony embed sayfasında M3U8 yolu bulunamadı: $embedUrl")
                             }
                         } else {
                            Log.w(name, "Playhouse/PlayAmony embed sayfası ayrıştırılamadı: $embedUrl")
                         }
                     }
                }
            }
        }

        if (!foundLinks) { Log.e("DiziFun", "loadLinks hiçbir yöntemle M3U8 linki bulamadı: $data") }
        return foundLinks
    }

    private fun hexToString(hex: String): String {
        return try {
            hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray().toString(Charsets.UTF_8)
        } catch (e: Exception) { Log.e("DiziFun", "hexToString hatası: $hex", e); "" }
    }
}