package com.supakeyboard.neuralkey.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supakeyboard.neuralkey.NeuralKeyApplication
import com.supakeyboard.neuralkey.data.db.entity.OpenRouterModelEntity
import com.supakeyboard.neuralkey.data.network.model.KeyAuthResponse
import com.supakeyboard.neuralkey.ime.theme.KeyboardTheme
import com.supakeyboard.neuralkey.ime.theme.KeyboardThemes
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15+ draws edge-to-edge; use light system-bar icons on our dark UI.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        setContent {
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(this).run {
                    copy(
                        primary = if (primary.value == 0UL) Color(0xFF7C4DFF) else primary,
                        secondary = if (secondary.value == 0UL) Color(0xFF00E5FF) else secondary
                    )
                }
            } else {
                darkColorScheme(
                    primary = Color(0xFF7C4DFF),
                    secondary = Color(0xFF00E5FF),
                    background = Color(0xFF0D0D11),
                    surface = Color(0xFF16161D)
                )
            }
            MaterialTheme(colorScheme = colorScheme) {
                CompanionDashboard()
            }
        }
    }
}

@Composable
fun CompanionDashboard() {
    val context = LocalContext.current
    val dynamicColorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        darkColorScheme(
            primary = Color(0xFF7C4DFF),
            secondary = Color(0xFF00E5FF),
            background = Color(0xFF0D0D11),
            surface = Color(0xFF16161D)
        )
    }
    val scope = rememberCoroutineScope()
    val app = NeuralKeyApplication.instance

    // DIAGNOSTIC: system bar insets seen by the settings Activity (Android 15+ forces edge-to-edge)
    val density = LocalDensity.current
    val statusTopPx = WindowInsets.statusBars.getTop(density)
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    LaunchedEffect(statusTopPx, navBottomPx) {
        Log.d("NeuralKeyDiag", "Settings insets: statusBar.top=${statusTopPx}px, navBar.bottom=${navBottomPx}px")
    }
    val repository = app.openRouterRepository
    val preferences = app.keyboardPreferences
    val secureKeyStorage = app.secureKeyStorage

    var selectedTab by remember { mutableIntStateOf(0) }

    // IME Status tracking
    var isEnabled by remember { mutableStateOf(checkIsImeEnabled(context)) }
    var isSelected by remember { mutableStateOf(checkIsImeSelected(context)) }

    // Recheck on resume
    LaunchedEffect(Unit) {
        isEnabled = checkIsImeEnabled(context)
        isSelected = checkIsImeSelected(context)
    }

    Scaffold(
        containerColor = Color(0xFF0D0D11),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF13131A))
                    .statusBarsPadding()
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "NeuralKey AI",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Next-Gen OpenRouter Intelligent Keyboard",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    // Status badge
                    val badgeColor = if (isEnabled && isSelected) Color(0xFF10B981) else Color(0xFFF59E0B)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .border(1.dp, badgeColor, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isEnabled && isSelected) "● Active IME" else "● Setup Needed",
                            color = badgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF7C4DFF),
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Color(0xFF7C4DFF)
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Setup", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Models", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("🎨 Themes", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Prefs", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            when (selectedTab) {
                0 -> SetupAndApiTab(
                    isEnabled = isEnabled,
                    isSelected = isSelected,
                    secureKeyStorage = secureKeyStorage,
                    repository = repository,
                    onRefreshImeStatus = {
                        isEnabled = checkIsImeEnabled(context)
                        isSelected = checkIsImeSelected(context)
                    }
                )
                1 -> ModelExplorerTab(
                    repository = repository,
                    preferences = preferences
                )
                2 -> ThemesTab(
                    preferences = preferences
                )
                3 -> PreferencesTab(
                    preferences = preferences
                )
            }
        }
    }
}

