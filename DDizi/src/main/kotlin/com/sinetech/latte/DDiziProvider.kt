package com.sinetech.latte

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import org.jsoup.nodes.Element

class DDiziProvider : MainAPI() {
    override var mainUrl              = "https://www.ddizi.im"
    override var name                 = "DDizi「🍿🎥」"
    override var lang                 = "tr"
    override val hasMainPage         = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport   = true
    override val supportedTypes      = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/yeni-eklenenler1" to "Son Eklenen Bölümler",
        "$mainUrl/yabanci-dizi-izle" to "Yabancı Diziler",
        "$mainUrl/eski.diziler" to "Eski Diziler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) {
            "${request.data}/$page"
        } else {
            request.data
        }
        Log.d("DDizi:", "Request for $url with page $page")
        val document = app.get(url, headers = getHeaders(mainUrl)).document
        
        val home = mutableListOf<SearchResponse>()
        
        // dizi-boxpost-cat div'lerini kontrol et
        try {
            val boxCatResults = document.select("div.dizi-boxpost-cat").mapNotNull { it.toSearchResult() }
            if (boxCatResults.isNotEmpty()) {
                Log.d("DDizi:", "Found ${boxCatResults.size} box-cat results")
                home.addAll(boxCatResults)
            }
        } catch (e: Exception) {
            Log.d("DDizi:", "Error parsing box-cat results: ${e.message}")
        }
        
        // dizi-boxpost div'lerini kontrol et
        try {
            val boxResults = document.select("div.dizi-boxpost").mapNotNull { it.toSearchResult() }
            if (boxResults.isNotEmpty()) {
                Log.d("DDizi:", "Found ${boxResults.size} box results")
                home.addAll(boxResults)
            }
        } catch (e: Exception) {
            Log.d("DDizi:", "Error parsing box results: ${e.message}")
        }
        
        // Sonraki sayfa kontrolü
        val hasNextPage = document.select(".pagination a").any { it.text().contains("Sonraki") }
        
        Log.d("DDizi:", "Added ${home.size} total episodes, hasNext: $hasNextPage")
        return newHomePageResponse(request.name, home, hasNextPage)
    }

    // Element sınıfı için extension fonksiyonu
    private fun Element.toSearchResult(): SearchResponse? {
        val linkElement = this.selectFirst("a") ?: return null
        val title = linkElement.text()?.trim() ?: return null
        val href = fixUrl(linkElement.attr("href") ?: return null)
        
        // Poster URL'yi doğru şekilde al
        val img = this.selectFirst("img.img-back, img.img-back-cat")
        val posterUrl = when {
            img?.hasAttr("data-src") == true -> fixUrlNull(img.attr("data-src"))
            img?.hasAttr("src") == true -> fixUrlNull(img.attr("src"))
            else -> null
        }
        
        // Açıklama ve yorum sayısını al
        val description = this.selectFirst("p")?.text()
        val commentCount = this.selectFirst("span.comments-ss")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
        
        Log.d("DDizi:", "Found item: $title, $href, posterUrl: $posterUrl, comments: $commentCount")
        
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        Log.d("DDizi:", "Searching for $query")
        
        // Form verilerini hazırla
        val formData = mapOf("arama" to query)
        
        // POST isteği gönder
        val document = app.post(
            "$mainUrl/arama/", 
            data = formData, 
            headers = getHeaders(mainUrl)
        ).document
        val results = ArrayList<SearchResponse>()
        
        // dizi-boxpost-cat sınıfını kontrol et (arama sonuçları)
        try {
            val boxCatResults = document.select("div.dizi-boxpost-cat").mapNotNull { it.toSearchResult() }
            if (boxCatResults.isNotEmpty()) {
                Log.d("DDizi:", "Found ${boxCatResults.size} box-cat results")
                results.addAll(boxCatResults)
            }
        } catch (e: Exception) {
            Log.d("DDizi:", "Error parsing box-cat search results: ${e.message}")
        }
        
        // Alternatif olarak dizi-boxpost sınıfını kontrol et
        if (results.isEmpty()) {
            try {
                val boxResults = document.select("div.dizi-boxpost").mapNotNull { it.toSearchResult() }
                if (boxResults.isNotEmpty()) {
                    Log.d("DDizi:", "Found ${boxResults.size} box results")
                    results.addAll(boxResults)
                }
            } catch (e: Exception) {
                Log.d("DDizi:", "Error parsing box search results: ${e.message}")
            }
        }
        
        // Alternatif seçiciler
        if (results.isEmpty()) {
            try {
                val altResults = document.select("div.dizi-listesi a, div.yerli-diziler li a, div.yabanci-diziler li a").mapNotNull { 
                    val title = it.text()?.trim() ?: return@mapNotNull null
                    val href = fixUrl(it.attr("href") ?: return@mapNotNull null)
                    
                    newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                        this.posterUrl = null
                    }
                }
                
                if (altResults.isNotEmpty()) {
                    Log.d("DDizi:", "Found ${altResults.size} alternative results")
                    results.addAll(altResults)
                }
            } catch (e: Exception) {
                Log.d("DDizi:", "Error parsing alternative search results: ${e.message}")
            }
        }
        
        Log.d("DDizi:", "Returning total ${results.size} search results")
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        Log.d("DDizi:", "Loading $url")
        val document = app.get(url, headers = getHeaders(mainUrl)).document

        // Başlık ve sezon/bölüm bilgilerini al
        val fullTitle = document.selectFirst("h1, h2, div.dizi-boxpost-cat a")?.text()?.trim() ?: ""
        Log.d("DDizi:", "Full title: $fullTitle")
        
        // Regex tanımlamaları
        val seasonRegex = Regex("""(\d+)\.?\s*Sezon""", RegexOption.IGNORE_CASE)
        val episodeRegex = Regex("""(\d+)\.?\s*Bölüm""", RegexOption.IGNORE_CASE)
        val finalRegex = Regex("""Sezon Finali""", RegexOption.IGNORE_CASE)

        // Başlıktan bilgileri çıkar
        val seasonMatch = seasonRegex.find(fullTitle)
        val episodeMatch = episodeRegex.find(fullTitle)
        val isSeasonFinal = finalRegex.find(fullTitle) != null
        
        // Sezon bilgisi yoksa varsayılan olarak 1. sezon kabul et
        val seasonNumber = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val episodeNumber = episodeMatch?.groupValues?.get(1)?.toIntOrNull()
        
        // Dizi adını ayıkla
        var title = fullTitle
        
        // Önce sezon bilgisini kontrol et
        if (seasonMatch != null) {
            val parts = fullTitle.split(seasonRegex)
            if (parts.isNotEmpty()) {
                title = parts[0].trim()
            }
        } 
        // Sezon bilgisi yoksa bölüm bilgisini kontrol et
        else if (episodeMatch != null) {
            val parts = fullTitle.split(episodeRegex)
            if (parts.isNotEmpty()) {
                title = parts[0].trim()
            }
        }
        
        // Başlığı temizle (nokta ve sayıları kaldır)
        title = title.replace(Regex("""^\d+\.?\s*"""), "").trim()
        
        Log.d("DDizi:", "Parsed title: $title, Season: $seasonNumber (default: 1), Episode: $episodeNumber, Final: $isSeasonFinal")
        
        // Poster URL'yi doğru şekilde al
        val posterImg = document.selectFirst("div.afis img, img.afis, img.img-back, img.img-back-cat")

        // Tüm bölümleri toplamak için sayfalama sistemini kullan
        val allEpisodes = mutableListOf<Episode>()
        var currentPage = 0
        var hasMorePages = true

        while (hasMorePages) {
            val pageUrl = if (url.contains("/dizi/") || url.contains("/diziler/")) {
                if (currentPage == 0) url else "$url/sayfa-$currentPage"
            } else {
                url
            }

            val pageDocument = if (currentPage == 0) document else app.get(pageUrl, headers = getHeaders(mainUrl)).document
            Log.d("DDizi:", "Loading page: $pageUrl")

            val pageEpisodes = pageDocument.select("div.bolumler a, div.sezonlar a, div.dizi-arsiv a, div.dizi-boxpost-cat a").map { ep ->
                val name = ep.text().trim()
                val href = fixUrl(ep.attr("href"))
                
                val epSeasonMatch = seasonRegex.find(name)
                val epEpisodeMatch = episodeRegex.find(name)
                val epIsSeasonFinal = finalRegex.find(name) != null
                
                val epSeasonNumber = epSeasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val epEpisodeNumber = epEpisodeMatch?.groupValues?.get(1)?.toIntOrNull()
                
                val epDescription = ep.parent()?.selectFirst("p")?.text()
                val epCommentCount = ep.parent()?.selectFirst("span.comments-ss")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
                
                Log.d("DDizi:", "Found episode: $name, Season: $epSeasonNumber, Episode: $epEpisodeNumber, Final: $epIsSeasonFinal, Comments: $epCommentCount")
                
                newEpisode(href) {
                    this.name = name
                    this.season = epSeasonNumber
                    this.episode = epEpisodeNumber
                    this.description = epDescription
                }
            }

            if (pageEpisodes.isNotEmpty()) {
                allEpisodes.addAll(pageEpisodes)
                currentPage++
                // Sonraki sayfa kontrolü
                hasMorePages = pageDocument.select(".pagination a").any { it.text().contains("Sonraki") }
                Log.d("DDizi:", "Found ${pageEpisodes.size} episodes on page $currentPage, hasMorePages: $hasMorePages")
            } else {
                hasMorePages = false
            }
        }

        Log.d("DDizi:", "Total episodes found: ${allEpisodes.size}")

        // Eğer hiç bölüm bulunamazsa ve şu anki sayfa bir bölüm sayfasıysa, sadece bu bölümü ekle
        if (allEpisodes.isEmpty() && !url.contains("/dizi/") && !url.contains("/diziler/")) {
            allEpisodes.add(
                newEpisode(url) {
                    this.name = fullTitle
                    this.season = seasonNumber
                    this.episode = episodeNumber
                    this.description = document.selectFirst("div.dizi-aciklama, div.aciklama, p")?.text()?.trim()
                }
            )
        }
        val poster = when {
            posterImg?.hasAttr("data-src") == true -> fixUrlNull(posterImg.attr("data-src"))
            posterImg?.hasAttr("src") == true -> fixUrlNull(posterImg.attr("src"))
            else -> null
        }
        
        // Açıklama bilgisini al
        val plot = document.selectFirst("div.dizi-aciklama, div.aciklama, p")?.text()?.trim()
        
        // Yorum sayısını al
        val commentCount = document.selectFirst("span.comments-ss")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
        Log.d("DDizi:", "Comment count: $commentCount")

        Log.d("DDizi:", "Loaded title: $title, poster: $poster")

        // Eğer dizi ana sayfasındaysak, bölümleri listele
        if (allEpisodes.isEmpty()) {
            try {
                if (url.contains("/dizi/") || url.contains("/diziler/")) {
                    // Dizi ana sayfasındayız, tüm bölümleri listele
                    val eps = document.select("div.bolumler a, div.sezonlar a, div.dizi-arsiv a, div.dizi-boxpost-cat a").map { ep ->
                        val name = ep.text().trim()
                        val href = fixUrl(ep.attr("href"))
                        
                        // Bölüm adından bilgileri çıkar
                        val epSeasonMatch = seasonRegex.find(name)
                        val epEpisodeMatch = episodeRegex.find(name)
                        val epIsSeasonFinal = finalRegex.find(name) != null
                        
                        // Sezon bilgisi yoksa varsayılan olarak 1. sezon kabul et
                        val epSeasonNumber = epSeasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                        val epEpisodeNumber = epEpisodeMatch?.groupValues?.get(1)?.toIntOrNull()
                        
                        // Açıklama ve yorum sayısını al
                        val epDescription = ep.parent()?.selectFirst("p")?.text()
                        val epCommentCount = ep.parent()?.selectFirst("span.comments-ss")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
                        
                        Log.d("DDizi:", "Episode: $name, Season: $epSeasonNumber (default: 1), Episode: $episodeNumber, Final: $epIsSeasonFinal, Comments: $epCommentCount")
                        
                        newEpisode(href) {
                            this.name = name
                            this.season = epSeasonNumber
                            this.episode = epEpisodeNumber
                            this.description = epDescription
                        }
                    }
                    Log.d("DDizi:", "Found ${eps.size} episodes")
                    allEpisodes.addAll(eps)
                } else {
                    // Bölüm sayfasındayız, sadece bu bölümü ekle
                    Log.d("DDizi:", "Single episode page, adding current episode with Season: $seasonNumber (default: 1)")
                    
                    allEpisodes.add(
                        newEpisode(url) {
                            this.name = fullTitle
                            this.season = seasonNumber
                            this.episode = episodeNumber
                            this.description = plot
                        }
                    )
                }
            } catch (e: Exception) {
                Log.d("DDizi:", "Error parsing episodes: ${e.message}")
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, allEpisodes) {
            this.posterUrl = poster
            this.plot = plot
            this.year = null
            this.tags = null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("DDizi:", "Loading links for $data")
        val document = app.get(data, headers = getHeaders(mainUrl)).document
        
        // Meta og:video etiketini kontrol et
        try {
            val ogVideo = document.selectFirst("meta[property=og:video]")?.attr("content")
            if (!ogVideo.isNullOrEmpty()) {
                Log.d("DDizi:", "Found og:video meta tag: $ogVideo")
                
                // Video bağlantısına istek at ve jwplayer yapılandırmasını bul
                val playerDoc = app.get(
                    ogVideo, 
                    headers = getHeaders(data)
                ).document
                val scripts = playerDoc.select("script")
                
                // jwplayer yapılandırmasını içeren script'i bul
                scripts.forEach { script ->
                    val content = script.html()
                    if (content.contains("jwplayer") && content.contains("sources")) {
                        Log.d("DDizi:", "Found jwplayer configuration")
                        
                        // sources kısmını regex ile çıkar
                        val sourcesRegex = Regex("""sources:\s*\[\s*\{(.*?)\}\s*,?\s*\]""", RegexOption.DOT_MATCHES_ALL)
                        val sourcesMatch = sourcesRegex.find(content)
                        
                        if (sourcesMatch != null) {
                            // file parametresini bul
                            val fileRegex = Regex("""file:\s*["'](.*?)["']""")
                            val fileMatch = fileRegex.find(sourcesMatch.groupValues[1])
                            
                            if (fileMatch != null) {
                                val fileUrl = fileMatch.groupValues[1]
                                Log.d("DDizi:", "Found video source: $fileUrl")
                                
                                // Dosya türünü belirle
                                val fileType = when {
                                    fileUrl.contains(".m3u8") || fileUrl.contains("hls") -> "hls"
                                    fileUrl.contains(".mp4") -> "mp4"
                                    else -> "hls" // Varsayılan olarak hls kabul et
                                }
                                
                                // Kalite bilgisini belirle
                                val qualityRegex = Regex("""label:\s*["'](.*?)["']""")
                                val qualityMatch = qualityRegex.find(sourcesMatch.groupValues[1])
                                val quality = qualityMatch?.groupValues?.get(1) ?: "Auto"
                                
                                Log.d("DDizi:", "Video type: $fileType, quality: $quality")
                                
                                // master.txt dosyası için özel başlıklar
                                val videoHeaders = if (fileUrl.contains("master.txt")) {
                                    mapOf(
                                        "accept" to "*/*",
                                        "accept-language" to "tr-TR,tr;q=0.5",
                                        "cache-control" to "no-cache",
                                        "pragma" to "no-cache",
                                        "sec-ch-ua" to "\"Chromium\";v=\"134\", \"Not:A-Brand\";v=\"24\"",
                                        "sec-ch-ua-mobile" to "?0",
                                        "sec-ch-ua-platform" to "\"Windows\"",
                                        "sec-fetch-dest" to "empty",
                                        "sec-fetch-mode" to "cors",
                                        "sec-fetch-site" to "cross-site",
                                        "user-agent" to USER_AGENT,
                                        "referer" to ogVideo // Player URL'sini referrer olarak kullan
                                    )
                                } else {
                                    getHeaders(ogVideo)
                                }
                                
                                Log.d("DDizi:", "Using headers for video source: ${videoHeaders.keys.joinToString()}")
                                
                                // ExtractorLink oluştur
                                callback.invoke(
                                    ExtractorLink(
                                        source = name,
                                        name = "$name - $quality",
                                        url = fileUrl,
                                        referer = ogVideo,
                                        quality = getQualityFromName(quality),
                                        headers = videoHeaders,
                                        type = if (fileType == "hls") ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                    )
                                )
                                // Eğer dosya türü hls ise, M3u8Helper ile işle
                                if (fileType == "hls") {
                                    try {
                                        Log.d("DDizi:", "Generating M3u8 for: $fileUrl")
                                        M3u8Helper.generateM3u8(
                                            name,
                                            fileUrl,
                                            ogVideo, // Player URL'sini referrer olarak kullan
                                            headers = videoHeaders
                                        ).forEach(callback)
                                    } catch (e: Exception) {
                                        Log.d("DDizi:", "Error generating M3u8: ${e.message}")
                                        
                                        // Doğrudan bağlantıyı dene
                                        if (fileUrl.contains("master.txt")) {
                                            try {
                                                Log.d("DDizi:", "Trying to get master.txt content directly")
                                                val masterContent = app.get(fileUrl, headers = videoHeaders).text
                                                Log.d("DDizi:", "Master.txt content length: ${masterContent.length}")
                                                
                                                // m3u8 bağlantılarını bul
                                                val m3u8Regex = Regex("""(https?://.*?\.m3u8[^"\s]*)""")
                                                val m3u8Matches = m3u8Regex.findAll(masterContent)
                                                
                                                m3u8Matches.forEach { m3u8Match ->
                                                    val m3u8Url = m3u8Match.groupValues[1]
                                                    Log.d("DDizi:", "Found m3u8 in master.txt: $m3u8Url")
                                                    
                                                    // Kalite bilgisini çıkar
                                                    val m3u8Quality = when {
                                                        m3u8Url.contains("1080") -> "1080p"
                                                        m3u8Url.contains("720") -> "720p"
                                                        m3u8Url.contains("480") -> "480p"
                                                        m3u8Url.contains("360") -> "360p"
                                                        else -> "Auto"
                                                    }
                                                    
                                                    callback.invoke(
                                    ExtractorLink(
                                        source = name,
                                        name = "$name - $quality",
                                        url = fileUrl,
                                        referer = ogVideo,
                                        quality = getQualityFromName(quality),
                                        headers = videoHeaders,
                                        type = if (fileType == "hls") ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                    )
                                )
                                                }
                                            } catch (e2: Exception) {
                                                Log.d("DDizi:", "Error parsing master.txt: ${e2.message}")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Yine de normal extractor'ları dene
                loadExtractor(ogVideo, data, subtitleCallback, callback)
            }
        } catch (e: Exception) {
            Log.d("DDizi:", "Error parsing og:video meta tag: ${e.message}")
        }
        

        return true
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
        
        // Standart HTTP başlıkları
        private fun getHeaders(referer: String): Map<String, String> {
            return mapOf(
                "accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
                "accept-language" to "tr-TR,tr;q=0.5",
                "cache-control" to "no-cache",
                "pragma" to "no-cache",
                "sec-ch-ua" to "\"Chromium\";v=\"134\", \"Not:A-Brand\";v=\"24\"",
                "sec-ch-ua-mobile" to "?0",
                "sec-ch-ua-platform" to "\"Windows\"",
                "sec-fetch-dest" to "document",
                "sec-fetch-mode" to "navigate",
                "sec-fetch-site" to "same-origin",
                "sec-fetch-user" to "?1",
                "upgrade-insecure-requests" to "1",
                "user-agent" to USER_AGENT,
                "referer" to referer
            )
        }
    }
}