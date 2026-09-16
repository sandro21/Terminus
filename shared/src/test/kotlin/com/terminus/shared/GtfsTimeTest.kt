package com.terminus.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class GtfsTimeTest {
    @Test
    fun keepsPastMidnightHours() {
        assertEquals(90_840, parseGtfsTime("25:14:00"))
    }
}
