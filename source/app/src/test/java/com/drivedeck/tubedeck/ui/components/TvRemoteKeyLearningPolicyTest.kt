package com.drivedeck.tubedeck.ui.components

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvRemoteKeyLearningPolicyTest {
    @Test fun doesNotInterceptSystemNavigationOrVolume() {
        for (key in listOf(
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_POWER
        )) assertFalse(TvRemoteKeyLearningPolicy.allowed(key))
    }
    @Test fun userChosenColorAndFunctionKeysCanBeRemapped() {
        assertTrue(TvRemoteKeyLearningPolicy.allowed(KeyEvent.KEYCODE_PROG_RED))
        assertEquals(TvPlayerKeyAction.NEXT,
            TvRemoteKeyLearningPolicy.remap(KeyEvent.KEYCODE_PROG_RED, 0,
                KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_PROG_GREEN)?.action)
        assertEquals(TvPlayerKeyAction.PREVIOUS,
            TvRemoteKeyLearningPolicy.remap(KeyEvent.KEYCODE_PROG_GREEN, 0,
                KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_PROG_GREEN)?.action)
        assertNull(TvRemoteKeyLearningPolicy.remap(
            KeyEvent.KEYCODE_PROG_RED, 2, KeyEvent.KEYCODE_PROG_RED, 0))
    }
}
