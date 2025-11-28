package org.nekotori;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.nekotori.bot.NapcatBot;
import org.nekotori.bot.StaticPrompts;
import org.nekotori.gemini.GenerateOptions;
import org.nekotori.gemini.NanoBananaClient;
import org.nekotori.napcat.*;
import org.nekotori.openai.*;
import org.nekotori.persistence.sqlite.*;
import org.nekotori.qbit.ApiResponse;
import org.nekotori.qbit.MagnetRequest;
import org.nekotori.qbit.QBitTorrentClient;
import org.nekotori.qbit.TorrentListResponse;
import org.nekotori.sd.StableDiffusionClient;
import org.nekotori.tts.SovitsClient;
import org.nekotori.util.Base64ToImageWithCommons;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;


@Slf4j
public class Application {
    private static final ExecutorService executorService = new ThreadPoolExecutor(8,16,
            60, TimeUnit.SECONDS,new LinkedBlockingQueue<>(1000));
    private static GroupPersistence groupPersistence;
    private static GroupChatHistoryPersistence historyPersistence;
    private static AiConversationPersistence conversationPersistence;
    private static QBitTorrentClient qBitTorrentClient;
    private static final AtomicBoolean isSDRunning = new AtomicBoolean(false);

    public static void main(String[] args) throws InterruptedException {

        qBitTorrentClient = new QBitTorrentClient("http://localhost:8002");
        CompletableFuture.runAsync(()->{
            log.info("=== 检查Qbit服务状态 ===");
            var statusResponse = qBitTorrentClient.getStatus();
            if (statusResponse.success()) {
                log.info("服务状态: {}", statusResponse.data());
            } else {
                log.info("错误: {}", statusResponse.message());
                return;
            }
            // 2. 检查 qBittorrent 状态
            log.info("\n=== 检查 qBittorrent 状态 ===");
            var qbtStatus = qBitTorrentClient.getQBitTorrentStatus();
            if (qbtStatus.success()) {
                log.info("qBittorrent 状态: {}", qbtStatus.data().status());
                log.info("版本: {}", qbtStatus.data().version());
            } else {
                log.info("错误: {}", qbtStatus.message());
            }
        },executorService);

        groupPersistence = new GroupPersistence();
        historyPersistence = new GroupChatHistoryPersistence();
        conversationPersistence = new AiConversationPersistence();
        Semaphore semaphore = new Semaphore(0);
        var bot = new NapcatBot();
        bot.onMessageEvent(PrivateMessageEvent.class)
                .flux()
                .subscribeOn(Schedulers.fromExecutor(executorService))
                .subscribe(event->{
                    AnalysisMessage analysisMessage = new AnalysisMessage();
                    String s = simpleMessage(event.getRaw_message());
                    analysisMessage.setContent(s);
                    analysisMessage.setUserId(event.getSender().getUser_id());
                    analysisMessage.setUser(event.getSender().getNickname());
                    var analysisMessages = new ArrayList<>();
                    analysisMessages.add(analysisMessage);
                    var list = new ArrayList<ChatMessage>();
                    list.add(new ChatMessage("system", StaticPrompts.prompt.get(11)));
                    list.add(new ChatMessage("user", JSONUtil.toJsonStr(analysisMessages)));
                    ChatRequest chatRequest = new ChatRequest();
                    chatRequest.setModel("deepseek-v3");
                    chatRequest.setStream(true);
                    chatRequest.setMessages(list);
                    DsClient.invoke(bot.getConfig().getDeepseek().getApiKey(), chatRequest, resp -> {
                        JSONArray entries = JSONUtil.parseArray(resp);
                        MessageBuilder builder = MessageBuilder.builder();
                        for (Object entry : entries) {
                            JSONObject entryJson = JSONUtil.parseObj(entry);
                            String type = entryJson.getStr("type");
                            String content = entryJson.getStr("content");
                            String targetUser = entryJson.getStr("targetUser");
                            if ("chat".equals(type)){
                                event.getClient().sendPrivateMessage(event.getSender().getUser_id(), MessageBuilder.builder()
                                        .plainText(content)
                                        .build());
                            }else if("audio".equals(type)){
                                SovitsClient.ttsV4(content,5,(data)->{
                                    event.getClient().sendPrivateMessage(event.getSender().getUser_id(), MessageBuilder.builder()
                                            .record(data)
                                            .build());
                                });
                            }else if("image".equals(type)){
//                                List<String> imageUrls = getImageUrls(event);
//                                event.getClient().sendPrivateMessage(event.getSender().getUser_id(), MessageBuilder.builder()
//                                        .plainText("好的，根据你的提示我设定了以下Prompt:"+ content)
//                                        .build());
//                                NanoBananaClient client = new NanoBananaClient("");
//                                generateImageAndSend(event,imageUrls,content,client);
                            }
                        }

                    });
                });


        bot.onMessageEvent(GroupMessageEvent.class)
                .flux()
                .subscribeOn(Schedulers.fromExecutor(executorService))
                .subscribe(event-> {
                            if(Math.random()<0.95){
                                return;
                            }
                            var list = new ArrayList<ChatMessage>();
                            list.add(new ChatMessage("system", StaticPrompts.prompt.get(10)));
                            list.add(new ChatMessage("user", event.getSender().getUser_id() + ":" +
                                    simpleMessage(event.getRaw_message())));
                            ChatRequest chatRequest = new ChatRequest();
                            chatRequest.setModel("deepseek-v3");
                            chatRequest.setStream(true);
                            chatRequest.setMessages(list);
                                DsClient.invoke(bot.getConfig().getDeepseek().getApiKey(), chatRequest, resp -> {
                                AiConversationEntity conv = new AiConversationEntity();
                                conv.setGroupId(event.getGroup_id());
                                conv.setTargetUserId(event.getSender().getUser_id());
                                conv.setRole("assistant");
                                conv.setMessage(resp);
                                conv.setTime(System.currentTimeMillis());
                                conversationPersistence.save(conv);
                                sendMessageWithAt(event,resp);
                            });
                        }
                );

        bot.onMessageEvent(GroupMessageEvent.class)
                .flux()
                .subscribeOn(Schedulers.fromExecutor(executorService))
                .subscribe(event->{
                    GroupChatHistoryEntity val = new GroupChatHistoryEntity();
                    val.setGroupId(event.getGroup_id());
                    val.setUserId(event.getSender().getUser_id());
                    val.setMessage(event.getRaw_message());
                    val.setTime(System.currentTimeMillis());
                    historyPersistence.save(val);
                    AiConversationEntity conv = new AiConversationEntity();
                    conv.setGroupId(event.getGroup_id());
                    conv.setTargetUserId(event.getSender().getUser_id());
                    conv.setRole("user");
                    conv.setMessage(event.getRaw_message());
                    conv.setTime(System.currentTimeMillis());
                    conversationPersistence.save(conv);
                    GroupEntity groupById = groupPersistence.getGroupById(event.getGroup_id());
                    if (groupById == null) {
                        groupPersistence.save(event.getGroup_id(),"",false,false,0,"","");
                    }
                });

        // 查看bt列表
        bot.onMessageEvent(GroupMessageEvent.class)
                .onCommand("btlist")
                .onSenderIdentity(event->{
                    GroupEntity groupById = groupPersistence.getGroupById(event.getGroup_id());
                    if (groupById == null) {
                        return false;
                    }
                    return groupById.getFeatures().contains("btlist");
                })
                .flux()
                .subscribeOn(Schedulers.fromExecutor(executorService))
                .subscribe(event->{
                    StringBuffer sb = new StringBuffer();
                    ApiResponse<TorrentListResponse> torrents = qBitTorrentClient.getTorrents();
                    torrents.data().torrents().forEach(torrent->{
                        sb.append("\n--- ").append(torrent.name()).append(" ---");
                        sb.append("\n进度: ").append(String.format("%.2f%%", torrent.getProgressPercentage()));
                        sb.append("\n状态: ").append(torrent.state());
                        sb.append("\n下载速度: ").append(torrent.getFormattedDownloadSpeed());
                        sb.append("\n大小: ").append(torrent.getFormattedSize());
                        sb.append("\nETA: ").append(torrent.getFormattedETA());
                    });
                    String message = sb.toString().trim();
                    event.getClient().sendGroupMessage(event.getGroup_id(),MessageBuilder.builder()
                            .plainText(message)
                            .build());
                });

        // 磁链下载
        bot.onMessageEvent(GroupMessageEvent.class)
                .onSenderIdentity(event-> event.getRaw_message().startsWith("magnet:?"))
                .flux()
                .subscribeOn(Schedulers.fromExecutor(executorService))
                .subscribe(event->{
                    var downloadResponse = qBitTorrentClient.addMagnetDownload(new MagnetRequest(event.getRaw_message().trim(),"movies"));
                    if (downloadResponse.success()) {
                        event.getClient().sendGroupMessage(event.getGroup_id(),MessageBuilder.builder()
                                .plainText("添加下载成功")
                                .build());
                    } else {
                        event.getClient().sendGroupMessage(event.getGroup_id(),MessageBuilder.builder()
                                .plainText("添加下载失败")
                                .build());
                    }
                });


        // Deepseek对话
        bot.onMessageEvent(GroupMessageEvent.class)
                .onAt()
                .flux()
                .subscribeOn(Schedulers.fromExecutor(executorService))
                .subscribe(event->{
                    var message = conversationPersistence.getAllMessagesByUser(event.getGroup_id(), event.getSender().getUser_id());
                    var reverse = CollectionUtil.reverse(message);
                    var analysisMessages = new ArrayList<>();
                    reverse.forEach(m->{
                        AnalysisMessage analysisMessage = new AnalysisMessage();
                        analysisMessage.setContent(simpleMessage(m.getMessage()));
                        analysisMessage.setUserId(!Objects.equals(m.getRole(), "assistant") ?m.getTargetUserId():0L);
                        analysisMessage.setUser(m.getRole());
                        analysisMessages.add(analysisMessage);
                    });

                    AnalysisMessage analysisMessage = new AnalysisMessage();
                    String s = simpleMessage(event.getRaw_message());
                    if(reverse.isEmpty()|| !Objects.equals(reverse.get(reverse.size() - 1).getMessage(), s)) {
                        analysisMessage.setContent(s);
                        analysisMessage.setUserId(event.getSender().getUser_id());
                        analysisMessage.setUser(event.getSender().getNickname());
                        analysisMessages.add(analysisMessage);
                    }
                    var list = new ArrayList<ChatMessage>();
                    list.add(new ChatMessage("system", StaticPrompts.prompt.get(11)));
                    list.add(new ChatMessage("user", JSONUtil.toJsonStr(analysisMessages)));
                    ChatRequest chatRequest = new ChatRequest();
                    chatRequest.setModel("deepseek-v3");
                    chatRequest.setStream(true);
                    chatRequest.setMessages(list);
                    DsClient.invoke(bot.getConfig().getDeepseek().getApiKey(), chatRequest, resp -> {
                        JSONArray entries = JSONUtil.parseArray(resp);
                        MessageBuilder builder = MessageBuilder.builder();
                        for (Object entry : entries) {
                            JSONObject entryJson = JSONUtil.parseObj(entry);
                            String type = entryJson.getStr("type");
                            String content = entryJson.getStr("content");
                            String targetUser = entryJson.getStr("targetUser");
                            if ("chat".equals(type)){
                                if(Math.random()>1){
                                    SovitsClient.ttsV4(content,5,(data)->{
                                                event.getClient().sendGroupMessage(event.getGroup_id(), MessageBuilder.builder()
                                                        .record(data)
                                                        .build());
                                            });
                                    continue;
                                }
                                event.getClient().sendGroupMessage(event.getGroup_id(), MessageBuilder.builder()
                                        .plainText(content)
                                        .build());
                            }else if("audio".equals(type)){
                                SovitsClient.ttsV4(content,5,(data)->{
                                    event.getClient().sendGroupMessage(event.getGroup_id(), MessageBuilder.builder()
                                            .record(data)
                                            .build());
                                });
                            }else if("image".equals(type)){
//                                List<String> imageUrls = getImageUrls(event);
                                event.getClient().sendGroupMessage(event.getGroup_id(), MessageBuilder.builder()
                                        .plainText("画图请使用-bnn + 提示词 指令哦")
                                        .build());
//                                GroupEntity groupById = groupPersistence.getGroupById(event.getGroup_id());
//                                String nanoApiKey = groupById.getNanoApiKey();
//                                Integer credits = groupById.getCredits();
//                                if(StringUtil.isNullOrEmpty(nanoApiKey) || credits<=0){
//                                    event.getClient().sendGroupMessage(event.getGroup_id(), MessageBuilder.builder()
//                                            .plainText("绘图功能未开启，请先购买Token哦")
//                                            .build());
//                                    return;
//                                }
//                                NanoBananaClient client = new NanoBananaClient(nanoApiKey);
//                                generateImageAndSend(event,imageUrls,content,client);
//                                groupById.setCredits(credits-1);
//                                groupPersistence.updateGroup(groupById);
                            }
                        }
                    });
                });

        // NanoBanana图像生成
        bot.onMessageEvent(GroupMessageEvent.class)
                .onCommand("bnn")
                .onSenderIdentity(event->{
                    Long groupId = event.getGroup_id();
                    GroupEntity group = groupPersistence.getGroupById(groupId);
                    return group != null && group.getNanoApiKey() != null && group.getCredits()>0;
                })
                .flux()
                .subscribeOn(Schedulers.fromExecutor(executorService))
                .subscribe(event->{
                    Long groupId = event.getGroup_id();
                    GroupEntity group = groupPersistence.getGroupById(groupId);
                    var nanoClient = new NanoBananaClient(group.getNanoApiKey());
                    List<Message> parse = MessageBuilder.parse(event);
                    String prompt = parse.stream().filter(message -> message.getType().equals("text"))
                                    .map(message -> message.getData().get("text"))
                                    .collect(Collectors.joining("\n"))
                                    .replace("-bnn","");
                    List<String> imageUrls = parse.stream().filter(message -> message.getType().equals("image"))
                            .map(message -> message.getData().get("url"))
                            .toList();
                    generateImageAndSend(event, imageUrls, prompt, nanoClient);
                    group.setCredits(group.getCredits()-1);
                    groupPersistence.updateGroup(group);
                });

        // Sd图像生成
        bot.onMessageEvent(GroupMessageEvent.class)
                .onCommand("sd")
                .onSenderIdentity(event->{
                    Long groupId = event.getGroup_id();
                    GroupEntity group = groupPersistence.getGroupById(groupId);
                    return group != null && group.getFeatures()!=null && group.getFeatures().contains("sd");
                })
                .flux()
                .subscribeOn(Schedulers.fromExecutor(executorService))
                .subscribe(event-> {
                    if(isSDRunning.get()){
                        event.getClient().sendGroupMessage(event.getGroup_id(), MessageBuilder.builder()
                                .plainText("抱歉，SD模型正在处理中，请稍后再试")
                                .build());
                        return;
                    }
                    isSDRunning.set(true);
                    String s = simpleMessage(event.getRaw_message());
                    StableDiffusionClient.drawWithNatureLang(s.contains("nsfw"),bot.getConfig().getDeepseek().getApiKey(),
                            s,text->{
                                if (text.startsWith("file:")) {
                                    isSDRunning.set(false);
                                    text = text.substring(5);
                                    String fileName =System.currentTimeMillis()+ "sd.png";
                                    Base64ToImageWithCommons.base64ToImage(text,fileName);
                                    event.getClient().sendGroupMessage(event.getGroup_id(), MessageBuilder.builder()
                                            .image("file:///"+ Paths.get(fileName).toAbsolutePath().toString())
                                            .build());
                                 return;
                                }
                                event.getClient().sendGroupMessage(event.getGroup_id(), MessageBuilder.builder()
                                        .plainText(text)
                                        .build());
                            });
                });

        // 私聊
        bot.onMessageEvent(PrivateMessageEvent.class)
                .onCommand("hello")
                .onSenderIdentity(event->1318985307L==event.getSender().getUser_id())
                .flux()
                .subscribeOn(Schedulers.fromExecutor(executorService))
                .subscribe(event->{
                    event.getClient().sendPrivateMessage(event.getSender().getUser_id(),MessageBuilder.builder()
                            .plainText("hello")
                            .build());
                    log.info("hello");
                });
        semaphore.acquire();
    }

