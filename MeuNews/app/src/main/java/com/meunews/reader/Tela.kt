@file:OptIn(ExperimentalMaterial3Api::class)

package com.meunews.reader

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.draw.alpha
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

class VM(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("news", 0)
    var feeds by mutableStateOf(load()); private set
    var sel by mutableStateOf<Feed?>(null); private set
    private val cacheFile = java.io.File(app.filesDir, "cache.json")
    private var all by mutableStateOf(loadCache())
    private var store: List<Article> = all
    private val mutex = Mutex()
    private var lastPublish = 0L
    val items: List<Article> by derivedStateOf { sel?.let { f -> all.filter { it.feed == f.url } } ?: all.distinctBy { it.link }.take(400) }
    var loading by mutableStateOf(false); private set
    var translate by mutableStateOf(prefs.getBoolean("tr", true)); private set

    var failed by mutableStateOf(setOf<String>()); private set
    var newCount by mutableStateOf(0); private set
    fun clearNew() { newCount = 0 }

    private fun ak() = "anc_" + (sel?.url ?: "all")
    /** Guarda a notícia que estava no topo da tela quando você parou de rolar. */
    fun saveAnchor(link: String, off: Int) {
        val k = ak(); prefs.edit().putString(k, link).putInt(k + "_o", off).apply()
    }
    /** Posição (índice, deslocamento) onde você parou, achada pela notícia (não pelo número). */
    fun anchorPos(): Pair<Int, Int> {
        val k = ak(); val l = prefs.getString(k, null) ?: return Pair(0, 0)
        val i = items.indexOfFirst { it.link == l }
        return if (i >= 0) Pair(i, prefs.getInt(k + "_o", 0)) else Pair(0, 0)
    }
    val lib = Library(app, viewModelScope)
    var libraryMode by mutableStateOf(0)
    var exploring by mutableStateOf(false)
    var reading by mutableStateOf<Article?>(null)
    var read by mutableStateOf(prefs.getStringSet("read", emptySet())!!.toSet()); private set
    fun markRead(link: String) {
        if (link in read) return
        read = read + link
        prefs.edit().putStringSet("read", read).apply()
    }

    init { Tr.init(app.filesDir); Og.init(app.filesDir); refresh() }

    private fun load(): List<Feed> {
        val raw = prefs.getString("feeds", null)
            ?: return listOf(Feed("IGN", "https://www.ign.com/rss/v2/articles/feed"), googleNewsFeed("Games"))
        val a = JSONArray(raw)
        return (0 until a.length()).map { a.getJSONObject(it).let { o -> Feed(o.getString("t"), o.getString("u")) } }
    }
    private fun save() = prefs.edit().putString("feeds", JSONArray(feeds.map { JSONObject().put("t", it.title).put("u", it.url) }).toString()).apply()

    private fun loadCache(): List<Article> = try {
        if (!prefs.getBoolean("cache_v2", false)) { prefs.edit().putBoolean("cache_v2", true).apply(); cacheFile.delete() }
        val a = JSONArray(cacheFile.readText())
        (0 until a.length()).map {
            a.getJSONObject(it).let { o ->
                Article(o.getString("t"), o.getString("l"), o.optString("s"), o.optString("i").ifBlank { null }, o.optString("o"), o.optLong("d"), o.optString("f"))
            }
        }
    } catch (e: Exception) { emptyList() }

    private fun saveCache(list: List<Article>) {
        try {
            cacheFile.writeText(JSONArray(list.map {
                JSONObject().put("t", it.title).put("l", it.link).put("s", it.summary).put("i", it.image ?: "")
                    .put("o", it.source).put("d", it.time).put("f", it.feed)
            }).toString())
        } catch (e: Exception) { }
    }

    /** Guarda até 30 notícias por fonte, para nenhuma sumir por causa de outras mais novas. */
    private fun merge(old: List<Article>, new: List<Article>): List<Article> {
        val oldTime = old.associate { (it.feed + it.link) to it.time }
        val fixed = new.map { a -> val ot = oldTime[a.feed + a.link]; if (ot != null && ot < a.time) a.copy(time = ot) else a }
        val fresh = fixed.map { it.feed + it.link }.toSet()
        return (fixed + old.filter { (it.feed + it.link) !in fresh }).distinctBy { it.feed + it.link }
            .groupBy { it.feed }.flatMap { (_, v) -> v.sortedByDescending { it.time }.take(30) }
            .sortedByDescending { it.time }
    }

    /** Busca a fonte; se o endereço estiver quebrado, tenta achar o feed certo do site. */
    private suspend fun fetchWithRepair(f: Feed): Pair<Feed, List<Article>> {
        var netErr = false
        fun fetch1(url: String): List<Article> = try {
            parseFeed(Net.get(url), f.title).map { it.copy(feed = url) }
        } catch (e: java.io.IOException) {
            netErr = !(e.message?.startsWith("HTTP 4") ?: false)
            emptyList()
        } catch (e: Exception) { emptyList() }
        val got = fetch1(f.url)
        if (got.isEmpty() && !netErr && !f.url.contains("news.google.com")) {
            val origin = try { java.net.URI(f.url).let { it.scheme + "://" + it.host } } catch (e: Exception) { null }
            val alt = if (origin != null) discoverFeed(origin) else null
            if (alt != null && alt != f.url) {
                val g2 = fetch1(alt)
                if (g2.isNotEmpty()) return Pair(Feed(f.title, alt), g2)
            }
        }
        return Pair(f, got)
    }

    private suspend fun publish(force: Boolean) {
        val now = System.currentTimeMillis()
        if (force || now - lastPublish > 500) {
            lastPublish = now
            val snap = store
            withContext(Dispatchers.Main) { all = snap }
        }
    }

    fun refresh() = viewModelScope.launch {
        loading = true
        val gate = Semaphore(8)
        val base = store.maxOfOrNull { it.time }
        var added = 0
        coroutineScope {
            (sel?.let { listOf(it) } ?: feeds).map { f ->
                async(Dispatchers.IO) {
                    val (feed, got) = gate.withPermit { fetchWithRepair(f) }
                    mutex.withLock {
                        if (feed != f) store = store.filter { it.feed != f.url }
                        if (got.isNotEmpty()) {
                            if (base != null) {
                                val known = store.map { it.feed + it.link }.toHashSet()
                                added += got.count { (it.feed + it.link) !in known && it.time > base }
                            }
                            store = merge(store, got)
                        }
                        publish(false)
                    }
                    withContext(Dispatchers.Main) {
                        if (feed != f) {
                            feeds = feeds.map { if (it == f) feed else it }; save()
                            if (sel == f) sel = feed
                        }
                        failed = if (got.isEmpty()) failed + feed.url else failed - feed.url
                    }
                }
            }.awaitAll()
        }
        mutex.withLock { publish(true) }
        if (added > 0) newCount += added
        withContext(Dispatchers.IO) { saveCache(store) }
        loading = false
    }
    fun select(f: Feed?) { sel = f; newCount = 0; refresh() }
    fun updateTranslate(v: Boolean) { translate = v; prefs.edit().putBoolean("tr", v).apply() }
    fun remove(f: Feed) { feeds = feeds - f; store = store.filter { it.feed != f.url }; all = store; save(); select(null) }
    fun addDirect(f: Feed) { if (feeds.none { it.url == f.url }) { feeds = feeds + f; save(); refresh() } }
    fun exportJson(): String = JSONArray(feeds.map { JSONObject().put("t", it.title).put("u", it.url) }).toString(2)
    fun importJson(text: String): Int {
        val a = JSONArray(text)
        val novos = (0 until a.length()).map { a.getJSONObject(it).let { o -> Feed(o.getString("t"), o.getString("u")) } }
            .distinctBy { it.url }.filter { n -> feeds.none { it.url == n.url } }
        feeds = feeds + novos; save(); refresh()
        return novos.size
    }
    fun add(input: String, googleNews: Boolean, onError: (String) -> Unit) = viewModelScope.launch {
        try {
            val f = withContext(Dispatchers.IO) { if (googleNews) googleNewsFeed(input.trim()) else resolveFeed(input) }
            if (feeds.none { it.url == f.url }) { feeds = feeds + f; save() }
            select(f)
        } catch (e: Exception) { onError(e.message ?: "Erro ao adicionar") }
    }
}

