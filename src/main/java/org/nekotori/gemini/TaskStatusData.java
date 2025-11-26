package org.nekotori.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 任务状态数据
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskStatusData {
    private String taskId;
    private String paramJson;
    private String completeTime;
    private TaskResponse response;
    private int successFlag;
    private String errorCode;
    private String errorMessage;
    private String operationType;
    private String createTime;

    // Getter和Setter方法
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getParamJson() { return paramJson; }
    public void setParamJson(String paramJson) { this.paramJson = paramJson; }

    public String getCompleteTime() { return completeTime; }
    public void setCompleteTime(String completeTime) { this.completeTime = completeTime; }

    public TaskResponse getResponse() { return response; }
    public void setResponse(TaskResponse response) { this.response = response; }

    public int getSuccessFlag() { return successFlag; }
    public void setSuccessFlag(int successFlag) { this.successFlag = successFlag; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }

    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}