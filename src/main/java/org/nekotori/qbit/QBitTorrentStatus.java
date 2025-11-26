package org.nekotori.qbit;

// QBitTorrentStatus.java
public record QBitTorrentStatus(
    String status,
    String version,
    String message
) {}