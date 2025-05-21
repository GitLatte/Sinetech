package com.sinetech.latte

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import java.net.URLEncoder // URL encode için
import kotlin.math.roundToInt // Sayı formatlama için

// --- API v2 Data Class'ları (load ve loadLinks için gerekli) ---
private data class KickUser(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("username") val username: String?,
    @JsonProperty("profile_pic") val profilePic: String?,
    @JsonProperty("followersCount") val followersCount: Int?,
    @JsonProperty("verified") val verified: Boolean?,
    @JsonProperty("bio") val bio: String?
)

private data class KickCategory(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("name") val name: String?,
    @JsonProperty("slug") val slug: String?
)

private data class KickLivestream(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("session_title") val sessionTitle: String?,
    @JsonProperty("is_live") val isLive: Boolean = false,
    @JsonProperty("viewer_count") val viewerCount: Int?,
    @JsonProperty("thumbnail_url") val thumbnailUrl: String?,
    @JsonProperty("categories") val categories: List<KickCategory>?
)

private data class KickChannelApiResponseV2(
    @JsonProperty("playback_url") val playbackUrl: String?,
    @JsonProperty("livestream") val livestream: KickLivestream?,
    @JsonProperty("user") val user: KickUser?,
    @JsonProperty("thumbnail") val thumbnail: String?
)

private data class KickStreamUser(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("username") val username: String?,
    @JsonProperty("bio") val bio: String?,
    @JsonProperty("profilepic") val profilepic: String?
)

private data class KickStreamChannel(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("slug") val slug: String?,
    @JsonProperty("user") val user: KickStreamUser?
)

private data class KickStreamThumbnail(
    @JsonProperty("src") val src: String?
)

private data class KickStreamItem(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("channel_id") val channelId: Int?,
    @JsonProperty("session_title") val sessionTitle: String?,
    @JsonProperty("is_live") val isLive: Boolean?,
    @JsonProperty("viewer_count") val viewerCount: Int?,
    @JsonProperty("language") val language: String?,
    @JsonProperty("thumbnail") val thumbnail: KickStreamThumbnail?,
    @JsonProperty("channel") val channel: KickStreamChannel?
)

private data class KickStreamApiResponse(
    @JsonProperty("current_page") val currentPage: Int?,
    @JsonProperty("data") val data: List<KickStreamItem>?,
    @JsonProperty("last_page") val lastPage: Int?
)


private data class AerokickSearchHit(
    @JsonProperty("Avatar") val avatar: String?,
    @JsonProperty("Followers") val followers: Int?,
    @JsonProperty("ID") val id: String?,
    @JsonProperty("Slug") val slug: String?,
    @JsonProperty("Username") val username: String?,
    @JsonProperty("Verified") val verified: Boolean?
)

private data class AerokickStreamersResult(
    @JsonProperty("hits") val hits: List<AerokickSearchHit>?,
    @JsonProperty("totalHits") val totalHits: Int?,
    @JsonProperty("hitsPerPage") val hitsPerPage: Int?,
    @JsonProperty("page") val page: Int?,
    @JsonProperty("totalPages") val totalPages: Int?
)

private data class AerokickSearchApiResponse(
    @JsonProperty("streamers") val streamers: AerokickStreamersResult?
)

