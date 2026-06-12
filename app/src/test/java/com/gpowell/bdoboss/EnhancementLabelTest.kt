package com.gpowell.bdoboss

import com.gpowell.bdoboss.ui.market.enhancementLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class EnhancementLabelTest {

    @Test
    fun `sid 0 is Base regardless of max`() {
        assertEquals("Base", enhancementLabel(0, 0))
        assertEquals("Base", enhancementLabel(0, 5))
        assertEquals("Base", enhancementLabel(0, 20))
    }

    @Test
    fun `accessory-style items use PRI to PEN for sids 1-5`() {
        assertEquals("PRI", enhancementLabel(1, 5))
        assertEquals("DUO", enhancementLabel(2, 5))
        assertEquals("TRI", enhancementLabel(3, 5))
        assertEquals("TET", enhancementLabel(4, 5))
        assertEquals("PEN", enhancementLabel(5, 5))
    }

    @Test
    fun `gear sids 16-20 use PRI to PEN even when max is 20`() {
        assertEquals("PRI", enhancementLabel(16, 20))
        assertEquals("DUO", enhancementLabel(17, 20))
        assertEquals("TRI", enhancementLabel(18, 20))
        assertEquals("TET", enhancementLabel(19, 20))
        assertEquals("PEN", enhancementLabel(20, 20))
    }

    @Test
    fun `gear plus levels stay numeric when max exceeds 5`() {
        assertEquals("+2", enhancementLabel(2, 20))
        assertEquals("+7", enhancementLabel(7, 20))
        assertEquals("+15", enhancementLabel(15, 20))
    }
}
