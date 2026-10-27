package ru.fourvic.chess.shared.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MessagesTest {

    @Test
    fun clientMessagesUseTypeDiscriminator() {
        val json = ClientMessage.Move("g1", "e2e4", 0).encode()
        assertEquals("""{"type":"move","gameId":"g1","uci":"e2e4","ply":0}""", json)
        assertEquals(ClientMessage.Move("g1", "e2e4", 0), decodeClientMessage(json))
        assertEquals(ClientMessage.CancelSeek, decodeClientMessage("""{"type":"cancel_seek"}"""))
    }

    @Test
    fun serverSnapshotRoundTrips() {
        val snapshot = GameSnapshot(
            gameId = "g1", white = PlayerInfo("a", "A", 1500), black = PlayerInfo("b", "B", 1600, provisional = true),
            clock = Clock(180, 2), rated = true, moves = listOf("e2e4"), whiteMs = 180_000, blackMs = 180_000,
            clockRunning = false, firstMoveMs = 25_000,
        )
        val message = ServerMessage.Game(snapshot)
        assertEquals(message, decodeServerMessage(message.encode()))
    }

    @Test
    fun unknownFieldsFromNewerServersAreIgnored() {
        val text = """{"type":"seeking","clock":{"initialSeconds":60,"incrementSeconds":0},"future":1}"""
        assertEquals(ServerMessage.Seeking(Clock(60, 0)), decodeServerMessage(text))
    }

    @Test
    fun speedFollowsTheEstimatedDuration() {
        assertEquals(Speed.BULLET, Clock(60, 0).speed)
        assertEquals(Speed.BULLET, Clock(120, 1).speed)
        assertEquals(Speed.BLITZ, Clock(180, 2).speed)
        assertEquals(Speed.RAPID, Clock(600, 5).speed)
        assertEquals(Speed.CLASSICAL, Clock(1800, 20).speed)
        assertEquals("3+2", Clock(180, 2).label)
        assertFailsWith<IllegalArgumentException> { Clock(0, 0) }
    }

    @Test
    fun tournamentMessagesRoundTrip() {
        val create: ClientMessage = ClientMessage.CreateTournament("Клуб", Clock(180, 0), minutes = 30)
        assertEquals(create, decodeClientMessage(create.encode()))
        assertEquals(ClientMessage.ListTournaments, decodeClientMessage("""{"type":"tournament_list"}"""))
        assertEquals(ClientMessage.JoinTournament("AB12CD"), decodeClientMessage("""{"type":"tournament_join","id":"AB12CD"}"""))

        val info = TournamentInfo(
            id = "hourly_bullet-1", name = "Hourly Bullet", clock = Clock(60, 0), minutes = 30,
            status = TournamentStatus.STARTED, msLeft = 600_000, players = 2, rated = true, schedule = "hourly_bullet",
        )
        val me = Standing(1, PlayerInfo("a", "A", 1500), score = 6, games = 3, fire = true, paused = false, sheet = listOf(2, 0, 4))
        val message: ServerMessage = ServerMessage.Tournament(info, listOf(me), me)
        assertEquals(message, decodeServerMessage(message.encode()))
    }
}
