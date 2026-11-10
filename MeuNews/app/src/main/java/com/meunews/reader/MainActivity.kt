@file:OptIn(ExperimentalMaterial3Api::class)

package com.meunews.reader

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
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
import org.json.JSONArray
import org.json.JSONObject

class VM(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("news", 0)
    var feeds by mutableStateOf(load()); private set
    var sel by mutableStateOf<Feed?>(null); private set
    var items by mutableStateOf(listOf<Article>()); private set
    var loading by mutableStateOf(false); private set
    var translate by mutableStateOf(prefs.getBoolean("tr", true)); private set

    init { refresh() }

    private fun load(): List<Feed> {
        val raw = prefs.getString("feeds", null)
            ?: return listOf(Feed("G1", "https://g1.globo.com/rss/g1/"), googleNewsFeed("tecnologia"))
        val a = JSONArray(raw)
        return (0 until a.length()).map { a.getJSONObject(it).let { o -> Feed(o.getString("t"), o.getString("u")) } }
    }
    private fun save() = prefs.edit().putString("feeds", JSONArray(feeds.map { JSONObject().put("t", it.title).put("u", it.url) }).toString()).apply()

    fun refresh() = viewModelScope.launch {
        loading = true
        items = withContext(Dispatchers.IO) {
            (sel?.let { listOf(it) } ?: feeds)
                .map { f -> async { runCatching { parseFeed(Net.get(f.url), f.title) }.getOrDefault(emptyList()) } }
                .awaitAll().flatten().distinctBy { it.link }.sortedByDescending { it.time }.take(200)
        }
        loading = false
    }
    fun select(f: Feed?) { sel = f; refresh() }
    fun setTranslate(v: Boolean) { translate = v; prefs.edit().putBoolean("tr", v).apply() }
    fun remove(f: Feed) { feeds = feeds - f; save(); select(null) }
    fun add(input: String, googleNews: Boolean, onError: (String) -> Unit) = viewModelScope.launch {
        try {
            val f = withContext(Dispatchers.IO) { if (googleNews) googleNewsFeed(input.trim()) else resolveFeed(input) }
            if (feeds.none { it.url == f.url }) { feeds = feeds + f; save() }
            select(f)
        } catch (e: Exception) { onError(e.message ?: "Erro ao adicionar") }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            val ctx = LocalContext.current
            val scheme = if (android.os.Build.VERSION.SDK_INT >= 31) { if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx) } else { if (dark) darkColorScheme() else lightColorScheme() }
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) { NewsApp() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsApp(vm: VM = viewModel()) {
    var showAdd by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.sel?.title ?: "Todas as notícias", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    Text("PT-BR", style = MaterialTheme.typography.labelMedium)
                    Switch(vm.translate, { vm.setTranslate(it) }, Modifier.padding(horizontal = 6.dp))
                    vm.sel?.let { IconButton({ vm.remove(it) }) { Icon(Icons.Default.Delete, "Remover") } }
                    IconButton({ vm.refresh() }) { Icon(Icons.Default.Refresh, "Atualizar") }
                })
        },
        floatingActionButton = { FloatingActionButton({ showAdd = true }) { Icon(Icons.Default.Add, "Adicionar") } }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(vm.sel == null, { vm.select(null) }, { Text("Todas") }) }
                items(vm.feeds) { f -> FilterChip(vm.sel == f, { vm.select(f) }, { Text(f.title) }) }
            }
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(vm.items, key = { it.link }) { ArticleCard(it, vm.translate) }
            }
        }
    }
    if (showAdd) AddDialog(vm) { showAdd = false }
}

@Composable
fun ArticleCard(a: Article, translate: Boolean) {
    val ctx = LocalContext.current
    val title by produceState(a.title, a, translate) { value = if (translate) withContext(Dispatchers.IO) { Tr.pt(a.title) } else a.title }
    val showSummary = a.summary.length > 30 && !a.summary.startsWith(a.title.take(25))
    val summary by produceState(a.summary, a, translate) { value = if (translate && showSummary) withContext(Dispatchers.IO) { Tr.pt(a.summary) } else a.summary }
    val img by produceState(a.image, a) { if (value == null) value = withContext(Dispatchers.IO) { ogImage(a.link) } }
    Card(Modifier.fillMaxWidth().clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.link))) },
        shape = RoundedCornerShape(16.dp)) {
        Column {
            img?.let { AsyncImage(it, null, Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Crop) }
            Column(Modifier.padding(14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (showSummary) Text(summary, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val ago = if (a.time > 0) " · " + DateUtils.getRelativeTimeSpanString(a.time) else ""
                Text(a.source + ago, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
