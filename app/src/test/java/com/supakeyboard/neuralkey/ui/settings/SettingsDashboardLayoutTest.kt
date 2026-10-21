package com.supakeyboard.neuralkey.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.supakeyboard.neuralkey.data.db.NeuralKeyDatabase
import com.supakeyboard.neuralkey.data.repository.OpenRouterRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class SettingsDashboardLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var showContent by mutableStateOf(true)

    private fun setDashboardContent() {
        showContent = true
        composeRule.setContent {
            if (showContent) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Box(modifier = Modifier.size(width = 360.dp, height = 780.dp)) {
                        CompanionDashboard()
                    }
                }
            }
        }
    }

    @After
    fun tearDown() {
        // Remove the composition first so Room flows stop collecting before the DB is closed.
        showContent = false
        composeRule.waitForIdle()
        OpenRouterRepository.resetInstance()
        NeuralKeyDatabase.resetInstance()
        com.supakeyboard.neuralkey.ime.clipboard.ClipboardHistoryManager.resetInstance()
    }

    private fun SemanticsNodeInteraction.assertSingleLineText(label: String) {
        val node = this.assertIsDisplayed().fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)
        action?.action?.invoke(results)
        assertTrue("Expected TextLayoutResult for '$label'", results.isNotEmpty())
        val layout = results.first()
        assertEquals(
            "'$label' must render on exactly 1 horizontal line (never wrap vertically), got lineCount=${layout.lineCount}, size=${layout.size}",
            1,
            layout.lineCount
        )
        assertFalse(
            "'$label' must not overflow vertically (didOverflowHeight=${layout.didOverflowHeight}, size=${layout.size})",
            layout.didOverflowHeight
        )
    }

    @Test
    fun setupNeededPillAndHeaderRenderHorizontallyOnCompactPhone() {
        setDashboardContent()
        composeRule.waitForIdle()

        val density = composeRule.density

        // 1. Verify "Setup Needed" pill is horizontal (width > height) and single-line height (<= 40dp)
        val statusPillBounds = composeRule.onNodeWithTag("ime_status_pill", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val pillWidthDp = with(density) { statusPillBounds.width.toDp() }
        val pillHeightDp = with(density) { statusPillBounds.height.toDp() }

        assertTrue(
            "Status pill must render horizontally on one line, got ${pillWidthDp}x${pillHeightDp}",
            pillWidthDp > pillHeightDp && pillHeightDp <= 40.dp
        )

        composeRule.onNodeWithText("Setup Needed", useUnmergedTree = true)
            .assertSingleLineText("Setup Needed")

        // 2. Verify "NeuralKey AI" and "v1.3 FREE" render on a single line without wrapping
        composeRule.onNodeWithText("NeuralKey AI", useUnmergedTree = true)
            .assertSingleLineText("NeuralKey AI")
        composeRule.onNodeWithText("v1.3 FREE", useUnmergedTree = true)
            .assertSingleLineText("v1.3 FREE")

        // 3. Verify all 5 tabs are displayed, evenly sized, and single-line
        val tabLabels = listOf("Setup", "Typing", "AI Tools", "Models", "Themes")
        for (i in 0..4) {
            val tabBounds = composeRule.onNodeWithTag("dashboard_tab_$i", useUnmergedTree = true)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
            val tabWidthDp = with(density) { tabBounds.width.toDp() }
            val tabHeightDp = with(density) { tabBounds.height.toDp() }
            assertTrue(
                "Tab $i must have balanced compact bounds, got ${tabWidthDp}x${tabHeightDp}",
                tabWidthDp >= 50.dp && tabHeightDp <= 64.dp
            )
            composeRule.onNodeWithText(tabLabels[i], useUnmergedTree = true)
                .assertSingleLineText(tabLabels[i])
        }
    }

    @Test
    fun allFiveDashboardTabsRenderSingleLineHeadersAndActionButtons() {
        setDashboardContent()
        composeRule.waitForIdle()

        // Tab 0: Setup
        composeRule.onNodeWithText("Save Key", useUnmergedTree = true)
            .assertSingleLineText("Save Key")
        composeRule.onNodeWithText("Verify Key", useUnmergedTree = true)
            .assertSingleLineText("Verify Key")
        composeRule.onNodeWithText("OpenRouter API Vault", useUnmergedTree = true)
            .assertSingleLineText("OpenRouter API Vault")

        // Tab 1: Typing & Posture
        composeRule.onNodeWithTag("dashboard_tab_1").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("0 Learned", useUnmergedTree = true)
            .assertSingleLineText("Learned Badge")
        composeRule.onNodeWithText("Teach", useUnmergedTree = true)
            .assertSingleLineText("Teach")
        composeRule.onNodeWithText("◀ Left", useUnmergedTree = true)
            .assertSingleLineText("◀ Left")
        composeRule.onNodeWithText("⤢ Full", useUnmergedTree = true)
            .assertSingleLineText("⤢ Full")
        composeRule.onNodeWithText("Right ▶", useUnmergedTree = true)
            .assertSingleLineText("Right ▶")

        // Tab 2: AI Tools
        composeRule.onNodeWithTag("dashboard_tab_2").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Save Custom AI Tool", useUnmergedTree = true)
            .assertSingleLineText("Save Custom AI Tool")
        composeRule.onNodeWithText("Save Global Directives", useUnmergedTree = true)
            .assertSingleLineText("Save Global Directives")

        // Tab 3: Models
        composeRule.onNodeWithTag("dashboard_tab_3").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("OpenRouter Model Catalog", useUnmergedTree = true)
            .assertSingleLineText("OpenRouter Model Catalog")
        composeRule.onNodeWithText("Sync Live", useUnmergedTree = true)
            .assertSingleLineText("Sync Live")

        // Tab 4: Themes
        composeRule.onNodeWithTag("dashboard_tab_4").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("🎨 15 Flagship Themes", useUnmergedTree = true)
            .assertSingleLineText("🎨 15 Flagship Themes")
    }
}
