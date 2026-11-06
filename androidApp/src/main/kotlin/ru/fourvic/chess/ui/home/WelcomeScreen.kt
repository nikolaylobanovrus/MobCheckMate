package ru.fourvic.chess.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.fourvic.chess.R
import ru.fourvic.chess.settings.AppLanguage
import ru.fourvic.chess.ui.common.AppIcon
import ru.fourvic.chess.ui.common.PrimaryButton
import ru.fourvic.chess.ui.common.SectionTitle
import ru.fourvic.chess.ui.common.pressable
import ru.fourvic.chess.ui.theme.LocalBrand

/**
 * First launch: what the app offers and the language. The app starts in English; picking another language
 * restarts the screen in it.
 */
@Composable
fun WelcomeScreen(current: AppLanguage, onLanguage: (AppLanguage) -> Unit, onStart: () -> Unit) {
    val brand = LocalBrand.current
    val scheme = MaterialTheme.colorScheme
    val logoScale = remember { Animatable(0.7f) }
    LaunchedEffect(Unit) { logoScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 120f)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(brand.gold.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(size.width / 2, size.height * 0.2f),
                        radius = size.width * 0.8f,
                    ),
                    radius = size.width * 0.8f,
                    center = Offset(size.width / 2, size.height * 0.2f),
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .scale(logoScale.value)
                    .size(168.dp)
                    .clip(RoundedCornerShape(44.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1D2836), Color(0xFF0E141C)))),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(168.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.welcome_tagline),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Feature(R.drawable.ic_public_filled, brand.online, R.string.welcome_feature_online)
                Feature(R.drawable.ic_smart_toy_filled, brand.computer, R.string.welcome_feature_computer)
                Feature(R.drawable.ic_extension_filled, brand.puzzles, R.string.welcome_feature_puzzles)

                Spacer(Modifier.height(10.dp))
                SectionTitle(stringResource(R.string.settings_language))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { language ->
                        val selected = language == current
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .pressable { onLanguage(language) }
                                .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh)
                                .border(2.dp, if (selected) scheme.primary else Color.Transparent, RoundedCornerShape(18.dp))
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                language.nativeName,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                PrimaryButton(stringResource(R.string.welcome_start), onStart, Modifier.fillMaxWidth(), icon = R.drawable.ic_arrow_forward)
            }
        }
    }
}

@Composable
private fun Feature(icon: Int, gradient: ru.fourvic.chess.ui.theme.Gradient, text: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(gradient.brush()), contentAlignment = Alignment.Center) {
            AppIcon(icon, size = 22.dp, tint = Color.White)
        }
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
