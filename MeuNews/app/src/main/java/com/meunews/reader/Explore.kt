@file:OptIn(ExperimentalMaterial3Api::class)

package com.meunews.reader

import androidx.activity.compose.BackHandler
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
import kotlinx.coroutines.withContext
import java.net.URLEncoder

data class Src(val name: String, val url: String, val pt: Boolean)
data class Sub(val name: String, val items: List<Src>)
data class Cat(val name: String, val subs: List<Sub>)
data class Variant(val label: String, val feed: Feed)

private fun pt(n: String, u: String) = Src(n, u, true)
private fun en(n: String, u: String) = Src(n, u, false)
private fun one(name: String, vararg i: Src) = Cat(name, listOf(Sub("Tudo", i.toList())))

val CATALOG = listOf(
    one("Notícias Brasil",
        pt("G1", "https://g1.globo.com/rss/g1/"), pt("Folha de S.Paulo", "https://feeds.folha.uol.com.br/emcimadahora/rss091.xml"),
        pt("UOL", "http://rss.uol.com.br/feed/noticias.xml"), pt("CNN Brasil", "https://www.cnnbrasil.com.br/feed/"),
        pt("Agência Brasil", "https://agenciabrasil.ebc.com.br/rss/ultimasnoticias/feed.xml"), pt("Poder360", "https://www.poder360.com.br/feed/"),
        pt("BBC News Brasil", "https://feeds.bbci.co.uk/portuguese/rss.xml"), pt("DW Brasil", "https://rss.dw.com/rdf/rss-br-all")),
    one("Notícias Mundo",
        en("BBC News", "https://feeds.bbci.co.uk/news/world/rss.xml"), en("CNN", "http://rss.cnn.com/rss/edition.rss"),
        en("The Guardian", "https://www.theguardian.com/world/rss"), en("The New York Times", "https://rss.nytimes.com/services/xml/rss/nyt/World.xml"),
        en("Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml"), en("NPR", "https://feeds.npr.org/1001/rss.xml")),
    one("Tecnologia",
        pt("Tecnoblog", "https://tecnoblog.net/feed/"), pt("Canaltech", "https://canaltech.com.br/rss/"),
        pt("Olhar Digital", "https://olhardigital.com.br/feed/"), pt("TecMundo", "https://rss.tecmundo.com.br/feed"),
        en("The Verge", "https://www.theverge.com/rss/index.xml"), en("TechCrunch", "https://techcrunch.com/feed/"),
        en("Ars Technica", "https://feeds.arstechnica.com/arstechnica/index"), en("Wired", "https://www.wired.com/feed/rss"),
        en("Engadget", "https://www.engadget.com/rss.xml"), en("Hacker News", "https://news.ycombinator.com/rss")),
    one("Games",
        pt("IGN Brasil", "https://br.ign.com/feed.xml"), pt("Jovem Nerd", "https://jovemnerd.com.br/feed/"),
        en("IGN", "https://www.ign.com/rss/v2/articles/feed"), en("GameSpot", "https://www.gamespot.com/feeds/mashup/"),
        en("Kotaku", "https://kotaku.com/rss"), en("Polygon", "https://www.polygon.com/rss/index.xml"),
        en("PC Gamer", "https://www.pcgamer.com/rss/"), en("Eurogamer", "https://www.eurogamer.net/feed"),
        en("Rock Paper Shotgun", "https://www.rockpapershotgun.com/feed")),
    Cat("Esportes", listOf(
        Sub("Futebol", listOf(pt("UOL Esporte", "http://rss.uol.com.br/feed/esporte.xml"), pt("Folha Esporte", "https://feeds.folha.uol.com.br/esporte/rss091.xml"),
            en("BBC Football", "https://feeds.bbci.co.uk/sport/football/rss.xml"), en("ESPN Soccer", "https://www.espn.com/espn/rss/soccer/news"))),
        Sub("Fórmula 1", listOf(pt("Grande Prêmio", "https://www.grandepremio.com.br/feed/"), en("Motorsport.com F1", "https://www.motorsport.com/rss/f1/news/"),
            en("Autosport F1", "https://www.autosport.com/rss/f1/news"), en("BBC Formula 1", "https://feeds.bbci.co.uk/sport/formula1/rss.xml"),
            en("RaceFans", "https://www.racefans.net/feed/"))),
        Sub("UFC / MMA", listOf(en("MMA Fighting", "https://www.mmafighting.com/rss/current"), en("MMA Junkie", "https://mmajunkie.usatoday.com/feed"),
            en("ESPN MMA", "https://www.espn.com/espn/rss/mma/news"))),
        Sub("Basquete / NBA", listOf(en("ESPN NBA", "https://www.espn.com/espn/rss/nba/news"))),
        Sub("NFL", listOf(en("ESPN NFL", "https://www.espn.com/espn/rss/nfl/news"))),
        Sub("Tênis", listOf(en("BBC Tennis", "https://feeds.bbci.co.uk/sport/tennis/rss.xml"))),
        Sub("Geral", listOf(en("BBC Sport", "https://feeds.bbci.co.uk/sport/rss.xml"), en("ESPN", "https://www.espn.com/espn/rss/news"))))),
    one("Cripto",
        pt("Portal do Bitcoin", "https://portaldobitcoin.uol.com.br/feed/"), pt("Cointelegraph Brasil", "https://br.cointelegraph.com/rss"),
        en("CoinDesk", "https://www.coindesk.com/arc/outboundfeeds/rss/"), en("Cointelegraph", "https://cointelegraph.com/rss"),
        en("Decrypt", "https://decrypt.co/feed"), en("Bitcoin Magazine", "https://bitcoinmagazine.com/.rss/full/"),
        en("The Block", "https://www.theblock.co/rss.xml")),
    one("Negócios",
        pt("InfoMoney", "https://www.infomoney.com.br/feed/"), pt("Exame", "https://exame.com/feed/"),
        en("Bloomberg Markets", "https://feeds.bloomberg.com/markets/news.rss"), en("CNBC", "https://www.cnbc.com/id/100003114/device/rss/rss.html"),
        en("Financial Times", "https://www.ft.com/rss/home"), en("Forbes", "https://www.forbes.com/business/feed/")),
    one("Ciência",
        en("NASA", "https://www.nasa.gov/rss/dyn/breaking_news.rss"), en("ScienceDaily", "https://www.sciencedaily.com/rss/all.xml"),
        en("Nature", "https://www.nature.com/nature.rss"), en("New Scientist", "https://www.newscientist.com/feed/home/"),
        en("Space.com", "https://www.space.com/feeds/all")),
    one("Entretenimento",
        en("Variety", "https://variety.com/feed/"), en("The Hollywood Reporter", "https://www.hollywoodreporter.com/feed/"),
        en("Collider", "https://collider.com/feed/"), en("Rolling Stone", "https://www.rollingstone.com/feed/")),
    one("Saúde", en("BBC Health", "https://feeds.bbci.co.uk/news/health/rss.xml"))
)