@Composable
private fun SetupAndApiTab(
    isEnabled: Boolean,
    isSelected: Boolean,
    secureKeyStorage: com.supakeyboard.neuralkey.data.security.SecureKeyStorage,
    repository: com.supakeyboard.neuralkey.data.repository.OpenRouterRepository,
    onRefreshImeStatus: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apiKeyInput by remember { mutableStateOf(secureKeyStorage.getApiKey() ?: "") }
    var keyAuthResult by remember { mutableStateOf<KeyAuthResponse?>(null) }
    var isCheckingKey by remember { mutableStateOf(false) }
    var keyCheckError by remember { mutableStateOf<String?>(null) }

    var testDraftText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Step-by-step IME Activation Wizard
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16161D)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🚀 IME Activation Wizard",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Follow these two quick steps to start typing with NeuralKey AI across all your apps.",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Step 1: Enable
                    WizardStepItem(
                        stepNumber = 1,
                        title = "Enable NeuralKey AI in System Settings",
                        isCompleted = isEnabled,
                        actionLabel = "Enable in Settings",
                        onAction = {
                            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                            onRefreshImeStatus()
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Step 2: Select
                    WizardStepItem(
                        stepNumber = 2,
                        title = "Select NeuralKey AI as Active Keyboard",
                        isCompleted = isSelected,
                        actionLabel = "Switch Input Method",
                        onAction = {
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.showInputMethodPicker()
                            onRefreshImeStatus()
                        }
                    )
                }
            }
        }

        // OpenRouter Hardware-Encrypted API Key Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16161D)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = Color(0xFF7C4DFF),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "OpenRouter API Key",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        if (secureKeyStorage.hasApiKey()) {
                            Text(
                                text = "🔒 Keystore Encrypted",
                                color = Color(0xFF10B981),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "NeuralKey uses your personal key to stream completions directly from OpenRouter without intermediary servers.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("sk-or-v1-...") },
                        placeholder = { Text("Paste your OpenRouter API key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF7C4DFF),
                            unfocusedBorderColor = Color(0xFF3F3F46)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (apiKeyInput.isNotBlank()) {
                                    runCatching { secureKeyStorage.saveApiKey(apiKeyInput) }
                                        .onSuccess { Toast.makeText(context, "API Key saved securely!", Toast.LENGTH_SHORT).show() }
                                        .onFailure { keyCheckError = it.message ?: "Secure storage unavailable" }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Key")
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isCheckingKey = true
                                    keyCheckError = null
                                    keyAuthResult = null
                                    val res = repository.checkApiKeyUsage()
                                    isCheckingKey = false
                                    res.onSuccess { auth ->
                                        keyAuthResult = auth
                                    }.onFailure { err ->
                                        keyCheckError = err.message ?: "Authentication failed"
                                    }
                                }
                            },
                            enabled = !isCheckingKey,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isCheckingKey) {
                                CircularProgressIndicator(color = Color(0xFF7C4DFF), strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            } else {
                                Text("Check Balance")
                            }
                        }

                        if (secureKeyStorage.hasApiKey()) {
                            IconButton(
                                onClick = {
                                    secureKeyStorage.clearApiKey()
                                    apiKeyInput = ""
                                    keyAuthResult = null
                                    Toast.makeText(context, "Key removed", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear Key", tint = Color(0xFFF87171))
                            }
                        }
                    }

                    // Key details & balance card
                    keyAuthResult?.let { auth ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E1E28))
                                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Label: ${auth.data.label ?: "Personal Key"}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Credit Usage: ${auth.data.formattedUsage}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF38BDF8)
                                )
                                Text(
                                    text = "Credit Limit: ${auth.data.formattedLimit}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                                Text(
                                    text = "Free Tier Account: ${if (auth.data.isFreeTier) "Yes" else "No"}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF34D399)
                                )
                            }
                        }
                    }

                    keyCheckError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "❌ $err", color = Color(0xFFF87171), fontSize = 12.sp)
                    }
                }
            }
        }

        // Live Test Field Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16161D)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "✍️ Interactive Keyboard Test Field",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap inside the box below to summon NeuralKey and test low-latency typing, spacebar trackpad, and AI tools.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = testDraftText,
                        onValueChange = { testDraftText = it },
                        placeholder = { Text("Try typing: 'Fix grammar on this draft...'") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF7C4DFF),
                            unfocusedBorderColor = Color(0xFF3F3F46)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun WizardStepItem(
    stepNumber: Int,
    title: String,
    isCompleted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1F1F2A))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (isCompleted) Color(0xFF10B981) else Color(0xFF3F3F46)),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                } else {
                    Text(text = "$stepNumber", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isCompleted) Color(0xFFCBD5E1) else Color.White
            )
        }

        if (!isCompleted) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(text = actionLabel, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ModelExplorerTab(
    repository: com.supakeyboard.neuralkey.data.repository.OpenRouterRepository,
    preferences: com.supakeyboard.neuralkey.data.preferences.KeyboardPreferences
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var freeOnlyFilter by remember { mutableStateOf(false) }
    var pinnedOnlyFilter by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    val allModels by repository.allModels.collectAsState(initial = emptyList())
    val activeModel by repository.activeModel.collectAsState(initial = null)
    val pinnedIds by preferences.pinnedModelIds.collectAsState(initial = emptySet())

    val filteredModels = remember(allModels, searchQuery, freeOnlyFilter, pinnedOnlyFilter) {
        allModels.filter { model ->
            val matchesQuery = searchQuery.isBlank() ||
                    model.name.contains(searchQuery, ignoreCase = true) ||
                    model.id.contains(searchQuery, ignoreCase = true) ||
                    model.description.contains(searchQuery, ignoreCase = true)
            val matchesFree = !freeOnlyFilter || model.isFree
            val matchesPinned = !pinnedOnlyFilter || pinnedIds.contains(model.id)
            matchesQuery && matchesFree && matchesPinned
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Sync & Search Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Model Catalog (${allModels.size} available)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Button(
                onClick = {
                    scope.launch {
                        isSyncing = true
                        val res = repository.refreshModels()
                        isSyncing = false
                        res.onSuccess { count ->
                            Toast.makeText(context, "Synced $count models from OpenRouter!", Toast.LENGTH_SHORT).show()
                        }.onFailure { err ->
                            Toast.makeText(context, "Sync error: ${err.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = !isSyncing,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(color = Color(0xFF7C4DFF), strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync Live", fontSize = 11.sp, color = Color.White)
                }
            }
        }

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search 300+ models (e.g. Gemini, Llama, Claude, Groq)...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF7C4DFF),
                unfocusedBorderColor = Color(0xFF3F3F46)
            )
        )

        // Filter chips row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                label = "⚡ Free Models Only",
                isSelected = freeOnlyFilter,
                onClick = { freeOnlyFilter = !freeOnlyFilter }
            )
            FilterChip(
                label = "⭐ Pinned Only",
                isSelected = pinnedOnlyFilter,
                onClick = { pinnedOnlyFilter = !pinnedOnlyFilter }
            )
        }

        // Models List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredModels, key = { it.id }) { model ->
                val isActive = model.id == activeModel?.id
                val isPinned = pinnedIds.contains(model.id)

                ModelCard(
                    model = model,
                    isActive = isActive,
                    isPinned = isPinned,
                    onSelect = {
                        scope.launch {
                            repository.setActiveModel(model.id)
                            Toast.makeText(context, "Active model: ${model.name}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onTogglePin = {
                        scope.launch {
                            repository.togglePinModel(model.id)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: OpenRouterModelEntity,
    isActive: Boolean,
    isPinned: Boolean,
    onSelect: () -> Unit,
    onTogglePin: () -> Unit
) {
    val borderColor = if (isActive) Color(0xFF7C4DFF) else Color(0xFF27272A)
    val bgColor = if (isActive) Color(0xFF1E1A2C) else Color(0xFF16161D)

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = model.id,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            imageVector = if (isPinned) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Pin Model",
                            tint = if (isPinned) Color(0xFFFBBF24) else Color(0xFF64748B)
                        )
                    }

                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF7C4DFF))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("ACTIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onSelect,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))
                        ) {
                            Text("Select", fontSize = 11.sp, color = Color(0xFFE2E8F0))
                        }
                    }
                }
            }

            if (model.description.isNotBlank()) {
                Text(
                    text = model.description,
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Specs badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cost badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (model.isFree) Color(0xFF065F46) else Color(0xFF27272A))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (model.isFree) "FREE" else model.formattedCost,
                        color = if (model.isFree) Color(0xFF34D399) else Color(0xFFCBD5E1),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Context length
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF27272A))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Context: ${model.formattedContextLength}",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }

                // Modality
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF27272A))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = model.modality,
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF7C4DFF) else Color(0xFF1E1E28))
            .border(1.dp, if (isSelected) Color(0xFF7C4DFF) else Color(0xFF3F3F46), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) Color.White else Color(0xFFCBD5E1)
        )
    }
}

