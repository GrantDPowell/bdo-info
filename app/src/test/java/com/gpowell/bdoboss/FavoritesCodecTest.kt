package com.gpowell.bdoboss

import com.gpowell.bdoboss.data.Favorite
import com.gpowell.bdoboss.data.FavoriteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesCodecTest {

    // -------------------------------------------------------------------------
    // Codec: round-trip with all 3 types populated
    // -------------------------------------------------------------------------

    private val sampleFavorites = listOf(
        Favorite(
            id = 1L,
            type = FavoriteType.PAGE,
            title = "Kzarka Guide",
            subtitle = "World Boss",
            addedAt = 1_700_000_000L,
            url = "https://bdocodex.com/kzarka",
        ),
        Favorite(
            id = 2L,
            type = FavoriteType.ITEM,
            title = "Kzarka's Latent Aura",
            subtitle = "Rare drop",
            addedAt = 1_700_000_100L,
            url = "https://bdocodex.com/item/12345",
            itemId = 12345,
        ),
        Favorite(
            id = 3L,
            type = FavoriteType.PLAYER,
            title = "OkimaSha",
            subtitle = "NA player",
            addedAt = 1_700_000_200L,
            region = "NA",
            familyName = "OkimaSha",
        ),
    )

    @Test fun `round-trips all three favorite types`() {
        val encoded = Favorite.encode(sampleFavorites)
        val decoded = Favorite.decode(encoded)
        assertEquals(sampleFavorites, decoded)
    }

    @Test fun `decode garbage returns empty list`() {
        assertEquals(emptyList<Favorite>(), Favorite.decode("not json at all"))
    }

    @Test fun `decode empty string returns empty list`() {
        assertEquals(emptyList<Favorite>(), Favorite.decode(""))
    }

    @Test fun `round-trip preserves all fields including defaults`() {
        val minimal = listOf(
            Favorite(id = 99L, type = FavoriteType.PAGE, title = "Home"),
        )
        val decoded = Favorite.decode(Favorite.encode(minimal))
        assertEquals(1, decoded.size)
        val fav = decoded[0]
        assertEquals(99L, fav.id)
        assertEquals(FavoriteType.PAGE, fav.type)
        assertEquals("Home", fav.title)
        assertEquals("", fav.subtitle)
        assertEquals(0L, fav.addedAt)
        assertEquals("", fav.url)
        assertEquals(0, fav.itemId)
        assertEquals("", fav.region)
        assertEquals("", fav.familyName)
    }

    // -------------------------------------------------------------------------
    // Natural-key matcher: PAGE matches on url
    // -------------------------------------------------------------------------

    @Test fun `PAGE - same url matches`() {
        val existing = Favorite(
            id = 1L, type = FavoriteType.PAGE, title = "Guide",
            url = "https://example.com/guide",
        )
        val match = Favorite.findMatch(
            list = listOf(existing),
            type = FavoriteType.PAGE,
            url = "https://example.com/guide",
        )
        assertNotNull(match)
        assertEquals(1L, match!!.id)
    }

    @Test fun `PAGE - different url does not match`() {
        val existing = Favorite(
            id = 1L, type = FavoriteType.PAGE, title = "Guide",
            url = "https://example.com/guide",
        )
        val match = Favorite.findMatch(
            list = listOf(existing),
            type = FavoriteType.PAGE,
            url = "https://example.com/other",
        )
        assertNull(match)
    }

    @Test fun `PAGE - empty url does not match another empty url`() {
        // Two PAGE favorites with no url should NOT collapse into each other
        // (they'd be distinguished by title in practice; empty url = not a valid PAGE key)
        val existing = Favorite(id = 1L, type = FavoriteType.PAGE, title = "A", url = "")
        val match = Favorite.findMatch(
            list = listOf(existing),
            type = FavoriteType.PAGE,
            url = "",
        )
        // Per spec: PAGE natural key is url; empty url is not a meaningful key, returns null
        assertNull(match)
    }

    // -------------------------------------------------------------------------
    // Natural-key matcher: ITEM matches on itemId, falls back to url
    // -------------------------------------------------------------------------

    @Test fun `ITEM - matches on itemId when nonzero`() {
        val existing = Favorite(
            id = 2L, type = FavoriteType.ITEM, title = "Aura",
            itemId = 12345,
            url = "https://bdocodex.com/item/12345",
        )
        val match = Favorite.findMatch(
            list = listOf(existing),
            type = FavoriteType.ITEM,
            itemId = 12345,
        )
        assertNotNull(match)
        assertEquals(2L, match!!.id)
    }

    @Test fun `ITEM - different itemId does not match`() {
        val existing = Favorite(id = 2L, type = FavoriteType.ITEM, title = "Aura", itemId = 12345)
        val match = Favorite.findMatch(
            list = listOf(existing),
            type = FavoriteType.ITEM,
            itemId = 99999,
        )
        assertNull(match)
    }

    @Test fun `ITEM - falls back to url when itemId is 0`() {
        val existing = Favorite(
            id = 3L, type = FavoriteType.ITEM, title = "Unknown Item",
            itemId = 0,
            url = "https://bdocodex.com/item/special",
        )
        val match = Favorite.findMatch(
            list = listOf(existing),
            type = FavoriteType.ITEM,
            itemId = 0,
            url = "https://bdocodex.com/item/special",
        )
        assertNotNull(match)
        assertEquals(3L, match!!.id)
    }

    @Test fun `ITEM - url fallback does not match different url`() {
        val existing = Favorite(
            id = 3L, type = FavoriteType.ITEM, title = "Unknown Item",
            itemId = 0,
            url = "https://bdocodex.com/item/special",
        )
        val match = Favorite.findMatch(
            list = listOf(existing),
            type = FavoriteType.ITEM,
            itemId = 0,
            url = "https://bdocodex.com/item/other",
        )
        assertNull(match)
    }

    // -------------------------------------------------------------------------
    // Natural-key matcher: PLAYER matches case-insensitively on region+familyName
    // -------------------------------------------------------------------------

    @Test fun `PLAYER - matches case-insensitively on region and familyName`() {
        val existing = Favorite(
            id = 4L, type = FavoriteType.PLAYER, title = "OkimaSha",
            region = "NA", familyName = "OkimaSha",
        )
        val match = Favorite.findMatch(
            list = listOf(existing),
            type = FavoriteType.PLAYER,
            region = "na",
            familyName = "okimasha",
        )
        assertNotNull(match)
        assertEquals(4L, match!!.id)
    }

    @Test fun `PLAYER - different familyName does not match`() {
        val existing = Favorite(
            id = 4L, type = FavoriteType.PLAYER, title = "OkimaSha",
            region = "NA", familyName = "OkimaSha",
        )
        val match = Favorite.findMatch(
            list = listOf(existing),
            type = FavoriteType.PLAYER,
            region = "NA",
            familyName = "SomeoneElse",
        )
        assertNull(match)
    }

    @Test fun `PLAYER - different region does not match`() {
        val existing = Favorite(
            id = 4L, type = FavoriteType.PLAYER, title = "OkimaSha",
            region = "NA", familyName = "OkimaSha",
        )
        val match = Favorite.findMatch(
            list = listOf(existing),
            type = FavoriteType.PLAYER,
            region = "EU",
            familyName = "OkimaSha",
        )
        assertNull(match)
    }

    // -------------------------------------------------------------------------
    // Cross-type: same natural-key args never match across types
    // -------------------------------------------------------------------------

    @Test fun `cross-type - PAGE never matches ITEM with same url`() {
        val existing = Favorite(
            id = 5L, type = FavoriteType.PAGE, title = "A Page",
            url = "https://bdocodex.com/item/12345",
        )
        val match = Favorite.findMatch(
            list = listOf(existing),
            type = FavoriteType.ITEM,
            url = "https://bdocodex.com/item/12345",
            itemId = 0,
        )
        assertNull(match)
    }

    @Test fun `cross-type - ITEM never matches PLAYER`() {
        val existing = Favorite(
            id = 6L, type = FavoriteType.ITEM, title = "An Item", itemId = 42,
        )
        val match = Favorite.findMatch(
            list = listOf(existing),
            type = FavoriteType.PLAYER,
            region = "NA",
            familyName = "SomePlayer",
        )
        assertNull(match)
    }

    @Test fun `cross-type - PLAYER never matches PAGE with same title`() {
        val existing = Favorite(
            id = 7L, type = FavoriteType.PLAYER, title = "OkimaSha",
            region = "NA", familyName = "OkimaSha",
        )
        val match = Favorite.findMatch(
            list = listOf(existing),
            type = FavoriteType.PAGE,
            url = "https://example.com",
        )
        assertNull(match)
    }

    // -------------------------------------------------------------------------
    // Edge: empty list always returns null
    // -------------------------------------------------------------------------

    @Test fun `findMatch on empty list returns null`() {
        assertNull(
            Favorite.findMatch(
                list = emptyList(),
                type = FavoriteType.PAGE,
                url = "https://example.com",
            ),
        )
    }

    // -------------------------------------------------------------------------
    // Multiple items in list — only matching one is returned
    // -------------------------------------------------------------------------

    @Test fun `findMatch returns only the matching item from a mixed list`() {
        val list = listOf(
            Favorite(id = 10L, type = FavoriteType.PAGE, title = "A", url = "https://a.com"),
            Favorite(id = 11L, type = FavoriteType.PAGE, title = "B", url = "https://b.com"),
            Favorite(id = 12L, type = FavoriteType.ITEM, title = "C", itemId = 99),
        )
        val match = Favorite.findMatch(list = list, type = FavoriteType.PAGE, url = "https://b.com")
        assertNotNull(match)
        assertEquals(11L, match!!.id)
    }
}
