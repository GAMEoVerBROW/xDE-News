@file:OptIn(ExperimentalMaterial3Api::class)

package com.meunews.reader

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.*

object Reader {
    private val junk = Regex("(?is)<(script|style|nav|header|footer|aside|form|noscript|svg|figure)\\b.*?</\\1>")
    private val article = Regex("(?is)<article\\b.*?</article>")
    private val para = Regex("(?is)<p\\b[^>]*>(.*?)</p>")
    private val spaces = Regex("\\s+")

    /** Extrai os parágrafos do texto principal da matéria. */
    fun extract(link: String): List<String> {
        val html = junk.replace(Net.get(link).take(600000), " ")
        val body = article.find(html)?.value ?: html
        return para.findAll(body).map { spaces.replace(plain(it.groupValues[1]), " ").trim() }
            .filter { it.length > 60 }.distinct().take(80).toList()
    }
}

@Composable
fun WebPage(url: String) {
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
        WebView(c).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            loadUrl(url)
        }
    })
}

@Composable
fun ReaderScreen(a: Article, vm: VM) {
    val ctx = LocalContext.current
    BackHandler { vm.reading = null }
    var translate by remember(a) { mutableStateOf(vm.translate) }
    var paras by remember(a) { mutableStateOf<List<String>?>(null) }
    var done by remember(a) { mutableStateOf<List<String>?>(null) }
    val img by produceState(a.image, a) { if (value == null) value = withContext(Dispatchers.IO) { ogImage(a.link) } }
    val title by produceState(a.title, a, translate) { value = if (translate) withContext(Dispatchers.IO) { Tr.pt(a.title) } else a.title }
    LaunchedEffect(a) {
        paras = withContext(Dispatchers.IO) { try { Reader.extract(a.link) } catch (e: Exception) { emptyList() } }
    }
    LaunchedEffect(paras, translate) {
        val p = paras
        if (translate && p != null && p.size >= 3 && done == null) {
            done = withContext(Dispatchers.IO) {
                val out = mutableListOf<String>()
                for (g in p.chunked(6)) out += coroutineScope { g.map { x -> async { Tr.pt(x) } }.awaitAll() }
                out
            }
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(a.source, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton({ vm.reading = null }) { Icon(Icons.Default.ArrowBack, "Voltar") } },
                actions = {
                    Text("PT-BR", style = MaterialTheme.typography.labelMedium)
                    Switch(translate, { translate = it }, Modifier.padding(horizontal = 6.dp))
                    IconButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.link))) }) { Icon(Icons.Default.ExitToApp, "Abrir no navegador") }
                })
            val p = paras
            when {
                p == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                p.size < 3 -> { Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }; LaunchedEffect(Unit) { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.link))); vm.reading = null } }
                else -> {
                    val shown = if (translate) (done ?: p) else p
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        item { img?.let { AsyncImage(it, null, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop) } }
                        item { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                        item { Text(a.source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                        if (translate && done == null) item {
                            Column { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Traduzindo…", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium) }
                        }
                        items(shown) { Text(it, style = MaterialTheme.typography.bodyLarge, lineHeight = 26.sp) }
                        item {
                            OutlinedButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.link))) }, Modifier.fillMaxWidth()) { Text("Abrir original no navegador") }
                        }
                    }
                }
            }
        }
    }
}

private fun hostOf(u: String) = try { java.net.URI(u).host ?: u } catch (e: Exception) { u }

@Composable
fun SourcesSheet(vm: VM, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val st = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = st) {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true,
            label = { Text("Buscar fonte") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f, fill = false)) {
            if (q.isBlank()) item {
                ListItem(headlineContent = { Text("Todas as fontes", fontWeight = FontWeight.SemiBold) },
                    trailingContent = { if (vm.sel == null) Text("✓", color = MaterialTheme.colorScheme.primary) },
                    colors = colors, modifier = Modifier.clickable { vm.select(null); onClose() })
            }
            items(vm.feeds.filter { it.title.contains(q, ignoreCase = true) }.sortedBy { it.title.lowercase() }) { f ->
                ListItem(headlineContent = { Text(f.title) }, supportingContent = { Text(hostOf(f.url)) },
                    trailingContent = { if (vm.sel == f) Text("✓", color = MaterialTheme.colorScheme.primary) },
                    colors = colors, modifier = Modifier.clickable { vm.select(f); onClose() })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
