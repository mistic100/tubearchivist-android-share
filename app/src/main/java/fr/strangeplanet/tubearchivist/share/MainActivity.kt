package fr.strangeplanet.tubearchivist.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.Pair;
import java.io.IOException
import java.security.GeneralSecurityException

class MainActivity : ComponentActivity() {
    private var sharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedText = extractSharedText(intent)
        setContent {
            TubeArchivistShareApp(
                store = remember { SecureConfigStore(applicationContext) },
                incomingShare = sharedText,
                onShareFinished = { finish() },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText = extractSharedText(intent)
    }

    private fun extractSharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val extraText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        return extraText ?: intent.dataString
    }
}

@Composable
private fun TubeArchivistShareApp(
    store: SecureConfigStore,
    incomingShare: String?,
    onShareFinished: () -> Unit,
) {
    val api = remember { TubeArchivistApi() }
    val initialRead = remember { store.read() }
    var configRead by remember { mutableStateOf(initialRead) }
    var showSettings by remember { mutableStateOf(initialRead !is ConfigReadResult.Loaded) }
    val background = colorResource(R.color.app_background)
    val highlight = colorResource(R.color.app_highlight)
    val accent = colorResource(R.color.app_accent)
    val text = colorResource(R.color.app_text)
    val colorScheme = darkColorScheme(
        primary = accent,
        onPrimary = text,
        primaryContainer = highlight,
        onPrimaryContainer = text,
        secondary = accent,
        onSecondary = text,
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        surfaceVariant = highlight,
        onSurfaceVariant = text,
        surfaceContainerLowest = background,
        surfaceContainerLow = highlight,
        surfaceContainer = highlight,
        surfaceContainerHigh = highlight,
        surfaceContainerHighest = highlight,
        outline = accent,
    )

    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            when (val currentConfig = configRead) {
                is ConfigReadResult.Loaded -> {
                    if (showSettings) {
                        SettingsScreen(
                            initialConfig = currentConfig.config,
                            store = store,
                            api = api,
                            onSaved = { saved ->
                                configRead = ConfigReadResult.Loaded(saved)
                                showSettings = false
                            },
                            onCancel = { showSettings = false },
                        )
                    } else if (incomingShare != null) {
                        ShareScreen(
                            sharedText = incomingShare,
                            config = currentConfig.config,
                            api = api,
                            onDone = onShareFinished,
                        )
                    } else {
                        HomeScreen(
                            config = currentConfig.config,
                            onSettings = { showSettings = true }
                        )
                    }
                }
                is ConfigReadResult.Empty -> SettingsScreen(
                    initialConfig = null,
                    store = store,
                    api = api,
                    onSaved = { saved ->
                        configRead = ConfigReadResult.Loaded(saved)
                        showSettings = false
                    },
                    onCancel = null,
                )
                is ConfigReadResult.Failed -> SettingsScreen(
                    initialConfig = null,
                    store = store,
                    api = api,
                    storageError = currentConfig.message,
                    onSaved = { saved ->
                        configRead = ConfigReadResult.Loaded(saved)
                        showSettings = false
                    },
                    onCancel = null,
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    config: InstanceConfig,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("TubeArchivist Share", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text("Ready to receive a YouTube link from the Android share menu.")
        Spacer(Modifier.height(24.dp))
        Text("Connected to " + config.baseUrl)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onSettings) {
            Text("Instance settings")
        }
    }
}

@Composable
private fun SettingsScreen(
    initialConfig: InstanceConfig?,
    store: SecureConfigStore,
    api: TubeArchivistApi,
    onSaved: (InstanceConfig) -> Unit,
    onCancel: (() -> Unit)?,
    storageError: String? = null,
) {
    var baseUrl by remember(initialConfig) { mutableStateOf(initialConfig?.baseUrl.orEmpty()) }
    var apiToken by remember(initialConfig) { mutableStateOf(initialConfig?.apiToken.orEmpty()) }
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(if (storageError != null) Pair(storageError, true) else null) }
    var busy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (initialConfig == null) "Connect to TubeArchivist" else "Instance settings",
            style = MaterialTheme.typography.headlineSmall,
        )
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it; status = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Instance URL") },
            placeholder = { Text("https://tubearchivist.example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = apiToken,
            onValueChange = { apiToken = it; status = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        status?.let {
            if (it.second) {
                WarningCard(it.first)
            } else {
                SuccessCard(it.first)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = !busy && baseUrl.isNotBlank() && apiToken.isNotBlank(),
                onClick = {
                    busy = true
                    status = null
                },
            ) {
                Text(if (busy) "Testing..." else "Test connection")
            }
            Button(
                enabled = !busy && baseUrl.isNotBlank() && apiToken.isNotBlank(),
                onClick = {
                    val config = InstanceConfig(baseUrl.trim().trimEnd('/'), apiToken.trim())
                    try {
                        api.validateConfig(config)
                        store.write(config)
                        onSaved(config)
                    } catch (error: ApiException) {
                        status = Pair(error.message ?: "Enter valid instance settings.", true)
                    } catch (error: GeneralSecurityException) {
                        status = Pair(error.message ?: "Could not encrypt the saved credentials.", true)
                    } catch (error: IOException) {
                        status = Pair(error.message ?: "Could not save the instance settings.", true)
                    }
                },
            ) {
                Text("Save")
            }
        }
        if (busy) {
            LaunchedEffect(baseUrl, apiToken) {
                val result = withContext(Dispatchers.IO) {
                    try {
                        api.testConnection(InstanceConfig(baseUrl, apiToken.trim()))
                        Pair("Connection successful.", false)
                    } catch (error: ApiException) {
                        Pair(error.message ?: "Connection test failed.", true)
                    }
                }
                status = result
                busy = false
            }
        }
        onCancel?.let { cancel ->
            TextButton(onClick = cancel) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun ShareScreen(
    sharedText: String,
    config: InstanceConfig,
    api: TubeArchivistApi,
    onDone: () -> Unit,
) {
    val parsed = remember(sharedText) { YouTubeLinkParser.parse(sharedText) }
    var confirmed by remember(sharedText) { mutableStateOf(false) }
    var retryCount by remember(sharedText) { mutableIntStateOf(0) }
    var state by remember(sharedText) { mutableStateOf<ShareState>(ShareState.Checking) }

    val link = (parsed as? LinkParseResult.Valid)?.link
    val needsConfirmation = link?.kind == YouTubeLinkKind.COLLECTION

    LaunchedEffect(sharedText, config, confirmed, retryCount) {
        when (parsed) {
            is LinkParseResult.Invalid -> state = ShareState.Failed(parsed.reason)
            is LinkParseResult.Valid -> {
                if (needsConfirmation && !confirmed) {
                    state = ShareState.ConfirmationRequired
                } else {
                    state = ShareState.Sending
                    try {
                        withContext(Dispatchers.IO) {
                            api.enqueue(config, parsed.link)
                        }
                        state = ShareState.Accepted
                    } catch (error: ApiException) {
                        state = ShareState.Failed(error.message ?: "Could not send the link.")
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Send to TubeArchivist", style = MaterialTheme.typography.headlineSmall)
        Text(sharedText, style = MaterialTheme.typography.bodyMedium)

        when (val currentState = state) {
            ShareState.Checking, ShareState.Sending -> {
                CircularProgressIndicator()
                Text(if (currentState == ShareState.Checking) "Checking shared link..." else "Sending request...")
            }
            ShareState.ConfirmationRequired -> {
                WarningCard(
                    "This playlist or channel may add many videos to TubeArchivist. Continue?"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { confirmed = true }) {
                        Text("Continue")
                    }
                    OutlinedButton(onClick = onDone) {
                        Text("Cancel")
                    }
                }
            }
            ShareState.Accepted -> {
                SuccessCard("Queued for processing.")
                Text("The item was successfully sent to TubeArchivist.")
                Button(onClick = onDone) {
                    Text("Done")
                }
            }
            is ShareState.Failed -> {
                WarningCard(currentState.message)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { retryCount++ }) {
                        Text("Retry")
                    }
                    OutlinedButton(onClick = onDone) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SuccessCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private sealed class ShareState {
    data object Checking : ShareState()
    data object ConfirmationRequired : ShareState()
    data object Sending : ShareState()
    data object Accepted : ShareState()
    data class Failed(val message: String) : ShareState()
}
