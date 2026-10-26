package ru.fourvic.chess.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.sp
import ru.fourvic.chess.R
import ru.fourvic.chess.ui.game.GameUiState
import ru.fourvic.chess.settings.LocalAppSettings
import ru.fourvic.chess.ui.theme.BoardPalette
import ru.fourvic.chess.shared.chess.BoardPiece
import ru.fourvic.chess.shared.chess.PieceKind
import ru.fourvic.chess.shared.chess.PlayerColor

/**
 * Board drawn on a single Canvas. Pieces are vector drawables from the Chessnut set (Apache 2.0),
 * so they look the same on every device regardless of system fonts.
 */
@Composable
fun ChessBoard(
    state: GameUiState,
    onSquareTapped: (String) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val painters = rememberPiecePainters()
    val settings = LocalAppSettings.current
    val palette = BoardPalette.of(settings.boardTheme)
    val textMeasurer = rememberTextMeasurer()
    val flipped = state.flipped
    val tapModifier = remember(flipped) {
        Modifier.pointerInput(flipped) {
            detectTapGestures { offset ->
                val cell = size.width / 8f
                val column = (offset.x / cell).toInt().coerceIn(0, 7)
                val row = (offset.y / cell).toInt().coerceIn(0, 7)
                onSquareTapped(squareAt(column, row, flipped))
            }
        }
    }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { this.contentDescription = contentDescription }
            .then(tapModifier),
    ) {
        val cell = size.width / 8f
        val cellSize = Size(cell, cell)
        for (row in 0 until 8) {
            for (column in 0 until 8) {
                val square = squareAt(column, row, flipped)
                val topLeft = Offset(column * cell, row * cell)
                val isLight = (column + row) % 2 == 0
                drawRect(if (isLight) palette.light else palette.dark, topLeft, cellSize)

                val lastMove = state.lastMove
                if (lastMove != null && (square == lastMove.from || square == lastMove.to)) {
                    drawRect(palette.lastMove, topLeft, cellSize)
                }
                if (square == state.selected) drawRect(palette.selected, topLeft, cellSize)
                if (square == state.checkedKing) drawRect(palette.check, topLeft, cellSize)

                if (settings.showCoordinates) {
                    // Opposite square colour keeps the label readable on every theme.
                    val style = TextStyle(
                        color = if (isLight) palette.dark else palette.light,
                        fontSize = (cell * 0.16f / density).sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (row == 7) {
                        val label = textMeasurer.measure(square.substring(0, 1), style)
                        // Colour is passed at draw time: cached layouts ignore it.
                        drawText(
                            label,
                            color = style.color,
                            topLeft = Offset(topLeft.x + cell - label.size.width - cell * 0.05f, topLeft.y + cell - label.size.height),
                        )
                    }
                    if (column == 0) {
                        drawText(
                            textMeasurer.measure(square.substring(1), style),
                            color = style.color,
                            topLeft = Offset(topLeft.x + cell * 0.05f, topLeft.y),
                        )
                    }
                }

                state.pieces[square]?.let { piece ->
                    val painter = painters.getValue(piece)
                    val inset = cell * 0.04f
                    translate(topLeft.x + inset, topLeft.y + inset) {
                        with(painter) { draw(Size(cell - 2 * inset, cell - 2 * inset)) }
                    }
                }

                if (settings.showLegalMoves && square in state.targets) {
                    val center = Offset(topLeft.x + cell / 2, topLeft.y + cell / 2)
                    if (state.pieces[square] == null) {
                        drawCircle(palette.target, radius = cell * 0.15f, center = center)
                    } else {
                        drawCircle(palette.target, radius = cell * 0.45f, center = center, style = Stroke(cell * 0.07f))
                    }
                }
            }
        }
    }
}

/** Square name under a board cell; row 0 is the top of the screen. */
internal fun squareAt(column: Int, row: Int, flipped: Boolean): String {
    val file = if (flipped) 'h' - column else 'a' + column
    val rank = if (flipped) row + 1 else 8 - row
    return "$file$rank"
}

@Composable
private fun rememberPiecePainters(): Map<BoardPiece, VectorPainter> {
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
