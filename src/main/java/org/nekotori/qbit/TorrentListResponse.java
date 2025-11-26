package org.nekotori.qbit;// TorrentListResponse.java
import java.util.List;

public record TorrentListResponse(
    List<TorrentInfo> torrents,
    int total,
    int downloading,
    int completed,
    int paused
) {}