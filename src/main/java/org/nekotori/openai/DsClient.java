package org.nekotori.openai;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.util.internal.StringUtil;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.internal.sse.RealEventSource;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class DsClient {

    private static final ExecutorService dsExecutor = new ThreadPoolExecutor(4,20,1000, TimeUnit.SECONDS,new ArrayBlockingQueue<>(5));

    private static final String dpUrl = "https://api.lkeap.cloud.tencent.com/v1";
    public static void invoke(String dpApiKey,ChatRequest chatRequest, Consumer<String> bigmodelResponseListener){
        dsExecutor.execute(()->{
            chatRequest.setStream(true);
            RequestBody body = RequestBody.create(okhttp3.MediaType.parse("application/json; charset=utf-8"),
                    JSONUtil.toJsonStr(chatRequest));
            Request request = new Request
                    .Builder()
                    .header("Authorization", "Bearer " + dpApiKey)
                    .header("Content-Type", "application/json")
                    .url(dpUrl)
                    .post(body)
                    .build();
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(2, TimeUnit.MINUTES)
                    .readTimeout(2, TimeUnit.MINUTES)
                    .build();
            RealEventSource realEventSource = new RealEventSource(request, new EventSourceListener() {

                private String message2Send = "";

                @Override
                public void onClosed(@NotNull EventSource eventSource) {
                    bigmodelResponseListener.accept(message2Send);
                    super.onClosed(eventSource);
                }

                @Override
                public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
                    try {
                        // 结束返回，不用解析
                        if ("[DONE]".equals(data)) {
                            return;
                        }
                        ObjectMapper objectMapper = new ObjectMapper();
                        JsonNode rootNode = objectMapper.readTree(data);
                        // 提取choices数组的第一个元素
                        JsonNode firstChoice = rootNode.get("choices").get(0);
                        JsonNode deltaNode = firstChoice.get("delta");
                        // 安全获取content和reasoning_content的值
                        String content = deltaNode.has("content") && !deltaNode.get("content").isNull()
                                ? deltaNode.get("content").asText()
                                : null;
                        String reasoningContent = deltaNode.has("reasoning_content") && !deltaNode.get("reasoning_content").isNull()
                                ? deltaNode.get("reasoning_content").asText()
                                : null;
                        System.out.println("content: " + content);
                        System.out.println("reasoning_content: " + reasoningContent);
                        if(!StringUtil.isNullOrEmpty(content)){
                            System.out.println("content: " + content);
                            message2Send = message2Send + content;
                            if(message2Send.contains("\n")){
                                String[] split = message2Send.trim().split("\\n");
                                message2Send = message2Send.trim().substring(split[0].length());
                                if(split[0].contains("我无法给到")){
                                    return;
                                }
                                dsExecutor.execute(()->bigmodelResponseListener.accept(split[0]));
                            }
                        }
                    } catch (IOException e) {
                        System.out.println("Error parsing JSON: " + e.getMessage());
                    }
                    super.onEvent(eventSource, id, type, data);
                }

                @Override
                public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
                    System.out.println("DsClient ERROR:"+response.message()+"/"+JSONUtil.toJsonStr(response.body()));
                    bigmodelResponseListener.accept("抱歉似乎我的核心模块出现了异常，请稍后再试试和我聊天");
                    super.onFailure(eventSource, t, response);
                }

                @Override
                public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
                    super.onOpen(eventSource, response);
                }
            });
            realEventSource.connect(okHttpClient);
        });
    }

}
