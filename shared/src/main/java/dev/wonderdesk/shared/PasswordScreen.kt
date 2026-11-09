package dev.wonderdesk.shared

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.security.SecureRandom

object Passwords {
    fun generate(length: Int, symbols: Boolean, random: SecureRandom = SecureRandom()): String {
        require(length in 12..64)
        val pools = mutableListOf("abcdefghijkmnpqrstuvwxyz", "ABCDEFGHJKLMNPQRSTUVWXYZ", "23456789")
        if (symbols) pools += "!@#$%&*+-=?"
        val alphabet = pools.joinToString("")
        val chars = pools.map { it[random.nextInt(it.length)] }.toMutableList()
        while (chars.size < length) chars += alphabet[random.nextInt(alphabet.length)]
        for (i in chars.lastIndex downTo 1) { val j = random.nextInt(i + 1); val old = chars[i]; chars[i] = chars[j]; chars[j] = old }
        return chars.joinToString("")
    }
}

@Composable
fun PasswordScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val op = rememberOperation()
    var length by remember { mutableFloatStateOf(24f) }
    var symbols by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    var copied by remember { mutableStateOf<String?>(null) }
    fun clearOwnClipboard() {
        val value = copied
        if (value != null && clipboard.primaryClip?.getItemAt(0)?.text?.toString() == value) clipboard.clearPrimaryClip()
        copied = null
    }
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { password = ""; visible = false } }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer); clearOwnClipboard(); handler.removeCallbacksAndMessages(null); activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Try the password generator", style = MaterialTheme.typography.titleLarge)
        Text("Vault does not store credentials yet. It is not a reviewed password manager. Generated values stay in memory only and are cleared when you leave.")
        Text("Length: ${length.toInt()} characters")
        Slider(length, { length = it }, valueRange = 12f..64f, steps = 51)
        Row { Checkbox(symbols, { symbols = it }); Text("Include symbols", Modifier.padding(top = 12.dp)) }
        AquaButton(onClick = { password = Passwords.generate(length.toInt(), symbols); visible = false; op.notice("Generated locally using SecureRandom. Not saved.") }) { Text("Generate password") }
        if (password.isNotEmpty()) {
            OutlinedTextField(password, {}, Modifier.fillMaxWidth(), readOnly = true, label = { Text("Generated password (not saved)") }, visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AquaSecondaryButton(onClick = { visible = !visible }) { Text(if (visible) "Hide" else "Reveal") }
                AquaSecondaryButton(onClick = {
                    val clip = ClipData.newPlainText("Generated password", password)
                    if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
                    clipboard.setPrimaryClip(clip); copied = password
                    handler.removeCallbacksAndMessages(null)
                    handler.postDelayed({ clearOwnClipboard() }, 30_000)
                    op.notice("Copied. Wonderdesk tries to clear this exact clipboard value after 30 seconds. Other clipboard apps may retain it.")
                }) { Text("Copy for 30 s") }
                TextButton(onClick = { password = ""; visible = false; clearOwnClipboard() }) { Text("Clear") }
            }
        }
        OperationStatus(op)
        Text("No network, logs, autofill service, credential database or recovery claims. Use a mature password manager for storage until a reviewed vault is implemented.", style = MaterialTheme.typography.bodySmall)
    }
}
