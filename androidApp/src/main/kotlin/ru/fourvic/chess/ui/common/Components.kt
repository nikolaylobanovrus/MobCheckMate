package ru.fourvic.chess.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.fourvic.chess.R
import ru.fourvic.chess.shared.protocol.Speed
import ru.fourvic.chess.ui.theme.Gradient
import ru.fourvic.chess.ui.theme.LocalBrand

// --- Interaction --------------------------------------------------------------------------------------------------

/** Clickable with a ripple and a slight springy shrink while pressed. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.965f else 1f, spring(dampingRatio = 0.55f, stiffness = 700f), label = "press")
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interaction, ripple(), enabled = enabled, role = role, onClick = onClick)
}

@Composable
fun AppIcon(@DrawableRes id: Int, modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = LocalContentColor.current) {
    Icon(painterResource(id), contentDescription = null, modifier = modifier.size(size), tint = tint)
}

// --- Cards --------------------------------------------------------------------------------------------------------

/** Vivid card for the main destinations: a gradient with a soft light in the corner, white content. */
@Composable
fun GradientCard(
    gradient: Gradient,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: RoundedCornerShape = RoundedCornerShape(28.dp),
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier.clip(shape)
    Box(
        (if (onClick != null) base.pressable(onClick = onClick) else base)
            .background(gradient.brush())
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.26f), Color.Transparent),
                        center = Offset(size.width * 0.05f, 0f),
                        radius = size.maxDimension * 0.75f,
                    ),
                    radius = size.maxDimension,
                    center = Offset(size.width * 0.05f, 0f),
                )
            },
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** Calm card on the surface: a hairline border in the dark theme, a soft fill in the light one. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = if (LocalBrand.current.dark) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val base = modifier.clip(shape)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            (if (onClick != null) base.pressable(onClick = onClick) else base)
                .background(color)
                .border(BorderStroke(1.dp, border), shape)
                .padding(contentPadding),
            content = content,
        )
    }
}

// --- People -------------------------------------------------------------------------------------------------------

private val AvatarGradients = listOf(
    Gradient(Color(0xFFFFB443), Color(0xFFF06A2A)),
    Gradient(Color(0xFF8E7BFF), Color(0xFF5440D6)),
    Gradient(Color(0xFF22C58B), Color(0xFF0C7B63)),
    Gradient(Color(0xFF40B8F5), Color(0xFF2667E8)),
    Gradient(Color(0xFFFF7AA8), Color(0xFFC2185B)),
    Gradient(Color(0xFFF7C64F), Color(0xFFB8741A)),
    Gradient(Color(0xFF5EE0D0), Color(0xFF138A9E)),
)

/**
 * Round avatar: a gradient picked by [seed] with the first letter of [name], or [icon] instead of the letter.
 * [country] adds the flag in the corner.
 */
@Composable
fun Avatar(
    name: String,
    seed: String = name,
    size: Dp = 44.dp,
    country: String? = null,
    @DrawableRes icon: Int? = null,
    gradient: Gradient? = null,
) {
    val fill = gradient ?: AvatarGradients[Math.floorMod(seed.hashCode(), AvatarGradients.size)]
    Box(Modifier.size(size)) {
        Box(
            Modifier.size(size).clip(CircleShape).background(fill.brush()),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                AppIcon(icon, size = size * 0.55f, tint = Color.White)
            } else {
                Text(
                    name.trim().firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = (size.value * 0.42f).sp, lineHeight = (size.value * 0.5f).sp),
                )
            }
        }
        val flag = flagEmoji(country)
        if (flag.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .size(size * 0.46f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Text(flag, fontSize = (size.value * 0.26f).sp, lineHeight = (size.value * 0.3f).sp)
            }
        }
    }
}

// --- Buttons ------------------------------------------------------------------------------------------------------

/** The main call to action: a tall gold pill. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
) {
    PillButton(text, onClick, modifier, icon, enabled, container, content)
}

/** A quieter pill on the surface. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    PillButton(text, onClick, modifier, icon, enabled, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    @DrawableRes icon: Int?,
    enabled: Boolean,
    container: Color,
    content: Color,
) {
    Row(
        modifier
            .heightIn(min = 52.dp)
            .clip(CircleShape)
            .pressable(enabled = enabled, onClick = onClick)
            .background(if (enabled) container else container.copy(alpha = 0.38f))
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (enabled) content else content.copy(alpha = 0.6f)
        if (icon != null) AppIcon(icon, size = 20.dp, tint = color)
        Text(text, color = color, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Square icon with a label under it, for the action bars under the board. */
