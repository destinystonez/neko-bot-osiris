package org.nekotori.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import okio.BufferedSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class OpenAIClient {
    
    private final OkHttpClient client;
    private final String apiKey;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    
    public OpenAIClient(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    // 图片转base64函数
    public String encodeImage(String imagePath) throws IOException {
        byte[] imageBytes = Files.readAllBytes(Paths.get(imagePath));
        return Base64.getEncoder().encodeToString(imageBytes);
    }
    
    // 流式响应处理接口
    public interface StreamResponseHandler {
        void onContent(String content);
        void onComplete(String fullReply);
        void onError(Throwable error);
    }
    
    // 提交信息至GPT-4o（流式响应）
    public void streamChatCompletion(String model, 
                                    String systemMessage, 
                                    String userText, 
                                    String imagePath, 
                                    StreamResponseHandler handler) {
        try {
            UserMessage userMessage = new UserMessage("user");

            // 构建消息
            List<Object> messages = new ArrayList<>();
            
            // 系统消息
            messages.add(new SystemMessage("system", systemMessage));
            
            // 用户消息（包含文本和图片）
            userMessage.addContent(new TextContent("text", userText));
            if(imagePath != null) {
                // 编码图片
                String base64Image = encodeImage(imagePath);
                String imageUrl = "data:image/jpeg;base64," + base64Image;
                userMessage.addContent(new ImageContent("image_url", new ImageUrl(imageUrl)));
            }

            messages.add(userMessage);
            
            // 构建请求体
            ChatRequest request = new ChatRequest(model, messages, true);
            
            String requestBody = objectMapper.writeValueAsString(request);
            
            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();
            
            // 执行请求并处理流式响应
            client.newCall(httpRequest).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handler.onError(e);
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        handler.onError(new IOException("Unexpected code: " + response));
                        return;
                    }
                    
                    StringBuilder fullReply = new StringBuilder();
                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody != null) {
                            BufferedSource source = responseBody.source();
                            while (!source.exhausted()) {
                                String line = source.readUtf8Line();
                                if (line != null && line.startsWith("data: ")) {
                                    String data = line.substring(6);
                                    if (data.equals("[DONE]")) {
                                        break;
                                    }
                                    
                                    try {
                                        ChatResponse chunk = objectMapper.readValue(data, ChatResponse.class);
                                        if (chunk != null && chunk.choices != null && !chunk.choices.isEmpty()) {
                                            ChatResponse.Choice choice = chunk.choices.get(0);
                                            if (choice.delta != null && choice.delta.content != null) {
                                                handler.onContent(choice.delta.content);
                                                fullReply.append(choice.delta.content);
                                            }
                                        }
                                    } catch (JsonProcessingException e) {
                                        // 忽略解析错误，继续处理下一个数据块
                                    }
                                }
                            }
                        }
                    }
                    
                    handler.onComplete(fullReply.toString());
                }
            });
            
        } catch (Exception e) {
            handler.onError(e);
        }
    }
    
    // 数据模型类
    public static class ChatRequest {
        public String model;
        public List<Object> messages;
        public boolean stream;
        
        public ChatRequest(String model, List<Object> messages, boolean stream) {
            this.model = model;
            this.messages = messages;
            this.stream = stream;
        }
    }
    
    public static class SystemMessage {
        public String role;
        public String content;
        
        public SystemMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
    
    public static class UserMessage {
        public String role;
        public List<Object> content;
        
        public UserMessage(String role) {
            this.role = role;
            this.content = new ArrayList<>();
        }
        
        public void addContent(Object contentItem) {
            this.content.add(contentItem);
        }
    }
    
    public static class TextContent {
        public String type;
        public String text;
        
        public TextContent(String type, String text) {
            this.type = type;
            this.text = text;
        }
    }
    
    public static class ImageContent {
        public String type;
        public ImageUrl image_url;
        
        public ImageContent(String type, ImageUrl imageUrl) {
            this.type = type;
            this.image_url = imageUrl;
        }
    }
    
    public static class ImageUrl {
        public String url;
        
        public ImageUrl(String url) {
            this.url = url;
        }
    }
    
    public static class ChatResponse {
        public List<Choice> choices;
        public static class Choice {
            public Delta delta;
            public static class Delta {
                public String content;
            }
        }
    }
}