private val Dark = darkColorScheme(
    primary = Color(0xFFFFC400), onPrimary = Color.Black,
    background = Color(0xFF0D0D0F), onBackground = Color(0xFFECECEC),
    surface = Color(0xFF0D0D0F), onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF26262B), onSurfaceVariant = Color(0xFFB4B4BC),
    surfaceContainer = Color(0xFF1B1B1F), surfaceContainerHighest = Color(0xFF1B1B1F),
    secondaryContainer = Color(0xFF34343A), onSecondaryContainer = Color(0xFFF2F2F2)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= 28)
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        Coil.setImageLoader(ImageLoader.Builder(this)
            .okHttpClient(OkHttpClient.Builder().addInterceptor { c ->
                c.proceed(c.request().newBuilder().header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36").build())
            }.build())
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(150L * 1024 * 1024).build() }
            .respectCacheHeaders(false).crossfade(true).build())
        setContent {
            MaterialTheme(colorScheme = Dark) {
                Surface(Modifier.fillMaxSize()) { Box(Modifier.windowInsetsPadding(WindowInsets.displayCutout)) { Gate { NewsApp() } } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsApp(vm: VM = viewModel()) {
    val ctx = LocalContext.current
    LaunchedEffect(vm.lib.message) { vm.lib.message?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); vm.lib.message = null } }
    Box(Modifier.fillMaxSize()) {
        FeedScreen(vm)
        if (vm.exploring) ExploreScreen(vm) { vm.exploring = false }
        if (vm.libraryMode != 0) LibraryScreen(vm) { vm.libraryMode = 0 }
        vm.reading?.let { ReaderScreen(it, vm) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(vm: VM) {
    var showAdd by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    val start = remember { vm.anchorPos() }
    val listState = rememberLazyListState(start.first, start.second)
    val scope = rememberCoroutineScope()
    var atTop by remember { mutableStateOf(start.first == 0 && start.second == 0) }
    val showTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    // ao trocar de fonte, volta para onde você parou nela
    LaunchedEffect(vm.sel) {
        val p = vm.anchorPos(); listState.scrollToItem(p.first, p.second); atTop = p.first == 0 && p.second == 0
    }
    // salva onde você parou toda vez que termina de rolar
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.drop(1).filter { !it }.collect {
            atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            vm.items.getOrNull(listState.firstVisibleItemIndex)?.let { a -> vm.saveAnchor(a.link, listState.firstVisibleItemScrollOffset) }
        }
    }
    LaunchedEffect(listState) { snapshotFlow { listState.firstVisibleItemIndex == 0 }.filter { it }.collect { vm.clearNew() } }
    // se você estava no topo, as novas já aparecem; se estava lendo mais abaixo, aparece o botão
    LaunchedEffect(vm.newCount) { if (vm.newCount > 0 && atTop) { listState.scrollToItem(0); vm.clearNew() } }
    val ctx = LocalContext.current
    val exportL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) try {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray()) }
            Toast.makeText(ctx, "Backup salvo!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(ctx, "Não foi possível salvar", Toast.LENGTH_LONG).show() }
    }
    val importL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val t = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            Toast.makeText(ctx, "${vm.importJson(t)} fontes novas importadas", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(ctx, "Arquivo de backup inválido", Toast.LENGTH_LONG).show() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.sel?.title ?: "Todas as notícias", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    Text("PT-BR", style = MaterialTheme.typography.labelMedium)
                    Switch(vm.translate, { vm.updateTranslate(it) }, Modifier.padding(horizontal = 6.dp))
                    vm.sel?.let { IconButton({ vm.remove(it) }) { Icon(Icons.Default.Delete, "Remover") } }
                    IconButton({ vm.refresh() }) { Icon(Icons.Default.Refresh, "Atualizar") }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Mais") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Favoritos") }, { menu = false; vm.libraryMode = 1 })
                            DropdownMenuItem({ Text("Ler depois (offline)") }, { menu = false; vm.libraryMode = 2 })
                            DropdownMenuItem({ Text("Explorar fontes") }, { menu = false; vm.exploring = true })
                            DropdownMenuItem({ Text("Salvar minhas fontes (backup)") }, { menu = false; exportL.launch("meunews-fontes.json") })
                            DropdownMenuItem({ Text("Restaurar fontes de um backup") }, { menu = false; importL.launch(arrayOf("*/*")) })
                        }
                    }
                })
        },
        floatingActionButton = { FloatingActionButton({ showAdd = true }) { Icon(Icons.Default.Add, "Adicionar") } }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ showSources = true }) { Icon(Icons.Default.Menu, "Fontes") }
            LazyRow(Modifier.weight(1f), contentPadding = PaddingValues(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(vm.sel == null, { vm.select(null) }, { Text("Todas") }) }
                items(vm.feeds) { f -> FilterChip(vm.sel == f, { vm.select(f) }, { Text(f.title) }) }
            }
            }
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (vm.items.isEmpty()) item { EmptyState(vm) }
                items(vm.items, key = { it.link }) { a ->
                    ArticleCard(a, vm.translate, a.link in vm.read, vm.lib.isFav(a.link), vm.lib.isLater(a.link), vm.lib.isOffline(a.link), a.link in vm.lib.busy,
                        { vm.lib.toggleFav(a) }, { vm.lib.toggleLater(a) }) { vm.markRead(a.link); vm.reading = a }
                }
            }
            if (vm.newCount > 0 && showTop) ExtendedFloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0); vm.clearNew() } },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary
            ) { Text("⬆ " + vm.newCount + (if (vm.newCount == 1) " nova" else " novas")) }
            }
        }
    }
    if (showAdd) AddDialog(vm) { showAdd = false }
    if (showSources) SourcesSheet(vm) { showSources = false }
}