@Composable
private fun ThemesTab(
    preferences: com.supakeyboard.neuralkey.data.preferences.KeyboardPreferences
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val selectedThemeId by preferences.selectedTheme.collectAsState(initial = "CYBERPUNK_NEON")
    val activeTheme = remember(selectedThemeId) { KeyboardThemes.getTheme(selectedThemeId) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header & Live Preview Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16161D)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "🎨 Theme Gallery",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Instant zero-restart tactile styling",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(activeTheme.accentPrimary.copy(alpha = 0.2f))
                                .border(1.dp, activeTheme.accentPrimary, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${activeTheme.emoji} ${activeTheme.name}",
                                color = activeTheme.accentPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Interactive Keyboard Mini Mockup
                    Text(
                        text = "LIVE KEYBOARD PREVIEW",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(activeTheme.backgroundBrush)
                            .border(1.dp, activeTheme.accentPrimary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Mini AI Command Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(activeTheme.commandBarBg)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("✨", fontSize = 12.sp)
                                    Text(
                                        text = "AI Command Bar",
                                        color = activeTheme.accentPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(activeTheme.accentSecondary.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "21 Prompts",
                                        color = activeTheme.accentSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Mock Row 1
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P").forEach { char ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(28.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(activeTheme.keyBgColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = char,
                                            color = activeTheme.keyTextColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // Mock Row 2
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("A", "S", "D", "F", "G", "H", "J", "K", "L").forEach { char ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(28.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(activeTheme.keyBgColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = char,
                                            color = activeTheme.keyTextColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // Mock Row 3 (Shift, letters, Del)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(activeTheme.keyFunctionalBgColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⇧", color = activeTheme.keyFunctionalTextColor, fontSize = 11.sp)
                                }
                                listOf("Z", "X", "C", "V", "B", "N", "M").forEach { char ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(28.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(activeTheme.keyBgColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = char,
                                            color = activeTheme.keyTextColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(activeTheme.keyFunctionalBgColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⌫", color = activeTheme.keyFunctionalTextColor, fontSize = 11.sp)
                                }
                            }

                            // Mock Row 4 (?123, emoji, spacebar, enter)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(activeTheme.keyFunctionalBgColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("?123", color = activeTheme.keyFunctionalTextColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(activeTheme.keyFunctionalBgColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🌐", fontSize = 10.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(4.5f)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(activeTheme.keyBgColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "NeuralKey AI",
                                        color = activeTheme.keySecondaryTextColor,
                                        fontSize = 9.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1.8f)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(activeTheme.accentPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "↵",
                                        color = if (activeTheme.isDark) Color.Black else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Theme selection list header
        item {
            Text(
                text = "AVAILABLE THEMES (9)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                letterSpacing = 1.sp
            )
        }

        // 8 Theme Cards
        items(KeyboardThemes.allThemes, key = { it.id }) { theme ->
            val isSelected = theme.id.equals(selectedThemeId, ignoreCase = true)
            val cardBorderColor = if (isSelected) theme.accentPrimary else Color(0xFF27272A)
            val cardBg = if (isSelected) Color(0xFF1B182B) else Color(0xFF16161D)

            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = cardBorderColor,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        scope.launch {
                            preferences.setSelectedTheme(theme.id)
                            Toast.makeText(context, "${theme.emoji} Applied ${theme.name} Theme", Toast.LENGTH_SHORT).show()
                        }
                    }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(text = theme.emoji, fontSize = 24.sp)
                            Column {
                                Text(
                                    text = theme.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (theme.isDark) "Dark / AMOLED" else "Light Studio",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.accentPrimary)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    color = if (theme.isDark) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        preferences.setSelectedTheme(theme.id)
                                        Toast.makeText(context, "${theme.emoji} Applied ${theme.name} Theme", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Apply", fontSize = 12.sp)
                            }
                        }
                    }

                    Text(
                        text = theme.description,
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        lineHeight = 16.sp
                    )

                    // Color swatches row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Palette:",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.SemiBold
                        )

                        // Swatch 1: Primary Accent
                        ColorSwatch(color = theme.accentPrimary, label = "Accent")
                        // Swatch 2: Secondary Accent
                        ColorSwatch(color = theme.accentSecondary, label = "Secondary")
                        // Swatch 3: Key Background
                        ColorSwatch(color = theme.keyBgColor, label = "Key")
                        // Swatch 4: Surface
                        ColorSwatch(color = theme.surfaceColor, label = "Surface")
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, label: String) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
    )
}

@Composable
private fun PreferencesTab(
    preferences: com.supakeyboard.neuralkey.data.preferences.KeyboardPreferences
) {
    val scope = rememberCoroutineScope()

    val hapticEnabled by preferences.hapticFeedbackEnabled.collectAsState(initial = true)
    val hapticIntensity by preferences.hapticIntensity.collectAsState(initial = 70)
    val suggestionsEnabled by preferences.suggestionsEnabled.collectAsState(initial = true)
    val autoCorrectionEnabled by preferences.autoCorrectionEnabled.collectAsState(initial = true)
    val soundEnabled by preferences.soundFeedbackEnabled.collectAsState(initial = false)
    val customPrompt by preferences.customSystemPrompt.collectAsState(initial = "")

    var promptInput by remember { mutableStateOf(customPrompt) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF16161D)),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Typing assistance", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Offline English dictionary. Backspace immediately after a correction restores your word.",
                        color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Word suggestions", color = Color.White, modifier = Modifier.weight(1f))
                        Switch(suggestionsEnabled, { enabled -> scope.launch { preferences.setSuggestionsEnabled(enabled) } })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Autocorrect on space", color = Color.White, modifier = Modifier.weight(1f))
                        Switch(autoCorrectionEnabled, { enabled -> scope.launch { preferences.setAutoCorrectionEnabled(enabled) } })
                    }
                }
            }
        }
        // Haptics Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16161D)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "📳 Tactile Haptic Mechanics",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Haptic Feedback on Press", color = Color.White, fontSize = 14.sp)
                            Text("Crisp KEYBOARD_TAP feedback", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                        Switch(
                            checked = hapticEnabled,
                            onCheckedChange = { scope.launch { preferences.setHapticFeedbackEnabled(it) } },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF7C4DFF))
                        )
                    }

                    if (hapticEnabled) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Haptic Intensity", color = Color.White, fontSize = 13.sp)
                                Text("$hapticIntensity%", color = Color(0xFF7C4DFF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = hapticIntensity.toFloat(),
                                onValueChange = { scope.launch { preferences.setHapticIntensity(it.toInt()) } },
                                valueRange = 1f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF7C4DFF),
                                    activeTrackColor = Color(0xFF7C4DFF)
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Key Press Sound", color = Color.White, fontSize = 14.sp)
                            Text("Subtle click on character input", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = { scope.launch { preferences.setSoundFeedbackEnabled(it) } },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF7C4DFF))
                        )
                    }
                }
            }
        }

        // Custom AI System Prompt Rules
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16161D)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🧠 Global AI Directives",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add custom constraints injected into all AI rewriting requests (e.g. 'Use British English spelling', 'Avoid passive voice').",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        placeholder = { Text("Enter custom rules for your AI models...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF7C4DFF),
                            unfocusedBorderColor = Color(0xFF3F3F46)
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                preferences.setCustomSystemPrompt(promptInput)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
                    ) {
                        Text("Save Directives")
                    }
                }
            }
        }
    }
}

private fun checkIsImeEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
    val enabledList = imm.enabledInputMethodList
    return enabledList.any { it.packageName == context.packageName }
}

private fun checkIsImeSelected(context: Context): Boolean {
    val defaultIme = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.DEFAULT_INPUT_METHOD
    )
    return defaultIme != null && defaultIme.contains(context.packageName)
}
