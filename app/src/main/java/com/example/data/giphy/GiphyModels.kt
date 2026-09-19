package com.example.data.giphy

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GiphyResponse(
    @Json(name = "data") val data: List<GiphyItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GiphyItem(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "images") val images: GiphyImages
)

@JsonClass(generateAdapter = true)
data class GiphyImages(
    @Json(name = "fixed_height") val fixedHeight: GiphyImageSize?,
    @Json(name = "original") val original: GiphyImageSize?
)

@JsonClass(generateAdapter = true)
data class GiphyImageSize(
    @Json(name = "url") val url: String?
)
