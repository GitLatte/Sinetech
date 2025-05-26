// Farklı bir youtube end-point kullanarak youtube içeriklerini çekmek için kullandığım eklenti
// Birden fazla youtube kanalının içeriklerini tek bir eklentide toplamak için kullanıyorum.
// Kullanımı:
// 1. Kanal ID'lerini "channels" listesine ekleyin.
// 2. Eklentiyi projenize ekleyin.
// 3. Eklentiyi kullanın.

package com.sinetech.latte

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.StringUtils.encodeUri
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class YTEglence : MainAPI() {
    override var mainUrl = "https://inv.nadeko.net"
    override var name = "Youtube Eğlence İçerikleri「🎭」"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Others, TvType.Podcast, TvType.Live)

    private val channels = listOf(
        Channel("UCdlEXiVLTEvA280oyMvr8Kw", "🎭 Güldür Güldür"),
        Channel("UCJhEfZoLs5P_idxX--yhWOA", "🎭 Çok Güzel Hareketler Bunlar"),
        Channel("UCOYerJedhQqSyhXkev8QRFA", "🎭 Arkadaşım Hoşgeldin"),
        Channel("UCJh9qWsZFjdO-Qn0LnZSXhA", "🎭 Olacak O kadar"),
        Channel("UCgc3VJYdM_R8oKGRuXxUbKQ", "🎞️ Avrupa Yakası"),
        Channel("UCoWj9KzdFJn72NnVKScWfYg", "🥳 Soğuk Savaş"),
        Channel("UCdakEeTJHMPz9MdejLKDRhg", "🗣️ Çimen Show"),
        Channel("UCPRWKmegVtLlHA50-JOnDPw", "🗣️ TuzBiber Stand-Up"),
        Channel("UCbDuXXisCUfUL03VzAYsOwQ", "🎞️ Tatlı Hayat"),
        Channel("UCPpBBr7sbZs1BwHfmR9dEyQ", "🇹🇻 Kanal D Arşiv")
        // Genel olarak herkesin isteyebileceği kanallar sırayla eklenebilir.
    )

    private data class Channel(val id: String, val name: String)

    private suspend fun getChannelContent(channel: Channel): List<HomePageList> {
        val homePageLists = mutableListOf<HomePageList>()

        // Canlı yayınları al
        val streamsUrl = "$mainUrl/channel/${channel.id}/streams?sort_by=popular"
        try {
            val streamsDocument = app.get(streamsUrl).document
            val liveStreams = streamsDocument.select(".pure-u-1.pure-u-md-1-4").mapNotNull { streamElement ->
                val titleElement = streamElement.selectFirst(".video-card-row a p")
                val title = titleElement?.text() ?: return@mapNotNull null

                val streamUrl = streamElement.selectFirst(".thumbnail a")?.attr("href") ?: return@mapNotNull null
                val videoId = streamUrl.substringAfter("watch?v=")

                val thumbnailUrl = streamElement.selectFirst(".thumbnail img")?.attr("src")

                newMovieSearchResponse(
                    title,
                    "$mainUrl/watch?v=$videoId",
                    TvType.Live
                ) {
                    this.posterUrl = if (thumbnailUrl != null) "$mainUrl$thumbnailUrl" else "$mainUrl/vi/$videoId/maxres.jpg"
                    this.posterHeaders = mapOf()
                    this.quality = SearchQuality.HD
                }
            }

            if (liveStreams.isNotEmpty()) {
                homePageLists.add(HomePageList(
                    "🔴 ${channel.name} Canlı Yayınlar",
                    liveStreams,
                    true
                ))
            }
        } catch (e: Exception) {
            android.util.Log.e("YTEglence", "Error loading streams for ${channel.name}: ${e.message}")
        }

        // En yeni videoları al
        val newestUrl = "$mainUrl/channel/${channel.id}?sort_by=newest"
        val newestDocument = app.get(newestUrl).document
        val newestVideos = newestDocument.select(".pure-u-1.pure-u-md-1-4").mapNotNull { videoElement ->
            val titleElement = videoElement.selectFirst(".video-card-row a p")
            val title = titleElement?.text() ?: return@mapNotNull null

            val videoUrl = videoElement.selectFirst(".thumbnail a")?.attr("href") ?: return@mapNotNull null
            val videoId = videoUrl.substringAfter("watch?v=")

            val thumbnailUrl = videoElement.selectFirst(".thumbnail img")?.attr("src")

            newMovieSearchResponse(
                title,
                "$mainUrl/watch?v=$videoId",
                TvType.Podcast
            ) {
                this.posterUrl = if (thumbnailUrl != null) "$mainUrl$thumbnailUrl" else "$mainUrl/vi/$videoId/maxres.jpg"
                this.posterHeaders = mapOf()
                this.quality = SearchQuality.HD
            }
        }

        if (newestVideos.isNotEmpty()) {
            homePageLists.add(HomePageList(
                "「${channel.name}」Son Youtube Videoları ",
                newestVideos,
                true
            ))
        }

        // Oynatma listelerini al
        val playlistsUrl = "$mainUrl/channel/${channel.id}/playlists"
        val playlistsDocument = app.get(playlistsUrl).document
        val playlists = playlistsDocument.select(".pure-u-1.pure-u-md-1-4").mapNotNull { playlistElement ->
            val titleElement = playlistElement.selectFirst(".video-card-row a p")
            val title = titleElement?.text() ?: return@mapNotNull null

            val playlistUrl = playlistElement.selectFirst(".thumbnail a")?.attr("href") ?: return@mapNotNull null
            val thumbnailUrl = playlistElement.selectFirst(".thumbnail img")?.attr("src")

            newMovieSearchResponse(
                title,
                "$mainUrl$playlistUrl",
                TvType.Podcast
            ) {
                this.posterUrl = if (thumbnailUrl != null) "$mainUrl$thumbnailUrl" else null
                this.posterHeaders = mapOf()
                this.quality = SearchQuality.HD
            }
        }

        if (playlists.isNotEmpty()) {
            homePageLists.add(HomePageList(
                "${channel.name} Youtube Oynatma Listeleri",
                playlists,
                true
            ))
        }

        return homePageLists
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val allContent = mutableListOf<HomePageList>()
        val allLiveStreams = mutableListOf<SearchResponse>()

        for (channel in channels) {
            try {
                val streamsUrl = "$mainUrl/channel/${channel.id}/streams"
                try {
                    val streamsDocument = app.get("$mainUrl/channel/${channel.id}/streams?sort_by=popular").document
                    val firstStream = streamsDocument.selectFirst(".pure-u-1.pure-u-md-1-4")?.let { streamElement ->
                        val titleElement = streamElement.selectFirst(".video-card-row a p")
                        val title = titleElement?.text() ?: return@let null

                        val streamUrl = streamElement.selectFirst(".thumbnail a")?.attr("href") ?: return@let null
                        val videoId = streamUrl.substringAfter("watch?v=")

                        val thumbnailUrl = streamElement.selectFirst(".thumbnail img")?.attr("src")

                        newMovieSearchResponse(
                            "${channel.name} - $title",
                            "$mainUrl/watch?v=$videoId",
                            TvType.Live
                        ) {
                            this.posterUrl = if (thumbnailUrl != null) "$mainUrl$thumbnailUrl" else "$mainUrl/vi/$videoId/maxres.jpg"
                            this.posterHeaders = mapOf()
                            this.quality = SearchQuality.HD
                        }
                    }
                    if (firstStream != null) {
                        allLiveStreams.add(firstStream)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("YTEglence", "Error loading streams for ${channel.name}: ${e.message}")
                }

                // En yeni videoları al
                val newestUrl = "$mainUrl/channel/${channel.id}?sort_by=newest"
                val newestDocument = app.get(newestUrl).document
                val newestVideos = newestDocument.select(".pure-u-1.pure-u-md-1-4").mapNotNull { videoElement ->
                    val titleElement = videoElement.selectFirst(".video-card-row a p")
                    val title = titleElement?.text() ?: return@mapNotNull null

                    val videoUrl = videoElement.selectFirst(".thumbnail a")?.attr("href") ?: return@mapNotNull null
                    val videoId = videoUrl.substringAfter("watch?v=")

                    val thumbnailUrl = videoElement.selectFirst(".thumbnail img")?.attr("src")

                    newMovieSearchResponse(
                        title,
                        "$mainUrl/watch?v=$videoId",
                        TvType.Podcast
                    ) {
                        this.posterUrl = if (thumbnailUrl != null) "$mainUrl$thumbnailUrl" else "$mainUrl/vi/$videoId/maxres.jpg"
                        this.posterHeaders = mapOf()
                        this.quality = SearchQuality.HD
                    }
                }

                if (newestVideos.isNotEmpty()) {
                    allContent.add(HomePageList(
                        "「${channel.name}」Son Youtube Videoları ",
                        newestVideos,
                        true
                    ))
                }

                // Oynatma listelerini al
                val playlistsUrl = "$mainUrl/channel/${channel.id}/playlists"
                val playlistsDocument = app.get(playlistsUrl).document
                val playlists = playlistsDocument.select(".pure-u-1.pure-u-md-1-4").mapNotNull { playlistElement ->
                    val titleElement = playlistElement.selectFirst(".video-card-row a p")
                    val title = titleElement?.text() ?: return@mapNotNull null

                    val playlistUrl = playlistElement.selectFirst(".thumbnail a")?.attr("href") ?: return@mapNotNull null
                    val thumbnailUrl = playlistElement.selectFirst(".thumbnail img")?.attr("src")

                    newMovieSearchResponse(
                        title,
                        "$mainUrl$playlistUrl",
                        TvType.Podcast
                    ) {
                        this.posterUrl = if (thumbnailUrl != null) "$mainUrl$thumbnailUrl" else null
                        this.posterHeaders = mapOf()
                        this.quality = SearchQuality.HD
                    }
                }

                if (playlists.isNotEmpty()) {
                    allContent.add(HomePageList(
                        "${channel.name} Youtube Oynatma Listeleri",
                        playlists,
                        true
                    ))
                }
            } catch (e: Exception) {
                android.util.Log.e("YTEglence", "Error loading channel ${channel.name}: ${e.message}")
            }
        }

        // Tüm canlı yayınları tek bir kategoride göster
        if (allLiveStreams.isNotEmpty()) {
            allContent.add(0, HomePageList(
                "🔴 Canlı Yayınlanan İçerikler",
                allLiveStreams,
                true
            ))
        }

        return newHomePageResponse(allContent, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val res = tryParseJson<List<SearchEntry>>(
            app.get("$mainUrl/api/v1/search?q=${query.encodeUri()}&page=1&type=video&fields=videoId,title").text
        )
        return res?.map { it.toSearchResponse(this) } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        // Oynatma listesi URL'si kontrolü
        if (url.contains("/playlist")) {
            val playlistId = Regex("playlist\\?list=([a-zA-Z0-9_-]+)").find(url)?.groups?.get(1)?.value
            val playlistInfo = app.get("$mainUrl/api/v1/playlists/$playlistId?fields=title,description,videos").text
            val playlistData = tryParseJson<PlaylistEntry>(playlistInfo)

            if (playlistData != null) {
                val episodes = playlistData.videos.mapIndexed { index, video ->
                    Episode(
                        data = video.videoId,
                        name = video.title,
                        season = null,
                        episode = index + 1,
                        posterUrl = "$mainUrl/vi/${video.videoId}/maxres.jpg",
                        rating = null
                    )
                }

                return newTvSeriesLoadResponse(
                    playlistData.title,
                    url,
                    TvType.Podcast,
                    episodes
                ) {
                    this.plot = playlistData.description
                    this.posterUrl = episodes.firstOrNull()?.posterUrl
                }
            }
            return null
        }

        // Normal video URL'si işleme
        val videoId = Regex("watch\\?v=([a-zA-Z0-9_-]+)").find(url)?.groups?.get(1)?.value
        val res = tryParseJson<VideoEntry>(
            app.get("$mainUrl/api/v1/videos/$videoId?fields=videoId,title,description,recommendedVideos,author,authorThumbnails,formatStreams,lengthSeconds,viewCount,publishedText").text
        )
        return res?.toLoadResponse(this)
    }

    private data class SearchEntry(
        val title: String,
        val videoId: String,
        val lengthSeconds: Int,
        val viewCount: Int,
        val publishedText: String,
        val author: String,
        val authorId: String,
        val videoThumbnails: List<Thumbnail>
    ) {
        fun toSearchResponse(provider: YTEglence): SearchResponse {
            android.util.Log.d("YTEglence", "Video dönüştürülüyor - başlık: $title, id: $videoId")
            return provider.newMovieSearchResponse(
                title,
                "${provider.mainUrl}/watch?v=$videoId",
                TvType.Podcast
            ) {
                this.posterUrl = videoThumbnails.firstOrNull()?.let { "${provider.mainUrl}${it.url}" } ?: "${provider.mainUrl}/vi/$videoId/maxres.jpg"
                this.posterHeaders = mapOf()
                this.quality = SearchQuality.HD
            }
        }
    }

    private data class VideoEntry(
        val title: String,
        val description: String,
        val videoId: String,
        val recommendedVideos: List<SearchEntry>,
        val author: String,
        val authorThumbnails: List<Thumbnail>,
        val lengthSeconds: Int = 0,
        val viewCount: Int = 0,
        val publishedText: String = "",
        val likeCount: Int = 0,
        val genre: String = "",
        val license: String = ""
    ) {
        suspend fun toLoadResponse(provider: YTEglence): LoadResponse {
            return provider.newMovieLoadResponse(
                title,
                "${provider.mainUrl}/watch?v=$videoId",
                TvType.Podcast,
                videoId
            ) {
                val duration = if (lengthSeconds > 0) {
                    val hours = lengthSeconds / 3600
                    val minutes = (lengthSeconds % 3600) / 60
                    val seconds = lengthSeconds % 60
                    if (hours > 0) {
                        String.format("%d:%02d:%02d", hours, minutes, seconds)
                    } else {
                        String.format("%02d:%02d", minutes, seconds)
                    }
                } else ""

                val views = if (viewCount > 0) {
                    when {
                        viewCount >= 1_000_000 -> String.format("%.1fM görüntülenme", viewCount / 1_000_000.0)
                        viewCount >= 1_000 -> String.format("%.1fB görüntülenme", viewCount / 1_000.0)
                        else -> "Görüntülenme: $viewCount"
                    }
                } else ""

                fun convertPublishedText(publishedText: String): String {
                    val today = LocalDate.now() // Mevcut tarihi al

                    // Regex ile tüm zaman ifadelerini tek seferde kontrol et
                    val match = Regex("(\\d+) (days?|weeks?|months?|years?) ago").find(publishedText)
                    match?.let {
                        val amount = it.groupValues[1].toInt() // Süre miktarı (örn: "3")
                        val unit = it.groupValues[2] // Süre birimi (örn: "weeks" veya "month")

                        // Süre birimine göre tarihten eksiltme yap
                        val actualDate = when {
                            unit.startsWith("day") -> today.minusDays(amount.toLong())
                            unit.startsWith("week") -> today.minusWeeks(amount.toLong())
                            unit.startsWith("month") -> today.minusMonths(amount.toLong())
                            unit.startsWith("year") -> today.minusYears(amount.toLong())
                            else -> today
                        }

                        // Formatlı tarih olarak döndür
                        return actualDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                    }

                    // Eğer tarih zaten "19 Apr 2025" gibi net bir formatta geliyorsa, direkt çevir
                    return publishedText.split(" ").let { parts ->
                        if (parts.size >= 3) {
                            val turkishMonths = mapOf(
                                "Jan" to "Ocak", "Feb" to "Şubat", "Mar" to "Mart", "Apr" to "Nisan",
                                "May" to "Mayıs", "Jun" to "Haziran", "Jul" to "Temmuz", "Aug" to "Ağustos",
                                "Sep" to "Eylül", "Oct" to "Ekim", "Nov" to "Kasım", "Dec" to "Aralık"
                            )
                            val day = parts[0]
                            val month = turkishMonths[parts[1]] ?: parts[1]
                            val year = parts[2]
                            "$day $month $year"
                        } else publishedText
                    }
                }

                val turkishDate = convertPublishedText(publishedText)


                val detailText = buildString {
                    if (publishedText.isNotEmpty()) append("📅 <b>Yayınlanma Tarihi:</b> $turkishDate<br>")
                    if (duration.isNotEmpty()) append("⏱️ <b>Video Süresi:</b> $duration<br>")
                    if (views.isNotEmpty()) append("👁️ <b>$views</b> <br>")
                    if (likeCount > 0) append("👍 <b>Beğeni:</b> $likeCount<br>")
                    if (genre.isNotEmpty()) append("🎬 <b>Tür:</b> $genre<br>")
                    if (license.isNotEmpty()) append("📜 <b>Lisans:</b> $license<br>")
                    append("<br>")
                }

                this.plot = buildString {
                    append(description)
                    if (detailText.isNotEmpty()) {
                        append("<br><br>")
                        append(detailText)
                    }
                }
                this.posterUrl = "${provider.mainUrl}/vi/$videoId/maxres.jpg"
                this.recommendations = recommendedVideos.map { video ->
                    provider.newMovieSearchResponse(
                        video.title,
                        "${provider.mainUrl}/watch?v=${video.videoId}",
                        TvType.Podcast
                    ) {
                        this.posterUrl = video.videoThumbnails.firstOrNull()?.url?.let { if (it.startsWith("/")) "${provider.mainUrl}$it" else it } ?: "${provider.mainUrl}/vi/${video.videoId}/maxres.jpg"
                        this.posterHeaders = mapOf()
                this.quality = SearchQuality.HD
                    }
                }
                this.actors = listOf(
                    ActorData(
                        Actor(author, authorThumbnails.lastOrNull()?.url ?: ""),
                        roleString = "Kanal Sahibi"
                    )
                )
            }
        }
    }


    private data class Thumbnail(
        val url: String
    )

    private data class PlaylistEntry(
        val title: String,
        val description: String,
        val videos: List<PlaylistVideo>
    )

    private data class PlaylistVideo(
        val title: String,
        val videoId: String
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val videoId = data
            val videoDocument = app.get("$mainUrl/watch?v=$videoId").document
            val hlsUrl = videoDocument.select("source[type='application/x-mpegURL']").firstOrNull()?.attr("src")
            if (!hlsUrl.isNullOrEmpty()) {
                callback.invoke(
                    ExtractorLink(
                        source = this.name,
                        name = "${this.name} (HLS)",
                        url = "$mainUrl$hlsUrl",
                        referer = mainUrl,
                        quality = Qualities.Unknown.value,
                        type = ExtractorLinkType.M3U8
                    )
                )
                return true
            }
            val videoInfo = app.get("$mainUrl/api/v1/videos/$videoId").text
            val videoData = tryParseJson<VideoEntry>(videoInfo)
            if (videoData != null) {
                callback.invoke(
                    ExtractorLink(
                        source = this.name,
                        name = "${name} (DASH)",
                        url = "$mainUrl/api/manifest/dash/id/$videoId",
                        referer = mainUrl,
                        quality = Qualities.P1080.value,
                        type = ExtractorLinkType.DASH
                    )
                )
                return true
            }
            return false
        } catch (e: Exception) {
            android.util.Log.e("YTEglence", "loadLinks error: ${e.message}", e)
            return false
        }
    }
}
