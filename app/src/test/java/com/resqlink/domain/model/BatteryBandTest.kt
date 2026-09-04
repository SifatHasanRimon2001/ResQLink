package com.resqlink.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryBandTest {
    @Test fun `maps documented battery thresholds`() {
        assertEquals(BatteryBand.CRITICAL, batteryBand(9))
        assertEquals(BatteryBand.LOW, batteryBand(10))
        assertEquals(BatteryBand.MODERATE, batteryBand(20))
        assertEquals(BatteryBand.MODERATE, batteryBand(50))
        assertEquals(BatteryBand.NORMAL, batteryBand(51))
    }
}
