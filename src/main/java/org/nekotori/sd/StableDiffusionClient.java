package org.nekotori.sd;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.nekotori.bot.StaticPrompts;
import org.nekotori.openai.ChatMessage;
import org.nekotori.openai.ChatRequest;
import org.nekotori.openai.DsClient;
import org.nekotori.openai.OllamaClient;
import org.nekotori.util.Lambda;
import org.w3c.dom.Text;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public class StableDiffusionClient {

    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(300))  // 长超时适合AI生成
            .writeTimeout(Duration.ofSeconds(30))
            .build();
    private static final String baseUrl = "http://127.0.0.1:7860/sdapi/v1";
    private static final ObjectMapper mapper = new ObjectMapper();

    public static Mono<Text2ImgResponse> txt2img(Text2ImgOptions options){

        return Mono.create(sink -> {
            Mono.fromCallable(()-> buildRequest(options))
                    .doOnNext(request -> httpClient.newCall(request)
                            .enqueue(txt2ImageCallBack(sink)))
                    .doOnError(sink::error)
                    .doFinally(s->{
                        log.info("stable diffusion generate finish");
                    })
                    .subscribe();
        });
    }

    public static void drawWithNatureLang(boolean nsfw,String apiKey, String text, Consumer<String> callback){
        var list = new ArrayList<ChatMessage>();
        list.add(new ChatMessage("system", StaticPrompts.prompt.get(12)));
        list.add(new ChatMessage("user", text));
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setModel("deepseek-v3");
        chatRequest.setStream(true);
        chatRequest.setMessages(list);
        DsClient.invoke(apiKey, chatRequest, resp->{
            try {
                JSONObject entries = JSONUtil.parseObj(resp);
                String prompt = entries.getStr("prompt");
                String negPrompt = entries.getStr("negative_prompt");
                Text2ImgOptions options = Text2ImgOptions.builder()
                        .prompt((nsfw?"nsfw nude":"")+ "masterpiece,best quality,ultra-detailed," + prompt)
                        .negativePrompt((nsfw?"censored nsfw censor bar":"")+"(worst quality, low quality:1.4)" + negPrompt)
                        .build();
                callback.accept("已生成定制Prompt,正在生成图像");
                log.info("prompt: {}",resp);
                txt2img(options)
                        .timeout(Duration.ofSeconds(300), Mono.empty())
                        .publishOn(Schedulers.boundedElastic())
                        .doOnError(e -> {
                            callback.accept("生成失败，请稍后再试，"+e.getMessage());
                        })
                        .doOnSuccess(img -> {
                            log.info("img: {}",img);
                            callback.accept("file:" + img.getImages().get(0));
                        })
                        .subscribe();
            }catch (Exception e){
                callback.accept("file:生成失败，请稍后再试，"+e.getMessage());
            }
        } );

    }

    private static @NotNull Request buildRequest(Text2ImgOptions options) throws JsonProcessingException {
        return new Request.Builder()
                .url(baseUrl + "/txt2img")
                .post(RequestBody.create(mapper.writeValueAsString(options),
                        MediaType.parse("application/json")))
                .build();
    }

    private static @NotNull Callback txt2ImageCallBack(MonoSink<Text2ImgResponse> sink) {
        return new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                sink.error(e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    sink.error(new IOException("Unexpected code " + response));
                    return;
                }
                if (response.body() == null) {
                    sink.error(new IOException("Response body is null"));
                    return;
                }
//                log.info("stable diffusion response: {}",response.body().string());
                var resp = Lambda.tryGet(()->{
                            Text2ImgResponse text2ImgResponse = new Text2ImgResponse();
                            JSONObject entries = JSONUtil.parseObj(response.body().string());
                            JSONArray images = entries.getJSONArray("images");
                            text2ImgResponse.setImages(images.toList(String.class));
                            return text2ImgResponse;
                        })
                        .orElse(null);
                sink.success(resp);
            }
        };
    }

    public static void main(String[] args) throws InterruptedException {
        txt2img(Text2ImgOptions.builder()
                .prompt("a woman with a red hat and a red dress")
                .negativePrompt("")
                .build())
                .timeout(Duration.ofSeconds(300),Mono.empty())
                .publishOn(Schedulers.boundedElastic())
                .doOnError(e->{
                    System.out.println(e.getMessage());
                })
                .doOnSuccess(System.out::println)
                .block();

    }
}
