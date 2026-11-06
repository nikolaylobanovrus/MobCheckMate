package ru.fourvic.chess.ui.game

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.random.Random
import ru.fourvic.chess.R
import ru.fourvic.chess.shared.chess.BoardPiece
import ru.fourvic.chess.shared.chess.PieceKind
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.chess.Promotion
import ru.fourvic.chess.ui.board.pieceDrawable
import ru.fourvic.chess.ui.common.AppIcon
import ru.fourvic.chess.ui.common.Avatar
import ru.fourvic.chess.ui.common.TypingDots
import ru.fourvic.chess.ui.common.pressable
import ru.fourvic.chess.ui.theme.Gradient
import ru.fourvic.chess.ui.theme.LocalBrand
import ru.fourvic.chess.ui.theme.TabularNumbers

// --- Layout -------------------------------------------------------------------------------------------------------

/**
 * Screen with a board: the opponent above it, the user below, then [panel] (status, moves, actions).
 * In landscape the board is on the left and everything else in a column on the right.
 */
@Composable
fun GameLayout(
    top: @Composable () -> Unit,
    board: @Composable (Modifier) -> Unit,
    bottom: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    panel: @Composable ColumnScope.() -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (landscape) {
        Row(
            modifier.fillMaxSize().navigationBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            board(Modifier.fillMaxSize().weight(1f, fill = false))
            Column(
                Modifier.widthIn(max = 380.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                top()
                panel()
                bottom()
                actions()
            }
        }
    } else {
        Column(modifier.fillMaxSize().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Box(Modifier.widthIn(max = 620.dp).padding(horizontal = 14.dp)) { top() }
                board(Modifier.fillMaxWidth().widthIn(max = 620.dp).padding(horizontal = 8.dp))
                Box(Modifier.widthIn(max = 620.dp).padding(horizontal = 14.dp)) { bottom() }
                Column(
                    Modifier.widthIn(max = 620.dp).fillMaxWidth().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = panel,
                )
            }
            Box(Modifier.widthIn(max = 620.dp).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) { actions() }
        }
    }
}

/** Row of action buttons, spread evenly. */
@Composable
fun ActionBar(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
        content()
    }
}

// --- Players ------------------------------------------------------------------------------------------------------

/** Clock of a player: time left and whether it is ticking. */
data class ClockFace(val ms: Long, val running: Boolean)

/**
 * A player next to the board: avatar, name, a line under it (rating or level), the material they are up and,
 * online, the clock. [toMove] marks whose turn it is.
 */
@Composable
fun PlayerStrip(
    name: String,
    detail: String,
    color: PlayerColor,
    balance: MaterialBalance,
    modifier: Modifier = Modifier,
    seed: String = name,
    country: String? = null,
    @DrawableRes avatarIcon: Int? = null,
    avatarGradient: Gradient? = null,
    toMove: Boolean = false,
    clock: ClockFace? = null,
    badge: String? = null,
) {
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .clip(CircleShape)
                .border(2.dp, if (toMove && clock == null) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                .padding(2.dp),
        ) {
            Avatar(name, seed = seed, size = 40.dp, country = country, icon = avatarIcon, gradient = avatarGradient)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (badge != null) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.error).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                CapturedPieces(balance, color)
            }
        }
        if (clock != null) ClockPill(clock, toMove)
    }
}

