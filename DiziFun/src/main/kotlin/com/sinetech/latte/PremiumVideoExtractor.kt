package com.sinetech.latte

import android.util.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink // Bu import doğru kalacak
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URI
import org.jsoup.Jsoup // HTML ayrıştırma için
import org.jsoup.nodes.Document // Document tipi için

class PremiumVideoExtractor : ExtractorApi() {
    override val name = "Playhouse"
    // mainUrl muhtemelen iframe içine gönderilen URL'ler için kullanılmayacak,
    // ama yine de tanımlı kalabilir.
    override val mainUrl = "https://playhouse.premiumvideo.click"
    override val requiresReferer = true

    // Hex decode fonksiyonu (gerekiyorsa DiziFun.kt'den alınabilir veya buradan silinebilir)
    // private fun hexToString(...) { ... }

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

        // Gelen iframe URL'sine göre görünen adı ve player tipini belirle
        val (displayName, playerType) = when {
            url.contains("/armony/") -> "PlayAmony" to "amony"
            url.contains("/player/") -> "PlayHouse" to "house"
            else -> "Bilinmeyen Oynatıcı" to "unknown" // Varsayılan veya Gujan'a özgü olabilir
        }

        if (playerType == "unknown") {
            Log.e(name, "Bilinmeyen oynatıcı tipi, link çıkarılamıyor: $url")
            return
        }

        // === Video.js (/armony/) Mantığı (İlk sıraya alalım, daha belirgin) ===
        if (playerType == "amony") {
             Log.d(name, "Video.js ayrıştırıcı kullanılıyor for: $url ($displayName)")
             try {
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
                             name      = displayName, // "PlayAmony"
                             url       = finalUrlForCallback, // Düzeltilmiş URL
                             type      = ExtractorLinkType.M3U8
                         ) { // newExtractorLink lambda
                            this.quality = Qualities.Unknown.value
                            this.referer = url // iframe URL'si referer olarak
                         }
                        )

                         // Video.js Altyazıları (newExtractorLink lambdasının dışında kalmalı)
                         val subtitlePattern = Regex("""player\.addRemoteTextTrack\(\s*\{\s*.*?src:\s*['"]([^'"]+)['"],\s*srclang:\s*['"]([^'"]+)['"],\s*label:\s*['"]([^'"]+)['"].*?\}\s*,\s*false\s*\)""", RegexOption.IGNORE_CASE)
                         subtitlePattern.findAll(embedPageSource).forEach { match ->
                             val relativePath = match.groups[1]?.value
                             val lang = match.groups[2]?.value
                             val label = match.groups[3]?.value
                             if (!relativePath.isNullOrBlank() && !lang.isNullOrBlank()) {
                                 try { baseUri.resolve(relativePath).toString() } catch (e: Exception) { null }?.let { fullSubUrl ->
                                     Log.d(name, "[Video.js] Altyazı Bulundu: $label ($lang) - $fullSubUrl")
                                     subtitleCallback.invoke(SubtitleFile(label ?: lang, fullSubUrl))
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

        } else if (playerType == "house") {
            // === JW Player (/player/ veya varsayılan) Mantığı ===
            Log.d(name, "JW Player ayrıştırıcı kullanılıyor for: $url ($displayName)")
            try {
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
                                name      = displayName, // "PlayHouse"
                                url       = finalUrlForCallback, // Düzeltilmiş URL
                                type      = ExtractorLinkType.M3U8
                            ) { // newExtractorLink lambda
                                this.quality = Qualities.Unknown.value
                                this.referer = url // iframe URL'si referer olarak
                            }
                        )

                        // JW Player Altyazıları (newExtractorLink lambdasının dışında kalmalı)
                        val tracksPattern = Regex("""tracks:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
                        val trackEntryPattern = Regex("""\{\s*file:\s*['"]([^'"]+)['"],\s*label:\s*['"]([^'"]+)['"]""")

                        tracksPattern.find(embedPageSource)?.groups?.get(1)?.value?.let { tracksBlock ->
                            trackEntryPattern.findAll(tracksBlock).forEach { match ->
                                val relativePath = match.groups[1]?.value
                                val label = match.groups[2]?.value
                                val lang = label?.let {
                                     when {
                                        it.contains("Türkçe", ignoreCase = true) -> "tr"
                                        it.contains("English", ignoreCase = true) -> "en"
                                        else -> "und"
                                    }
                                } ?: "und"

                                if (!relativePath.isNullOrBlank() && label != null) {
                                    try { baseUri.resolve(relativePath).toString() } catch (e: Exception) { null }?.let { fullSubUrl ->
                                        Log.d(name, "[JW Player] Altyazı Bulundu: $label ($lang) - $fullSubUrl")
                                        subtitleCallback.invoke(SubtitleFile(label, fullSubUrl))
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
        } else {
            Log.w(name, "Belirlenen player tipi için ayrıştırıcı mantığı uygulanmadı: $playerType")
        }
    }
}