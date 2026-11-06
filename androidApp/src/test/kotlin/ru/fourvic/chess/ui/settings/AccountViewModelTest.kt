package ru.fourvic.chess.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import ru.fourvic.chess.online.ConnectionState
import ru.fourvic.chess.online.FakeOnlineClient
import ru.fourvic.chess.shared.protocol.ClientMessage
import ru.fourvic.chess.shared.protocol.ErrorCode
import ru.fourvic.chess.shared.protocol.PlayerInfo
import ru.fourvic.chess.shared.protocol.ServerMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val guest = PlayerInfo("me", "Игрок12345", 1500, provisional = true)
    private val client = FakeOnlineClient(guest)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun welcome(name: String = guest.name, registered: Boolean) =
        ServerMessage.Welcome(guest.copy(name = name), token = "", ratings = emptyList(), registered = registered)

    /** What the real client does with a welcome: it becomes the current one and is passed on. */
    private fun AccountViewModel.receive(message: ServerMessage) {
        if (message is ServerMessage.Welcome) client.welcome.value = message
        onMessage(message)
    }

    @Test
    fun guestSavesTheAccount() {
        val vm = AccountViewModel(client)
        assertFalse(vm.state.registered)
        assertEquals(guest, vm.state.me)

        vm.save("  Магнус ", "correct horse")
        assertEquals(ClientMessage.Register("Магнус", "correct horse"), client.sent.last())
        assertEquals(AccountRequest.SAVE, vm.state.pending)

        vm.receive(welcome("Магнус", registered = true))
        assertNull(vm.state.pending)
        assertEquals(AccountRequest.SAVE, vm.state.done)
        assertTrue(vm.state.registered)
        assertEquals("Магнус", vm.state.me?.name)
    }

    @Test
    fun takenNameIsReported() {
        val vm = AccountViewModel(client)
        vm.save("Магнус", "correct horse")
        vm.receive(ServerMessage.Error(ErrorCode.NAME_TAKEN, "This name is taken"))
        assertNull(vm.state.pending)
        assertEquals(AccountError.NAME_TAKEN, vm.state.error)
        assertNull(vm.state.done)

        vm.dismiss()
        assertNull(vm.state.error)
    }

    @Test
    fun errorsOfGamesDoNotEndAnAccountRequest() {
        val vm = AccountViewModel(client)
        vm.changePassword("old password", "new password")
        vm.receive(ServerMessage.Error(ErrorCode.NO_SUCH_GAME, "No such game"))
        assertEquals(AccountRequest.CHANGE_PASSWORD, vm.state.pending)
        assertNull(vm.state.error)

        vm.receive(ServerMessage.PasswordChanged)
        assertEquals(AccountRequest.CHANGE_PASSWORD, vm.state.done)
    }

    @Test
    fun errorWithoutARequestIsIgnored() {
        val vm = AccountViewModel(client)
        vm.receive(ServerMessage.Error(ErrorCode.WRONG_PASSWORD, "Wrong name or password"))
        assertNull(vm.state.error)
    }

    @Test
    fun signInWaitsForTheSavedAccountAfterReconnecting() {
        val vm = AccountViewModel(client)
        vm.signIn("Магнус", "correct horse")
        assertEquals(ClientMessage.Login("Магнус", "correct horse"), client.sent.last())

        // The client stores the new token and connects again; the dialog keeps waiting meanwhile.
        vm.receive(ServerMessage.LoggedIn("new-token"))
        client.state.value = ConnectionState.CONNECTING
        client.state.value = ConnectionState.OFFLINE
        assertEquals(AccountRequest.SIGN_IN, vm.state.pending)
        assertNull(vm.state.error)

        client.state.value = ConnectionState.CONNECTED
        vm.receive(welcome("Магнус", registered = true))
        assertEquals(AccountRequest.SIGN_IN, vm.state.done)
        assertTrue(vm.state.registered)
    }

    @Test
    fun wrongPasswordAndLockout() {
        val vm = AccountViewModel(client)
        vm.signIn("Магнус", "wrong one")
        vm.receive(ServerMessage.Error(ErrorCode.WRONG_PASSWORD, "Wrong name or password"))
        assertEquals(AccountError.WRONG_PASSWORD, vm.state.error)

        vm.signIn("Магнус", "wrong two")
        assertNull(vm.state.error) // a new attempt clears the old error
        vm.receive(ServerMessage.Error(ErrorCode.TOO_MANY_ATTEMPTS, "Too many attempts"))
        assertEquals(AccountError.TOO_MANY_ATTEMPTS, vm.state.error)
    }

    @Test
    fun signInThatReconnectsAsTheGuestFails() {
        val vm = AccountViewModel(client)
        vm.signIn("Магнус", "correct horse")
        vm.receive(welcome(registered = false))
        assertNull(vm.state.pending)
        assertEquals(AccountError.OFFLINE, vm.state.error)
    }

    @Test
    fun offlineRequestFailsAtOnce() {
        client.state.value = ConnectionState.OFFLINE
        val vm = AccountViewModel(client)
        vm.save("Магнус", "correct horse")
        assertTrue(client.sent.isEmpty())
        assertNull(vm.state.pending)
        assertEquals(AccountError.OFFLINE, vm.state.error)
    }

    @Test
    fun droppedConnectionEndsAWaitingRequest() {
        val vm = AccountViewModel(client)
        vm.rename("Магнус")
        client.state.value = ConnectionState.OFFLINE
        assertNull(vm.state.pending)
        assertEquals(AccountError.OFFLINE, vm.state.error)
    }

    @Test
    fun accountTakenByAnotherDeviceEndsAWaitingRequest() {
        val vm = AccountViewModel(client)
        vm.changePassword("old password", "new password")
        client.state.value = ConnectionState.REPLACED
        assertEquals(ConnectionState.REPLACED, vm.state.connection)
        assertEquals(AccountError.OFFLINE, vm.state.error)
    }

    @Test
    fun signOutAndDelete() {
        val vm = AccountViewModel(client)
        vm.signOut()
        assertEquals(1, client.signOuts)

        vm.delete()
        assertEquals(ClientMessage.DeleteAccount, client.sent.last())
        vm.receive(ServerMessage.AccountDeleted)
        assertTrue(vm.state.deleted)
    }
}
