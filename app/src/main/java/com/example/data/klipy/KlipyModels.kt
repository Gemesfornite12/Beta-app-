package com.example.data.klipy

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class KlipyResponse(
    @Json(name = "result") val result: Boolean? = true,
    @Json(name = "data") val data: List<KlipyItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class KlipyItem(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String?,
    @Json(name = "files") val files: KlipyFiles?,
    @Json(name = "file") val file: KlipyFiles?
) {
    fun getBestUrl(): String {
        val f = files ?: file ?: return ""
        return f.hd?.gif?.url 
            ?: f.hd?.webp?.url 
            ?: f.preview?.gif?.url 
            ?: f.medium?.gif?.url 
            ?: f.tiny?.gif?.url 
            ?: ""
    }
}

@JsonClass(generateAdapter = true)
data class KlipyFiles(
    @Json(name = "hd") val hd: KlipyFormatGroup?,
    @Json(name = "preview") val preview: KlipyFormatGroup?,
    @Json(name = "medium") val medium: KlipyFormatGroup?,
    @Json(name = "tiny") val tiny: KlipyFormatGroup?
)

@JsonClass(generateAdapter = true)
data class KlipyFormatGroup(
    @Json(name = "gif") val gif: KlipyMediaFormat?,
    @Json(name = "webp") val webp: KlipyMediaFormat?
)

@JsonClass(generateAdapter = true)
data class KlipyMediaFormat(
    @Json(name = "url") val url: String?
)
