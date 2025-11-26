package org.nekotori.gemini;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class NanoBananaClient {
    private final String apiKey;
    private final String baseUrl;
    private final String generateUrl = "/draw/nano-banana";
    private final String recordInfoUrl = "/draw/result";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public NanoBananaClient(String apiKey) {
        this(apiKey, "https://api.grsai.com/v1");
    }

    public NanoBananaClient(String apiKey, String baseUrl) {

        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String generateImage(String prompt, GenerateOptions options) throws Exception {
        if (options == null) {
            options = new GenerateOptions();
        }
        options.setPrompt(prompt);
        // 构建请求体

        String requestBodyJson = objectMapper.writeValueAsString(options);
        log.info("请求体：\n{}", requestBodyJson);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + generateUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build();
        log.info("发送请求完成");
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.info("请求失败,{}", response.body());
            throw new RuntimeException("HTTP error: " + response.statusCode());
        }

        log.info("请求成功，返回结果：\n{}", response.body());
        BaseApiResponse apiResponse = objectMapper.readValue(response.body(), BaseApiResponse.class);

        if (apiResponse.getCode() != 0) {
            throw new RuntimeException("Generation failed: " + apiResponse.getMsg());
        }
        log.info("请求成功，返回结果：\n{}", apiResponse.getData());
        // 解析data字段获取taskId
        if (apiResponse.getData() instanceof Map) {
            Map<String, Object> data = (Map<String, Object>) apiResponse.getData();
            if (data.containsKey("id")) {
                return data.get("id").toString();
            }
        }

        throw new RuntimeException("Failed to get taskId from response");
    }

    public Data getTaskStatus(String taskId) throws Exception {
        Map<String, String> id = Map.of("id", taskId);
        String body = objectMapper.writeValueAsString(id);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl +recordInfoUrl))
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP error: " + response.statusCode());
        }

        GsrResponseData apiResponse = objectMapper.readValue(response.body(), GsrResponseData.class);
        log.info(JSONUtil.toJsonPrettyStr(apiResponse));
        if (apiResponse.getCode() != 0) {
            throw new RuntimeException("Failed to get task status: " + apiResponse.getMsg());
        }

        // 将data转换为TaskStatusData
        if (apiResponse.getData() != null) {
            return apiResponse.getData();
        }

        throw new RuntimeException("Invalid response data format");
    }

    public String waitForCompletion(String taskId, long maxWaitTime) throws Exception {
        long startTime = System.currentTimeMillis();
        log.info("开始等待");
        while (System.currentTimeMillis() - startTime < maxWaitTime) {
            Data status = getTaskStatus(taskId);
            log.info("当前状态：{}%", status.getProgress());
            if (Objects.equals(status.getStatus(), "failed")){
                throw   new RuntimeException("Generation failed: " + status.getFailureReason());
            }
            switch (status.getProgress()) {
                case 0-> log.info("Task start generating...");
                case 100-> {
                    log.info("Generation completed successfully!");
                    log.info(status.getResults().get(0).getUrl());
                    return status.getResults().get(0).getUrl();
                }
                default -> log.info("Task is generating...");
            }

            Thread.sleep(3000);
        }
        log.info("等待超时");
        throw new RuntimeException("Generation timeout");
    }

    public String waitForCompletion(String taskId) throws Exception {
        return waitForCompletion(taskId, 300000); // 5分钟默认超时
    }

    // 异步版本的方法
    public CompletableFuture<String> generateImageAsync(String prompt, GenerateOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("开始异步作画任务");
                return generateImage(prompt, options);
            } catch (Exception e) {
                log.info("异步作画任务失败{}", e.getMessage());
                return null;
            }
        });
    }

    public CompletableFuture<String> waitForCompletionAsync(String taskId, long maxWaitTime) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("等待作图结果");
                return waitForCompletion(taskId, maxWaitTime);
            } catch (Exception e) {
                log.info("等待作图结果失败：{}", e.getMessage());
                return null;
            }
        });
    }
    public static void main(String[] args) {
        NanoBananaClient client = new NanoBananaClient("sk-df622582ad494d62881519381f9ac2c0");

        try {
            log.info("Starting image generation...");

            // 配置选项
            GenerateOptions options = new GenerateOptions();
            options.setModel("nano-banana-pro");

            String taskId = client.generateImage("beautiful girl", options);

            log.info("Task ID: {}. Waiting for completion...", taskId);

            String result = client.waitForCompletion(taskId);

            log.info("Image generated successfully!");
            log.info("Result Image URL: " + result);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}