@Composable
fun ArticleCard(a: Article, translate: Boolean, read: Boolean, fav: Boolean, later: Boolean, offline: Boolean, busy: Boolean,
                onFav: () -> Unit, onLater: () -> Unit, onClick: () -> Unit) {
    val ct = if (translate) Tr.cached(a.title) else null
    val title by produceState(ct ?: a.title, a, translate) { value = if (translate) withContext(Dispatchers.IO) { Tr.pt(a.title) } else a.title }
    val showSummary = a.summary.length > 30 && !a.summary.startsWith(a.title.take(25))
    val cs = if (translate && showSummary) Tr.cached(a.summary) else null
    val summary by produceState(cs ?: a.summary, a, translate) { value = if (translate && showSummary) withContext(Dispatchers.IO) { Tr.pt(a.summary) } else a.summary }
    val img by produceState(a.image ?: Og.peek(a.link), a) { if (value == null) value = withContext(Dispatchers.IO) { ogImage(a.link) } }
    Card(Modifier.fillMaxWidth().alpha(if (read) 0.5f else 1f).clickable { onClick() }, shape = RoundedCornerShape(16.dp)) {
        Column {
            img?.let { AsyncImage(it, null, Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Crop) }
            Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 14.dp, bottom = 2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (showSummary) Text(summary, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val ago = if (a.time > 0) " · " + DateUtils.getRelativeTimeSpanString(a.time) else ""
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(a.source + ago + (if (read) " · Lida" else "") + (if (offline) " · ⬇ Offline" else ""), Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onFav) {
                        Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favoritar",
                            tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onLater) { Text(if (busy) "⏳" else if (later) "🔖" else "📑") }
                }
            }
        }
    }
}

