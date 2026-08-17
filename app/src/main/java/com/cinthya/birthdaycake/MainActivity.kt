package com.cinthya.birthdaycake

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cinthya.birthdaycake.model.GameScreen
import com.cinthya.birthdaycake.ui.components.GamePageScaffold
import com.cinthya.birthdaycake.ui.components.dialog.DialogOverlayHost
import com.cinthya.birthdaycake.ui.screen.FinalPage
import com.cinthya.birthdaycake.ui.screen.MiniHuntPage
import com.cinthya.birthdaycake.ui.screen.MixingPage
import com.cinthya.birthdaycake.ui.theme.BirthdayCakeTheme
import com.cinthya.birthdaycake.ui.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BirthdayCakeTheme {
                // The page background and the dialog scrim have to reach the system bars,
                // so nothing pads the root: the app is laid out edge to edge and hands the
                // insets down as a value, for GamePageScaffold to apply to the content and
                // the cat only.
                //
                // These are read here rather than taken from a Scaffold. A Scaffold with
                // `contentWindowInsets = WindowInsets(0)` reports zero in its inner
                // padding - that is what the parameter means - so routing systemInsets
                // through it handed every page an empty PaddingValues, and the bottom
                // button sat under the navigation bar.
                BirthdayCakeApp(
                    modifier = Modifier.fillMaxSize(),
                    systemInsets = WindowInsets.safeDrawing.asPaddingValues()
                )
            }
        }
    }
}

/**
 * The whole game in one place: the page for the current screen, the dialog layer over it,
 * and - drawn once, above both - the cat.
 *
 * Swapping pages with a `when` over [GameScreen] is still enough while there are this few
 * of them; no navigation library involved. What changed is who decides: [GameViewModel]
 * moves the screen and the dialog in a single update, so the two can never disagree.
 */
@Composable
fun BirthdayCakeApp(
    modifier: Modifier = Modifier,
    systemInsets: PaddingValues = PaddingValues(0.dp),
    gameViewModel: GameViewModel = viewModel()
) {
    val game by gameViewModel.uiState.collectAsStateWithLifecycle()

    GamePageScaffold(
        expression = game.expression,
        modifier = modifier,
        systemInsets = systemInsets,
        overlay = {
            DialogOverlayHost(
                active = game.dialog,
                onAdvance = gameViewModel::onDialogAdvance,
                contentPadding = systemInsets
            )
        }
    ) {
        when (game.screen) {
            GameScreen.MIXING -> MixingPage(
                pantry = game.pantry,
                bowlCount = game.bowlCount,
                isMixReady = game.isMixReady,
                onIngredientDropped = gameViewModel::onIngredientDropped,
                onMixClick = gameViewModel::onMixClick
            )

            GameScreen.HUNT_INGREDIENTS -> MiniHuntPage(
                onFinished = gameViewModel::onHuntFinished,
                onExpressionChange = gameViewModel::onPageExpression
            )

            GameScreen.FINAL -> FinalPage()
        }
    }
}
