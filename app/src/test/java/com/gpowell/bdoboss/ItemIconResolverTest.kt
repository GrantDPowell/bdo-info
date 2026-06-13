package com.gpowell.bdoboss

import com.gpowell.bdoboss.data.market.buildIconUrl
import com.gpowell.bdoboss.data.market.parseAcIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemIconResolverTest {

    @Test fun `buildIconUrl joins base, path and rel icon`() {
        assertEquals(
            "https://bdocodex.com/items/new_icon/03_etc/04_dropitem/00044195.webp",
            buildIconUrl("items", "new_icon/03_etc/04_dropitem/00044195.webp"),
        )
    }

    // Real ac.php shape: BOM-prefixed JSON array with escaped slashes.
    private val sample = "﻿[" +
        """{"value":44195,"name":"Memory Fragment","grade":0,"link_type":"item",""" +
        """"icon":"new_icon\/03_etc\/04_dropitem\/00044195.webp","icon_path":"items"},""" +
        """{"value":16001,"name":"Black Stone","link_type":"item",""" +
        """"icon":"new_icon\/03_etc\/11_enchant_material\/00000008.webp","icon_path":"items"},""" +
        """{"value":999,"name":"Some Knowledge","link_type":"knowledge","icon":"x.webp","icon_path":"k"},""" +
        """{"value":555,"name":"No Icon Item","link_type":"item","icon":"","icon_path":"items"}""" +
        "]"

    @Test fun `parseAcIcons maps every item id with an icon, strips BOM`() {
        val map = parseAcIcons(sample)
        assertEquals(
            "https://bdocodex.com/items/new_icon/03_etc/04_dropitem/00044195.webp",
            map[44195],
        )
        // Shared icon: Black Stone's icon filename is NOT its id — proves we need this lookup.
        assertEquals(
            "https://bdocodex.com/items/new_icon/03_etc/11_enchant_material/00000008.webp",
            map[16001],
        )
        assertTrue("non-item link types are skipped", !map.containsKey(999))
        assertFalse("blank-icon entries are skipped", map.containsKey(555))
        assertEquals(2, map.size)
    }

    @Test fun `parseAcIcons on garbage returns empty`() {
        assertEquals(emptyMap<Int, String>(), parseAcIcons("not json"))
        assertEquals(emptyMap<Int, String>(), parseAcIcons(""))
    }
}
