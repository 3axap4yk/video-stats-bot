package com.project.service;

public class YouTubeException extends Exception {
    public YouTubeException(String message) {
        super(message);
    }

    public YouTubeException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Пустой {@code items} в ответе YouTube Data API — видео удалено, приватное или неверный id. */
    public boolean indicatesVideoNotFound() {
        String m = getMessage();
        return m != null && m.contains("не найдено");
    }
}