    private static void generateImageAndSend(MessageEvent event, List<String> imageUrls, String prompt, NanoBananaClient nanoClient) {
        GenerateOptions generateOptions = new GenerateOptions();
        generateOptions.setUrls(imageUrls);
        generateOptions.setPrompt(prompt);
        generateOptions.setModel("nano-banana-pro");
        generateOptions.setImageSize("1K");
        long start = System.currentTimeMillis();
        event.getClient().sendGroupMessage(event.getGroup_id(),MessageBuilder.builder()
                .plainText("图像生成中，请稍后")
                .build());
        nanoClient.generateImageAsync(prompt,generateOptions)
                .thenCompose(taskId-> nanoClient.waitForCompletionAsync(taskId,300000))
                .thenAccept(response->{
                    if (response == null){
                        event.getClient().sendGroupMessage(event.getGroup_id(),MessageBuilder.builder()
                                .plainText("图像生成失败")
                                .build());
                        return;
                    }
                    log.info(response);
                    long time = (System.currentTimeMillis() - start)/1000;
                    event.getClient().sendGroupMessage(event.getGroup_id(),MessageBuilder.builder()
                            .plainText("图像生成完成，耗时"+time+"秒，等待图像上传中")
                            .build());
                    event.getClient().sendGroupMessage(event.getGroup_id(),MessageBuilder.builder()
                            .at(event.getSender().getUser_id())
                            .image(response)
                            .build());
                });
    }

    private static @NotNull String simpleMessage(String rawMessage) {
        return rawMessage.replaceAll("\\[.*]", "")
                .replaceAll("（.*）", "");
    }

    private static void sendMessageWithAt(GroupMessageEvent event, String resp) {
        if(resp.startsWith("@")){
            String[] split = resp.split("#");
            var id = split[0].replace("@","");
            var message = split[1];
            event.getClient().sendGroupMessage(event.getGroup_id(),MessageBuilder.builder()
                    .at(Long.parseLong(id))
                    .plainText(message)
                    .build());
        }else if(resp.matches("\\d+:.*")){
            String[] split = resp.split(":");
            var id = split[0];
            var message = split[1];
            event.getClient().sendGroupMessage(event.getGroup_id(),MessageBuilder.builder()
                    .at(Long.parseLong(id))
                    .plainText(message)
                    .build());
        } else {
            event.getClient().sendGroupMessage(event.getGroup_id(),MessageBuilder.builder()
                .plainText(resp)
                .build());
        }
    }

    private static List<String> getImageUrls(MessageEvent event){
        List<Message> parse = MessageBuilder.parse(event);
        return parse.stream().filter(message -> message.getType().equals("image"))
                .map(message -> message.getData().get("url"))
                .toList();
    }
}