/** Pieces [color] is up, drawn in the opponent's colour (as if taken), and the points ahead. */
@Composable
private fun CapturedPieces(balance: MaterialBalance, color: PlayerColor) {
    val kinds = if (color == PlayerColor.WHITE) balance.white else balance.black
    val opponent = if (color == PlayerColor.WHITE) PlayerColor.BLACK else PlayerColor.WHITE
    val score = if (color == PlayerColor.WHITE) balance.score else -balance.score
    if (kinds.isEmpty() && score <= 0) return
    // Black pieces need a light backing on the dark theme.
    val backing = if (LocalBrand.current.dark && opponent == PlayerColor.BLACK && kinds.isNotEmpty()) Color(0xFFDCD8CC) else Color.Transparent
    Row(
        Modifier.clip(CircleShape).background(backing).padding(horizontal = if (backing == Color.Transparent) 0.dp else 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // Same pieces overlap, different ones sit apart.
        kinds.groupBy { it }.forEach { (kind, group) ->
            Box(Modifier.width(16.dp + 7.dp * (group.size - 1)).height(16.dp)) {
                group.indices.forEach { index ->
                    Image(
                        painterResource(pieceDrawable(BoardPiece(opponent, kind))),
                        contentDescription = null,
                        modifier = Modifier.offset(x = 7.dp * index).size(16.dp),
                    )
                }
            }
        }
        if (score > 0) {
            Text(
                "+$score",
                style = MaterialTheme.typography.labelMedium,
                color = if (backing == Color.Transparent) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF3A3F47),
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

@Composable
private fun ClockPill(clock: ClockFace, toMove: Boolean) {
    val low = clock.ms < 20_000
    val scheme = MaterialTheme.colorScheme
    val active = toMove && clock.running
    val container = when {
        active && low -> scheme.error
        active -> scheme.primary
        else -> scheme.surfaceContainerHigh
    }
    val content = when {
        active && low -> scheme.onError
        active -> scheme.onPrimary
        low -> scheme.error
        else -> scheme.onSurface
    }
    val pulse = if (active && low) {
        val transition = rememberInfiniteTransition(label = "low")
        transition.animateFloat(1f, 1.05f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "pulse").value
    } else {
        1f
    }
    Row(
        Modifier.scale(pulse).clip(RoundedCornerShape(14.dp)).background(container).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (active) AppIcon(R.drawable.ic_timer_filled, size = 18.dp, tint = content)
        Text(
            formatClock(clock.ms),
            style = MaterialTheme.typography.titleLarge.merge(TabularNumbers),
            color = content,
        )
    }
}

/** 5:03 normally; 0:09.4 under ten seconds, when every tenth matters. */
internal fun formatClock(ms: Long): String {
    val clamped = ms.coerceAtLeast(0)
    val totalSeconds = clamped / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (clamped < 10_000) {
        "%d:%02d.%d".format(minutes, seconds, (clamped % 1000) / 100)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

// --- Material -----------------------------------------------------------------------------------------------------

/**
 * Who is up material: the extra pieces of each side by kind (as lichess shows them) and White's lead in points.
 * Comparing the sides instead of counting captures keeps promotions right.
 */
data class MaterialBalance(val white: List<PieceKind>, val black: List<PieceKind>, val score: Int) {
    companion object {
        val EVEN = MaterialBalance(emptyList(), emptyList(), 0)
    }
}

private val ORDER = listOf(PieceKind.QUEEN, PieceKind.ROOK, PieceKind.BISHOP, PieceKind.KNIGHT, PieceKind.PAWN)
private fun PieceKind.points() = when (this) {
    PieceKind.QUEEN -> 9
    PieceKind.ROOK -> 5
    PieceKind.BISHOP, PieceKind.KNIGHT -> 3
    PieceKind.PAWN -> 1
    PieceKind.KING -> 0
}

fun materialBalance(pieces: Map<String, BoardPiece>): MaterialBalance {
    val white = mutableListOf<PieceKind>()
    val black = mutableListOf<PieceKind>()
    var score = 0
    for (kind in ORDER) {
        val difference = pieces.values.count { it.kind == kind && it.color == PlayerColor.WHITE } -
            pieces.values.count { it.kind == kind && it.color == PlayerColor.BLACK }
        repeat(difference.coerceAtLeast(0)) { white += kind }
        repeat((-difference).coerceAtLeast(0)) { black += kind }
        score += difference * kind.points()
    }
    return MaterialBalance(white, black, score)
}

// --- Moves and status ---------------------------------------------------------------------------------------------

/** The moves so far in one scrolling line, the latest highlighted and kept in view. */
@Composable
fun MoveList(san: List<String>, modifier: Modifier = Modifier) {
    if (san.isEmpty()) return
    val listState = rememberLazyListState()
    LaunchedEffect(san.size) { listState.animateScrollToItem(san.lastIndex) }
    LazyRow(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
        state = listState,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(san) { index, move ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index % 2 == 0) {
                    Text(
                        "${index / 2 + 1}.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, end = 2.dp),
                    )
                }
                val last = index == san.lastIndex
                Text(
                    move,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (last) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (last) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** One-line state of the game in a soft pill, with dots while the other side thinks. */
@Composable
fun StatusLine(text: String, modifier: Modifier = Modifier, thinking: Boolean = false, alert: Boolean = false, @DrawableRes icon: Int? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(if (alert) scheme.errorContainer else scheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val color = if (alert) scheme.onErrorContainer else scheme.onSurface
            if (icon != null) AppIcon(icon, size = 18.dp, tint = color)
            Text(text, style = MaterialTheme.typography.titleSmall, color = color, textAlign = TextAlign.Center)
            if (thinking) TypingDots(color)
        }
    }
}

/** A piece on a light square-coloured tile, so black pieces stay visible on the dark theme. */
@Composable
fun PieceTile(piece: BoardPiece, size: Dp, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(if (highlighted) Color(0xFFF7DC8C) else Color(0xFFEEEED2)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(pieceDrawable(piece)), contentDescription = null, modifier = Modifier.size(size * 0.86f))
    }
}

// --- Promotion ----------------------------------------------------------------------------------------------------

/** The four pieces a pawn can become, as pictures in the mover's colour. */
@Composable
internal fun PromotionDialog(color: PlayerColor = PlayerColor.WHITE, onChosen: (Promotion?) -> Unit) {
    Dialog(onDismissRequest = { onChosen(null) }) {
        Column(
            Modifier.clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.promotion_title), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Promotion.QUEEN to PieceKind.QUEEN,
                    Promotion.ROOK to PieceKind.ROOK,
                    Promotion.BISHOP to PieceKind.BISHOP,
                    Promotion.KNIGHT to PieceKind.KNIGHT,
                ).forEach { (promotion, kind) ->
                    val name = pieceName(kind)
                    PieceTile(
                        BoardPiece(color, kind),
                        64.dp,
                        Modifier.clip(RoundedCornerShape(18.dp)).pressable { onChosen(promotion) }.semantics { contentDescription = name },
                        highlighted = kind == PieceKind.QUEEN,
                    )
                }
            }
        }
    }
}

@Composable
private fun pieceName(kind: PieceKind): String = stringResource(
    when (kind) {
        PieceKind.QUEEN -> R.string.piece_queen
        PieceKind.ROOK -> R.string.piece_rook
        PieceKind.BISHOP -> R.string.piece_bishop
        else -> R.string.piece_knight
    },
)

// --- Results ------------------------------------------------------------------------------------------------------

enum class Outcome { WIN, LOSS, DRAW, NEUTRAL }

/** Result of a game: a big icon and title that pop in, the reason, and the next steps in [actions]. */
@Composable
fun ResultCard(
    title: String,
    outcome: Outcome,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    extra: @Composable () -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit,
) {
    val brand = LocalBrand.current
    val accent = when (outcome) {
        Outcome.WIN -> brand.gold
        Outcome.LOSS -> brand.loss
        Outcome.DRAW, Outcome.NEUTRAL -> brand.draw
    }
    val icon = when (outcome) {
        Outcome.WIN -> R.drawable.ic_emoji_events_filled
        Outcome.LOSS -> R.drawable.ic_flag_filled
        Outcome.DRAW -> R.drawable.ic_handshake
        Outcome.NEUTRAL -> R.drawable.ic_info
    }
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(spring(dampingRatio = 0.6f, stiffness = 300f), initialScale = 0.85f),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(28.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                AppIcon(icon, size = 34.dp, tint = accent)
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            extra()
            Spacer(Modifier.height(4.dp))
            actions()
        }
    }
}

/** Confetti raining down once, for a win. */
@Composable
fun Confetti(modifier: Modifier = Modifier, seed: Int = 7) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(2600, easing = LinearEasing)) }
    if (progress.value >= 1f) return
    val colors = listOf(Color(0xFFF5C04E), Color(0xFF3DD68C), Color(0xFF8E7BFF), Color(0xFFFF7AA8), Color(0xFF40B8F5), Color.White)
    val pieces = remember(seed) {
        val random = Random(seed)
        List(90) {
            floatArrayOf(random.nextFloat(), random.nextFloat() * 0.6f, 0.6f + random.nextFloat() * 0.8f, random.nextFloat() * 360f, random.nextInt(colors.size).toFloat(), 0.5f + random.nextFloat())
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        for (p in pieces) {
            val (x, delay, speed, spin, colorIndex, sizeFactor) = p
            val local = ((t - delay * 0.4f) * speed).coerceIn(0f, 1.2f)
            if (local <= 0f) continue
            val y = -20f + local * (size.height + 40f)
            val sway = kotlin.math.sin((local * 6 + x * 10).toDouble()).toFloat() * 24f
            val w = 10f * sizeFactor
            rotate(spin + local * 540f, Offset(x * size.width + sway, y)) {
                drawRect(
                    colors[colorIndex.toInt()].copy(alpha = (1.2f - local).coerceIn(0f, 1f)),
                    topLeft = Offset(x * size.width + sway - w / 2, y - w),
                    size = Size(w, w * 2),
                )
            }
        }
    }
}

private operator fun FloatArray.component6() = this[5]

/** Small square board picture size for previews. */
val PreviewBoardSize: Dp = 112.dp
