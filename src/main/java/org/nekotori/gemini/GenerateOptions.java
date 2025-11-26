package org.nekotori.gemini;

import lombok.Data;

import java.util.List;

/**
 * {
 *   "model": "nano-banana-fast",
 *   "prompt": "提示词",
 *   "aspectRatio": "auto",
 *   "imageSize": "1K",
 *   "urls": [
 *     "https://example.com/example.png"
 *   ],
 *   "webHook": "https://example.com/callback",
 *   "shutProgress": false
 * }
 */
@Data
public class GenerateOptions {
    private String model = "nano-banana";
    private String prompt;
    private String aspectRatio = "auto";
    private String imageSize = "1K";
    private List<String> urls;
    private String webHook = "-1";
    private boolean shutProgress = false;

}