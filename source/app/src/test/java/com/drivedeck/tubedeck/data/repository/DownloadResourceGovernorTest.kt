package com.drivedeck.tubedeck.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadResourceGovernorTest {
    @Test
    fun highEndUnmeteredDeviceUsesSafeParallelism() {
        assertEquals(
            4,
            DownloadResourceGovernor.computeTarget(
                memoryClassMb = 4_096,
                cpuCores = 8,
                lowMemory = false,
                powerSave = false,
                severeThermal = false,
                meteredNetwork = false
            )
        )
    }

    @Test
    fun meteredNetworkReducesNewHeavyTransfersWithoutSerializingQueue() {
        assertEquals(
            2,
            DownloadResourceGovernor.computeTarget(
                memoryClassMb = 2_048,
                cpuCores = 8,
                lowMemory = false,
                powerSave = false,
                severeThermal = false,
                meteredNetwork = true
            )
        )
    }

    @Test
    fun powerSaverTakesPriorityOverFastHardware() {
        assertEquals(
            1,
            DownloadResourceGovernor.computeTarget(
                memoryClassMb = 4_096,
                cpuCores = 12,
                lowMemory = false,
                powerSave = true,
                severeThermal = false,
                meteredNetwork = false
            )
        )
    }

    @Test
    fun thermalOrLowMemoryPressureSerializesHeavyWork() {
        assertEquals(
            1,
            DownloadResourceGovernor.computeTarget(
                memoryClassMb = 512,
                cpuCores = 4,
                lowMemory = false,
                powerSave = false,
                severeThermal = true,
                meteredNetwork = false
            )
        )
        assertEquals(
            1,
            DownloadResourceGovernor.computeTarget(
                memoryClassMb = 512,
                cpuCores = 4,
                lowMemory = true,
                powerSave = false,
                severeThermal = false,
                meteredNetwork = false
            )
        )
    }
}
