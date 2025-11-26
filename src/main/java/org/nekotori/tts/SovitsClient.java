package org.nekotori.tts;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.sse.RealEventSource;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class SovitsClient {

    private static final ExecutorService vitsExecutor = new ThreadPoolExecutor(4,20,1000, TimeUnit.SECONDS,new ArrayBlockingQueue<>(5));
    private static final String vitsUrl = "http://localhost:9872";

    public static void ttsV4(String input,Integer model ,Consumer<String> voicePathConsumer) {

        int ra = new Random().nextInt(100000000);
        String hash = generateRandomString(11);

//        setModel(model, hash);

        JSONArray dataArray = assembleGenerateRequest(input, model, ra);
        processJoin(1,64,hash, dataArray);
        processData(resp->{
            String voicePath = resp.getJSONObject("output")
                    .getJSONArray("data")
                    .getJSONObject(0)
                    .getStr("path");
            voicePathConsumer.accept(voicePath);
        }, hash);

    }

    private static void setModel(Integer model, String hash) {
        JSONArray setGPT = new JSONArray(JSONConfig.create().setIgnoreNullValue(false));
        setGPT.add(v4Model.get(model).getStr("gpt_name"));
        processJoin(4,5, hash,setGPT);
        processData(resp->{}, hash);
        JSONArray setSovit = new JSONArray(JSONConfig.create().setIgnoreNullValue(false));
        setSovit.add(v4Model.get(model).getStr("sovits_name"));
        setSovit.add("中文");
        setSovit.add("中文");
        processJoin(3,6, hash,setGPT);
        processData(resp->{}, hash);
    }

    private static @NotNull JSONArray assembleGenerateRequest(String input, Integer model, int ra) {

        JSONArray dataArray = new JSONArray(JSONConfig.create().setIgnoreNullValue(false));
        dataArray.add(input);
        dataArray.add("中英混合");
        dataArray.add(v4Model.get(model));
        dataArray.add(null);
        dataArray.add(v4Model.get(model).getStr("text"));
        dataArray.add("中文");
        dataArray.add(5);
        dataArray.add(1);
        dataArray.add(1);
        dataArray.add("凑四句一切");
        dataArray.add(20);
        dataArray.add(1);
        dataArray.add(false);
        dataArray.add(true);
        dataArray.add(0.3);
        dataArray.add(ra);
        dataArray.add(true);
        dataArray.add(true);
        dataArray.add(1.35);
        dataArray.add(32);
        dataArray.add(false);
        return dataArray;
    }

    private static void processData(Consumer<JSONObject> dataConsumer, String hash) {
        vitsExecutor.execute(()->{
            String requestUrl = vitsUrl+"/queue/data?session_hash="+ hash;
            Request request = new Request
                    .Builder()
                    .url(requestUrl)
                    .build();
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(2, TimeUnit.MINUTES)
                    .readTimeout(2, TimeUnit.MINUTES)
                    .build();
            RealEventSource realEventSource = new RealEventSource(request, new EventSourceListener() {
                @Override
                public void onClosed(@NotNull EventSource eventSource) {
                    super.onClosed(eventSource);
                }

                @Override
                public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
                    System.out.println(data);
                    JSONObject resp = JSONUtil.parseObj(data);
                    String msg = resp.getStr("msg");
                    if(msg!=null && msg.equals("process_completed")){
                        dataConsumer.accept(resp);
                    }
                    if(msg!=null && msg.equals("close_stream")){
                        eventSource.cancel();
                    }
                    super.onEvent(eventSource, id, type, data);
                }

                @Override
                public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
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

    private static void processJoin(Integer fnId,Integer triggerId,String hash, JSONArray dataArray) {
        JSONObject entries = new JSONObject();
        entries.putIfAbsent("event_data",null);
        entries.putIfAbsent("fn_index",fnId);
        entries.putIfAbsent("session_hash", hash);
        entries.putIfAbsent("data", dataArray);
        if (triggerId != null){
            entries.putIfAbsent("trigger_id",triggerId);
        }
        System.out.println(JSONUtil.toJsonStr(entries, JSONConfig.create().setIgnoreNullValue(false)));
        HttpUtil.createPost(vitsUrl + "/queue/join")
                .header("Content-Type", "application/json")
                .body(JSONUtil.toJsonStr(entries, JSONConfig.create().setIgnoreNullValue(false)))
                .execute();
    }

    public static String generateRandomString(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();

        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }

        return sb.toString();
    }

    private static final Map<Integer,JSONObject> v4Model = new ConcurrentHashMap<>();

    static {
        JSONObject preTrain = new JSONObject();
        preTrain.putIfAbsent("gpt_name","GPT_weights_v4/刻晴_ZH-e10.ckpt");
        preTrain.putIfAbsent("sovits_name","SoVITS_weights_v4/刻晴_ZH_e10_s490_l32.pth");
        preTrain.putIfAbsent("path","E:\\ai\\GPT-SoVITS-v2pro-20250604\\TEMP\\gradio\\9ef63576acc4408168273109dd2a8b65895a362fb2c10420e9014f064fdd6c59\\默认这么多式样这么多质地这么多选择啊这就是消费呀真是令人难以抵御的魅力.wav");
        preTrain.putIfAbsent("url","http://localhost:9872/file=E:\\ai\\GPT-SoVITS-v2pro-20250604\\TEMP\\gradio\\9ef63576acc4408168273109dd2a8b65895a362fb2c10420e9014f064fdd6c59\\默认这么多式样这么多质地这么多选择啊这就是消费呀真是令人难以抵御的魅力.wav");
        preTrain.putIfAbsent("orig_name","【默认】这么多式样、这么多质地、这么多选择…啊，这就是「消费」呀，真是令人难以抵御的魅力。.wav");
        preTrain.putIfAbsent("size",783148);
        preTrain.putIfAbsent("mime_type","audio/wav");
        JSONObject meta = new JSONObject();
        meta.putIfAbsent("_type","gradio.FileData");
        preTrain.putIfAbsent("meta",meta);
        preTrain.putIfAbsent("text","【默认】这么多式样、这么多质地、这么多选择…啊，这就是「消费」呀，真是令人难以抵御的魅力。");
        v4Model.put(2,preTrain);

        preTrain = new JSONObject();
        preTrain.putIfAbsent("path","E:\\\\ai\\\\GPT-SoVITS-v2pro-20250604\\\\TEMP\\\\gradio\\\\dcbc5e77b54df8f0fd2bb5a35040107f035ad0f68307aeeaaf8da47e3d5d3587\\\\东雪莲-非常感谢莲能在我困扰的不行的时候又开了次情感电台我是今年大一新生女孩子.wav");
        preTrain.putIfAbsent("url","http://localhost:9872/file=E:\\\\ai\\\\GPT-SoVITS-v2pro-20250604\\\\TEMP\\\\gradio\\\\dcbc5e77b54df8f0fd2bb5a35040107f035ad0f68307aeeaaf8da47e3d5d3587\\\\东雪莲-非常感谢莲能在我困扰的不行的时候又开了次情感电台我是今年大一新生女孩子.wav");
        preTrain.putIfAbsent("orig_name","东雪莲-非常感谢莲能在我困扰的不行的时候又开了次情感电台。我是今年大一新生女孩子。.wav");
        preTrain.putIfAbsent("size",568052);
        preTrain.putIfAbsent("mime_type","audio/wav");
        meta = new JSONObject();
        meta.putIfAbsent("_type","gradio.FileData");
        preTrain.putIfAbsent("meta",meta);
        preTrain.putIfAbsent("text","非常感谢莲能在我困扰的不行的时候又开了次情感电台。我是今年大一新生女孩子。");
        v4Model.put(5,preTrain);

        preTrain = new JSONObject();
        preTrain.putIfAbsent("path","E:\\ai\\GPT-SoVITS-v2pro-20250604\\TEMP\\gradio\\f8b8c20ff143e728c815e41c720d76e757665e1171b2cc0488d363ee18aeac68\\默认嘿嘿毕竟找活人不是我擅长的事嘛如果让我找的是边界另一边的人.wav");
        preTrain.putIfAbsent("url","http://localhost:9872/file=E:\\\\ai\\\\GPT-SoVITS-v2pro-20250604\\\\TEMP\\\\gradio\\\\f8b8c20ff143e728c815e41c720d76e757665e1171b2cc0488d363ee18aeac68\\\\默认嘿嘿毕竟找活人不是我擅长的事嘛如果让我找的是边界另一边的人.wav");
        preTrain.putIfAbsent("orig_name","【默认】嘿嘿，毕竟找活人不是我擅长的事嘛，如果让我找的是「边界」另一边的人….wav");
        preTrain.putIfAbsent("size",652204);
        preTrain.putIfAbsent("mime_type","audio/wav");
        meta = new JSONObject();
        meta.putIfAbsent("_type","gradio.FileData");
        preTrain.putIfAbsent("meta",meta);
        preTrain.putIfAbsent("text","嘿嘿，毕竟找活人不是我擅长的事嘛，如果让我找的是「边界」另一边的人…");
        v4Model.put(4,preTrain);

        preTrain = new JSONObject();
        preTrain.putIfAbsent("path","E:\\ai\\GPT-SoVITS-v2pro-20250604\\TEMP\\gradio\\c7aa99a813b2dfe397cd8df486f6402f88bdcf2ad4fae830a9cc10408e121fe3\\默认在此之前请您务必继续享受旅居拉古那的时光.wav");
        preTrain.putIfAbsent("url","http://localhost:9872/file=E:\\\\ai\\\\GPT-SoVITS-v2pro-20250604\\\\TEMP\\\\gradio\\\\c7aa99a813b2dfe397cd8df486f6402f88bdcf2ad4fae830a9cc10408e121fe3\\\\默认在此之前请您务必继续享受旅居拉古那的时光.wav");
        preTrain.putIfAbsent("orig_name","【默认】在此之前，请您务必继续享受旅居拉古那的时光。.wav");
        preTrain.putIfAbsent("size",381540);
        preTrain.putIfAbsent("mime_type","audio/wav");
        meta = new JSONObject();
        meta.putIfAbsent("_type","gradio.FileData");
        preTrain.putIfAbsent("meta",meta);
        preTrain.putIfAbsent("text","在此之前，请您务必继续享受旅居拉古那的时光。");
        v4Model.put(3,preTrain);
    }
}
