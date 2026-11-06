package ru.fourvic.chess.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.fourvic.chess.R
import ru.fourvic.chess.settings.LocalAppSettings
import ru.fourvic.chess.shared.chess.BoardPiece
import ru.fourvic.chess.shared.chess.PieceKind
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.ui.game.GameUiState
import ru.fourvic.chess.ui.theme.BoardPalette
import ru.fourvic.chess.ui.theme.Manrope

/** A piece sliding from one square to another, [progress] going from 0 to 1. */
private data class Slide(val piece: BoardPiece, val from: String, val to: String)

private data class Dragged(val piece: BoardPiece, val from: String, val position: Offset)

/**
 * The board, drawn on one Canvas with the Cburnett pieces (BSD licence).
 *
 * Moves are made by tapping the piece and then the square, or by dragging the piece; both end up as taps in
 * [onSquareTapped], so the screens keep one input path. The last move slides into place; a move made by dragging
 * does not, as the piece is already there. Pieces of [movableColor] can be dragged; null means none.
 */
@Composable
fun ChessBoard(
    state: GameUiState,
    onSquareTapped: (String) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    movableColor: PlayerColor? = state.sideToMove,
    interactive: Boolean = true,
    cornerRadius: Float = 14f,
    coordinates: Boolean = LocalAppSettings.current.showCoordinates,
) {
    val painters = rememberPiecePainters()
    val settings = LocalAppSettings.current
    val palette = BoardPalette.of(settings.boardTheme)
    val textMeasurer = rememberTextMeasurer()
    val flipped = state.flipped

    // --- Sliding the last move ---
    val slideProgress = remember { Animatable(1f) }
    var slides by remember { mutableStateOf(emptyList<Slide>()) }
    var seenPly by remember { mutableIntStateOf(state.ply) }
    var droppedOn by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.ply, state.lastMove) {
        val move = state.lastMove
        val stepForward = state.ply == seenPly + 1
        seenPly = state.ply
        if (move == null || !stepForward || droppedOn == move.to) {
            droppedOn = null
            slides = emptyList()
            return@LaunchedEffect
        }
        droppedOn = null
        val piece = state.pieces[move.to] ?: return@LaunchedEffect
        val moving = mutableListOf(Slide(piece, move.from, move.to))
        // Castling: the rook slides too.
        if (piece.kind == PieceKind.KING && kotlin.math.abs(move.from[0] - move.to[0]) == 2) {
            val rank = move.to[1]
            val (rookFrom, rookTo) = if (move.to[0] > move.from[0]) "h$rank" to "f$rank" else "a$rank" to "d$rank"
            state.pieces[rookTo]?.let { moving += Slide(it, rookFrom, rookTo) }
        }
        slides = moving
        slideProgress.snapTo(0f)
        slideProgress.animateTo(1f, tween(durationMillis = 190, easing = FastOutSlowInEasing))
        slides = emptyList()
    }

    // --- Dragging ---
    var dragged by remember { mutableStateOf<Dragged?>(null) }
    val current by rememberUpdatedState(state)
    val tap by rememberUpdatedState(onSquareTapped)
    val movable by rememberUpdatedState(movableColor)

    val input = if (!interactive) Modifier else Modifier.pointerInput(flipped) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val cell = size.width / 8f
            fun squareUnder(position: Offset): String? {
                if (position.x < 0 || position.y < 0 || position.x >= size.width || position.y >= size.height) return null
                return squareAt((position.x / cell).toInt().coerceIn(0, 7), (position.y / cell).toInt().coerceIn(0, 7), flipped)
            }
            val start = squareUnder(down.position) ?: return@awaitEachGesture
            val piece = current.pieces[start]
            var slopPassed = false
            val afterSlop = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                slopPassed = true
                change.consume()
            }
            if (afterSlop == null) {
                // Lifted without moving: a tap. (Cancelled gestures, such as a scroll taking over, do nothing.)
                if (!slopPassed && currentEvent.changes.any { it.id == down.id && !it.pressed }) tap(start)
                return@awaitEachGesture
            }
            if (piece == null || piece.color != movable) return@awaitEachGesture
            if (current.selected != start) tap(start)
            dragged = Dragged(piece, start, afterSlop.position)
            drag(afterSlop.id) { change ->
                dragged = dragged?.copy(position = change.position)
                change.consume()
            }
            val target = dragged?.position?.let(::squareUnder)
            dragged = null
            if (target != null && target != start) {
                droppedOn = target
                tap(target)
            }
        }
    }

    val shape = RoundedCornerShape(cornerRadius.dp)
    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(10.dp, shape, clip = false)
            .clip(shape)
            .semantics { this.contentDescription = contentDescription }
            .then(input),
    ) {
        val cell = size.width / 8f
        val cellSize = Size(cell, cell)
        val sliding = slides.associateBy { it.to }
        val drag = dragged
        val hovered = drag?.let { d ->
            val column = (d.position.x / cell).toInt()
            val row = (d.position.y / cell).toInt()
            if (column in 0..7 && row in 0..7) squareAt(column, row, flipped) else null
        }

        for (row in 0 until 8) {
            for (column in 0 until 8) {
                val square = squareAt(column, row, flipped)
                val topLeft = Offset(column * cell, row * cell)
                val isLight = (column + row) % 2 == 0
                drawRect(if (isLight) palette.light else palette.dark, topLeft, cellSize)

                val lastMove = state.lastMove
                if (lastMove != null && (square == lastMove.from || square == lastMove.to)) drawRect(palette.lastMove, topLeft, cellSize)
                if (square == state.selected) drawRect(palette.selected, topLeft, cellSize)
                if (square == hovered) {
                    drawRect(Color.White.copy(alpha = 0.22f), topLeft, cellSize)
                    drawRect(Color.White.copy(alpha = 0.85f), topLeft + Offset(cell * 0.03f, cell * 0.03f), Size(cell * 0.94f, cell * 0.94f), style = Stroke(cell * 0.06f))
                }
                if (square == state.checkedKing) {
                    val center = topLeft + Offset(cell / 2, cell / 2)
                    drawCircle(
                        Brush.radialGradient(listOf(palette.check, palette.check.copy(alpha = 0.55f), Color.Transparent), center, cell * 0.72f),
                        radius = cell * 0.72f,
                        center = center,
                    )
                }
                if (coordinates) drawCoordinates(textMeasurer, square, row, column, topLeft, cell, if (isLight) palette.dark else palette.light)
            }
        }

        // Pieces on their squares, except those sliding or being dragged.
        for (row in 0 until 8) {
            for (column in 0 until 8) {
                val square = squareAt(column, row, flipped)
                val piece = state.pieces[square] ?: continue
                if (square in sliding || drag?.from == square) continue
                drawPiece(painters.getValue(piece), Offset(column * cell, row * cell), cell)
            }
        }

        // Legal moves: a dot on an empty square, a ring around a piece that can be taken.
        if (settings.showLegalMoves) {
            for (target in state.targets) {
                val (column, row) = cellOf(target, flipped)
                val center = Offset(column * cell + cell / 2, row * cell + cell / 2)
                if (state.pieces[target] == null) {
                    drawCircle(palette.target, radius = cell * 0.16f, center = center)
                } else {
                    drawCircle(palette.target, radius = cell * 0.44f, center = center, style = Stroke(cell * 0.09f))
                }
            }
        }

        // The moving pieces on top.
        for (slide in slides) {
            val (fromColumn, fromRow) = cellOf(slide.from, flipped)
            val (toColumn, toRow) = cellOf(slide.to, flipped)
            val t = slideProgress.value
            val position = Offset(
                (fromColumn + (toColumn - fromColumn) * t) * cell,
                (fromRow + (toRow - fromRow) * t) * cell,
            )
            drawPiece(painters.getValue(slide.piece), position, cell)
        }
        if (drag != null) {
            val lifted = 1.3f
            val topLeft = drag.position - Offset(cell * lifted / 2, cell * lifted * 0.75f)
            drawCircle(Color.Black.copy(alpha = 0.18f), radius = cell * 0.42f, center = drag.position + Offset(0f, cell * 0.1f))
            translate(topLeft.x, topLeft.y) {
                scale(lifted, pivot = Offset.Zero) { drawPiece(painters.getValue(drag.piece), Offset.Zero, cell) }
            }
        }
    }
}

