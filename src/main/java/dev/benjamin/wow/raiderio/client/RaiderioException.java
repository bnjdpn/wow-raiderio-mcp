package dev.benjamin.wow.raiderio.client;

public class RaiderioException extends RuntimeException {
    public RaiderioException(String message) { super(message); }
    public RaiderioException(String message, Throwable cause) { super(message, cause); }
}
