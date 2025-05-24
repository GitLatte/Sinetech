package com.sinetech.latte

import android.util.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URI
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class GujanExtractor : ExtractorApi() {
    override val name = "Gujan"
    // mainUrl muhtemelen iframe içine gönderilen URL'ler için kullanılmayacak,
    // ama yine de tanımlı kalabilir.
    override val mainUrl = "https://gujan.premiumvideo.click"
    override val requiresReferer = true

    // DiziFun.kt'deki hexToString ile aynı olmalı (eğer ayrı bir dosya olarak kullanılacaksa)
    private fun hexToString(hex: String): String {
        return try {
            hex.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
                .toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(name, "Hex decode error for: $hex", e)
            ""
        }
    }

    override suspend fun getUrl(
        url: String, // iframe URL'si (örneğin //playhouse.premiumvideo.click/player/...)
        referer: String?, // DiziFun sayfasının URL'si
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d(name, "getUrl çağrıldı: url=$url, referer=$referer")

        val embedPageSource = try {
            app.get(url, referer = referer).text
        } catch (e: Exception) {
            Log.e(name, "Embed sayfası alınamadı: $url", e)
            return
        }

        val baseUri = try { URI(url) } catch (e: Exception) {
            Log.e(name, "Geçersiz URI: $url", e)
            return
        }
        var successful = false

        // Gelen iframe URL'sine göre görünen adı ve player tipini belirle
        val (displayName, playerType) = when {
            url.contains("/player/") -> "PlayHouse" to "house"
            url.contains("/armony/") -> "PlayAmony" to "amony"
            url.contains("gujan.") -> "Gujan" to "gujan"
            else -> "Bilinmeyen Oynatıcı" to "unknown"
        }

        if (playerType == "unknown") {
             Log.e(name, "Bilinmeyen oynatıcı tipi, link çıkarılamıyor: $url")
             return
        }

        // --- JW Player Mantığını Dene ---
        try {
            Log.d(name, "JW Player ayrıştırıcı deneniyor for: $url ($displayName)")
            val m3u8Pattern = Regex("""file:\s*['"]([^'"]+\.m3u8)['"]""")
            val relativeM3u8Path = m3u8Pattern.find(embedPageSource)?.groups?.get(1)?.value

            if (!relativeM3u8Path.isNullOrBlank()) {
                val fullM3u8Url = try { baseUri.resolve(relativeM3u8Path).toString() } catch (e: Exception) { null }
                if (fullM3u8Url != null) {
                    Log.i(name, "[JW Player] Bulunan M3U8 URL ($displayName): $fullM3u8Url")

                    // M3U8 URL'sine player tipini ekle
                    val finalUrlForCallback = "$fullM3u8Url?player=$playerType#ignored"
                    Log.i(name, "[JW Player] Nihai Oynatma URL ($displayName): $finalUrlForCallback")

                    callback.invoke(
                          newExtractorLink(
                            source    = this.name,
                            name      = displayName,
                            url       = finalUrlForCallback, // Düzeltilmiş URL
                            type      = ExtractorLinkType.M3u8
                        ) { // newExtractorLink lambda
                            this.quality = Qualities.Unknown.value
                            this.referer = url // iframe URL'si referer olarak
                          }
                       )
                    successful = true

                    // JW Player Altyazıları (newExtractorLink lambdasının dışında kalmalı)
                    val tracksPattern = Regex("""tracks:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
                    val trackEntryPattern = Regex("""\{\s*file:\s*['"]([^'"]+)['"],\s*label:\s*['"]([^'"]+)['"]""")
                    tracksPattern.find(embedPageSource)?.groups?.get(1)?.value?.let { tracksBlock ->
                        trackEntryPattern.findAll(tracksBlock).forEach { match ->
                            val subRelativePath = match.groups[1]?.value
                            val subLabel = match.groups[2]?.value
                            val subLang = subLabel?.let {
                                when { it.contains("Türkçe", true) -> "tr"; it.contains("English", true) -> "en"; else -> "und" }
                            } ?: "und"
                            if (!subRelativePath.isNullOrBlank() && subLabel != null) {
                                try { baseUri.resolve(subRelativePath).toString() } catch (e: Exception) { null }?.let { fullSubUrl ->
                                    Log.d(name, "[JW Player] Altyazı Bulundu: $subLabel ($subLang) - $fullSubUrl")
                                    subtitleCallback.invoke(SubtitleFile(subLabel, fullSubUrl))
                                }
                            }
                        }
                    }
                } else {
                    Log.w(name, "[JW Player] M3U8 URL oluşturulamadı: $url ($displayName)")
                }
            } else {
                Log.d(name, "[JW Player] Script içinde M3U8 linki bulunamadı: $url ($displayName)")
            }
        } catch (e: Exception) {
            Log.e(name, "[JW Player] Ayrıştırma hatası: $url ($displayName)", e)
        }

        // --- JW Player başarısız olduysa Video.js Mantığını Dene ---
        if (!successful) {
            try {
                Log.d(name, "Video.js ayrıştırıcı deneniyor for: $url ($displayName)")
                val document = Jsoup.parse(embedPageSource)
                val sourceTag = document.selectFirst("video#my-video_html5_api > source[type='application/x-mpegURL'], video > source[src*=.m3u8]")
                val relativeM3u8Path = sourceTag?.attr("src")

                if (!relativeM3u8Path.isNullOrBlank()) {
                    val fullM3u8Url = try { baseUri.resolve(relativeM3u8Path).toString() } catch (e: Exception) { null }
                    if (fullM3u8Url != null) {
                        Log.i(name, "[Video.js] Bulunan M3U8 URL ($displayName): $fullM3u8Url")

                         // M3U8 URL'sine player tipini ekle
                        val finalUrlForCallback = "$fullM3u8Url?player=$playerType#ignored"
                        Log.i(name, "[Video.js] Nihai Oynatma URL ($displayName): $finalUrlForCallback")

                        callback.invoke(
                          newExtractorLink(
                            source    = this.name,
                            name      = displayName,
                            url       = finalUrlForCallback, // Düzeltilmiş URL
                            type      = ExtractorLinkType.M3u8
                        ) { // newExtractorLink lambda
                            this.quality = Qualities.Unknown.value
                            this.referer = url // iframe URL'si referer olarak
                          }
                       )
                        successful = true

                        // Video.js Altyazıları (newExtractorLink lambdasının dışında kalmalı)
                        val subtitlePattern = Regex("""player\.addRemoteTextTrack\(\s*\{\s*.*?src:\s*['"]([^'"]+)['"],\s*srclang:\s*['"]([^'"]+)['"],\s*label:\s*['"]([^'"]+)['"].*?\}\s*,\s*false\s*\)""", RegexOption.IGNORE_CASE)
                        subtitlePattern.findAll(embedPageSource).forEach { match ->
                            val subRelativePath = match.groups[1]?.value
                            val subLang = match.groups[2]?.value
                            val subLabel = match.groups[3]?.value
                            if (!subRelativePath.isNullOrBlank() && !subLang.isNullOrBlank()) {
                                try { baseUri.resolve(subRelativePath).toString() } catch (e: Exception) { null }?.let { fullSubUrl ->
                                    Log.d(name, "[Video.js] Altyazı Bulundu: $subLabel ($subLang) - $fullSubUrl")
                                    subtitleCallback.invoke(SubtitleFile(subLabel ?: subLang, fullSubUrl))
                                }
                            }
                        }
                    } else {
                        Log.w(name, "[Video.js] M3U8 URL oluşturulamadı: $url ($displayName)")
                    }
                } else {
                    Log.d(name, "[Video.js] M3U8 source etiketi bulunamadı: $url ($displayName)")
                }
            } catch (e: Exception) {
                Log.e(name, "[Video.js] Ayrıştırma hatası: $url ($displayName)", e)
            }
        }

        if (!successful) {
            Log.e(name, "Hiçbir yöntemle M3U8 linki bulunamadı: $url")
        }
    }
}