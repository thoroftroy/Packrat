package dev.wonderdesk.shared

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.json.JSONArray

@Composable
fun CalculatorScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("calculations", Context.MODE_PRIVATE) }
    var input by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    var history by remember {
        mutableStateOf(runCatching { val array = JSONArray(prefs.getString("history", "[]")); (0 until array.length()).map { array.getString(it) } }.getOrDefault(emptyList()))
    }
    fun evaluate() {
        try {
            result = Expression.format(Expression.evaluate(input)); error = ""
            history = (listOf("$input = $result") + history).take(50)
            prefs.edit().putString("history", JSONArray(history).toString()).apply()
        } catch (e: Exception) { error = e.message ?: "Invalid expression"; result = "" }
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { OutlinedTextField(input, { input = it.take(512) }, Modifier.fillMaxWidth(), label = { Text("Expression") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { evaluate() }), isError = error.isNotEmpty()) }
        if (error.isNotEmpty()) item { Text(error, color = MaterialTheme.colorScheme.error) }
        if (result.isNotEmpty()) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(result, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { context.copyText("Calculation result", result) }) { Text("Copy") }
        } }
        item { Text("Scientific functions use radians. Supports + − * / % ^, parentheses, pi and e.", style = MaterialTheme.typography.bodySmall) }
        item { Row(Modifier.fillMaxWidth()) {
            listOf("sqrt(", "sin(", "cos(", "ln(").forEach { key -> TextButton(onClick = { input += key }, Modifier.weight(1f)) { Text(key) } }
        } }
        items(listOf(listOf("7", "8", "9", "/"), listOf("4", "5", "6", "*"), listOf("1", "2", "3", "-"), listOf("0", ".", "^", "+"), listOf("(", ")", "C", "="))) { keys ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                keys.forEach { key ->
                    val onKey: () -> Unit = { when (key) { "=" -> evaluate(); "C" -> { input = ""; result = ""; error = "" }; else -> if (input.length < 512) input += key } }
                    if (key == "=") AquaButton(onClick = onKey, modifier = Modifier.weight(1f), contentPadding = PaddingValues(6.dp)) { Text(key) }
                    else AquaSecondaryButton(onClick = onKey, modifier = Modifier.weight(1f), contentPadding = PaddingValues(6.dp)) { Text(key) }
                }
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("History", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { history = emptyList(); prefs.edit().remove("history").apply() }, enabled = history.isNotEmpty()) { Text("Clear history") }
        } }
        items(history) { line -> TextButton(onClick = { input = line.substringBefore(" = ") }, Modifier.fillMaxWidth()) { Text(line) } }
    }
}
