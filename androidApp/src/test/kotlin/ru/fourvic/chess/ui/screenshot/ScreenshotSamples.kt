package ru.fourvic.chess.ui.screenshot

import android.os.SystemClock
import ru.fourvic.chess.online.ConnectionState
import ru.fourvic.chess.shared.chess.ChessGame
import ru.fourvic.chess.shared.chess.ChessMove
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.PlayerInfo
import ru.fourvic.chess.shared.protocol.RatingInfo
import ru.fourvic.chess.shared.protocol.Speed
import ru.fourvic.chess.shared.protocol.TournamentInfo
import ru.fourvic.chess.shared.protocol.TournamentStatus
import ru.fourvic.chess.ui.game.GameUiState
import ru.fourvic.chess.ui.online.OnlineUiState
import ru.fourvic.chess.ui.puzzles.PuzzleUiState
import ru.fourvic.chess.ui.tournaments.TournamentsUiState

/** Sample data shared by the screenshot tests and the store images. */
internal object Samples {
    val ratings = listOf(
        RatingInfo(Speed.BULLET, 1488, 12, provisional = false),
        RatingInfo(Speed.BLITZ, 1542, 37, provisional = false),
        RatingInfo(Speed.RAPID, 1611, 12, provisional = false),
        RatingInfo(Speed.CLASSICAL, 1500, 0, provisional = true),
    )

    /** Position after the given UCI moves, as the board would show it. */
    fun board(fen: String = ChessGame.START_FEN, moves: List<String> = emptyList(), flipped: Boolean = false): GameUiState {
        val game = ChessGame(fen)
        moves.forEach { check(game.play(ChessMove.fromUci(it)!!)) { "illegal $it" } }
        return GameUiState(
            pieces = ChessGame.SQUARES_FROM_WHITE.mapNotNull { square -> game.pieceAt(square)?.let { square to it } }.toMap(),
            lastMove = game.moves.lastOrNull(),
            sideToMove = game.sideToMove,
            flipped = flipped,
            ply = game.moves.size,
            san = game.sanMoves,
        )
    }

    /** The daily puzzle preview used on the home screen. */
    val dailyPreview: GameUiState
        get() = board("r2qr1k1/b1p2ppp/pp4n1/P1P1p3/4P1n1/B2P2Pb/3NBP1P/RN1QR1K1 b - - 1 16", listOf("b6c5"))

    fun arenas(winner: String = "Lukas_chess"): List<TournamentInfo> {
        fun arena(id: String, schedule: String, clock: Clock, minutes: Int, status: TournamentStatus, msLeft: Long, players: Int, winner: String? = null) =
            TournamentInfo(id, schedule, clock, minutes, status, msLeft, players, rated = true, schedule = schedule, winner = winner)
        return listOf(
            arena("hourly_blitz-1", "hourly_blitz", Clock(180, 0), 30, TournamentStatus.STARTED, 1_354_000, 48),
            arena("hourly_bullet-2", "hourly_bullet", Clock(60, 0), 30, TournamentStatus.CREATED, 1_954_000, 17),
            arena("daily_blitz-3", "daily_blitz", Clock(180, 2), 60, TournamentStatus.CREATED, 6_154_000, 63),
            arena("daily_rapid-4", "daily_rapid", Clock(600, 0), 60, TournamentStatus.CREATED, 11_554_000, 21),
            arena("hourly_bullet-1", "hourly_bullet", Clock(60, 0), 30, TournamentStatus.FINISHED, 0, 52, winner = winner),
        )
    }

    fun online(me: PlayerInfo) = OnlineUiState(connection = ConnectionState.CONNECTED, me = me, ratings = ratings, registered = true)

    fun puzzles(solved: Boolean = false) = PuzzleUiState(userRating = 1630, streak = 5, dailySolvedToday = solved, dailyPreview = dailyPreview)

    fun tournaments() = TournamentsUiState(
        connection = ConnectionState.CONNECTED,
        list = arenas(),
        listReceivedAt = SystemClock.elapsedRealtime(),
        loading = false,
    )
}
