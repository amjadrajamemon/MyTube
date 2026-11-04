package com.drivedeck.tubedeck.player

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarMediaTransportKeyPolicyTest {
    @Test fun alternateCarStereoNextPreviousCodesAreRecognized() {
        for (code in listOf(
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_PAGE_UP
        )) assertEquals(CarTransportAction.NEXT, CarMediaTransportKeyPolicy.classify(code))
        for (code in listOf(
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN
        )) assertEquals(CarTransportAction.PREVIOUS, CarMediaTransportKeyPolicy.classify(code))
    }

    @Test fun mediaFastForwardIsNotMistakenForTheNextSong() {
        assertNull(CarMediaTransportKeyPolicy.classify(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
        assertNull(CarMediaTransportKeyPolicy.classify(KeyEvent.KEYCODE_MEDIA_REWIND))
        assertEquals(CarTransportAction.NEXT,
            CarMediaTransportKeyPolicy.classify(
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                customNext = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            ))
    }

    @Test fun learnedSteeringWheelKeysWorkOutsideFocusedComposeControls() {
        assertEquals(CarTransportAction.NEXT,
            CarMediaTransportKeyPolicy.classify(
                KeyEvent.KEYCODE_PROG_RED, customNext = KeyEvent.KEYCODE_PROG_RED
            ))
        assertEquals(CarTransportAction.PREVIOUS,
            CarMediaTransportKeyPolicy.classify(
                KeyEvent.KEYCODE_PROG_GREEN, customPrevious = KeyEvent.KEYCODE_PROG_GREEN
            ))
    }

    @Test fun androidBackNavigationAndVolumeAreNeverIntercepted() {
        for (key in listOf(
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_CENTER
        )) {
            assertNull(CarMediaTransportKeyPolicy.classify(key, customNext = key, customPrevious = key))
        }
    }

    @Test fun normalDownUpAndOrphanUpDeliverExactlyOneAction() {
        assertTrue(CarMediaTransportKeyPolicy.shouldTrigger(KeyEvent.ACTION_DOWN, 0, false))
        assertFalse(CarMediaTransportKeyPolicy.shouldTrigger(KeyEvent.ACTION_DOWN, 1, true))
        assertFalse(CarMediaTransportKeyPolicy.shouldTrigger(KeyEvent.ACTION_UP, 0, true))
        assertTrue(CarMediaTransportKeyPolicy.shouldTrigger(KeyEvent.ACTION_UP, 0, false))
        assertFalse(CarMediaTransportKeyPolicy.shouldTrigger(KeyEvent.ACTION_MULTIPLE, 0, false))
        assertTrue(CarMediaTransportKeyPolicy.DUPLICATE_WINDOW_MS in 1L..250L)
    }
}
