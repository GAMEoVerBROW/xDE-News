package com.meunews.reader

import android.text.Html
import android.util.Xml
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
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
        val r = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.9, text/html;q=0.8, */*;q=0.5")
            .build()
        http.newCall(r).execute().use {
            if (!it.isSuccessful) throw java.io.IOException("HTTP " + it.code)
            return it.body?.string() ?: ""
        }
    }

    fun bytes(url: String): ByteArray {
        val r = Request.Builder().url(url).header("User-Agent", UA).build()
        http.newCall(r).execute().use {
            if (!it.isSuccessful) throw java.io.IOException("HTTP " + it.code)
            return it.body?.bytes() ?: ByteArray(0)
        }
    }
}

fun plain(html: String) = Html.fromHtml(html, 0).toString().replace('\uFFFC', ' ').trim()

private fun unesc(s: String) = s.replace("&amp;", "&").replace("&#38;", "&").replace("&#x26;", "&")

private val imgRe = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
private val dateFmts = listOf("EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm zzz", "dd MMM yyyy HH:mm:ss Z", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd")

private fun parseDate(s: String): Long {
    for (f in dateFmts) try {
        return SimpleDateFormat(f, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s.trim())!!.time
    } catch (e: Exception) {}
    return 0L
}

private fun isImg(url: String?, type: String?, medium: String?) =
    !url.isNullOrBlank() && (medium == "image" || type?.startsWith("image") == true || Regex("\\.(jpe?g|png|webp|gif)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(url))

private val ENT = mapOf("nbsp" to " ", "copy" to "©", "reg" to "®", "hellip" to "…", "mdash" to "—", "ndash" to "–", "lsquo" to "‘", "rsquo" to "’",
    "ldquo" to "“", "rdquo" to "”", "bull" to "•", "middot" to "·", "laquo" to "«", "raquo" to "»", "euro" to "€", "trade" to "™", "eacute" to "é",
    "egrave" to "è", "agrave" to "à", "aacute" to "á", "atilde" to "ã", "ccedil" to "ç", "iacute" to "í", "oacute" to "ó", "otilde" to "õ", "uacute" to "ú",
    "ecirc" to "ê", "ocirc" to "ô", "acirc" to "â", "uuml" to "ü", "ouml" to "ö", "auml" to "ä", "szlig" to "ß", "ntilde" to "ñ", "pound" to "£", "deg" to "°", "times" to "×")

/** Lê o texto do elemento atual (inclusive com tags dentro) e consome até o fim dele. */
private fun readText(p: XmlPullParser): String {
    val sb = StringBuilder()
    var depth = 1
    while (depth > 0) {
        when (p.next()) {
            XmlPullParser.TEXT, XmlPullParser.CDSECT -> sb.append(p.text)
            XmlPullParser.START_TAG -> depth++
            XmlPullParser.END_TAG -> depth--
            XmlPullParser.END_DOCUMENT -> return sb.toString()
        }
    }
    return sb.toString()
}

fun parseFeed(xmlIn: String, fallback: String): List<Article> {
    val xml = xmlIn.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
    val p = Xml.newPullParser()
    p.setInput(StringReader(xml))
    try { p.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) } catch (e: Exception) { }
    for ((k, v) in ENT) try { p.defineEntityReplacementText(k, v) } catch (e: Exception) { }
    val now = System.currentTimeMillis()
    val out = mutableListOf<Article>()
    var channel: String? = null; var inItem = false
    var t = ""; var l = ""; var guid = ""; var s = ""; var img: String? = null; var d = 0L; var src: String? = null
    var ev = p.eventType
    while (ev != XmlPullParser.END_DOCUMENT) {
        if (ev == XmlPullParser.START_TAG) {
            val n = p.name
            if (n == "item" || n == "entry") { inItem = true; t = ""; l = ""; guid = ""; s = ""; img = null; d = 0; src = null }
            else if (!inItem && n == "title" && channel == null) channel = readText(p).trim()
            else if (inItem) when (n) {
                "title" -> t = plain(readText(p))
                "link" -> {
                    val h = p.getAttributeValue(null, "href"); val rel = p.getAttributeValue(null, "rel"); val x = readText(p).trim()
                    if (l.isBlank() && (rel == null || rel == "alternate")) l = if (!h.isNullOrBlank()) h else x
                }
                "feedburner:origLink" -> { val x = readText(p).trim(); if (x.isNotBlank()) l = x }
                "guid", "id" -> guid = readText(p).trim()
                "description", "summary", "content:encoded", "content" -> {
                    val x = readText(p)
                    if (n == "content:encoded" || s.isBlank()) s = x
                    if (img == null) img = imgRe.find(x)?.groupValues?.get(1)?.let { unesc(it) }
                }
                "pubDate", "published", "updated", "dc:date" -> { val x = readText(p); if (d == 0L) d = parseDate(x) }
                "source" -> src = readText(p).trim()
                "media:thumbnail" -> { if (img == null) img = p.getAttributeValue(null, "url"); readText(p) }
                "media:content", "enclosure" -> {
                    val u = p.getAttributeValue(null, "url")
                    if (isImg(u, p.getAttributeValue(null, "type"), p.getAttributeValue(null, "medium"))) img = u
                    readText(p)
                }
            }
        } else if (ev == XmlPullParser.END_TAG && (p.name == "item" || p.name == "entry")) {
            inItem = false
            if (l.isBlank() && guid.startsWith("http")) l = guid
            if (t.isNotBlank() && l.isNotBlank())
                out += Article(t, l, plain(s).take(400), img, src ?: channel ?: fallback, if (d > 0) d else now - out.size * 1000L)
        }
        ev = p.next()
    }
    return out
}

