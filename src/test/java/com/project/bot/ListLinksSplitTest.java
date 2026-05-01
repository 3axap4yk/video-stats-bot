package com.project.bot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ListLinksSplitTest {

    private static final int MAX_MESSAGE_LENGTH = 4096;

    // Копируем логику разбиения из ListLinks для тестирования
    private String[] splitMessage(String text) {
        if (text == null || text.length() <= MAX_MESSAGE_LENGTH) {
            return new String[]{text};
        }

        java.util.ArrayList<String> parts = new java.util.ArrayList<>();
        int start = 0;
        int totalLength = text.length();

        while (start < totalLength) {
            int end = Math.min(start + MAX_MESSAGE_LENGTH, totalLength);

            // Если конец не в конце строки, ищем последний перенос строки
            if (end < totalLength) {
                int lastNewLine = text.lastIndexOf('\n', end);
                if (lastNewLine > start) {
                    end = lastNewLine + 1;
                }
            }

            parts.add(text.substring(start, end));
            start = end;
        }

        return parts.toArray(new String[0]);
    }

    @Test
    @DisplayName("Короткое сообщение - не разбивается")
    void shortMessage_shouldNotSplit() {
        String shortMessage = "Короткое сообщение";
        String[] parts = splitMessage(shortMessage);

        assertEquals(1, parts.length);
        assertEquals(shortMessage, parts[0]);
    }

    @Test
    @DisplayName("Пустое сообщение")
    void emptyMessage() {
        String[] parts = splitMessage("");
        assertEquals(1, parts.length);
        assertEquals("", parts[0]);
    }

    @Test
    @DisplayName("Null сообщение")
    void nullMessage() {
        String[] parts = splitMessage(null);
        assertEquals(1, parts.length);
        assertNull(parts[0]);
    }

    @Test
    @DisplayName("Сообщение ровно 4096 символов")
    void exactLengthMessage() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4096; i++) {
            sb.append('a');
        }
        String exactMessage = sb.toString();

        String[] parts = splitMessage(exactMessage);

        assertEquals(1, parts.length);
        assertEquals(4096, parts[0].length());
    }

    @Test
    @DisplayName("Сообщение больше 4096 - разбивается")
    void longMessage_splits() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append('a');
        }
        String longMessage = sb.toString();

        String[] parts = splitMessage(longMessage);

        assertNotNull(parts);
        assertTrue(parts.length >= 2, "Должно быть минимум 2 части, получено: " + parts.length);

        for (String part : parts) {
            assertTrue(part.length() <= MAX_MESSAGE_LENGTH,
                    "Часть не должна превышать " + MAX_MESSAGE_LENGTH + ", длина: " + part.length());
        }
    }

    @Test
    @DisplayName("Сообщение с переносами строк")
    void messageWithLineBreaks() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("Строка номер ").append(i).append("\n");
        }
        String messageWithBreaks = sb.toString();

        String[] parts = splitMessage(messageWithBreaks);

        for (String part : parts) {
            assertTrue(part.length() <= MAX_MESSAGE_LENGTH);
        }
    }

    @Test
    @DisplayName("Общая длина сохраняется")
    void totalLengthPreserved() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("Тестовая строка ").append(i).append("\n");
        }
        String original = sb.toString();
        int originalLength = original.length();

        String[] parts = splitMessage(original);

        int totalLength = 0;
        for (String part : parts) {
            totalLength += part.length();
        }

        assertEquals(originalLength, totalLength);
    }
}