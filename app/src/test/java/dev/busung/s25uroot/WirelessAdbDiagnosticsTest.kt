package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Test

class WirelessAdbDiagnosticsTest {

    @Test
    fun `a refused certificate is reported as unpaired, not as a connection problem`() {
        // The device saying it does not know this app needs the opposite response from a network
        // fault: one is "pair again", the other is "turn wireless debugging on".
        assertEquals(
            WirelessAdbAuthState.PairingRejected,
            wirelessAdbFailureState(pairingRejected = true, portUnavailable = true),
        )
    }

    @Test
    fun `a missing port is its own state`() {
        assertEquals(
            WirelessAdbAuthState.PortUnavailable,
            wirelessAdbFailureState(pairingRejected = false, portUnavailable = true),
        )
    }

    @Test
    fun `anything else is a connection failure`() {
        assertEquals(
            WirelessAdbAuthState.ConnectionFailed,
            wirelessAdbFailureState(pairingRejected = false, portUnavailable = false),
        )
    }

    @Test
    fun `every state has a distinct meaning for the card`() {
        // The card's whole job is to say which of these it is, so a state that nothing can produce -
        // or one that means the same as another - would make it say the wrong thing.
        val states = WirelessAdbAuthState.entries
        assertEquals(states.size, states.toSet().size)
        assertEquals(7, states.size)
    }
}