/** Aceita endereço de site OU de feed; descobre o RSS sozinho (evita feeds de comentários). */
fun resolveFeed(input: String): Feed {
    var u = input.trim(); if (!u.startsWith("http")) u = "https://$u"
    val body = Net.get(u)
    val host = URI(u).host ?: u
    if (Regex("<(rss|feed|rdf)", RegexOption.IGNORE_CASE).containsMatchIn(body.take(1500))) return Feed(host, u)
    val typeRe = Regex("type=[\"']application/(rss|atom)\\+xml", RegexOption.IGNORE_CASE)
    val links = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(body).map { it.value }.filter { typeRe.containsMatchIn(it) }
        .mapNotNull { tag -> Regex("href=[\"']([^\"']+)[\"']").find(tag)?.groupValues?.get(1)?.replace("&amp;", "&")?.let { h -> Pair(h, tag) } }.toList()
    val bad = Regex("comment|coment", RegexOption.IGNORE_CASE)
    val best = links.firstOrNull { !bad.containsMatchIn(it.first + it.second) } ?: links.firstOrNull()
        ?: throw Exception("Nenhum feed RSS encontrado nesse site")
    return Feed(host, URI(u).resolve(best.first).toString())
}

fun googleNewsFeed(topic: String) = Feed(
    "Google News: $topic",
    "https://news.google.com/rss/search?q=${URLEncoder.encode(topic, "UTF-8")}&hl=pt-BR&gl=BR&ceid=BR:pt-419"
)

private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

/** Imagem og:image das páginas, com cache em disco (inclusive "não tem"), no máximo 3 buscas ao mesmo tempo. */
object Og {
    private val cache = ConcurrentHashMap<String, String>()
    private val gate = java.util.concurrent.Semaphore(3)
    private var file: java.io.File? = null
    private var n = 0

    fun init(dir: java.io.File) {
        if (file != null) return
        val f = java.io.File(dir, "og.json"); file = f
        try { val j = JSONObject(f.readText()); for (k in j.keys()) cache[k] = j.getString(k) } catch (e: Exception) { }
    }

    fun peek(link: String): String? = cache[link]?.ifBlank { null }

    private fun save() {
        try {
            if (cache.size > 6000) cache.keys.take(2000).forEach { cache.remove(it) }
            file?.writeText(JSONObject(cache as Map<*, *>).toString())
        } catch (e: Exception) { }
    }

    fun get(link: String): String? {
        cache[link]?.let { return it.ifBlank { null } }
        if (link.contains("reddit.com") || link.contains("news.google.com")) return null
        gate.acquire()
        val r = try { fetch(link) } finally { gate.release() }
        cache[link] = r ?: ""
        synchronized(this) { n++; if (n % 20 == 0) save() }
        return r
    }

    private fun fetch(link: String): String? = try {
        val html = Net.get(link).take(150000)
        val m = Regex("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?: Regex("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
        m?.let { unesc(it) }
    } catch (e: Exception) { null }
}

fun ogImage(link: String): String? = Og.get(link)

/** Traduz para PT-BR (no máximo 4 pedidos ao mesmo tempo); guarda no disco para abrir já traduzido. */
object Tr {
    private val cache = ConcurrentHashMap<String, String>()
    private val gate = java.util.concurrent.Semaphore(4)
    private var file: java.io.File? = null
    private var n = 0

    fun init(dir: java.io.File) {
        if (file != null) return
        val f = java.io.File(dir, "tr.json"); file = f
        try { val j = JSONObject(f.readText()); for (k in j.keys()) cache[k] = j.getString(k) } catch (e: Exception) { }
    }

    fun cached(text: String): String? = cache[text]

    private fun save() {
        try {
            if (cache.size > 6000) cache.keys.take(2000).forEach { cache.remove(it) }
            file?.writeText(JSONObject(cache as Map<*, *>).toString())
        } catch (e: Exception) { }
    }

    fun pt(text: String): String {
        if (text.isBlank()) return text
        cache[text]?.let { return it }
        gate.acquire()
        try {
            cache[text]?.let { return it }
            val u = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=pt-BR&dt=t&q=" + URLEncoder.encode(text.take(1500), "UTF-8")
            val j = JSONArray(Net.get(u))
            val r = if (j.optString(2).startsWith("pt")) text else buildString {
                val a = j.getJSONArray(0); for (i in 0 until a.length()) append(a.getJSONArray(i).getString(0))
            }
            cache[text] = r
            synchronized(this) { n++; if (n % 25 == 0) save() }
            return r
        } catch (e: Exception) {
            return text
        } finally { gate.release() }
    }
}
