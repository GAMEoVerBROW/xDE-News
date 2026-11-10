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
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

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

@Composable
fun AddButton(f: Feed, vm: VM) {
    if (vm.feeds.any { it.url == f.url }) OutlinedButton({}, enabled = false) { Text("✓ Seguindo") }
    else Button({ vm.addDirect(f) }) { Text("+ Seguir") }
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
        items(CATALOG) { k ->
            val (cor, emoji) = STYLE[k.name] ?: Pair(0xFF37474F, "📰")
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
                AddButton(Feed(s.name, s.url), vm)
            }
            if (s.desc.isNotBlank()) Text(s.desc.take(160), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (open) {
                val heads by produceState<List<String>?>(null, s.url) {
                    value = withContext(Dispatchers.IO) {
                        try { parseFeed(Net.get(s.url), "").take(4).map { Tr.pt(it.title) } } catch (e: Exception) { emptyList() }
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
        CATALOG.flatMap { c -> c.subs.flatMap { sb -> sb.items.map { Triple(it, c.name, sb.name) } } }
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
            AddButton(v.feed, vm)
        }
    }
}
