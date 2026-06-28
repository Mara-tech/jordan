package com.mara.jordan.app.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NetworkUtilsTest {

    @Test
    public void removeEndingSlash_withTrailingSlash_removesIt() {
        assertEquals("https://example.com/jordan", NetworkUtils.removeEndingSlash("https://example.com/jordan/"));
    }

    @Test
    public void removeEndingSlash_withoutTrailingSlash_unchanged() {
        assertEquals("https://example.com/jordan", NetworkUtils.removeEndingSlash("https://example.com/jordan"));
    }

    @Test
    public void removeEndingSlash_withDoubleTrailingSlash_removesOnlyLast() {
        assertEquals("https://example.com/jordan/", NetworkUtils.removeEndingSlash("https://example.com/jordan//"));
    }

    @Test
    public void removeEndingSlash_emptyString_unchanged() {
        assertEquals("", NetworkUtils.removeEndingSlash(""));
    }

    @Test
    public void removeEndingSlash_slashOnly_returnsEmpty() {
        assertEquals("", NetworkUtils.removeEndingSlash("/"));
    }
}
