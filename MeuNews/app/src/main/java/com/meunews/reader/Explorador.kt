@file:OptIn(ExperimentalMaterial3Api::class)

package com.meunews.reader

import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/** Ordem fixa dos tópicos, com cor e emoji de cada um (independe de qualquer outro arquivo). */
private val TOPICS = listOf(
    Triple("Notícias Brasil", 0xFF1B5E20, "🇧🇷"), Triple("Notícias Mundo", 0xFF0D47A1, "🌍"), Triple("Política", 0xFF455A64, "🏛️"),
    Triple("Tecnologia", 0xFF4527A0, "💻"), Triple("Inteligência Artificial", 0xFF512DA8, "🤖"), Triple("Games", 0xFF880E4F, "🎮"),
    Triple("Esportes", 0xFFE65100, "⚽"), Triple("Cripto", 0xFFB26A00, "₿"), Triple("Negócios", 0xFF006064, "📈"),
    Triple("Ciência", 0xFF283593, "🔬"), Triple("Entretenimento", 0xFFAD1457, "🎬"), Triple("Música", 0xFF6A1B9A, "🎵"),
    Triple("Anime & Geek", 0xFF00838F, "🎌"), Triple("Carros", 0xFFBF360C, "🚗"), Triple("Programação", 0xFF1565C0, "👨‍💻"),
    Triple("Saúde", 0xFF00695C, "🩺"), Triple("Moda e Beleza", 0xFF8E24AA, "👗"), Triple("Viagem", 0xFF0277BD, "✈️"),
    Triple("Culinária", 0xFFD84315, "🍳"), Triple("Casa e Design", 0xFF5D4037, "🏠"), Triple("Meio Ambiente", 0xFF2E7D32, "🌱"),
    Triple("Educação", 0xFF3949AB, "🎓"), Triple("Curiosidades", 0xFFF57F17, "🤯"), Triple("Reddit", 0xFFE64A19, "👽"),
    Triple("Medium", 0xFF37474F, "✍️"))
private val PALETTE = listOf(0xFF1B5E20, 0xFF0D47A1, 0xFF4527A0, 0xFF880E4F, 0xFFE65100, 0xFF006064, 0xFF283593, 0xFFAD1457, 0xFF00695C, 0xFF6A1B9A)
private val ORDER = TOPICS.map { norm(it.first) }

private fun look(name: String): Pair<Long, String> {
    val k = norm(name)
    val t = TOPICS.firstOrNull { norm(it.first) == k }
    return if (t != null) Pair(t.second, t.third) else Pair(PALETTE[(k.hashCode() and 0x7fffffff) % PALETTE.size], "📰")
}

/** Catálogo completo = base + extensões. Junta tópicos e subtópicos de mesmo nome, sem repetir, na ordem fixa. */
val ALL: List<Cat> = run {
    val out = mutableListOf<Cat>()
    fun add(c: Cat) {
        val subs0 = c.subs.map { if (c.subs.size == 1 && it.name == "Tudo") Sub("Sites", it.items) else it }
        val i = out.indexOfFirst { norm(it.name) == norm(c.name) }
        if (i < 0) { out += Cat(c.name, subs0.map { Sub(it.name, it.items.distinctBy { s -> s.url }) }); return }
        val subs = out[i].subs.toMutableList()
        for (sx in subs0) {
            val j = subs.indexOfFirst { norm(it.name) == norm(sx.name) }
            if (j < 0) subs += Sub(sx.name, sx.items.distinctBy { s -> s.url })
            else subs[j] = Sub(subs[j].name, (subs[j].items + sx.items).distinctBy { s -> s.url })
        }
        out[i] = Cat(out[i].name, subs)
    }
    for (c in CATALOG + EXTRA1 + EXTRA2 + EXTRA3) add(c)
    out.filter { c -> c.subs.any { it.items.isNotEmpty() } }
        .sortedBy { c -> ORDER.indexOf(norm(c.name)).let { if (it < 0) 999 else it } }
}

private fun hostName(u: String) = try { java.net.URI(u).host?.removePrefix("www.") ?: u } catch (e: Exception) { u }

private fun langLabel(s: Src) = when {
    s.pt -> "Português"
    s.lang.isBlank() || s.lang.startsWith("en") -> "Inglês · traduzido"
    else -> s.lang.uppercase() + " · traduzido"
}

private fun fmt(n: Long) = if (n >= 1000) "${n / 1000} mil" else "$n"