private fun hostName(u: String) = try { java.net.URI(u).host?.removePrefix("www.") ?: u } catch (e: Exception) { u }

@Composable
fun AddButton(f: Feed, vm: VM) {
    if (vm.feeds.any { it.url == f.url }) OutlinedButton({}, enabled = false) { Text("✓ Adicionada") }
    else Button({ vm.addDirect(f) }) { Text("Adicionar") }
}

@Composable
fun ExploreScreen(vm: VM, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var tab by remember { mutableIntStateOf(0) }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(title = { Text("Explorar fontes") },
                navigationIcon = { IconButton(onClose) { Icon(Icons.Default.ArrowBack, "Voltar") } })
            TabRow(tab) {
                Tab(tab == 0, { tab = 0 }) { Text("Catálogo", Modifier.padding(14.dp)) }
                Tab(tab == 1, { tab = 1 }) { Text("Pesquisar no Google News", Modifier.padding(14.dp)) }
            }
            if (tab == 0) CatalogTab(vm) else GNewsTab(vm)
        }
    }
}

@Composable
fun CatalogTab(vm: VM) {
    var cat by remember { mutableStateOf<Cat?>(null) }
    var sub by remember { mutableStateOf<Sub?>(null) }
    var q by remember { mutableStateOf("") }
    val c = cat
    val list = remember(cat, sub, q) {
        val s = sub
        val base = when {
            c == null -> CATALOG.flatMap { it.subs }.flatMap { it.items }
            s != null -> s.items
            else -> c.subs.flatMap { it.items }
        }
        base.distinctBy { it.url }.filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp), singleLine = true, label = { Text("Buscar site") })
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(c == null, { cat = null; sub = null }, { Text("Todas") }) }
            items(CATALOG) { k -> FilterChip(c == k, { cat = k; sub = null }, { Text(k.name) }) }
        }
        if (c != null && c.subs.size > 1) LazyRow(Modifier.padding(top = 6.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(sub == null, { sub = null }, { Text("Tudo") }) }
            items(c.subs) { s -> FilterChip(sub == s, { sub = s }, { Text(s.name) }) }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.url }) { s ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(s.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(hostName(s.url) + " · " + (if (s.pt) "Português" else "Inglês (traduz para PT-BR)"),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AddButton(Feed(s.name, s.url), vm)
                    }
                }
            }
        }
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
fun GNewsTab(vm: VM) {
    var topic by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(topic, { topic = it }, Modifier.weight(1f), singleLine = true, label = { Text("Assunto (ex.: Fórmula 1, bitcoin)") })
            Spacer(Modifier.width(8.dp))
            Button({ searched = topic.trim() }) { Text("Buscar") }
        }
        if (searched.isNotBlank()) LazyColumn(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Escolha qual versão do feed combina mais com você:", style = MaterialTheme.typography.labelLarge) }
            items(variants(searched), key = { it.feed.url }) { v -> GNewsOption(v, vm) }
        }
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