@Composable
fun AddDialog(vm: VM, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var news by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    AlertDialog(onDismissRequest = onClose,
        title = { Text("Adicionar fonte") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!news, { news = false }, { Text("Site / RSS") })
                    FilterChip(news, { news = true }, { Text("Google News") })
                }
                OutlinedTextField(text, { text = it }, Modifier.padding(top = 12.dp), singleLine = true,
                    label = { Text(if (news) "Assunto (ex.: inteligência artificial)" else "Endereço do site ou do feed") })
            }
        },
        confirmButton = {
            TextButton({
                if (text.isNotBlank()) { vm.add(text, news) { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }; onClose() }
            }) { Text("Adicionar") }
        },
        dismissButton = { TextButton(onClose) { Text("Cancelar") } })
}

@Composable
fun EmptyState(vm: VM) {
    val f = vm.sel
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (vm.loading) Text("Carregando notícias…")
        else if (f != null && f.url in vm.failed) {
            Text("Essa fonte não retornou notícias", fontWeight = FontWeight.SemiBold)
            Text("O site pode ter desativado o RSS ou estar bloqueando o app.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ vm.refresh() }) { Text("Tentar de novo") }
                Button({ vm.remove(f) }) { Text("Remover fonte") }
            }
        } else Text("Nenhuma notícia ainda. Toque em ↻ para atualizar.")
    }
}