private fun DrawScope.drawPiece(painter: VectorPainter, topLeft: Offset, cell: Float) {
    val inset = cell * 0.03f
    translate(topLeft.x + inset, topLeft.y + inset) {
        with(painter) { draw(Size(cell - 2 * inset, cell - 2 * inset)) }
    }
}

private fun DrawScope.drawCoordinates(
    measurer: androidx.compose.ui.text.TextMeasurer,
    square: String,
    row: Int,
    column: Int,
    topLeft: Offset,
    cell: Float,
    color: Color,
) {
    val style = TextStyle(fontFamily = Manrope, fontSize = (cell * 0.17f / density).sp, fontWeight = FontWeight.ExtraBold)
    if (row == 7) {
        val label = measurer.measure(square.substring(0, 1), style)
        // Colour is passed at draw time: cached layouts ignore it.
        drawText(label, color = color, topLeft = Offset(topLeft.x + cell - label.size.width - cell * 0.06f, topLeft.y + cell - label.size.height - cell * 0.01f))
    }
    if (column == 0) {
        drawText(measurer.measure(square.substring(1), style), color = color, topLeft = Offset(topLeft.x + cell * 0.06f, topLeft.y + cell * 0.02f))
    }
}

/** Square name under a board cell; row 0 is the top of the screen. */
internal fun squareAt(column: Int, row: Int, flipped: Boolean): String {
    val file = if (flipped) 'h' - column else 'a' + column
    val rank = if (flipped) row + 1 else 8 - row
    return "$file$rank"
}

