package com.supakeyboard.neuralkey.ime.media

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.LruCache
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

@Immutable
enum class MediaTabCategory(val label: String, val emoji: String) {
    GIFS("GIFs", "🎬"),
    STICKERS("Stickers", "✨"),
    OPEN_STICKERS("Noto Open", "😎")
}

@Immutable
data class GifStickerItem(
    val id: String,
    val title: String,
    val previewUrl: String,
    val fullUrl: String,
    val mimeType: String = "image/gif",
    val source: String = "GIPHY",
    val isSticker: Boolean = false,
    val tags: String = ""
)

enum class MediaCommitResult {
    COMMITTED_RICH_MEDIA,
    INSERTED_LINK_FALLBACK,
    FAILED
}

/**
 * Federated Internet-Wide GIF & Sticker Search Engine.
 * Searches across GIPHY Global GIFs & Transparent Stickers, Wikimedia Commons Open Media,
 * and Google's Open-Source Apache-2.0 Noto Animated Emoji Stickers.
 */
class GifStickerRepository(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val cacheDir = File(context.applicationContext.cacheDir, "gif_sticker_cache").apply {
        if (!exists()) mkdirs()
    }

    // 12 MB in-memory byte cache for instant 60fps grid scrolling and 0ms tap-to-send
    private val memoryCache = object : LruCache<String, ByteArray>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    suspend fun searchMedia(
        query: String,
        category: MediaTabCategory
    ): List<GifStickerItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        when (category) {
            MediaTabCategory.GIFS -> coroutineScope {
                val giphyDeferred = async { fetchGiphyMedia(cleanQuery, isSticker = false) }
                val wikiDeferred = async {
                    if (cleanQuery.isNotEmpty()) fetchWikimediaAnimatedGifs(cleanQuery) else emptyList()
                }
                val giphyItems = giphyDeferred.await()
                val wikiItems = wikiDeferred.await()
                val combined = (giphyItems + wikiItems).distinctBy { it.fullUrl }
                combined.ifEmpty { filterNotoAnimatedStickers(cleanQuery) }
            }

            MediaTabCategory.STICKERS -> coroutineScope {
                val giphyDeferred = async { fetchGiphyMedia(cleanQuery, isSticker = true) }
                val notoItems = filterNotoAnimatedStickers(cleanQuery)
                val giphyStickers = giphyDeferred.await()
                (giphyStickers + notoItems).distinctBy { it.fullUrl }
            }

            MediaTabCategory.OPEN_STICKERS -> coroutineScope {
                val notoItems = filterNotoAnimatedStickers(cleanQuery)
                val wikiDeferred = async {
                    if (cleanQuery.isNotEmpty()) fetchWikimediaAnimatedGifs(cleanQuery) else emptyList()
                }
                (notoItems + wikiDeferred.await()).distinctBy { it.fullUrl }
            }
        }
    }

    internal fun parseGiphyResponse(rawJson: String, isSticker: Boolean): List<GifStickerItem> {
        return runCatching {
            val root = json.parseToJsonElement(rawJson).jsonObject
            val dataArray = root["data"]?.jsonArray ?: return emptyList()
            dataArray.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val rawTitle = obj["title"]?.jsonPrimitive?.content?.trim().orEmpty()
                val title = rawTitle
                    .replace(Regex("\\s*GIF\\s*by.*$", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\s*Sticker\\s*by.*$", RegexOption.IGNORE_CASE), "")
                    .ifBlank { if (isSticker) "Sticker" else "GIF" }

                val images = obj["images"]?.jsonObject ?: return@mapNotNull null
                val previewUrl = extractImageUrl(images, "fixed_height_small")
                    ?: extractImageUrl(images, "fixed_width_small")
                    ?: extractImageUrl(images, "downsized_small")
                    ?: extractImageUrl(images, "fixed_height")
                    ?: return@mapNotNull null

                val fullUrl = extractImageUrl(images, "fixed_height")
                    ?: extractImageUrl(images, "downsized")
                    ?: extractImageUrl(images, "original")
                    ?: previewUrl

                GifStickerItem(
                    id = "giphy_$id",
                    title = title,
                    previewUrl = previewUrl,
                    fullUrl = fullUrl,
                    mimeType = "image/gif",
                    source = "GIPHY",
                    isSticker = isSticker
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun extractImageUrl(images: JsonObject, key: String): String? {
        val url = images[key]?.jsonObject?.get("url")?.jsonPrimitive?.content?.trim()
        return url?.takeIf { it.startsWith("https://") }
    }

    private fun fetchGiphyMedia(query: String, isSticker: Boolean): List<GifStickerItem> {
        val typeSegment = if (isSticker) "stickers" else "gifs"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        for (apiKey in PUBLIC_GIPHY_KEYS) {
            val url = if (query.isBlank()) {
                "https://api.giphy.com/v1/$typeSegment/trending?api_key=$apiKey&limit=30&rating=pg-13"
            } else {
                "https://api.giphy.com/v1/$typeSegment/search?api_key=$apiKey&q=$encodedQuery&limit=30&rating=pg-13&lang=en"
            }

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val items = runCatching {
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    val body = resp.body?.string().orEmpty()
                    parseGiphyResponse(body, isSticker)
                }
            }.getOrDefault(emptyList())

            if (items.isNotEmpty()) {
                return items
            }
        }
        return emptyList()
    }

    internal fun parseWikimediaResponse(rawJson: String): List<GifStickerItem> {
        return runCatching {
            val root = json.parseToJsonElement(rawJson).jsonObject
            val pages = root["query"]?.jsonObject?.get("pages")?.jsonObject ?: return emptyList()
            pages.values.mapNotNull { pageElem ->
                val page = pageElem.jsonObject
                val pageId = page["pageid"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val rawTitle = page["title"]?.jsonPrimitive?.content
                    ?.removePrefix("File:")
                    ?.substringBeforeLast('.')
                    ?.replace('_', ' ')
                    ?.trim()
                    .orEmpty()
                val info = page["imageinfo"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@mapNotNull null
                val url = info["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val thumbUrl = info["thumburl"]?.jsonPrimitive?.content ?: url
                val mime = info["mime"]?.jsonPrimitive?.content ?: "image/gif"
                if (!url.startsWith("https://") || (!mime.contains("gif") && !mime.contains("webp") && !mime.contains("png"))) {
                    return@mapNotNull null
                }
                GifStickerItem(
                    id = "wiki_$pageId",
                    title = rawTitle.ifBlank { "Open GIF" },
                    previewUrl = thumbUrl,
                    fullUrl = url,
                    mimeType = mime,
                    source = "Wikimedia Open",
                    isSticker = mime.contains("png") || mime.contains("webp")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun fetchWikimediaAnimatedGifs(query: String): List<GifStickerItem> {
        val encoded = URLEncoder.encode("filetype:bitmap animated gif $query", "UTF-8")
        val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search" +
            "&gsrsearch=$encoded&gsrnamespace=6&gsrlimit=12&prop=imageinfo&iiprop=url|mime&iiurlwidth=220&format=json"

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val body = resp.body?.string().orEmpty()
                parseWikimediaResponse(body)
            }
        }.getOrDefault(emptyList())
    }

    fun filterNotoAnimatedStickers(query: String): List<GifStickerItem> {
        if (query.isBlank()) return NOTO_ANIMATED_STICKERS
        val lower = query.lowercase(Locale.ROOT).trim()
        val matched = NOTO_ANIMATED_STICKERS.filter {
            it.title.lowercase(Locale.ROOT).contains(lower) ||
                it.tags.lowercase(Locale.ROOT).contains(lower)
        }
        return matched.ifEmpty { NOTO_ANIMATED_STICKERS }
    }

    /**
     * Downloads or retrieves cached media bytes for rendering animated GIFs/Stickers in the keyboard.
     */
    suspend fun fetchMediaBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        memoryCache.get(url)?.let { return@withContext it }

        val diskFile = getDiskCacheFile(url)
        if (diskFile.exists() && diskFile.length() > 64) {
            val bytes = runCatching { diskFile.readBytes() }.getOrNull()
            if (bytes != null) {
                memoryCache.put(url, bytes)
                return@withContext bytes
            }
        }

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val bytes = resp.body?.bytes() ?: return@use null
                if (bytes.size > 64) {
                    memoryCache.put(url, bytes)
                    runCatching { diskFile.writeBytes(bytes) }
                    bytes
                } else null
            }
        }.getOrNull()
    }

    /**
     * Commits an animated GIF or Sticker directly into the host application's input field
     * via Android's `InputConnectionCompat.commitContent` + `FileProvider`, with automatic
     * clipboard + link fallback for plain-text fields.
     */
    suspend fun commitMediaToEditor(
        item: GifStickerItem,
        inputConnection: InputConnection?,
        editorInfo: EditorInfo?
    ): MediaCommitResult = withContext(Dispatchers.IO) {
        if (inputConnection == null || editorInfo == null) return@withContext MediaCommitResult.FAILED

        val bytes = fetchMediaBytes(item.fullUrl) ?: fetchMediaBytes(item.previewUrl)
        if (bytes != null) {
            val ext = when {
                item.mimeType.contains("webp") -> "webp"
                item.mimeType.contains("png") -> "png"
                else -> "gif"
            }
            val shareFile = File(cacheDir, "share_${hashUrl(item.fullUrl)}.$ext")
            runCatching { shareFile.writeBytes(bytes) }

            val authority = "${context.packageName}.fileprovider"
            val contentUri = runCatching {
                FileProvider.getUriForFile(context, authority, shareFile)
            }.getOrNull()

            if (contentUri != null) {
                val acceptedMimes = EditorInfoCompat.getContentMimeTypes(editorInfo)
                val supportsImageCommit = acceptedMimes.any { mime ->
                    ClipDescription.compareMimeTypes(mime, item.mimeType) ||
                        ClipDescription.compareMimeTypes(mime, "image/gif") ||
                        ClipDescription.compareMimeTypes(mime, "image/*") ||
                        ClipDescription.compareMimeTypes(mime, "*/*")
                }

                // Always attempt InputConnectionCompat.commitContent first (many apps accept it even if mime list is broad)
                val committed = withContext(Dispatchers.Main) {
                    runCatching {
                        val description = ClipDescription(item.title, arrayOf(item.mimeType))
                        val contentInfo = InputContentInfoCompat(
                            contentUri,
                            description,
                            Uri.parse(item.fullUrl)
                        )
                        val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                        InputConnectionCompat.commitContent(
                            inputConnection,
                            editorInfo,
                            contentInfo,
                            flags,
                            null
                        )
                    }.getOrDefault(false)
                }

                if (committed || supportsImageCommit) {
                    return@withContext MediaCommitResult.COMMITTED_RICH_MEDIA
                }

                // Fallback for plain text editors: copy rich URI + link to clipboard and commit URL
                withContext(Dispatchers.Main) {
                    runCatching {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newUri(context.contentResolver, item.title, contentUri))
                    }
                    runCatching {
                        inputConnection.commitText("${item.fullUrl} ", 1)
                    }
                }
                return@withContext MediaCommitResult.INSERTED_LINK_FALLBACK
            }
        }

        // Last-resort fallback if download failed: commit direct URL
        withContext(Dispatchers.Main) {
            runCatching {
                inputConnection.commitText("${item.fullUrl} ", 1)
            }
        }
        MediaCommitResult.INSERTED_LINK_FALLBACK
    }

    private fun getDiskCacheFile(url: String): File {
        return File(cacheDir, "media_${hashUrl(url)}.gif")
    }

    private fun hashUrl(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        return digest.take(10).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val USER_AGENT = "NeuralKey-AI-Keyboard/1.3 (https://github.com/rzrmayur/NeuralKey-AI-Keyboard)"

        private val PUBLIC_GIPHY_KEYS = listOf(
            "GlVGYHkr3WSBnllca54iNt0yFbjz7L65",
            "sXpGFDGZs0Dv1mmNFvYaGUvYwKX0PWIh",
            "dc6zaTOxFJmzC"
        )

        val QUICK_REACTION_CHIPS = listOf(
            "🔥 Trending" to "",
            "😂 Laugh" to "laughing funny",
            "❤️ Love" to "love heart",
            "🥳 Party" to "celebrate party",
            "👍 Thumbs Up" to "thumbs up yes",
            "🤯 Mind Blown" to "mind blown shocked",
            "🐱 Cats" to "cute cat",
            "🐶 Dogs" to "happy dog",
            "💃 Dance" to "happy dance vibe",
            "😭 Crying" to "crying sad",
            "👏 Applause" to "applause clapping",
            "🙏 Thank You" to "thank you",
            "😎 Deal With It" to "cool sunglasses",
            "🫡 Salute" to "salute respect",
            "🍿 Popcorn" to "popcorn drama",
            "🚀 LFG" to "rocket hype"
        )

        private fun notoSticker(codepoint: String, title: String, tags: String): GifStickerItem {
            val url = "https://fonts.gstatic.com/s/e/notoemoji/latest/$codepoint/512.gif"
            return GifStickerItem(
                id = "noto_$codepoint",
                title = title,
                previewUrl = url,
                fullUrl = url,
                mimeType = "image/gif",
                source = "Google Noto (Apache 2.0)",
                isSticker = true,
                tags = tags
            )
        }

        /**
         * Curated library of Google Open-Source Noto Animated Emoji Stickers (Apache 2.0 License).
         * Works 100% keylessly and provides crisp 512x512 animated stickers.
         */
        val NOTO_ANIMATED_STICKERS: List<GifStickerItem> = listOf(
            notoSticker("1f525", "🔥 Fire", "fire hot lit trending hype"),
            notoSticker("1f602", "😂 Tears of Joy", "laughing lol funny haha"),
            notoSticker("1f923", "🤣 ROFL", "rofl laughing hilarious funny"),
            notoSticker("2764_fe0f", "❤️ Red Heart", "love heart romance"),
            notoSticker("1f973", "🥳 Partying Face", "party celebrate birthday congrats"),
            notoSticker("1f92f", "🤯 Mind Blown", "mind blown shocked exploding head wow"),
            notoSticker("1f44d", "👍 Thumbs Up", "thumbs up yes ok approve"),
            notoSticker("1f44f", "👏 Clapping", "clap applause bravo congrats"),
            notoSticker("1f680", "🚀 Rocket", "rocket launch lfg hype space"),
            notoSticker("1f62d", "😭 Loudly Crying", "crying sad sobbing tears"),
            notoSticker("1f60e", "😎 Smiling Sunglasses", "cool deal with it boss chill"),
            notoSticker("1fae1", "🫡 Saluting Face", "salute respect sir yes"),
            notoSticker("1f64f", "🙏 Folded Hands", "thank you please pray grateful"),
            notoSticker("2728", "✨ Sparkles", "sparkles magic shine stars"),
            notoSticker("1f4af", "💯 Hundred Points", "100 percent facts real"),
            notoSticker("1f389", "🎉 Party Popper", "tada celebrate congrats party"),
            notoSticker("1f970", "🥰 Smiling Hearts", "love adorable cute sweet"),
            notoSticker("1f60d", "😍 Heart Eyes", "love crush amazing wow"),
            notoSticker("1f914", "🤔 Thinking Face", "thinking hmm wonder question"),
            notoSticker("1f440", "👀 Eyes", "eyes looking dramatea watch"),
            notoSticker("1f451", "👑 Crown", "crown king queen royal winner"),
            notoSticker("26a1", "⚡ High Voltage", "zap lightning fast power energy"),
            notoSticker("1f916", "🤖 Robot", "robot ai tech cyber"),
            notoSticker("1f47b", "👻 Ghost", "ghost spooky boo funny"),
            notoSticker("1f431", "🐱 Cat Face", "cat kitty meow cute"),
            notoSticker("1f436", "🐶 Dog Face", "dog puppy woof cute"),
            notoSticker("1f984", "🦄 Unicorn", "unicorn magic rainbow"),
            notoSticker("1f37f", "🍿 Popcorn", "popcorn movie drama watching")
        )

        @Volatile
        private var instance: GifStickerRepository? = null

        fun getInstance(context: Context): GifStickerRepository {
            return instance ?: synchronized(this) {
                instance ?: GifStickerRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
