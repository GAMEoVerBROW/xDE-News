package com.meunews.reader

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.security.MessageDigest

private const val HASH = "5cbac1e02f18524b29aa3da866a7f8a3e469f2ec0762dc6ef07a3ced2439d04b"

private fun sha256(s: String) =
    MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

/** Pede a senha só na primeira vez; depois o app lembra. */
@Composable
fun Gate(content: @Composable () -> Unit) {
    val prefs = LocalContext.current.getSharedPreferences("gate", Context.MODE_PRIVATE)
    var ok by remember { mutableStateOf(prefs.getString("k", "") == HASH) }
    if (ok) content() else {
        var text by remember { mutableStateOf("") }
        var err by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("Meu News", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Digite a senha de acesso", Modifier.padding(top = 8.dp, bottom = 24.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(text, { text = it; err = false }, Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = PasswordVisualTransformation(), isError = err, label = { Text("Senha") },
                supportingText = { if (err) Text("Senha incorreta") })
            Button({
                if (sha256(text.trim()) == HASH) { prefs.edit().putString("k", HASH).apply(); ok = true } else err = true
            }, Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Entrar") }
        }
    }
}
