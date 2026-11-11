@file:OptIn(ExperimentalMaterial3Api::class)

package com.meunews.reader

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class OfflineDoc(val title: String, val paras: List<String>, val translated: List<String>)

fun articleToJson(a: Article) = JSONObject().put("t", a.title).put("l", a.link).put("s", a.summary)
    .put("i", a.image ?: "").put("o", a.source).put("d", a.time).put("f", a.feed)

fun jsonToArticle(o: JSONObject) = Article(o.getString("t"), o.getString("l"), o.optString("s"),
    o.optString("i").ifBlank { null }, o.optString("o"), o.optLong("d"), o.optString("f"))

/** Favoritos e "Ler depois" (com texto e imagem baixados para ler sem internet). */
class Library(app: Application, private val scope: CoroutineScope) {
    private val dir = File(app.filesDir, "lib").apply { mkdirs() }

    private fun readList(name: String): List<Article> = try {
        val a = JSONArray(File(dir, name).readText())
        (0 until a.length()).map { jsonToArticle(a.getJSONObject(it)) }
    } catch (e: Exception) { emptyList() }

    private fun writeList(name: String, l: List<Article>) {
        try { File(dir, name).writeText(JSONArray(l.map { articleToJson(it) }).toString()) } catch (e: Exception) { }
    }

    private fun key(link: String) = java.security.MessageDigest.getInstance("MD5").digest(link.toByteArray()).joinToString("") { "%02x".format(it) }

    var favs by mutableStateOf(readList("fav.json")); private set
    var later by mutableStateOf(readList("later.json")); private set
    var offline by mutableStateOf(dir.list()?.filter { it.endsWith(".doc") }?.map { it.removeSuffix(".doc") }?.toSet() ?: emptySet<String>()); private set
    var busy by mutableStateOf(setOf<String>()); private set
    var message by mutableStateOf<String?>(null)

    fun isFav(link: String) = favs.any { it.link == link }
    fun isLater(link: String) = later.any { it.link == link }
    fun isOffline(link: String) = key(link) in offline

    fun toggleFav(a: Article) {
        favs = if (isFav(a.link)) favs.filter { it.link != a.link } else listOf(a) + favs
        writeList("fav.json", favs)
    }

    fun toggleLater(a: Article) {
        if (isLater(a.link)) {
            later = later.filter { it.link != a.link }
            writeList("later.json", later)
            val k = key(a.link)
            File(dir, "$k.doc").delete(); File(dir, "$k.img").delete()
            offline = offline - k
        } else {
            later = listOf(a) + later
            writeList("later.json", later)
            scope.launch {
                busy = busy + a.link
                val ok = download(a)
                busy = busy - a.link
                message = if (ok) "Baixado para ler offline" else "Salvo em Ler depois (esse site não permite baixar o texto)"
            }
        }
    }

    fun doc(link: String): OfflineDoc? {
        if (!isOffline(link)) return null
        return try {
            val o = JSONObject(File(dir, key(link) + ".doc").readText())
            fun l(n: String) = o.getJSONArray(n).let { a -> (0 until a.length()).map { a.getString(it) } }
            OfflineDoc(o.optString("title"), l("p"), l("t"))
        } catch (e: Exception) { null }
    }

    fun imageFile(link: String): File? = File(dir, key(link) + ".img").takeIf { it.exists() }

    private suspend fun download(a: Article): Boolean = withContext(Dispatchers.IO) {
        try {
            val paras = Reader.extract(a.link)
            if (paras.size < 3) return@withContext false
            val tr = mutableListOf<String>()
            for (g in paras.chunked(6)) tr += coroutineScope { g.map { x -> async { Tr.pt(x) } }.awaitAll() }
            val k = key(a.link)
            val img = a.image ?: Og.peek(a.link) ?: ogImage(a.link)
            if (img != null) try { File(dir, "$k.img").writeBytes(Net.bytes(img)) } catch (e: Exception) { }
            val o = JSONObject().put("title", Tr.pt(a.title)).put("p", JSONArray(paras)).put("t", JSONArray(tr))
            File(dir, "$k.doc").writeText(o.toString())
            withContext(Dispatchers.Main) { offline = offline + k }
            true
        } catch (e: Exception) { false }
    }
}

@Composable
fun LibraryScreen(vm: VM, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var tab by remember { mutableIntStateOf(if (vm.libraryMode == 2) 1 else 0) }
    val list = if (tab == 0) vm.lib.favs else vm.lib.later
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(title = { Text("Biblioteca") }, navigationIcon = { IconButton(onClose) { Icon(Icons.Default.ArrowBack, "Voltar") } })
            TabRow(tab) {
                Tab(tab == 0, { tab = 0 }) { Text("Favoritos (" + vm.lib.favs.size + ")", Modifier.padding(14.dp)) }
                Tab(tab == 1, { tab = 1 }) { Text("Ler depois (" + vm.lib.later.size + ")", Modifier.padding(14.dp)) }
            }
            if (list.isEmpty()) Text(
                if (tab == 0) "Toque no coração de uma notícia para guardá-la aqui."
                else "Toque em 📑 numa notícia para ler depois. O texto é baixado para você ler sem internet.",
                Modifier.padding(32.dp))
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.link }) { a ->
                    ArticleCard(a, vm.translate, a.link in vm.read, vm.lib.isFav(a.link), vm.lib.isLater(a.link), vm.lib.isOffline(a.link), a.link in vm.lib.busy,
                        { vm.lib.toggleFav(a) }, { vm.lib.toggleLater(a) }) { vm.markRead(a.link); vm.reading = a }
                }
            }
        }
    }
}
