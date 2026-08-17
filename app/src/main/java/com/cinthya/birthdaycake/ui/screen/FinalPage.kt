package com.cinthya.birthdaycake.ui.screen

import androidx.annotation.RawRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.cinthya.birthdaycake.R
import com.cinthya.birthdaycake.model.CharacterExpressions
import com.cinthya.birthdaycake.ui.components.GamePageScaffold
import com.cinthya.birthdaycake.ui.components.GamePageScope
import com.cinthya.birthdaycake.ui.theme.BirthdayCakeTheme
import com.cinthya.birthdaycake.ui.theme.GameColors

/** Sized off the page width so the cake scales with the screen. */
private const val CAKE_WIDTH = 0.75f
private const val MESSAGE_WIDTH = 0.85f

/**
 * All three animations ship faster than a background flourish wants to be, so each plays
 * below 1x. The confetti is the slowest because it covers the whole page.
 */
private const val CONFETTI_SPEED = 0.5f
private const val SPARKLE_SPEED = 0.6f
private const val HEART_SPEED = 0.5f

@Composable
fun GamePageScope.FinalPage(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        // Drawn first, so it stays behind the cake and the message.
        LoopingLottie(
            R.raw.confetti,
            CONFETTI_SPEED,
            Modifier.matchParentSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // The cat belongs to the scaffold; this only keeps its corner clear.
            Spacer(Modifier.size(characterSize))

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(CAKE_WIDTH)
                    .padding(vertical = 16.dp)
            ) {
                Image(
                    painterResource(R.drawable.img_cake),
                    contentDescription = stringResource(R.string.cd_birthday_cake),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Bound to this box rather than the page, so they decorate the cake only.
                LoopingLottie(R.raw.sparkle, SPARKLE_SPEED, Modifier.matchParentSize())
                LoopingLottie(R.raw.heart, HEART_SPEED, Modifier.matchParentSize())
            }

            Text(
                stringResource(R.string.final_title),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = GameColors.MainBlack
            )

            Spacer(Modifier.height(12.dp))

            Text(
                stringResource(R.string.final_message),
                modifier = Modifier.fillMaxWidth(MESSAGE_WIDTH),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = GameColors.MainBlack
            )

            Spacer(Modifier.weight(0.5f))
        }
    }
}

/**
 * A decorative animation that loops for as long as the page is on screen.
 *
 * The `.lottie` files are zip containers; Lottie detects that from the raw resource itself,
 * so they load through the same [LottieCompositionSpec.RawRes] a bare `.json` would.
 */
@Composable
private fun LoopingLottie(
    @RawRes res: Int,
    speed: Float,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(res))

    LottieAnimation(
        composition,
        modifier = modifier,
        iterations = LottieConstants.IterateForever,
        speed = speed,
        contentScale = contentScale
    )
}

@Preview(showBackground = true)
@Composable
private fun FinalPagePrev() {
    BirthdayCakeTheme {
        GamePageScaffold(CharacterExpressions.LAUGHING) {
            FinalPage()
        }
    }
}
