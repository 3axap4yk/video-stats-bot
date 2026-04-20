package com.project.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlResolverTest {

    private final UrlResolver resolver = new UrlResolver();

    @Test
    void isValidUrlReturnsTrueForHttpAndHttpsLinks() {
        assertTrue(resolver.isValidUrl("https://youtube.com/watch?v=abc"));
        assertTrue(resolver.isValidUrl("http://vk.com/video-1_2"));
    }

    @Test
    void isValidUrlReturnsFalseForInvalidOrUnsupportedLinks() {
        assertFalse(resolver.isValidUrl(null));
        assertFalse(resolver.isValidUrl(" "));
        assertFalse(resolver.isValidUrl("not-a-url"));
        assertFalse(resolver.isValidUrl("ftp://youtube.com/watch?v=abc"));
    }

    @Test
    void resolvePlatformDetectsYoutubeHosts() {
        assertEquals(UrlResolver.Platform.YOUTUBE, resolver.resolvePlatform("https://youtube.com/watch?v=abc"));
        assertEquals(UrlResolver.Platform.YOUTUBE, resolver.resolvePlatform("https://m.youtube.com/watch?v=abc"));
        assertEquals(UrlResolver.Platform.YOUTUBE, resolver.resolvePlatform("https://youtu.be/abc"));
    }

    @Test
    void resolvePlatformDetectsVkHosts() {
        assertEquals(UrlResolver.Platform.VK, resolver.resolvePlatform("https://vk.com/video-1_2"));
        assertEquals(UrlResolver.Platform.VK, resolver.resolvePlatform("https://m.vk.com/video-1_2"));
        assertEquals(UrlResolver.Platform.VK, resolver.resolvePlatform("https://vkvideo.ru/video-1_2"));
    }

    @Test
    void resolvePlatformReturnsUnknownForOtherOrInvalidHosts() {
        assertEquals(UrlResolver.Platform.UNKNOWN, resolver.resolvePlatform("https://example.com/video"));
        assertEquals(UrlResolver.Platform.UNKNOWN, resolver.resolvePlatform("https://notyoutube.com/watch?v=abc"));
        assertEquals(UrlResolver.Platform.UNKNOWN, resolver.resolvePlatform("bad-url"));
        assertEquals(UrlResolver.Platform.UNKNOWN, resolver.resolvePlatform("ftp://vk.com/video-1_2"));
    }
}
