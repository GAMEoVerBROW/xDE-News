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

    /** Pedido com identificação própria (o Reddit exige um app identificado, não aceita "navegador"). */
    fun getWith(url: String, ua: String): String {
        val r = Request.Builder().url(url).header("User-Agent", ua)
            .header("Accept", "application/atom+xml, application/rss+xml, application/json, */*;q=0.5").build()
        http.newCall(r).execute().use {
            if (!it.isSuccessful) throw java.io.IOException("HTTP " + it.code)
            return it.body?.string() ?: ""
        }
    }

    private val fast = http.newBuilder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS).readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS).build()

    /** Lê só o começo da página (até </head>) em vez de baixar a página inteira: bem mais rápido para achar a capa. */
    fun peekHtml(url: String, maxBytes: Long = 150_000): String {
        val r = Request.Builder().url(url).header("User-Agent", UA).header("Accept", "text/html,*/*;q=0.8").build()
        fast.newCall(r).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP " + resp.code)
            val src = resp.body?.source() ?: return ""
            val sb = StringBuilder()
            val buf = okio.Buffer()
            val end = Regex("</head>", RegexOption.IGNORE_CASE)
            var total = 0L
            while (total < maxBytes) {
                val n = src.read(buf, 8192)
                if (n < 0) break
                total += n
                val chunk = buf.readUtf8()
                sb.append(chunk)
                if (end.containsMatchIn(sb.takeLast(chunk.length + 7))) break
            }
            return sb.toString()
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
                out += Article(t, l, plain(s).take(400), img, src ?: channel ?: fallback, if (d in 1..(now + 120000L)) d else if (d > 0) now else now - out.size * 1000L)
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

private const val RUA = "android:com.meunews.reader:v1.0 (leitor de noticias pessoal)"
private val redditGate = Any()
private var redditLast = 0L

/** Lê um subreddit com 3 tentativas (RSS, RSS do old.reddit e a API em JSON), devagar, como o Reddit pede. */
fun fetchReddit(feedUrl: String, name: String): List<Article> {
    synchronized(redditGate) {
        val wait = 1000 - (System.currentTimeMillis() - redditLast)
        if (wait > 0) Thread.sleep(wait)
        redditLast = System.currentTimeMillis()
    }
    val sub = Regex("/r/([^/]+)").find(feedUrl)?.groupValues?.get(1)
    val tries = mutableListOf<() -> List<Article>>()
    tries += { parseFeed(Net.getWith(feedUrl, RUA), name) }
    tries += { parseFeed(Net.getWith(feedUrl.replace("://www.reddit.com", "://old.reddit.com"), RUA), name) }
    if (sub != null) tries += { redditJson(sub) }
    for (t in tries) {
        try { val r = t(); if (r.isNotEmpty()) return r } catch (e: Exception) { }
        Thread.sleep(700)
    }
    return emptyList()
}

private fun redditJson(sub: String): List<Article> {
    val j = JSONObject(Net.getWith("https://www.reddit.com/r/$sub/.json?limit=30&raw_json=1", RUA))
    val ch = j.getJSONObject("data").getJSONArray("children")
    val out = mutableListOf<Article>()
    for (i in 0 until ch.length()) {
        val d = ch.getJSONObject(i).getJSONObject("data")
        val perma = d.optString("permalink")
        val title = d.optString("title")
        if (title.isBlank() || perma.isBlank()) continue
        var img: String? = null
        val prev = d.optJSONObject("preview")?.optJSONArray("images")?.optJSONObject(0)?.optJSONObject("source")?.optString("url")
        if (!prev.isNullOrBlank()) img = prev
        else { val th = d.optString("thumbnail"); if (th.startsWith("http")) img = th }
        val cu = d.optDouble("created_utc", 0.0)
        out += Article(plain(title), "https://www.reddit.com$perma", d.optString("selftext").take(400), img, "r/$sub",
            if (cu > 0) (cu * 1000).toLong() else System.currentTimeMillis())
    }
    return out
}

/** Guarda no disco a hora em que cada notícia apareceu pela primeira vez (nunca muda), para ela não voltar ao topo. */
object Seen {
    private val map = ConcurrentHashMap<String, Long>()
    private var file: java.io.File? = null
    @Volatile private var dirty = false

    fun init(dir: java.io.File) {
        if (file != null) return
        val f = java.io.File(dir, "seen.json"); file = f
        try { val j = JSONObject(f.readText()); for (k in j.keys()) map[k] = j.getLong(k) } catch (e: Exception) { }
    }

    /** Devolve a hora da 1ª vez que a notícia foi vista (e registra se for nova). */
    fun touch(key: String, now: Long): Long {
        val prev = map.putIfAbsent(key, now)
        if (prev == null) dirty = true
        return prev ?: now
    }

    fun save() {
        if (!dirty) return
        dirty = false
        try {
            if (map.size > 15000) map.entries.sortedBy { it.value }.take(map.size - 11000).forEach { map.remove(it.key) }
            file?.writeText(JSONObject(map as Map<*, *>).toString())
        } catch (e: Exception) { }
    }
}

private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

/** Imagem og:image das páginas, com cache em disco (inclusive "não tem"), no máximo 3 buscas ao mesmo tempo. */
object Og {
    private val cache = ConcurrentHashMap<String, String>()
    private val gate = java.util.concurrent.Semaphore(6)
    private var file: java.io.File? = null
    private var n = 0

    fun init(dir: java.io.File) {
        if (file != null) return
        val f = java.io.File(dir, "og.json"); file = f
        try { val j = JSONObject(f.readText()); for (k in j.keys()) cache[k] = j.getString(k) } catch (e: Exception) { }
    }

    fun peek(link: String): String? = cache[link]?.ifBlank { null }
    fun known(link: String): Boolean = cache.containsKey(link)

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
        val html = Net.peekHtml(link)
        val m = Regex("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?: Regex("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
        m?.let { unesc(it) }
    } catch (e: Exception) { null }
}

fun ogImage(link: String): String? = Og.get(link)

private val PT_WORDS = setOf("de", "do", "da", "dos", "das", "que", "para", "com", "não", "uma", "um", "os", "as", "em", "no", "na", "nos", "nas", "por",
    "mais", "como", "foi", "são", "ao", "aos", "seu", "sua", "pelo", "pela", "já", "mas", "ser", "tem", "sobre", "após", "entre", "está", "ele", "ela", "você", "quando", "também", "muito")
private val EN_WORDS = setOf("the", "and", "of", "to", "in", "is", "for", "with", "on", "at", "by", "from", "as", "are", "this", "that", "it", "be", "new", "how", "why",
    "what", "after", "over", "you", "your", "will", "has", "have", "its", "an", "not", "was", "who", "can", "all", "more", "about", "into", "out", "up")

/** Palpite rápido: o texto já está em português? (evita pedir tradução à toa) */
private fun looksPt(t: String): Boolean {
    val w = t.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
    val pt = w.count { it in PT_WORDS }
    val en = w.count { it in EN_WORDS }
    return (pt >= 2 && pt > en) || (pt >= 1 && en == 0 && t.any { it in "ãõçÃÕÇ" })
}

/**
 * Traduz para PT-BR. Os pedidos de vários cards/parágrafos são juntados em LOTES (um único pedido ao tradutor
 * para vários textos), o que é muito mais rápido. Tudo fica guardado no disco para abrir já traduzido.
 */
object Tr {
    private val cache = ConcurrentHashMap<String, String>()
    private class Req(val text: String) {
        val done = java.util.concurrent.CountDownLatch(1)
        @Volatile var result: String? = null
    }
    private val queue = java.util.concurrent.LinkedBlockingQueue<Req>()
    private val pending = ConcurrentHashMap<String, Req>()
    private val pool = java.util.concurrent.Executors.newFixedThreadPool(4) { r -> Thread(r).apply { isDaemon = true } }
    @Volatile private var started = false
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
        if (looksPt(text)) { cache[text] = text; return text }
        start()
        val req = pending.getOrPut(text) { Req(text).also { queue.add(it) } }
        req.done.await(25, java.util.concurrent.TimeUnit.SECONDS)
        return req.result ?: text
    }

    private fun start() {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            Thread {
                while (true) {
                    val first = queue.take()
                    Thread.sleep(60) // espera juntar mais pedidos
                    val reqs = mutableListOf(first)
                    queue.drainTo(reqs)
                    var batch = mutableListOf<Req>()
                    var chars = 0
                    for (r in reqs) {
                        val len = minOf(r.text.length, 1500)
                        if (batch.isNotEmpty() && (chars + len > 1800 || batch.size >= 15)) {
                            val b = batch; pool.execute { runBatch(b) }
                            batch = mutableListOf(); chars = 0
                        }
                        batch += r; chars += len
                    }
                    if (batch.isNotEmpty()) { val b = batch; pool.execute { runBatch(b) } }
                }
            }.apply { isDaemon = true }.start()
        }
    }

    private fun raw(q: String): Pair<String, String> {
        val u = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=pt-BR&dt=t&q=" + URLEncoder.encode(q, "UTF-8")
        val j = try { JSONArray(Net.get(u)) } catch (e: Exception) { Thread.sleep(300); JSONArray(Net.get(u)) }
        val a = j.getJSONArray(0)
        val sb = StringBuilder()
        for (i in 0 until a.length()) sb.append(a.getJSONArray(i).optString(0))
        return Pair(sb.toString(), j.optString(2))
    }

    private fun single(t: String): String { val (r, lang) = raw(t); return if (lang.startsWith("pt")) t else r }

    private fun runBatch(b: List<Req>) {
        try {
            val texts = b.map { it.text.replace('\n', ' ').take(1500) }
            var out: List<String>? = null
            if (texts.size > 1) {
                val (r, lang) = raw(texts.joinToString("\n"))
                if (!lang.startsWith("pt")) {
                    val parts = r.trimEnd('\n').split("\n")
                    if (parts.size == texts.size) out = parts.map { it.trim() }
                }
            }
            val res = out ?: texts.map { single(it) }
            for (i in b.indices) { b[i].result = res[i]; cache[b[i].text] = res[i] }
            synchronized(this) { n += b.size; if (n >= 25) { n = 0; save() } }
        } catch (e: Exception) {
        } finally {
            for (r in b) { pending.remove(r.text); r.done.countDown() }
        }
    }
}
