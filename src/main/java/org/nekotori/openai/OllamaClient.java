package org.nekotori.openai;

import cn.hutool.json.JSONUtil;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class OllamaClient {

    private static final ExecutorService dsExecutor = new ThreadPoolExecutor(4,20,1000, TimeUnit.SECONDS,new ArrayBlockingQueue<>(5));

    private static final String dpUrl = "http://localhost:11434/api/chat";
    
    public static void invoke(String apiKey, ChatRequest chatRequest, Consumer<String> bigmodelResponseListener) {
        dsExecutor.execute(() -> {
            chatRequest.setStream(true);
            chatRequest.setThink(false);
            log.info("chatRequest: " + JSONUtil.toJsonStr(chatRequest));
            RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"),
                    JSONUtil.toJsonStr(chatRequest));
            Request request = new Request
                    .Builder()
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/x-ndjson")
                    .url(dpUrl)
                    .post(body)
                    .build();
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(2, TimeUnit.MINUTES)
                    .readTimeout(2, TimeUnit.MINUTES)
                    .build();
            
            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    bigmodelResponseListener.accept("抱歉似乎我的核心模块出现了异常，请稍后再试试和我聊天");
                    return;
                }

                String message2Send = "";
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {
                    
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.trim().isEmpty()) {
                            continue;
                        }
                        
                        log.info("NDJSON line: " + line);
                        OllamaResponse ollamaResponse = JSONUtil.toBean(line, OllamaResponse.class);
                        
                        // 结束返回，不用解析
                        if (ollamaResponse.isDone()) {
                            break;
                        }
                        
                        String content = ollamaResponse.getMessage().getContent();
                        System.out.println("content: " + content);
                        
                        if (!StringUtil.isNullOrEmpty(content)) {
                            message2Send = message2Send + content;
                            if (message2Send.contains("\n")) {
                                String[] split = message2Send.trim().split("\\n");
                                message2Send = message2Send.trim().substring(split[0].length());
                                if (split[0].contains("我无法给到")) {
                                    return;
                                }
                                dsExecutor.execute(() -> bigmodelResponseListener.accept(split[0]));
                            }
                        }
                    }
                }
                
                // 发送剩余的消息
                if (!message2Send.trim().isEmpty()) {
                    bigmodelResponseListener.accept(message2Send.trim());
                }

                
            } catch (IOException e) {
                log.error("Error processing NDJSON stream", e);
                bigmodelResponseListener.accept("抱歉似乎我的核心模块出现了异常，请稍后再试试和我聊天");
            }
        });
    }

}