/** Column and row of a square on screen. */
internal fun cellOf(square: String, flipped: Boolean): Pair<Int, Int> {
    val file = square[0] - 'a'
    val rank = square[1] - '1'
    return if (flipped) (7 - file) to rank else file to (7 - rank)
}

@Composable
internal fun rememberPiecePainters(): Map<BoardPiece, VectorPainter> {
    @Composable
    fun painter(id: Int) = rememberVectorPainter(ImageVector.vectorResource(id))

    val white = PlayerColor.WHITE
    val black = PlayerColor.BLACK
    return mapOf(
        BoardPiece(white, PieceKind.KING) to painter(R.drawable.piece_wk),
        BoardPiece(white, PieceKind.QUEEN) to painter(R.drawable.piece_wq),
        BoardPiece(white, PieceKind.ROOK) to painter(R.drawable.piece_wr),
        BoardPiece(white, PieceKind.BISHOP) to painter(R.drawable.piece_wb),
        BoardPiece(white, PieceKind.KNIGHT) to painter(R.drawable.piece_wn),
        BoardPiece(white, PieceKind.PAWN) to painter(R.drawable.piece_wp),
        BoardPiece(black, PieceKind.KING) to painter(R.drawable.piece_bk),
        BoardPiece(black, PieceKind.QUEEN) to painter(R.drawable.piece_bq),
        BoardPiece(black, PieceKind.ROOK) to painter(R.drawable.piece_br),
        BoardPiece(black, PieceKind.BISHOP) to painter(R.drawable.piece_bb),
        BoardPiece(black, PieceKind.KNIGHT) to painter(R.drawable.piece_bn),
        BoardPiece(black, PieceKind.PAWN) to painter(R.drawable.piece_bp),
    )
}

/** Drawable of one piece, for small piece pictures outside the board. */
fun pieceDrawable(piece: BoardPiece): Int = when (piece.color) {
    PlayerColor.WHITE -> when (piece.kind) {
        PieceKind.KING -> R.drawable.piece_wk
        PieceKind.QUEEN -> R.drawable.piece_wq
        PieceKind.ROOK -> R.drawable.piece_wr
        PieceKind.BISHOP -> R.drawable.piece_wb
        PieceKind.KNIGHT -> R.drawable.piece_wn
        PieceKind.PAWN -> R.drawable.piece_wp
    }
    PlayerColor.BLACK -> when (piece.kind) {
        PieceKind.KING -> R.drawable.piece_bk
        PieceKind.QUEEN -> R.drawable.piece_bq
        PieceKind.ROOK -> R.drawable.piece_br
        PieceKind.BISHOP -> R.drawable.piece_bb
        PieceKind.KNIGHT -> R.drawable.piece_bn
        PieceKind.PAWN -> R.drawable.piece_bp
    }
}
