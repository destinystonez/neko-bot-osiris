package org.nekotori.qbit;// TorrentInfo.java
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record TorrentInfo(
    String hash,
    String name,
    long size,
    double progress,
    String state,
    @JsonProperty("dlspeed") long downloadSpeed,
    @JsonProperty("upspeed") long uploadSpeed,
    long downloaded,
    long uploaded,
    double ratio,
    long eta,
    @JsonProperty("save_path") String savePath,
    String category,
    @JsonProperty("added_on") long addedOn,
    @JsonProperty("completion_on") long completionOn,
    @JsonProperty("num_seeds") int numSeeds,
    @JsonProperty("num_leechs") int numLeechs
) {
    public Instant getAddedOnInstant() {
        return Instant.ofEpochSecond(addedOn);
    }
    
    public Instant getCompletionOnInstant() {
        return completionOn > 0 ? Instant.ofEpochSecond(completionOn) : null;
    }
    
    public double getProgressPercentage() {
        return progress * 100;
    }
    
    public String getFormattedSize() {
        return formatBytes(size);
    }
    
    public String getFormattedDownloadSpeed() {
        return formatBytes(downloadSpeed) + "/s";
    }
    
    public String getFormattedUploadSpeed() {
        return formatBytes(uploadSpeed) + "/s";
    }
    
    public String getFormattedDownloaded() {
        return formatBytes(downloaded);
    }
    
    public String getFormattedUploaded() {
        return formatBytes(uploaded);
    }
    
    public String getFormattedETA() {
        if (eta == 8640000) return "∞";
        if (eta <= 0) return "完成";
        
        long hours = eta / 3600;
        long minutes = (eta % 3600) / 60;
        long seconds = eta % 60;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}