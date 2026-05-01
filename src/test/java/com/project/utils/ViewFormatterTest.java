package com.project.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ViewFormatterTest {

    @Test
    void formatViews_shouldFormatThousands() {
        assertEquals("1 234", ViewFormatter.formatViews(1234));
        assertEquals("1 234 567", ViewFormatter.formatViews(1234567));
        assertEquals("0", ViewFormatter.formatViews(0));
        assertEquals("1 000 000", ViewFormatter.formatViews(1000000));
    }

    @Test
    void escapeHtml_shouldReplaceSpecialChars() {
        assertEquals("&lt;tag&gt;", ViewFormatter.escapeHtml("<tag>"));
        assertEquals("&amp;amp;", ViewFormatter.escapeHtml("&amp;"));
        assertEquals("&quot;quoted&quot;", ViewFormatter.escapeHtml("\"quoted\""));
        assertEquals("&#39;single&#39;", ViewFormatter.escapeHtml("'single'"));
    }

    @Test
    void escapeHtml_shouldReturnEmptyForNull() {
        assertEquals("", ViewFormatter.escapeHtml(null));
    }

    @Test
    void escapeHtml_shouldKeepNormalText() {
        String normal = "Hello World! 123";
        assertEquals(normal, ViewFormatter.escapeHtml(normal));
    }
}