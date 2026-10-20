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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supakeyboard.neuralkey.NeuralKeyApplication
import com.supakeyboard.neuralkey.data.db.entity.OpenRouterModelEntity
import com.supakeyboard.neuralkey.data.db.entity.UserPromptEntity
import com.supakeyboard.neuralkey.data.network.model.KeyAuthResponse
import com.supakeyboard.neuralkey.ime.haptics.HapticManager
import com.supakeyboard.neuralkey.ime.mvi.OneHandedMode
import com.supakeyboard.neuralkey.ime.theme.KeyboardThemes
import kotlinx.coroutines.delay
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
                    background = Color(0xFF090A0F),
                    surface = Color(0xFF131520)
                )
            }
            MaterialTheme(colorScheme = colorScheme) {
                CompanionDashboard()
            }
        }
    }
}

private data class DashboardTabItem(
    val index: Int,
    val label: String,
    val icon: ImageVector
)

private fun onAccentColor(accent: Color): Color {
    return if (accent.luminance() > 0.42f) Color(0xFF090A0F) else Color.White
}

@Composable
fun CompanionDashboard() {
    val context = LocalContext.current
    val app = NeuralKeyApplication.instance

    val density = LocalDensity.current
    val statusTopPx = WindowInsets.statusBars.getTop(density)
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    LaunchedEffect(statusTopPx, navBottomPx) {
        Log.d("NeuralKeyDiag", "Settings insets: statusBar.top=${statusTopPx}px, navBar.bottom=${navBottomPx}px")
    }

    val repository = app.openRouterRepository
    val preferences = app.keyboardPreferences
    val secureKeyStorage = app.secureKeyStorage
    val keyMemoryStore = app.keyMemoryStore

    var selectedTab by remember { mutableIntStateOf(0) }

    val activeModel by repository.activeModel.collectAsState(initial = null)
    val selectedThemeId by preferences.selectedTheme.collectAsState(initial = "CYBERPUNK_NEON")
    val activeTheme = remember(selectedThemeId) { KeyboardThemes.getTheme(selectedThemeId) }
    val learnedWordsCount by keyMemoryStore.learnedWordCount.collectAsState()
    val oneHandedMode by preferences.oneHandedMode.collectAsState(initial = OneHandedMode.OFF)

    var isEnabled by remember { mutableStateOf(checkIsImeEnabled(context)) }
    var isSelected by remember { mutableStateOf(checkIsImeSelected(context)) }

    LaunchedEffect(Unit) {
        isEnabled = checkIsImeEnabled(context)
        isSelected = checkIsImeSelected(context)
    }

    val tabs = remember {
        listOf(
            DashboardTabItem(0, "Setup", Icons.Default.PhonelinkSetup),
            DashboardTabItem(1, "Typing", Icons.Default.Memory),
            DashboardTabItem(2, "AI Tools", Icons.Default.AutoAwesome),
            DashboardTabItem(3, "Models", Icons.Default.Keyboard),
            DashboardTabItem(4, "Themes", Icons.Default.Palette)
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val isReady = isEnabled && isSelected
    val statusColor by animateColorAsState(
        targetValue = if (isReady) Color(0xFF10B981) else Color(0xFFF59E0B),
        animationSpec = tween(450),
        label = "statusColor"
    )

    Scaffold(
        containerColor = Color(0xFF090A0F),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                activeTheme.accentPrimary.copy(alpha = 0.14f),
                                Color(0xFF11131E),
                                Color(0xFF090A0F)
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 10.dp)
            ) {
                // Hero Header Row: Left side uses weight(1f) so the right status pill is measured first and never wraps
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(activeTheme.accentPrimary, activeTheme.accentSecondary)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🧠", fontSize = 18.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "NeuralKey AI",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF7C4DFF).copy(alpha = 0.25f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "v1.2 FREE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFC4B5FD),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Private Key Memory • 32 AI Tools • Foldable Ready",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Single-line Status Pill (measured first because it has no weight)
                    Row(
                        modifier = Modifier
                            .testTag("ime_status_pill")
                            .clip(RoundedCornerShape(20.dp))
                            .background(statusColor.copy(alpha = 0.14f))
                            .border(1.dp, statusColor.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                            .clickable { selectedTab = 0 }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = if (isReady) "Active IME" else "Setup Needed",
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Telemetry Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryMiniChip(
                        label = "⚡ ${activeModel?.name ?: "Gemini 2.5 Flash"}",
                        accent = activeTheme.accentPrimary,
                        onClick = { selectedTab = 3 }
                    )
                    TelemetryMiniChip(
                        label = "${activeTheme.emoji} ${activeTheme.name}",
                        accent = activeTheme.accentSecondary,
                        onClick = { selectedTab = 4 }
                    )
                    TelemetryMiniChip(
                        label = "🧠 $learnedWordsCount Words Learned",
                        accent = Color(0xFF34D399),
                        onClick = { selectedTab = 1 }
                    )
                    TelemetryMiniChip(
                        label = when (oneHandedMode) {
                            OneHandedMode.OFF -> "📱 Full / Auto-Fold"
                            OneHandedMode.LEFT -> "🤚 Left One-Handed"
                            OneHandedMode.RIGHT -> "🤚 Right One-Handed"
                        },
                        accent = Color(0xFF38BDF8),
                        onClick = { selectedTab = 1 }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 5-Tab Bar: Icon + Single-line label on every tab so widths never jump or truncate
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF141622))
                        .border(1.dp, Color(0xFF232738), RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tabs.forEach { tab ->
                        val selected = selectedTab == tab.index
                        val pillBg by animateColorAsState(
                            targetValue = if (selected) activeTheme.accentPrimary else Color.Transparent,
                            animationSpec = tween(220),
                            label = "tabBg"
                        )
                        val contentCol by animateColorAsState(
                            targetValue = if (selected) onAccentColor(activeTheme.accentPrimary) else Color(0xFF94A3B8),
                            animationSpec = tween(220),
                            label = "tabText"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dashboard_tab_${tab.index}")
                                .clip(RoundedCornerShape(10.dp))
                                .background(pillBg)
                                .clickable { selectedTab = tab.index }
                                .padding(vertical = 7.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = contentCol,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = tab.label,
                                color = contentCol,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
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
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        initialOffsetX = { width -> direction * (width / 4) }
                    ) + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(180),
                            targetOffsetX = { width -> -direction * (width / 5) }
                        ) + fadeOut(tween(150)))
                },
                label = "dashboardTabTransition"
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> SetupAndApiTab(
                        isEnabled = isEnabled,
                        isSelected = isSelected,
                        secureKeyStorage = secureKeyStorage,
                        repository = repository,
                        accentColor = activeTheme.accentPrimary,
                        onRefreshImeStatus = {
                            isEnabled = checkIsImeEnabled(context)
                            isSelected = checkIsImeSelected(context)
                        }
                    )
                    1 -> SmartTypingAndPostureTab(
                        app = app,
                        preferences = preferences,
                        keyMemoryStore = keyMemoryStore,
                        accentColor = activeTheme.accentPrimary
                    )
                    2 -> AIStudioPromptsTab(
                        repository = repository,
                        preferences = preferences,
                        accentColor = activeTheme.accentPrimary
                    )
                    3 -> ModelExplorerTab(
                        repository = repository,
                        preferences = preferences,
                        accentColor = activeTheme.accentPrimary
                    )
                    4 -> ThemesTab(
                        preferences = preferences
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryMiniChip(
    label: String,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF151824))
            .border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFE2E8F0),
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun SetupAndApiTab(
    isEnabled: Boolean,
    isSelected: Boolean,
    secureKeyStorage: com.supakeyboard.neuralkey.data.security.SecureKeyStorage,
    repository: com.supakeyboard.neuralkey.data.repository.OpenRouterRepository,
    accentColor: Color,
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
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Step-by-step IME Activation Wizard
        item {
            GlassCard {
                Text(
                    text = "🚀 Instant Activation Wizard",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Complete these two quick steps to unlock NeuralKey AI across WhatsApp, Gmail, Slack, and all your apps.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(12.dp))

                WizardStepItem(
                    stepNumber = 1,
                    title = "Enable NeuralKey AI in System Settings",
                    isCompleted = isEnabled,
                    actionLabel = "Enable",
                    accentColor = accentColor,
                    onAction = {
                        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        onRefreshImeStatus()
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                WizardStepItem(
                    stepNumber = 2,
                    title = "Select NeuralKey AI as Default Keyboard",
                    isCompleted = isSelected,
                    actionLabel = "Select",
                    accentColor = accentColor,
                    onAction = {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.showInputMethodPicker()
                        onRefreshImeStatus()
                    }
                )
            }
        }

        // OpenRouter Hardware-Encrypted API Key Card
        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "OpenRouter API Vault",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (secureKeyStorage.hasApiKey()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "🔒 AES-256",
                                color = Color(0xFF34D399),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "100% Free Tier Supported! Generate a free key at openrouter.ai/keys to stream 300+ models directly without middleman servers.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("sk-or-v1-...", fontSize = 12.sp) },
                    placeholder = { Text("Paste your OpenRouter API key", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color(0xFF2D3348)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (apiKeyInput.isNotBlank()) {
                                runCatching { secureKeyStorage.saveApiKey(apiKeyInput) }
                                    .onSuccess { Toast.makeText(context, "API Key encrypted & saved!", Toast.LENGTH_SHORT).show() }
                                    .onFailure { keyCheckError = it.message ?: "Secure storage unavailable" }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Save Key",
                            color = onAccentColor(accentColor),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
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
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isCheckingKey) {
                            CircularProgressIndicator(color = accentColor, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Text(
                                text = "Verify Key",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
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

                AnimatedVisibility(
                    visible = keyAuthResult != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    keyAuthResult?.let { auth ->
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF191D2E))
                                    .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "✓ Verified: ${auth.data.label ?: "Personal Key"}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Credit Usage: ${auth.data.formattedUsage} • Limit: ${auth.data.formattedLimit}",
                                        fontSize = 12.sp,
                                        color = accentColor
                                    )
                                    Text(
                                        text = "Free Tier Enabled: ${if (auth.data.isFreeTier) "Yes (Use ⚡ Free Models at $0)" else "Paid + Free Tier"}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF34D399)
                                    )
                                }
                            }
                        }
                    }
                }

                keyCheckError?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "❌ $err", color = Color(0xFFF87171), fontSize = 12.sp)
                }
            }
        }

        // Live Interactive Keyboard Test Sandbox
        item {
            GlassCard {
                Text(
                    text = "✍️ Interactive Keyboard Sandbox",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap below to summon NeuralKey and test Adaptive Key Memory, Next-Word Predictions, One-Handed Mode, and Email AI tools.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "📧 Sample Email Draft" to "hi team can we move tomorrow's sync to 3pm? need to finish the release notes.",
                        "🧠 Test Typo & Undo" to "Please recieve teh attached ",
                        "📋 Meeting Notes" to "decided to launch v1.2 on friday. mayur owns apk build, sarah owns qa."
                    ).forEach { (label, sample) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E2235))
                                .clickable { testDraftText = sample }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFCBD5E1),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = testDraftText,
                    onValueChange = { testDraftText = it },
                    placeholder = { Text("Tap here to test typing, spacebar trackpad, or ✨ AI Studio...", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color(0xFF2D3348)
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmartTypingAndPostureTab(
    app: NeuralKeyApplication,
    preferences: com.supakeyboard.neuralkey.data.preferences.KeyboardPreferences,
    keyMemoryStore: com.supakeyboard.neuralkey.ime.typing.KeyMemoryStore,
    accentColor: Color
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    val hapticManager = remember { HapticManager.getInstance(context) }

    val suggestionsEnabled by preferences.suggestionsEnabled.collectAsState(initial = true)
    val autoCorrectionEnabled by preferences.autoCorrectionEnabled.collectAsState(initial = true)
    val keyMemoryEnabled by preferences.keyMemoryEnabled.collectAsState(initial = true)
    val nextWordEnabled by preferences.nextWordPredictionEnabled.collectAsState(initial = true)
    val oneHandedMode by preferences.oneHandedMode.collectAsState(initial = OneHandedMode.OFF)
    val splitOnFold by preferences.splitKeyboardOnFold.collectAsState(initial = true)
    val hapticEnabled by preferences.hapticFeedbackEnabled.collectAsState(initial = true)
    val hapticIntensity by preferences.hapticIntensity.collectAsState(initial = 70)
    val soundEnabled by preferences.soundFeedbackEnabled.collectAsState(initial = false)

    val learnedCount by keyMemoryStore.learnedWordCount.collectAsState()
    val topLearnedWords by keyMemoryStore.topLearnedWords.collectAsState()
    var customWordInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Adaptive Key Memory & Next-Word Intelligence Card
        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp)
                    ) {
                        Text(
                            text = "🧠 Adaptive Key Memory",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "100% offline personal vocabulary & bigram next-word learning",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.18f))
                            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "$learnedCount Learned",
                            color = accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                ModernToggleRow(
                    title = "Key Memory (Remember What You Type)",
                    subtitle = "Automatically learns your personal words, names, and phrasing frequency offline",
                    checked = keyMemoryEnabled,
                    accentColor = accentColor,
                    onCheckedChange = { scope.launch { preferences.setKeyMemoryEnabled(it) } }
                )

                ModernToggleRow(
                    title = "Contextual Next-Word Predictions",
                    subtitle = "Predicts your next likely word after pressing Space using N-gram memory",
                    checked = nextWordEnabled,
                    accentColor = accentColor,
                    onCheckedChange = { scope.launch { preferences.setNextWordPredictionEnabled(it) } }
                )

                ModernToggleRow(
                    title = "SCOWL 100k+ Word Suggestions",
                    subtitle = "Prefix + QWERTY spatial typo completion strip",
                    checked = suggestionsEnabled,
                    accentColor = accentColor,
                    onCheckedChange = { scope.launch { preferences.setSuggestionsEnabled(it) } }
                )

                ModernToggleRow(
                    title = "Smart Autocorrect on Space",
                    subtitle = "Conservative typo fixes; press Backspace immediately to undo",
                    checked = autoCorrectionEnabled,
                    accentColor = accentColor,
                    onCheckedChange = { scope.launch { preferences.setAutoCorrectionEnabled(it) } }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Teach custom word row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customWordInput,
                        onValueChange = { customWordInput = it },
                        placeholder = { Text("Teach custom word (e.g. NeuralKey)", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color(0xFF2D3348)
                        )
                    )
                    Button(
                        onClick = {
                            if (customWordInput.isNotBlank()) {
                                app.teachCustomWord(customWordInput)
                                Toast.makeText(context, "Added '${customWordInput.trim()}' to Key Memory!", Toast.LENGTH_SHORT).show()
                                customWordInput = ""
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = onAccentColor(accentColor), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Teach",
                            color = onAccentColor(accentColor),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                AnimatedVisibility(visible = topLearnedWords.isNotEmpty()) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOP LEARNED VOCABULARY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = "Clear Memory",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF87171),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.clickable {
                                    app.clearKeyMemory()
                                    Toast.makeText(context, "Cleared learned Key Memory", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            topLearnedWords.take(15).forEach { (word, count) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1D2133))
                                        .border(1.dp, Color(0xFF2E344E), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "$word ×$count",
                                        fontSize = 11.sp,
                                        color = Color(0xFFE2E8F0),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. One-Handed Mode & Foldable / Flip Posture Engine
        item {
            GlassCard {
                Text(
                    text = "📱 One-Handed & Foldable Posture",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Engineered for standard phones, Galaxy Z Flip cover screens, and Galaxy Z Fold / Pixel Pro Fold unfolded inner displays.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "ONE-HANDED COMPACT DOCK MODE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple(OneHandedMode.LEFT, "◀ Left", "82% Left"),
                        Triple(OneHandedMode.OFF, "⤢ Full", "100% Width"),
                        Triple(OneHandedMode.RIGHT, "Right ▶", "82% Right")
                    ).forEach { (mode, label, subtitle) ->
                        val selected = oneHandedMode == mode
                        val borderCol by animateColorAsState(
                            targetValue = if (selected) accentColor else Color(0xFF282D42),
                            label = "postureBorder"
                        )
                        val bgCol by animateColorAsState(
                            targetValue = if (selected) accentColor.copy(alpha = 0.16f) else Color(0xFF181B29),
                            label = "postureBg"
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bgCol)
                                .border(if (selected) 2.dp else 1.dp, borderCol, RoundedCornerShape(12.dp))
                                .clickable {
                                    scope.launch { preferences.setOneHandedMode(mode) }
                                }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) accentColor else Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                ModernToggleRow(
                    title = "Foldable Unfolded Ergonomic Split Layout",
                    subtitle = "Automatically splits keys into left/right thumb clusters with dual spacebars on unfolded displays (≥600dp)",
                    checked = splitOnFold,
                    accentColor = accentColor,
                    onCheckedChange = { scope.launch { preferences.setSplitKeyboardOnFold(it) } }
                )
            }
        }

        // 3. Tactile Hardware Haptics Card
        item {
            GlassCard {
                Text(
                    text = "📳 Tactile Hardware Haptics",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))

                ModernToggleRow(
                    title = "Haptic Feedback on Key Press",
                    subtitle = "Hardware KEYBOARD_TAP & spacebar trackpad CLOCK_TICK pulses",
                    checked = hapticEnabled,
                    accentColor = accentColor,
                    onCheckedChange = { scope.launch { preferences.setHapticFeedbackEnabled(it) } }
                )

                AnimatedVisibility(visible = hapticEnabled) {
                    Column {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Vibration Amplitude", color = Color.White, fontSize = 13.sp)
                            Text("$hapticIntensity%", color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = hapticIntensity.toFloat(),
                            onValueChange = {
                                val next = it.toInt()
                                scope.launch { preferences.setHapticIntensity(next) }
                                hapticManager.performKeyTap(view, true, next)
                            },
                            valueRange = 1f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor
                            )
                        )
                    }
                }

                ModernToggleRow(
                    title = "Key Press Audio Click",
                    subtitle = "Subtle acoustic click on character input",
                    checked = soundEnabled,
                    accentColor = accentColor,
                    onCheckedChange = { scope.launch { preferences.setSoundFeedbackEnabled(it) } }
                )
            }
        }
    }
}

@Composable
private fun AIStudioPromptsTab(
    repository: com.supakeyboard.neuralkey.data.repository.OpenRouterRepository,
    preferences: com.supakeyboard.neuralkey.data.preferences.KeyboardPreferences,
    accentColor: Color
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val userPrompts by repository.userPrompts.collectAsState(initial = UserPromptEntity.defaultPrompts())
    val customDirective by preferences.customSystemPrompt.collectAsState(initial = "")

    var directiveInput by remember(customDirective) { mutableStateOf(customDirective) }
    var newTitle by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("Email & Drafts") }
    var newInstruction by remember { mutableStateOf("") }
    var selectedFilterCategory by remember { mutableStateOf("All") }

    val categories = listOf("All", "Email & Drafts", "Productivity", "Smart Writing", "Humor & Vibes", "Styles", "Translation")
    val creationCategories = listOf("Email & Drafts", "Productivity", "Smart Writing", "Styles", "Humor & Vibes", "Translation")

    val allPrompts = remember(userPrompts) {
        userPrompts.ifEmpty { UserPromptEntity.defaultPrompts() }
    }

    val filteredPrompts = remember(allPrompts, selectedFilterCategory) {
        if (selectedFilterCategory == "All") allPrompts
        else allPrompts.filter { it.category.equals(selectedFilterCategory, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Create Custom Email / Productivity AI Action
        item {
            GlassCard {
                Text(
                    text = "✨ Create Custom AI Action",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Build your own 1-tap Email template, code reviewer, or tone rewriter for the in-keyboard AI Studio.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Action Title (e.g. 'Weekly Status Email')", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color(0xFF2D3348)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    creationCategories.forEach { cat ->
                        FilterChip(
                            label = cat,
                            isSelected = newCategory == cat,
                            accentColor = accentColor,
                            onClick = { newCategory = cat }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newInstruction,
                    onValueChange = { newInstruction = it },
                    label = { Text("Prompt Instruction (what should AI do?)", fontSize = 12.sp) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color(0xFF2D3348)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (newTitle.isNotBlank() && newInstruction.isNotBlank()) {
                            scope.launch {
                                repository.insertUserPrompt(
                                    title = newTitle.trim(),
                                    instruction = newInstruction.trim(),
                                    category = newCategory
                                )
                                Toast.makeText(context, "Added '${newTitle.trim()}' to AI Studio!", Toast.LENGTH_SHORT).show()
                                newTitle = ""
                                newInstruction = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = onAccentColor(accentColor), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Save Custom AI Tool",
                        color = onAccentColor(accentColor),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // 2. Global AI Directives Card
        item {
            GlassCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Global AI System Directives",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Persistent rules automatically appended to every AI request (e.g., 'Sign emails as Best, Mayur', 'Use British English').",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = directiveInput,
                    onValueChange = { directiveInput = it },
                    placeholder = { Text("Enter global rules for all AI actions...", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color(0xFF2D3348)
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            preferences.setCustomSystemPrompt(directiveInput)
                            Toast.makeText(context, "Global AI directives saved!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF23283C))
                ) {
                    Text(
                        text = "Save Global Directives",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // 3. Built-In & Custom Prompt Library
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "AI ACTION LIBRARY (${allPrompts.size} TOOLS)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 0.8.sp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            label = cat,
                            isSelected = selectedFilterCategory == cat,
                            accentColor = accentColor,
                            onClick = { selectedFilterCategory = cat }
                        )
                    }
                }
            }
        }

        items(filteredPrompts, key = { it.id }) { prompt ->
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = prompt.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accentColor.copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = prompt.category,
                                    fontSize = 10.sp,
                                    color = accentColor,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = prompt.promptInstruction,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!prompt.isBuiltIn) {
                        IconButton(
                            onClick = {
                                scope.launch { repository.deleteUserPrompt(prompt.id) }
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Custom Prompt", tint = Color(0xFFF87171))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelExplorerTab(
    repository: com.supakeyboard.neuralkey.data.repository.OpenRouterRepository,
    preferences: com.supakeyboard.neuralkey.data.preferences.KeyboardPreferences,
    accentColor: Color
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Sync & Search Header: Left Column uses weight(1f) so Sync Live button never wraps vertically
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            ) {
                Text(
                    text = "OpenRouter Model Catalog",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${allModels.size} models cached • Instant Search",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

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
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        color = onAccentColor(accentColor),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = onAccentColor(accentColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Sync Live",
                        fontSize = 12.sp,
                        color = onAccentColor(accentColor),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search 300+ models (Gemini, Llama, Claude)...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = accentColor,
                unfocusedBorderColor = Color(0xFF2D3348)
            )
        )

        // Filter chips row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                label = "⚡ Free Models Only",
                isSelected = freeOnlyFilter,
                accentColor = accentColor,
                onClick = { freeOnlyFilter = !freeOnlyFilter }
            )
            FilterChip(
                label = "⭐ Pinned Only",
                isSelected = pinnedOnlyFilter,
                accentColor = accentColor,
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
                    accentColor = accentColor,
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
    accentColor: Color,
    onSelect: () -> Unit,
    onTogglePin: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isActive) accentColor else Color(0xFF232738),
        label = "modelBorder"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isActive) accentColor.copy(alpha = 0.12f) else Color(0xFF131520),
        label = "modelBg"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(if (isActive) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
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
                                .background(accentColor)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                color = onAccentColor(accentColor),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    } else {
                        Button(
                            onClick = onSelect,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF232738))
                        ) {
                            Text(
                                text = "Select",
                                fontSize = 11.sp,
                                color = Color(0xFFE2E8F0),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            if (model.description.isNotBlank()) {
                Text(
                    text = model.description,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (model.isFree) Color(0xFF065F46) else Color(0xFF1F2334))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (model.isFree) "FREE ($0.00)" else model.formattedCost,
                        color = if (model.isFree) Color(0xFF34D399) else Color(0xFFCBD5E1),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1F2334))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Context: ${model.formattedContextLength}",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1F2334))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = model.modality,
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false
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
    accentColor: Color = Color(0xFF7C4DFF),
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) accentColor else Color(0xFF181B28),
        label = "chipBg"
    )
    val textCol by animateColorAsState(
        targetValue = if (isSelected) onAccentColor(accentColor) else Color(0xFFCBD5E1),
        label = "chipText"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, if (isSelected) accentColor else Color(0xFF2D3348), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textCol,
            maxLines = 1,
            softWrap = false
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

    var themeCategoryFilter by remember { mutableStateOf("All") }
    var lastTappedMockKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lastTappedMockKey) {
        if (lastTappedMockKey != null) {
            delay(200)
            lastTappedMockKey = null
        }
    }

    val filteredThemes = remember(themeCategoryFilter) {
        when (themeCategoryFilter) {
            "Dark & Neon" -> KeyboardThemes.allThemes.filter { it.isDark && it.id != "MIDNIGHT_OLED" }
            "Pure AMOLED" -> KeyboardThemes.allThemes.filter { it.id == "MIDNIGHT_OLED" || it.id == "CARBON_TITANIUM" || it.id == "EMERALD_MATRIX" }
            "Light Studio" -> KeyboardThemes.allThemes.filter { !it.isDark }
            else -> KeyboardThemes.allThemes
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header & Interactive Animated Live Preview Card
        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp)
                    ) {
                        Text(
                            text = "🎨 15 Flagship Themes",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tap any key below to test live theme physics",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(activeTheme.accentPrimary.copy(alpha = 0.2f))
                            .border(1.dp, activeTheme.accentPrimary, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "${activeTheme.emoji} ${activeTheme.name}",
                            color = activeTheme.accentPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Live Interactive Keyboard Mini Mockup
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
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("✨", fontSize = 12.sp)
                                Text(
                                    text = "AI Studio • 32 Tools",
                                    color = activeTheme.accentPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(activeTheme.accentSecondary.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Key Memory Active",
                                    color = activeTheme.accentSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Mock Row 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P").forEach { char ->
                                val pressed = lastTappedMockKey == char
                                val scale by animateFloatAsState(
                                    targetValue = if (pressed) 0.88f else 1f,
                                    animationSpec = spring(stiffness = Spring.StiffnessHigh),
                                    label = "mockScale"
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .scale(scale)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(if (pressed) activeTheme.accentPrimary else activeTheme.keyBgColor)
                                        .clickable { lastTappedMockKey = char },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = char,
                                        color = if (pressed) onAccentColor(activeTheme.accentPrimary) else activeTheme.keyTextColor,
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
                                val pressed = lastTappedMockKey == char
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(if (pressed) activeTheme.accentPrimary else activeTheme.keyBgColor)
                                        .clickable { lastTappedMockKey = char },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = char,
                                        color = if (pressed) onAccentColor(activeTheme.accentPrimary) else activeTheme.keyTextColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Mock Row 4 (?123, spacebar, enter)
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
                                    .weight(4.8f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(activeTheme.keyBgColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "⚡ ${activeTheme.name}",
                                    color = activeTheme.accentPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
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
                                    color = onAccentColor(activeTheme.accentPrimary),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Theme category filter row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Dark & Neon", "Pure AMOLED", "Light Studio").forEach { cat ->
                    FilterChip(
                        label = cat,
                        isSelected = themeCategoryFilter == cat,
                        accentColor = activeTheme.accentPrimary,
                        onClick = { themeCategoryFilter = cat }
                    )
                }
            }
        }

        // 15 Theme Cards
        items(filteredThemes, key = { it.id }) { theme ->
            val isSelected = theme.id.equals(selectedThemeId, ignoreCase = true)
            val cardBorderColor by animateColorAsState(
                targetValue = if (isSelected) theme.accentPrimary else Color(0xFF232738),
                label = "themeBorder"
            )
            val cardBg by animateColorAsState(
                targetValue = if (isSelected) theme.accentPrimary.copy(alpha = 0.12f) else Color(0xFF131520),
                label = "themeBg"
            )

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
                            Toast.makeText(context, "${theme.emoji} Applied ${theme.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(text = theme.emoji, fontSize = 24.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = theme.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (theme.isDark) "Dark / AMOLED Surface" else "Daylight Studio Surface",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                                    color = onAccentColor(theme.accentPrimary),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        preferences.setSelectedTheme(theme.id)
                                        Toast.makeText(context, "${theme.emoji} Applied ${theme.name}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Apply",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Text(
                        text = theme.description,
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        lineHeight = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Palette:",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                        ColorSwatch(color = theme.accentPrimary)
                        ColorSwatch(color = theme.accentSecondary)
                        ColorSwatch(color = theme.keyBgColor)
                        ColorSwatch(color = theme.surfaceColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassCard(
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131520)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF232738), RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun ModernToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = onAccentColor(accentColor),
                checkedTrackColor = accentColor
            )
        )
    }
}

@Composable
private fun WizardStepItem(
    stepNumber: Int,
    title: String,
    isCompleted: Boolean,
    actionLabel: String,
    accentColor: Color,
    onAction: () -> Unit
) {
    val badgeBg by animateColorAsState(
        targetValue = if (isCompleted) Color(0xFF10B981) else Color(0xFF282D42),
        label = "wizardStepBadge"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF191C2B))
            .border(
                1.dp,
                if (isCompleted) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFF262A3E),
                RoundedCornerShape(14.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
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
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isCompleted) Color(0xFFCBD5E1) else Color.White,
                modifier = Modifier.weight(1f)
            )
        }

        if (!isCompleted) {
            Button(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 12.sp,
                    color = onAccentColor(accentColor),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
    )
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
