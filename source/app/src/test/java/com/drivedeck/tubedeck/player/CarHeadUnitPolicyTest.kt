package com.drivedeck.tubedeck.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarHeadUnitPolicyTest {
    @Test fun legacyCarUnitTagsAreDetected() {
        assertTrue(CarHeadUnitPolicy.isLegacyFirmwareTag("XY AUTO 6.2 X2"))
        assertTrue(CarHeadUnitPolicy.isLegacyFirmwareTag("YT9216BFJ_00012"))
        assertTrue(CarHeadUnitPolicy.isLegacyFirmwareTag("yt5760b_8259"))
        assertTrue(CarHeadUnitPolicy.isLegacyFirmwareTag("ac8227l"))
    }

    @Test fun standardPhonesAndTabletsAreNotForcedIntoSafeMode() {
        assertFalse(CarHeadUnitPolicy.isLegacyFirmwareTag("Google Pixel 8"))
        assertFalse(CarHeadUnitPolicy.isLegacyFirmwareTag("Samsung SM-T510"))
        assertFalse(CarHeadUnitPolicy.isLegacyFirmwareTag(""))
    }
}
