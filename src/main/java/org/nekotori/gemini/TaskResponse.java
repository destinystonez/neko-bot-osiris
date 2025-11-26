package org.nekotori.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 任务响应
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskResponse {
    private String resultImageUrl;

    public String getResultImageUrl() { return resultImageUrl; }
    public void setResultImageUrl(String resultImageUrl) { this.resultImageUrl = resultImageUrl; }
}