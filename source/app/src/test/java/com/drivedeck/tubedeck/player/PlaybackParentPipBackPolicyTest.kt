package com.drivedeck.tubedeck.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackParentPipBackPolicyTest {
    @Test fun isolatedSlowBackKeepsPipAndNeverMaximizesPlayer() {
        assertEquals(ParentPipBackAction.KEEP_PIP_AND_EXIT, PlaybackParentPipBackPolicy.onBack(1, false))
    }

    @Test fun deliberateQuickSecondBackShowsThreeChoices() {
        assertEquals(ParentPipBackAction.SHOW_CHOICES, PlaybackParentPipBackPolicy.onBack(2, false))
    }

    @Test fun backWhileChoicesVisibleKeepsPipAndLeavesTheApp() {
        assertEquals(ParentPipBackAction.KEEP_PIP_AND_EXIT, PlaybackParentPipBackPolicy.onBack(1, true))
    }

    @Test fun tripleRapidBackPausesWhetherChoicesHaveRenderedOrNot() {
        assertEquals(ParentPipBackAction.PAUSE_AND_EXIT, PlaybackParentPipBackPolicy.onBack(3, true))
        assertEquals(ParentPipBackAction.PAUSE_AND_EXIT, PlaybackParentPipBackPolicy.onBack(3, false))
    }
}
