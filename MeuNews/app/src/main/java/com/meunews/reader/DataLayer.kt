package com.meunews.reader

import android.text.Html
import android.util.Xml
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URI
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

data class Feed(val title: String, val url: String)
data class Article(val title: String, val link: String, val summary: String, val image: String?, val source: String, val time: Long, val feed: String = "")

object Net {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS).build()
    fun get(url: String): String {
        val r = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Android) MeuNews").build()
        http.newCall(r).execute().use { return it.body?.string() ?: "" }
    }
}

fun plain(html: String) = Html.fromHtml(html, 0).toString().replace('\uFFFC', ' ').trim()

private val imgRe = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
private val dateFmts = listOf("EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, dd MMM yyyy HH:mm:ss Z", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

private fun parseDate(s: String): Long {
    for (f in dateFmts) try {
        return SimpleDateFormat(f, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s.trim())!!.time
    } catch (e: Exception) {}
    return 0L
}

private fun isImg(url: String?, type: String?, medium: String?) =
    !url.isNullOrBlank() && (medium == "image" || type?.startsWith("image") == true || Regex("\\.(jpe?g|png|webp|gif)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(url))

fun parseFeed(xml: String, fallback: String): List<Article> {
    val p = Xml.newPullParser(); p.setInput(StringReader(xml))
    val out = mutableListOf<Article>()
    var channel: String? = null; var inItem = false
    var t = ""; var l = ""; var s = ""; var img: String? = null; var d = 0L; var src: String? = null
    var ev = p.eventType
    while (ev != XmlPullParser.END_DOCUMENT) {
        if (ev == XmlPullParser.START_TAG) {
            val n = p.name
            if (n == "item" || n == "entry") { inItem = true; t = ""; l = ""; s = ""; img = null; d = 0; src = null }
            else if (!inItem && n == "title" && channel == null) channel = p.nextText().trim()
            else if (inItem) when (n) {
                "title" -> t = plain(p.nextText())
                "link" -> { val h = p.getAttributeValue(null, "href"); val x = p.nextText().trim(); if (l.isBlank()) l = if (!h.isNullOrBlank()) h else x }
                "description", "summary", "content:encoded", "content" -> {
                    val x = p.nextText()
                    if (n == "content:encoded" || s.isBlank()) s = x
                    if (img == null) img = imgRe.find(x)?.groupValues?.get(1)
                }
                "pubDate", "published", "updated", "dc:date" -> if (d == 0L) d = parseDate(p.nextText())
                "source" -> src = p.nextText().trim()
                "media:thumbnail" -> if (img == null) img = p.getAttributeValue(null, "url")
                "media:content", "enclosure" -> {
                    val u = p.getAttributeValue(null, "url")
                    if (isImg(u, p.getAttributeValue(null, "type"), p.getAttributeValue(null, "medium"))) img = u
                }
            }
        } else if (ev == XmlPullParser.END_TAG && (p.name == "item" || p.name == "entry")) {
            inItem = false
            if (t.isNotBlank() && l.isNotBlank()) out += Article(t, l, plain(s).take(400), img, src ?: channel ?: fallback, d)
        }
        ev = p.next()
    }
    return out
}

/** Aceita endereço de site OU de feed; descobre o RSS sozinho. */
fun resolveFeed(input: String): Feed {
    var u = input.trim(); if (!u.startsWith("http")) u = "https://$u"
    val body = Net.get(u)
    val host = URI(u).host ?: u
    if (Regex("<(rss|feed|rdf)", RegexOption.IGNORE_CASE).containsMatchIn(body.take(1500))) return Feed(host, u)
    val link = Regex("<link[^>]+type=[\"']application/(rss|atom)\\+xml[\"'][^>]*>", RegexOption.IGNORE_CASE).find(body)?.value
    val href = link?.let { Regex("href=[\"']([^\"']+)[\"']").find(it)?.groupValues?.get(1) }
        ?: throw Exception("Nenhum feed RSS encontrado nesse site")
    return Feed(host, URI(u).resolve(href).toString())
}

fun googleNewsFeed(topic: String) = Feed(
    "Google News: $topic",
    "https://news.google.com/rss/search?q=${URLEncoder.encode(topic, "UTF-8")}&hl=pt-BR&gl=BR&ceid=BR:pt-419"
)

/** Busca a imagem og:image da própria página da notícia (quando o feed não traz thumb). */
fun ogImage(link: String): String? = try {
    val html = Net.get(link).take(150000)
    Regex("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
        ?: Regex("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
} catch (e: Exception) { null }

/** Traduz para PT-BR; se o texto já for português, devolve o original. */
object Tr {
    private val cache = ConcurrentHashMap<String, String>()
    fun pt(text: String): String {
        if (text.isBlank()) return text
        cache[text]?.let { return it }
        return try {
            val u = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=pt-BR&dt=t&q=" + URLEncoder.encode(text.take(1500), "UTF-8")
            val j = JSONArray(Net.get(u))
            val r = if (j.optString(2).startsWith("pt")) text else buildString {
                val a = j.getJSONArray(0); for (i in 0 until a.length()) append(a.getJSONArray(i).getString(0))
            }
            cache[text] = r; r
        } catch (e: Exception) { text }
    }
}
