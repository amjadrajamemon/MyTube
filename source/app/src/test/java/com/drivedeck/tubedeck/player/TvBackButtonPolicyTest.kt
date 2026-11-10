package com.drivedeck.tubedeck.player

import org.junit.Assert.assertEquals
import org.junit.Test

class TvBackButtonPolicyTest {
    @Test fun defaultIsPredictableActionMenu() {
        assertEquals(TvBackButtonPolicy.SHOW_ACTION_MENU, TvBackButtonPolicy.normalize(null))
        assertEquals(TvBackButtonPolicy.SHOW_ACTION_MENU, TvBackButtonPolicy.normalize("invalid"))
        assertEquals(TvBackButtonAction.SHOW_ACTION_MENU, TvBackButtonPolicy.decide(null, true))
    }

    @Test fun miniPipRequiresEnabledCapabilitySetting() {
        assertEquals(TvBackButtonAction.MINI_PIP,
            TvBackButtonPolicy.decide(TvBackButtonPolicy.MINI_PIP, true))
        assertEquals(TvBackButtonAction.SHOW_ACTION_MENU,
            TvBackButtonPolicy.decide(TvBackButtonPolicy.MINI_PIP, false))
    }

    @Test fun closeChoiceIsUnconditional() {
        assertEquals(TvBackButtonAction.CLOSE_IMMEDIATELY,
            TvBackButtonPolicy.decide(TvBackButtonPolicy.CLOSE_IMMEDIATELY, false))
        assertEquals(TvBackButtonAction.CLOSE_IMMEDIATELY,
            TvBackButtonPolicy.decide(TvBackButtonPolicy.CLOSE_IMMEDIATELY, true))
    }
}