/** Busca sites/feeds de verdade na internet (índice público de feeds). */
suspend fun webSearch(query: String): List<Src> = withContext(Dispatchers.IO) {
    try {
        val j = JSONObject(Net.get("https://cloud.feedly.com/v3/search/feeds?count=20&locale=pt&query=" + URLEncoder.encode(query, "UTF-8")))
        val a = j.optJSONArray("results") ?: return@withContext emptyList<Src>()
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val id = o.optString("feedId").removePrefix("feed/")
            if (!id.startsWith("http")) return@mapNotNull null
            val lang = o.optString("language")
            Src(plain(o.optString("title")).ifBlank { hostName(id) }, id, lang.startsWith("pt"), plain(o.optString("description")),
                o.optString("iconUrl").ifBlank { null }, o.optLong("subscribers"), lang, o.optString("website").ifBlank { null })
        }
    } catch (e: Exception) { emptyList() }
}

private fun norm(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

private fun needsFind(u: String) = u.endsWith("#find")
private fun feedTitle(s: Src) = if (s.url.contains("news.google.com")) "Google News: ${s.name}" else s.name

/** Descobre o RSS de um site a partir da página inicial. */
suspend fun discoverFeed(site: String): String? = withContext(Dispatchers.IO) {
    val base = site.removeSuffix("#find").trimEnd('/')
    try { return@withContext resolveFeed(base).url } catch (e: Exception) { }
    for (g in listOf("/feed/", "/feed", "/rss", "/rss.xml", "/feed.xml", "/atom.xml", "/index.xml")) {
        try {
            if (Regex("<(rss|feed|rdf)", RegexOption.IGNORE_CASE).containsMatchIn(Net.get(base + g).take(1500))) return@withContext base + g
        } catch (e: Exception) { }
    }
    null
}

suspend fun feedUrlOf(s: Src): String? = if (needsFind(s.url)) discoverFeed(s.url) else s.url

@Composable
fun AddButton(s: Src, vm: VM) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val title = feedTitle(s)
    if (vm.feeds.any { it.url == s.url || it.title == title }) OutlinedButton({}, enabled = false) { Text("✓ Seguindo") }
    else Button({
        if (!busy) {
            busy = true
            scope.launch {
                val u = feedUrlOf(s)
                if (u == null) Toast.makeText(ctx, "Não encontrei o feed de " + s.name, Toast.LENGTH_LONG).show()
                else vm.addDirect(Feed(title, u))
                busy = false
            }
        }
    }, enabled = !busy) { Text(if (busy) "…" else "+ Seguir") }
}

@Composable
fun ExploreScreen(vm: VM, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf<Cat?>(null) }
    var sub by remember { mutableStateOf<Sub?>(null) }
    var gTopic by remember { mutableStateOf<String?>(null) }
    val t = topic
    val g = gTopic
    val back: () -> Unit = {
        when {
            g != null -> { gTopic = null }
            t != null -> { topic = null; sub = null }
            else -> { onClose() }
        }
    }
    BackHandler(onBack = back)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(if (g != null) "Google News" else t?.name ?: "Descobrir") },
                navigationIcon = { IconButton(back) { Icon(Icons.Default.ArrowBack, "Voltar") } })
            when {
                g != null -> GNewsResults(g, vm)
                t != null -> TopicPage(t, sub, { sub = it }, vm)
                else -> {
                    OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp), singleLine = true,
                        label = { Text("Pesquisar tópico, site ou colar link RSS") },
                        leadingIcon = { Icon(Icons.Default.Search, null) })
                    if (q.isBlank()) TopicGrid { topic = it; sub = null }
                    else SearchResults(q, vm, { gTopic = it }, onClose)
                }
            }
        }
    }
}

