package org.nekotori.qbit;

// QBitTorrentException.java
public class QBitTorrentException extends RuntimeException {
    private final int statusCode;
    
    public QBitTorrentException(String message) {
        super(message);
        this.statusCode = 500;
    }
    
    public QBitTorrentException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
    
    public QBitTorrentException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 500;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
}