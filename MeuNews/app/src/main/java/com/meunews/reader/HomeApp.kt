@file:OptIn(ExperimentalMaterial3Api::class)

package com.meunews.reader

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.view.WindowManager
import android.widget.Toast
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
    val items: List<Article> get() = sel?.let { f -> all.filter { it.feed == f.url } } ?: all
    var loading by mutableStateOf(false); private set
    var translate by mutableStateOf(prefs.getBoolean("tr", true)); private set

    var exploring by mutableStateOf(false)
    var reading by mutableStateOf<Article?>(null)
    var read by mutableStateOf(prefs.getStringSet("read", emptySet())!!.toSet()); private set
    fun markRead(link: String) {
        if (link in read) return
        read = read + link
        prefs.edit().putStringSet("read", read).apply()
    }

    init { refresh() }

    private fun load(): List<Feed> {
        val raw = prefs.getString("feeds", null)
            ?: return listOf(Feed("IGN", "https://www.ign.com/rss/v2/articles/feed"), googleNewsFeed("Games"))
        val a = JSONArray(raw)
        return (0 until a.length()).map { a.getJSONObject(it).let { o -> Feed(o.getString("t"), o.getString("u")) } }
    }
    private fun save() = prefs.edit().putString("feeds", JSONArray(feeds.map { JSONObject().put("t", it.title).put("u", it.url) }).toString()).apply()

    private fun loadCache(): List<Article> = try {
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

    private fun merge(old: List<Article>, new: List<Article>): List<Article> {
        val fresh = new.map { it.link }.toSet()
        return (new + old.filter { it.link !in fresh }).distinctBy { it.link }.sortedByDescending { it.time }.take(500)
    }

    fun refresh() = viewModelScope.launch {
        loading = true
        val gate = Semaphore(8)
        coroutineScope {
            (sel?.let { listOf(it) } ?: feeds).map { f ->
                async(Dispatchers.IO) {
                    val got = gate.withPermit {
                        try { parseFeed(Net.get(f.url), f.title).map { it.copy(feed = f.url) } } catch (e: Exception) { emptyList() }
                    }
                    if (got.isNotEmpty()) withContext(Dispatchers.Main) { all = merge(all, got) }
                }
            }.awaitAll()
        }
        withContext(Dispatchers.IO) { saveCache(all) }
        loading = false
    }
    fun select(f: Feed?) { sel = f; refresh() }
    fun updateTranslate(v: Boolean) { translate = v; prefs.edit().putBoolean("tr", v).apply() }
    fun remove(f: Feed) { feeds = feeds - f; all = all.filter { it.feed != f.url }; save(); select(null) }
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
    Box(Modifier.fillMaxSize()) {
        FeedScreen(vm)
        vm.reading?.let { ReaderScreen(it, vm) }
        if (vm.exploring) ExploreScreen(vm) { vm.exploring = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(vm: VM) {
    var showAdd by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
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
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(vm.items, key = { it.link }) { a -> ArticleCard(a, vm.translate, a.link in vm.read) { vm.markRead(a.link); vm.reading = a } }
            }
        }
    }
    if (showAdd) AddDialog(vm) { showAdd = false }
    if (showSources) SourcesSheet(vm) { showSources = false }
}

@Composable
fun ArticleCard(a: Article, translate: Boolean, read: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val title by produceState(a.title, a, translate) { value = if (translate) withContext(Dispatchers.IO) { Tr.pt(a.title) } else a.title }
    val showSummary = a.summary.length > 30 && !a.summary.startsWith(a.title.take(25))
    val summary by produceState(a.summary, a, translate) { value = if (translate && showSummary) withContext(Dispatchers.IO) { Tr.pt(a.summary) } else a.summary }
    val img by produceState(a.image, a) { if (value == null) value = withContext(Dispatchers.IO) { ogImage(a.link) } }
    Card(Modifier.fillMaxWidth().alpha(if (read) 0.5f else 1f).clickable { onClick() },
        shape = RoundedCornerShape(16.dp)) {
        Column {
            img?.let { AsyncImage(it, null, Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Crop) }
            Column(Modifier.padding(14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (showSummary) Text(summary, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val ago = if (a.time > 0) " · " + DateUtils.getRelativeTimeSpanString(a.time) else ""
                Text(a.source + ago + (if (read) " · Lida" else ""), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