@Composable
fun ActionButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    container: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val background = container ?: if (highlighted) scheme.primary else scheme.surfaceContainerHigh
    val tint = when {
        container != null -> Color.White
        highlighted -> scheme.onPrimary
        else -> scheme.onSurface
    }
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .pressable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(width = 56.dp, height = 44.dp).clip(RoundedCornerShape(16.dp)).background(background),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, size = 22.dp, tint = tint)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Round icon button on a tonal background, used in top bars. */
@Composable
fun RoundIconButton(@DrawableRes icon: Int, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .pressable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, size = 22.dp)
    }
}

// --- Bars and headers ---------------------------------------------------------------------------------------------

/** Title bar of inner screens: a round back button, the title and optional actions on the right. */
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) RoundIconButton(R.drawable.ic_arrow_back, stringResource(R.string.back), onBack)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

/** Large title of a tab with an optional element on the right. */
@Composable
fun TabHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

// --- Small pieces -------------------------------------------------------------------------------------------------

/** Small rounded label with an optional icon. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.labelLarge,
) {
    Row(
        modifier.clip(CircleShape).background(container).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) AppIcon(icon, size = 16.dp, tint = content)
        Text(text, style = style, color = content, maxLines = 1)
    }
}

/** Segmented choice with a thumb that slides to the selected item. */
@Composable
fun SegmentedControl(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<Int?> = emptyList(),
) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh)
            .padding(4.dp),
    ) {
        val itemWidth = maxWidth / items.size
        val offset by animateDpAsState(itemWidth * selected, spring(dampingRatio = 0.8f, stiffness = 500f), label = "segment")
        Box(
            Modifier
                .offset { IntOffset(offset.roundToPx(), 0) }
                .width(itemWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(scheme.surfaceContainerLowest.takeIf { !LocalBrand.current.dark } ?: scheme.surfaceBright),
        )
        Row(Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, label ->
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(role = Role.Tab) { onSelect(index) },
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val color = if (index == selected) scheme.onSurface else scheme.onSurfaceVariant
                    icons.getOrNull(index)?.let { AppIcon(it, size = 18.dp, tint = color) }
                    Text(label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Pulsing dot for things happening right now. */
@Composable
fun LiveDot(color: Color = MaterialTheme.colorScheme.error, size: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "live")
    val pulse by transition.animateFloat(0.6f, 1.8f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing)), label = "pulse")
    val fade by transition.animateFloat(0.55f, 0f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing)), label = "fade")
    Box(Modifier.size(size * 2), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).scale(pulse).alpha(fade).clip(CircleShape).background(color))
        Box(Modifier.size(size).clip(CircleShape).background(color))
    }
}

/** Three dots that blink in turn, for "thinking" and "waiting". */
@Composable
fun TypingDots(color: Color = LocalContentColor.current) {
    val transition = rememberInfiniteTransition(label = "dots")
    val phase by transition.animateFloat(0f, 3f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val active = phase.toInt() == index
            Box(Modifier.size(5.dp).alpha(if (active) 1f else 0.35f).clip(CircleShape).background(color))
        }
    }
}

enum class Tone { NEUTRAL, INFO, SUCCESS, WARNING, DANGER }

/** Rounded notice with an icon and optional actions on the right. */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    tone: Tone = Tone.NEUTRAL,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        Tone.NEUTRAL -> scheme.surfaceContainerHigh to scheme.onSurface
        Tone.INFO -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        Tone.SUCCESS -> scheme.secondaryContainer to scheme.onSecondaryContainer
        Tone.WARNING -> scheme.primaryContainer to scheme.onPrimaryContainer
        Tone.DANGER -> scheme.errorContainer to scheme.onErrorContainer
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(18.dp), modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) AppIcon(icon, size = 20.dp)
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
            actions()
        }
    }
}

/** Big number with a caption, for ratings and scores. */
@Composable
fun StatBlock(value: String, label: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, accent: Color? = null) {
    SurfaceCard(modifier, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), shape = RoundedCornerShape(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) AppIcon(icon, size = 16.dp, tint = accent ?: MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = accent ?: MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** Icon for a speed of play: bolt for bullet, fire for blitz, timer for rapid, hourglass for classical. */
@DrawableRes
fun speedIcon(speed: Speed): Int = when (speed) {
    Speed.BULLET -> R.drawable.ic_bolt_filled
    Speed.BLITZ -> R.drawable.ic_local_fire_department_filled
    Speed.RAPID -> R.drawable.ic_timer_filled
    Speed.CLASSICAL -> R.drawable.ic_hourglass_top
}

@Composable
fun speedColor(speed: Speed): Color = when (speed) {
    Speed.BULLET -> Color(0xFFFFB443)
    Speed.BLITZ -> Color(0xFFFF7A45)
    Speed.RAPID -> LocalBrand.current.win
    Speed.CLASSICAL -> MaterialTheme.colorScheme.tertiary
}