@Composable
fun TopicGrid(onPick: (Cat) -> Unit) {
    LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(ALL) { k ->
            val (cor, emoji) = look(k.name)
            Card(Modifier.fillMaxWidth().height(104.dp).clickable { onPick(k) }, shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(cor))) {
                Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(emoji, fontSize = 28.sp)
                    Text(k.name, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TopicPage(t: Cat, sub: Sub?, onSub: (Sub?) -> Unit, vm: VM) {
    val list = (if (sub == null) t.subs.flatMap { it.items } else sub.items).distinctBy { it.url }
    Column(Modifier.fillMaxSize()) {
        if (t.subs.size > 1) LazyRow(Modifier.padding(top = 12.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(sub == null, { onSub(null) }, { Text("Tudo") }) }
            items(t.subs) { s -> FilterChip(sub == s, { onSub(s) }, { Text(s.name) }) }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.url }) { SourceCard(it, vm) }
        }
    }
}

@Composable
fun SourceCard(s: Src, vm: VM) {
    var open by remember { mutableStateOf(false) }
    val host = hostName(s.site ?: s.url)
    Card(Modifier.fillMaxWidth().clickable { open = !open }, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
                    Text(s.name.take(1).uppercase(), fontWeight = FontWeight.Bold)
                    AsyncImage(s.icon ?: "https://www.google.com/s2/favicons?domain=$host&sz=128", null, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(s.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(host + " · " + langLabel(s) + (if (s.followers > 0) " · " + fmt(s.followers) + " seguidores" else ""),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                AddButton(s, vm)
            }
            if (s.desc.isNotBlank()) Text(s.desc.take(160), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (open) {
                val heads by produceState<List<String>?>(null, s.url) {
                    value = withContext(Dispatchers.IO) {
                        try { val u = feedUrlOf(s); if (u == null) emptyList() else parseFeed(Net.get(u), "").take(4).map { Tr.pt(it.title) } } catch (e: Exception) { emptyList() }
                    }
                }
                val h = heads
                Column(Modifier.padding(top = 10.dp)) {
                    when {
                        h == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        h.isEmpty() -> Text("Não consegui carregar uma prévia agora", style = MaterialTheme.typography.bodySmall)
                        else -> h.forEach { Text("• $it", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionCard(title: String, sub: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SearchResults(q: String, vm: VM, onGoogle: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val ql = q.trim()
    val nq = norm(ql)
    val matches = remember(nq) {
        ALL.flatMap { c -> c.subs.flatMap { sb -> sb.items.map { Triple(it, c.name, sb.name) } } }
            .filter { (s, cn, sn) -> norm(s.name).contains(nq) || norm(hostName(s.url)).contains(nq) || norm(cn).contains(nq) || norm(sn).contains(nq) }
            .map { it.first }.distinctBy { it.url }
    }
    var web by remember(nq) { mutableStateOf<List<Src>?>(null) }
    LaunchedEffect(nq) {
        if (nq.length >= 2) { delay(600); web = webSearch(ql) } else web = emptyList()
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ActionCard("🔎  Pesquisar “$ql” no Google News", "Escolha a versão do feed que combina mais") { onGoogle(ql) } }
        if (ql.contains('.') && !ql.contains(' ')) item {
            ActionCard("🌐  Seguir $ql", "O app descobre o RSS do site sozinho") {
                vm.add(ql, false) { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
                onClose()
            }
        }
        if (matches.isNotEmpty()) item { Text("No catálogo", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge) }
        items(matches, key = { it.url }) { SourceCard(it, vm) }
        item { Text("Resultados da internet", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge) }
        val w = web
        if (w == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (w.isEmpty()) item { Text("Nada encontrado agora. Verifique a conexão ou tente outro nome.", style = MaterialTheme.typography.bodySmall) }
        else items(w.distinctBy { it.url }.filter { r -> matches.none { it.url == r.url } }, key = { it.url }) { SourceCard(it, vm) }
    }
}

private fun variants(t: String): List<Variant> {
    fun f(label: String, extra: String, hl: String, gl: String, ceid: String) = Variant(label,
        Feed("Google News: $t ($label)", "https://news.google.com/rss/search?q=${URLEncoder.encode(t + extra, "UTF-8")}&hl=$hl&gl=$gl&ceid=$ceid"))
    return listOf(
        f("Brasil · português", "", "pt-BR", "BR", "BR:pt-419"),
        f("Brasil · últimas 24 horas", " when:1d", "pt-BR", "BR", "BR:pt-419"),
        f("Mundo · inglês (traduzido)", "", "en-US", "US", "US:en"))
}

@Composable
fun GNewsResults(topic: String, vm: VM) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("“$topic” — escolha qual versão do feed combina mais com você:", style = MaterialTheme.typography.labelLarge) }
        items(variants(topic), key = { it.feed.url }) { v -> GNewsOption(v, vm) }
    }
}

@Composable
fun GNewsOption(v: Variant, vm: VM) {
    val heads by produceState<List<String>?>(null, v.feed.url) {
        value = withContext(Dispatchers.IO) {
            try { parseFeed(Net.get(v.feed.url), "").take(3).map { Tr.pt(it.title) } } catch (e: Exception) { emptyList() }
        }
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(v.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val h = heads
            when {
                h == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 10.dp))
                h.isEmpty() -> Text("Sem resultados para essa versão", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                else -> h.forEach { Text("• $it", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            Spacer(Modifier.height(10.dp))
            AddButton(Src(v.feed.title, v.feed.url, true), vm)
        }
    }
}
