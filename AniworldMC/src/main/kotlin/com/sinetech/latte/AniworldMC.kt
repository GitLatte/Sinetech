package com.sinetech.latte

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
// apmap import'unu kaldırıyoruz, artık kullanılmayacak
// import com.lagradost.cloudstream3.utils.Coroutines.apmap
import kotlinx.coroutines.* // Coroutines için gerekli importlar
import com.lagradost.cloudstream3.mvvm.logError // Hata loglamak için
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

open class AniworldMC : MainAPI() {
    override var mainUrl = "https://aniworld.to"
    override var name = "AniworldMC「👒⚔🏴‍☠️🌊」"
    override val hasMainPage = true
    override var lang = "de"

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val document = app.get(mainUrl).document
        val item = arrayListOf<HomePageList>()
        document.select("div.carousel").map { ele ->
            val header = ele.selectFirst("h2")?.text() ?: return@map
            val home = ele.select("div.coverListItem").mapNotNull {
                it.toSearchResult()
            }
            if (home.isNotEmpty()) item.add(HomePageList(header, home))
        }
        return newHomePageResponse(item)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val json = app.post(
            "$mainUrl/ajax/search",
            data = mapOf("keyword" to query),
            referer = "$mainUrl/search",
            headers = mapOf(
                "x-requested-with" to "XMLHttpRequest"
            )
        )
        return tryParseJson<List<AnimeSearch>>(json.text)?.filter {
            !it.link.contains("episode-") && it.link.contains(
                "/stream"
            )
        }?.map {
            newAnimeSearchResponse(
                it.title?.replace(Regex("</?em>"), "") ?: "",
                fixUrl(it.link),
                TvType.Anime
            ) {
            }
        } ?: throw ErrorLoadingException()

    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.series-title span")?.text() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.seriesCoverBox img")?.attr("data-src"))
        val tags = document.select("div.genres li a").map { it.text() }
        val year = document.selectFirst("span[itemprop=startDate] a")?.text()?.toIntOrNull()
        val description = document.select("p.seri_des").text()
        val actor =
            document.select("li:contains(Schauspieler:) ul li a").map { it.select("span").text() }

        val episodes = mutableListOf<Episode>()
        document.select("div#stream > ul:first-child li").map { ele ->
            val page = ele.selectFirst("a")
            val epsDocument = app.get(fixUrl(page?.attr("href") ?: return@map)).document
            epsDocument.select("div#stream > ul:nth-child(4) li").mapNotNull { eps ->
                val episodeUrl = fixUrl(eps.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
                val episodeNumber = eps.selectFirst("a")?.text()?.toIntOrNull()
                val seasonNumber = page.text().toIntOrNull()

                episodes.add(
                    newEpisode(episodeUrl) {
                        this.episode = episodeNumber
                        this.season = seasonNumber
                        // runTime varsa buraya eklenebilir:
                        // this.runTime = null // veya bulunan süre bilgisi
                    }
                )
            }
        }

        return newAnimeLoadResponse(
            title,
            url,
            TvType.Anime
        ) {
            engName = title
            posterUrl = poster
            this.year = year
            addEpisodes(
                DubStatus.Subbed,
                episodes
            )
            addActors(actor)
            plot = description
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val hosters = document.select("div.hosterSiteVideo ul li").map {
            Triple(
                it.attr("data-lang-key"),
                it.attr("data-link-target"),
                it.select("h4").text()
            )
        }.filter {
            it.third != "Vidoza"
        }

        // apmap yerine async ve awaitAll kullanıyoruz
        coroutineScope { // Yeni bir coroutineScope oluşturuyoruz
            hosters.map { (langKey, linkTarget, hosterName) -> // Listeyi mapliyoruz
                async(Dispatchers.IO) { // Her öğe için paralel bir async görev oluşturuyoruz
                    try {
                        val redirectUrl = app.get(fixUrl(linkTarget)).url
                        val lang = langKey.getLanguage(document)
                        val name = "${hosterName} [${lang}]"

                        if (hosterName == "VOE") {
                            Voe().getUrl(redirectUrl, data, subtitleCallback) { link ->
                                callback.invoke(
                                    ExtractorLink(
                                        source = name,
                                        name = name,
                                        url = link.url,
                                        referer = link.referer,
                                        quality = link.quality,
                                        type = link.type,
                                        headers = link.headers,
                                        extractorData = link.extractorData
                                    )
                                )
                            }
                        } else {
                            loadExtractor(redirectUrl, data, subtitleCallback) { link ->
                                callback.invoke(
                                    ExtractorLink(
                                        source = name,
                                        name = name,
                                        url = link.url,
                                        referer = link.referer,
                                        quality = link.quality,
                                        type = link.type,
                                        headers = link.headers,
                                        extractorData = link.extractorData
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        logError(e) // Hataları loglayın
                    }
                }
            }.awaitAll() // Tüm async görevlerin tamamlanmasını bekliyoruz
        }


        return true
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val title = this.selectFirst("h3")?.text() ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    private fun String.getLanguage(document: Document): String? {
        val titleText = document.selectFirst("div.changeLanguageBox img[data-lang-key=$this]")?.attr("title")

        return titleText?.let {
            when {
                it.startsWith("mit Untertitel Deutsch", ignoreCase = true) -> "Almanca Altyazılı"
                it.startsWith("mit Untertitel Englisch", ignoreCase = true) -> "İngilizce Altyazılı"
                else -> it.removePrefix("mit ").trim()
            }
        }
    }

    private data class AnimeSearch(
        @JsonProperty("link") val link: String,
        @JsonProperty("title") val title: String? = null,
    )

}

class Dooood : DoodLaExtractor() {
    override var mainUrl = "https://doodstream.com"
}