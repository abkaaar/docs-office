package com.docsoffice.app.feature.vault

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsoffice.app.core.designsystem.components.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onBack: () -> Unit,
    onOpenDocument: (String) -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.lock()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vault") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Back" },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.unlocked) {
                        IconButton(
                            onClick = viewModel::lock,
                            modifier = Modifier.semantics { contentDescription = "Lock vault" },
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock vault")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.setupPinMode || !state.hasPin -> {
                    PinSetup(
                        error = state.error,
                        onSetPin = viewModel::setPin,
                    )
                }
                !state.unlocked -> {
                    UnlockPane(
                        error = state.error,
                        onUnlockPin = viewModel::unlockWithPin,
                        onBiometric = {
                            launchBiometric(
                                activity = context as? FragmentActivity,
                                onSuccess = viewModel::unlockAfterBiometric,
                                onError = {
                                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                },
                            )
                        },
                    )
                }
                else -> {
                    if (state.items.isEmpty()) {
                        Text(
                            "Vault is empty. In Files, select documents and tap Move to vault.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.items, key = { it.id }) { item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.openItem(item.id, onOpenDocument)
                                        }
                                        .semantics {
                                            contentDescription = "Vault item ${item.displayName}"
                                        }
                                        .padding(vertical = 8.dp),
                                ) {
                                    Text(item.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${item.extension.uppercase()} · ${formatBytes(item.sizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Row {
                                        TextButton(onClick = { viewModel.moveOut(item.id) }) {
                                            Text("Move out")
                                        }
                                        TextButton(onClick = { viewModel.deleteItem(item.id) }) {
                                            Text("Delete")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PinSetup(
    error: String?,
    onSetPin: (String, String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Text("Create a vault PIN", style = MaterialTheme.typography.titleMedium)
    Text("Use 4–12 digits. Biometrics can unlock after setup.")
    OutlinedTextField(
        value = pin,
        onValueChange = { if (it.length <= 12 && it.all(Char::isDigit)) pin = it },
        label = { Text("PIN") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "New vault PIN" },
    )
    OutlinedTextField(
        value = confirm,
        onValueChange = { if (it.length <= 12 && it.all(Char::isDigit)) confirm = it },
        label = { Text("Confirm PIN") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Confirm vault PIN" },
    )
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(
        onClick = { onSetPin(pin, confirm) },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Save vault PIN" },
    ) { Text("Save PIN") }
}

@Composable
private fun UnlockPane(
    error: String?,
    onUnlockPin: (String) -> Unit,
    onBiometric: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    Text("Unlock vault", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = pin,
        onValueChange = { if (it.length <= 12 && it.all(Char::isDigit)) pin = it },
        label = { Text("PIN") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Vault PIN" },
    )
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(
        onClick = { onUnlockPin(pin) },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Unlock with PIN" },
    ) { Text("Unlock") }
    OutlinedButton(
        onClick = onBiometric,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Unlock with biometrics" },
    ) { Text("Use fingerprint / face") }
}

private fun launchBiometric(
    activity: FragmentActivity?,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    if (activity == null) {
        onError("Biometrics unavailable")
        return
    }
    val manager = BiometricManager.from(activity)
    val can = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
    if (can != BiometricManager.BIOMETRIC_SUCCESS) {
        onError("Biometrics not available on this device")
        return
    }
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Vault")
            .setSubtitle("Confirm biometrics to open private documents")
            .setNegativeButtonText("Cancel")
            .build(),
    )
}
