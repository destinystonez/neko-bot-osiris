package org.nekotori.gemini;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Data {
    private String id;
    private List<Result> results;
    private Integer progress;
    private String status;
    @JsonProperty("failure_reason")
    private String failureReason;
    private String error;

    // 无参构造器
    public Data() {
    }

    // 全参构造器
    public Data(String id, List<Result> results, Integer progress, String status, String failureReason, String error) {
        this.id = id;
        this.results = results;
        this.progress = progress;
        this.status = status;
        this.failureReason = failureReason;
        this.error = error;
    }

    // getter 和 setter 方法
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<Result> getResults() {
        return results;
    }

    public void setResults(List<Result> results) {
        this.results = results;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    // toString 方法
    @Override
    public String toString() {
        return "Data{" +
                "id='" + id + '\'' +
                ", results=" + results +
                ", progress=" + progress +
                ", status='" + status + '\'' +
                ", failureReason='" + failureReason + '\'' +
                ", error='" + error + '\'' +
                '}';
    }
}