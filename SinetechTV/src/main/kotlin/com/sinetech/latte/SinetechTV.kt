package com.sinetech.latte

import android.content.SharedPreferences
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities

class SinetechTV(
    private val enabledPlaylists: List<String>,
    override var lang: String,
    private val sharedPref: SharedPreferences?
) : MainAPI() {
    companion object {
        private const val ENABLED_PLAYLISTS_KEY = "enabled_playlists"
    }
    override var mainUrl =
        "https://raw.githubusercontent.com/GitLatte/patr0n/refs/heads/site/lists/"
    override var name = "SinetechTV"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override var sequentialMainPage = true
    override val supportedTypes = setOf(TvType.Live)
    private var playlists = mutableMapOf<String, Playlist?>()
    private val urlList: List<String>
        get() {
            val savedPlaylists = if (enabledPlaylists.isNotEmpty()) {
                sharedPref?.edit()?.apply {
                    putString(ENABLED_PLAYLISTS_KEY, enabledPlaylists.toJson())
                    apply()
                }
                enabledPlaylists
            } else {
                sharedPref?.getString(ENABLED_PLAYLISTS_KEY, null)?.let {
                    try {
                        parseJson<List<String>>(it)
                    } catch (e: Exception) {
                        emptyList()
                    }
                } ?: emptyList()
            }
            
            return savedPlaylists.map { "$mainUrl/$it" }
        }


    private suspend fun getTVChannels(url: String): List<TVChannel> {
        if (playlists[url] == null) {
            playlists[url] = IptvPlaylistParser().parseM3U(app.get(url).text)
        }
        return playlists[url]!!.items
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val currentPlaylists = urlList
        if (currentPlaylists.isEmpty()) {
            return newHomePageResponse( HomePageList(
                "Lütfen eklenti ayarlarından en az bir playlist seçin",
                emptyList(),
                isHorizontalImages = true
            ), false)
        }
        val sections = currentPlaylists.map {
            val data = getTVChannels(it)
            val sectionTitle = it.substringAfter("playlist_", "").replace(".m3u", "").trim().capitalize()
            val show = data.map { showData ->
                sharedPref?.edit()?.apply {
                    putString(showData.url, showData.toJson())
                    apply()
                }
                showData.toSearchResponse(apiName = this@SinetechTV.name)
            }
            HomePageList(
                sectionTitle,
                show,
                isHorizontalImages = true
            )
        }
        return newHomePageResponse( sections, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponses = urlList.map { url ->
            val data = getTVChannels(url)
            data.filter {
                it.attributes["tvg-id"]?.contains(query) ?: false ||
                        it.title?.lowercase()?.contains(query.lowercase()) ?: false
            }.map { it.toSearchResponse(apiName = this@SinetechTV.name) }
        }.flatten()
        return searchResponses
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query)
    }

    override suspend fun load(url: String): LoadResponse {
        val tvChannel = sharedPref?.getString(url, null)?.let { parseJson<TVChannel>(it) }
            ?: throw ErrorLoadingException("Önbellekten kanal yüklenirken hata oluştu")

        val streamUrl = tvChannel.url.toString()
        val channelName = tvChannel.title ?: tvChannel.attributes["tvg-id"].toString()
        val posterUrl = tvChannel.attributes["tvg-logo"].toString()

        return LiveStreamLoadResponse(
            channelName,
            streamUrl,
            this.name,
            url,
            posterUrl
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        callback.invoke(
            ExtractorLink(
                "Sinetech TV",
                "Sinetech TV",
                data,
                "",
                Qualities.Unknown.value,
                isM3u8 = true,
                headers = emptyMap()
            )
        )
        return true
    }
}

data class Playlist(
    val items: List<TVChannel> = emptyList(),
)

data class TVChannel(
    val title: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val url: String? = null,
    val userAgent: String? = null,
) {
    fun toSearchResponse(apiName: String): SearchResponse {
        val streamUrl = url.toString()
        val channelName = title ?: attributes["tvg-id"].toString()
        val posterUrl = attributes["tvg-logo"].toString()
        return LiveSearchResponse(
            channelName,
            streamUrl,
            apiName,
            TvType.Live,
            posterUrl,
        )
    }
}