class KickTR : MainAPI() {
    override var mainUrl = "https://kick.com"
    override var name = "Kick.com Canlı Yayınlar「🤳🏻」"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)

    private val cloudflareInterceptor = CloudflareKiller()

    private fun formatFollowers(count: Int?): String? {
        if (count == null) return null
        return when {
            count >= 1_000_000 -> "${(count / 100_000.0).roundToInt() / 10.0} Mn"
            count >= 1_000 -> "${(count / 100.0).roundToInt() / 10.0} B"
            else -> count.toString()
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val allLists = mutableListOf<HomePageList>()
        var mainPageHasNext = false

        try {
            val limit = 20
            val apiUrl = "$mainUrl/stream/livestreams/tr?page=$page&limit=$limit&sort=desc&strict=true"
            val response = app.get(apiUrl, interceptor = cloudflareInterceptor)
            val apiData = AppUtils.tryParseJson<KickStreamApiResponse>(response.text)
            val liveStreamsList = mutableListOf<SearchResponse>()
            var hasNextLive = false

            val results = apiData?.data
            if (results != null) {
                val currentPage = apiData.currentPage ?: page
                val lastPage = apiData.lastPage ?: (currentPage + if (results.isNotEmpty()) 1 else 0)
                hasNextLive = currentPage < lastPage
                mainPageHasNext = hasNextLive

                results.forEach { stream ->
                     val channelInfo = stream.channel
                     val channelSlug = channelInfo?.slug
                     val userInfo = channelInfo?.user
                     val streamerName = userInfo?.username ?: "Bilinmeyen"
                     val channelProfilePic = userInfo?.profilepic

                     val title = stream.sessionTitle ?: streamerName

                     val poster = stream.thumbnail?.src ?: channelProfilePic

                     if (channelSlug.isNullOrBlank()) {
                         return@forEach
                     }
                     val kickUrl = "$mainUrl/$channelSlug"
                     val displayName = if (stream.sessionTitle != null && streamerName != "Bilinmeyen") {
                         "$streamerName - ${stream.sessionTitle}"
                     } else {
                         stream.sessionTitle ?: streamerName
                     }

                     liveStreamsList.add(newMovieSearchResponse(displayName, kickUrl, TvType.Live) {
                         this.posterUrl = poster
                     })
                 }
            } else {
                 if (apiData == null) println("[Ana Sayfa - Canlı]: Ham yanıt (ilk 500): ${response.text.take(500)}")
            }
            allLists.add(HomePageList("🇹🇷͜͜͡͡✯ Şu anda Canlı Türk Yayıncılar", liveStreamsList))

        } catch (e: Exception) {
            e.printStackTrace()
            allLists.add(HomePageList("Türkçe Canlı Yayınlar (Hata)", emptyList()))
        }

        if (page == 1) {
            try {
                val aerokickUrl = "https://aerokick.app/stats/channels?page=1"
                val document = app.get(aerokickUrl, interceptor = cloudflareInterceptor).document
                val popularChannelsList = mutableListOf<SearchResponse>()
                val channelCards = document.select("a[href^=\"/stats/channels/\"]")

                channelCards.forEach { card ->
                    val aerokickHref = card.attr("href")
                    val channelName = aerokickHref.substringAfterLast('/')
                    if (channelName.isBlank()) return@forEach
                    val kickUrl = "$mainUrl/$channelName"
                    val posterElement = card.selectFirst("div.inset-0 img:not([class*='blur'])") ?: card.selectFirst("div.inset-0 img:last-of-type")
                    val posterUrl = posterElement?.attr("src")
                    val displayName = card.selectFirst("button")?.text()?.trim() ?: channelName

                    popularChannelsList.add(newMovieSearchResponse(displayName, kickUrl, TvType.Live) {
                        this.posterUrl = posterUrl
                    })
                }
                 allLists.add(HomePageList("🔥 Popüler Kanallar (Arama ile istediğinizi bulun)", popularChannelsList))

            } catch (e: Exception) {
                e.printStackTrace()
                println("[Ana Sayfa - Popüler]: Aerokick popüler kanallarını alma hatası.")
                allLists.add(HomePageList("Popüler Kanallar (Hata)", emptyList()))
            }
        }

        return newHomePageResponse(allLists, hasNext = mainPageHasNext)

    } // getMainPage sonu


    // !!! Aerokick ARAMA API'sini KULLANAN search() (Takipçi/Verified ile) !!!
    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchApiUrl = "https://api.aerokick.app/api/v1/stats/search?q=$encodedQuery"
        val searchResults = mutableListOf<SearchResponse>()
        try {
            val response = app.get(searchApiUrl, interceptor = cloudflareInterceptor)
            val apiData = AppUtils.tryParseJson<AerokickSearchApiResponse>(response.text)
            val results = apiData?.streamers?.hits
            if (results == null) {
                 return emptyList()
            } else {
                 results.forEach { item ->
                    val channelSlug = item.slug
                    var channelName = item.username
                    val poster = item.avatar
                    val followers = item.followers
                    val isVerified = item.verified == true

                    if (channelSlug.isNullOrBlank() || channelName.isNullOrBlank()) {
                         return@forEach
                    }

                    // İsim Formatlama
                    var displayName = channelName
                    if (isVerified) {
                        displayName += " ✅"
                    }
                    val formattedFollowers = formatFollowers(followers)
                    if (formattedFollowers != null) {
                        displayName += " ($formattedFollowers Takipçi)"
                    }

                    val kickUrl = "$mainUrl/$channelSlug"
                     searchResults.add(newMovieSearchResponse(
                        name = displayName,
                        url = kickUrl,
                        type = TvType.Live,
                    ) {
                        this.posterUrl = poster
                    })
                 }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
        return searchResults
    }


     override suspend fun load(url: String): LoadResponse? {
        val streamerName = url.substringAfterLast('/')
        if (streamerName.isBlank()) {
            return null
        }

        val apiUrl = "$mainUrl/api/v2/channels/$streamerName"
        var apiResponse: KickChannelApiResponseV2? = null
        try {
            val response = app.get(apiUrl, interceptor = cloudflareInterceptor)
            try {
                apiResponse = response.parsed()
            } catch (parseError: Exception) {
                 parseError.printStackTrace()
                 return null
            }

            val user = apiResponse?.user
            val livestream = apiResponse?.livestream

            if (user?.username == null) {
                 return null
            }

            var finalUsername = user.username
            val isUserVerified = user.verified == true
            if(isUserVerified) {
                finalUsername += " ✅"
            }
            val followersFormatted = formatFollowers(user.followersCount)

            if (livestream?.isLive == true) {
                 val title = livestream.sessionTitle ?: finalUsername
                 val poster = livestream.thumbnailUrl ?: apiResponse.thumbnail ?: user.profilePic
                 val category = livestream.categories?.firstOrNull()

                 return newMovieLoadResponse(
                     name = title,
                     url = url,
                     type = TvType.Live,
                     dataUrl = url,
                 ) {
                     this.posterUrl = poster
                     this.plot = buildString {
                         append("👤 Kick Yayıncısı: $finalUsername\n") // Doğrulanmış ismi kullan
                         if (followersFormatted != null) append("👥 Takipçi: $followersFormatted\n") // Takipçi
                         if (category?.name != null) append("🎮🎧 Yayın Kategorisi: ${category.name}\n")
                         append("👀 Anlık İzleyici: ${livestream.viewerCount ?: "Bilinmiyor"}")
                         val bio = user.bio // Bio alanı KickUser'da varsa
                         if (!bio.isNullOrBlank()) append("\n\n📜 Hakkında:\n$bio")
                     }.trim()
                     val tagsList = mutableListOf<String>()
                     if (category?.name != null) {
                         tagsList.add(category.name)
                     }
                     this.tags = tagsList
                 }
            } else {
                val offlineMessage = "🚦 Yayın Durumu: ⛔ Şu anda çevrimdışı.\nSadece canlı yayın varken izleyebilirsiniz."

                return newMovieLoadResponse(
                    name = finalUsername,
                    url = url,
                    type = TvType.Live,
                    dataUrl = url,
                ) {
                    this.posterUrl = user.profilePic ?: apiResponse.thumbnail
                    this.plot = buildString {
                         append("👤 Kick Yayıncısı: $finalUsername\n")
                         if (followersFormatted != null) append("👥 Takipçi: $followersFormatted\n")
                         append("\n$offlineMessage")
                         val bio = user.bio
                         if (!bio.isNullOrBlank()) append("\n\n📜 Hakkında:\n$bio")
                    }.trim()
                    this.tags = listOf("Çevrimdışı")
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val streamerName = data.substringAfterLast('/')
        if (streamerName.isBlank()) {
            return false
        }

        val apiUrl = "$mainUrl/api/v2/channels/$streamerName"
        var playbackUrl: String? = null

        try {
            val apiResponse = app.get(apiUrl, interceptor = cloudflareInterceptor).parsed<KickChannelApiResponseV2>()
            if (apiResponse.livestream?.isLive == true && !apiResponse.playbackUrl.isNullOrBlank()) {
                playbackUrl = apiResponse.playbackUrl
            } else {
                return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

        if (!playbackUrl.isNullOrBlank()) {
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = "Kick Canlı Yayın",
                    url = playbackUrl,
                    type = ExtractorLinkType.M3U8 
                ) {
                    this.quality = Qualities.Unknown.value 
                    this.referer = "$mainUrl/"
                }
            )
            return true
        }
        return false
    }
}