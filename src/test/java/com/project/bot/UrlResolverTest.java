package com.project.bot;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UrlResolverTest {

    private final UrlResolver resolver = new UrlResolver();

    @Test
    void isValidUrlReturnsTrueForHttpAndHttpsLinks() {
        assertTrue(resolver.isValidUrl("https://youtube.com/watch?v=abc"));
        assertTrue(resolver.isValidUrl("http://vk.com/video-1_2"));
    }

    @Test
    void isValidUrl_shouldReturnFalseForNullAndEmpty() {
        assertFalse(resolver.isValidUrl(null));
        assertFalse(resolver.isValidUrl(""));
        assertFalse(resolver.isValidUrl("   "));
    }

    @Test
    void isValidUrlReturnsFalseForInvalidOrUnsupportedLinks() {
        assertFalse(resolver.isValidUrl("not-a-url"));
        assertFalse(resolver.isValidUrl("ftp://youtube.com/watch?v=abc"));
    }

    @Test
    void resolvePlatformDetectsYoutubeHosts() {
        assertEquals(UrlResolver.Platform.YOUTUBE, resolver.resolvePlatform("https://youtube.com/watch?v=abc"));
        assertEquals(UrlResolver.Platform.YOUTUBE, resolver.resolvePlatform("https://m.youtube.com/watch?v=abc"));
        assertEquals(UrlResolver.Platform.YOUTUBE, resolver.resolvePlatform("https://youtu.be/abc"));
        assertEquals(UrlResolver.Platform.YOUTUBE, resolver.resolvePlatform("https://www.youtube.com/shorts/abc123"));
        assertEquals(UrlResolver.Platform.YOUTUBE, resolver.resolvePlatform("https://youtube.com/live/abc123"));
    }

    @Test
    void resolvePlatformDetectsVkHosts() {
        assertEquals(UrlResolver.Platform.VK, resolver.resolvePlatform("https://vk.com/video-1_2"));
        assertEquals(UrlResolver.Platform.VK, resolver.resolvePlatform("https://m.vk.com/video-1_2"));
        assertEquals(UrlResolver.Platform.VK, resolver.resolvePlatform("https://vkvideo.ru/video-1_2"));
        assertEquals(UrlResolver.Platform.VK, resolver.resolvePlatform("https://vk.com/clip123456_789"));
    }

    @Test
    void resolvePlatformReturnsUnknownForOtherOrInvalidHosts() {
        assertEquals(UrlResolver.Platform.UNKNOWN, resolver.resolvePlatform("https://example.com/video"));
        assertEquals(UrlResolver.Platform.UNKNOWN, resolver.resolvePlatform("https://notyoutube.com/watch?v=abc"));
        assertEquals(UrlResolver.Platform.UNKNOWN, resolver.resolvePlatform("bad-url"));
        assertEquals(UrlResolver.Platform.UNKNOWN, resolver.resolvePlatform("ftp://vk.com/video-1_2"));
    }

    @Test
    void resolvePlatform_shouldHandleEdgeCases() {
        assertEquals(UrlResolver.Platform.UNKNOWN, resolver.resolvePlatform(""));
        assertEquals(UrlResolver.Platform.UNKNOWN, resolver.resolvePlatform(null));
        assertEquals(UrlResolver.Platform.UNKNOWN, resolver.resolvePlatform("https://"));
    }

    @Test
    void pointsToExistingVideo_shouldReturnFalseForInvalidUrl() {
        assertFalse(resolver.pointsToExistingVideo("https://youtube.com/watch?v=invalid_id_12345"));
        // VK может возвращать успех для несуществующего видео — пропускаем
        assertFalse(resolver.pointsToExistingVideo("not-a-url"));
        assertFalse(resolver.pointsToExistingVideo(""));
        assertFalse(resolver.pointsToExistingVideo(null));
    }

    @Test
    void resolverHandlesVariousYoutubeUrlFormats() {
        assertEquals(UrlResolver.Platform.YOUTUBE, resolver.resolvePlatform("https://youtube.com/watch?v=dQw4w9WgXcQ"));
        assertEquals(UrlResolver.Platform.YOUTUBE, resolver.resolvePlatform("https://youtu.be/dQw4w9WgXcQ"));
        assertEquals(UrlResolver.Platform.YOUTUBE, resolver.resolvePlatform("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=120"));
        assertEquals(UrlResolver.Platform.YOUTUBE, resolver.resolvePlatform("https://m.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertEquals(UrlResolver.Platform.YOUTUBE, resolver.resolvePlatform("https://youtube.com/shorts/dQw4w9WgXcQ"));
        assertEquals(UrlResolver.Platform.YOUTUBE, resolver.resolvePlatform("https://youtube.com/live/dQw4w9WgXcQ"));
    }

    @Test
    void resolverHandlesVariousVkUrlFormats() {
        assertEquals(UrlResolver.Platform.VK, resolver.resolvePlatform("https://vk.com/video-123456_789"));
        assertEquals(UrlResolver.Platform.VK, resolver.resolvePlatform("https://m.vk.com/video-123456_789?list=abc"));
        assertEquals(UrlResolver.Platform.VK, resolver.resolvePlatform("https://vkvideo.ru/video-123456_789"));
        assertEquals(UrlResolver.Platform.VK, resolver.resolvePlatform("https://vk.com/wall-123456_789?z=video-123456_789"));
    }
}