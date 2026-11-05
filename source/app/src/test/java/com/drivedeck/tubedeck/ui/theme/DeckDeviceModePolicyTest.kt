package com.drivedeck.tubedeck.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class DeckDeviceModePolicyTest {
    @Test fun manualSelectionIsNotOverriddenByDeviceHints() {
        assertEquals("Cinema Deck", DeckDeviceModePolicy.theme(
            "Cinema Deck", false, false, true, true, 1024))
    }

    @Test fun carIsPrioritizedEvenWhenTheInputSystemLooksLikeTv() {
        assertEquals("CommandDrive Auto", DeckDeviceModePolicy.theme(
            "NeoGlass Luxe", true, false, true, true, 1024))
    }

    @Test fun tvTabletAndPhoneGetDistinctExperiences() {
        assertEquals("StreamPlus Luxe TV", DeckDeviceModePolicy.theme(
            "NeoGlass Luxe", true, false, false, true, 500))
        assertEquals("NeoGlass Luxe", DeckDeviceModePolicy.theme(
            "Cinema Deck", true, false, false, false, 850))
        assertEquals("Midnight Editorial Pro", DeckDeviceModePolicy.theme(
            "Cinema Deck", true, false, false, false, 380))
    }

    @Test fun explicitCarModeOnlyOverridesWhenHeadUnitIdentified() {
        assertEquals("CommandDrive Auto", DeckDeviceModePolicy.theme(
            "Cinema Deck", false, true, true, false, 900))
        assertEquals("Cinema Deck", DeckDeviceModePolicy.theme(
            "Cinema Deck", false, true, false, false, 900))
    }
}
