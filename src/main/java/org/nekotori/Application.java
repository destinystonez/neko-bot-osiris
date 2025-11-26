package org.nekotori;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.nekotori.bot.NapcatBot;
import org.nekotori.bot.StaticPrompts;
import org.nekotori.gemini.GenerateOptions;
import org.nekotori.gemini.NanoBananaClient;
import org.nekotori.napcat.GroupMessageEvent;
import org.nekotori.napcat.Message;
import org.nekotori.napcat.MessageBuilder;
import org.nekotori.napcat.PrivateMessageEvent;
import org.nekotori.openai.ChatMessage;
import org.nekotori.openai.ChatRequest;
import org.nekotori.openai.DsClient;
import org.nekotori.persistence.sqlite.GroupChatHistoryEntity;
import org.nekotori.persistence.sqlite.GroupChatHistoryPersistence;
import org.nekotori.persistence.sqlite.GroupEntity;
import org.nekotori.persistence.sqlite.GroupPersistence;
import org.nekotori.qbit.ApiResponse;
import org.nekotori.qbit.MagnetRequest;
import org.nekotori.qbit.QBitTorrentClient;
import org.nekotori.qbit.TorrentListResponse;
import org.nekotori.tts.SovitsClient;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;


@Slf4j
public class Application {
    private static final ExecutorService executorService = new ThreadPoolExecutor(8,16,
            60, TimeUnit.SECONDS,new LinkedBlockingQueue<>(1000));
    private static GroupPersistence groupPersistence;
    private static GroupChatHistoryPersistence historyPersistence;
    private static QBitTorrentClient qBitTorrentClient;

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
        Semaphore semaphore = new Semaphore(0);
        var bot = new NapcatBot();
        bot.onMessageEvent(GroupMessageEvent.class)
                .flux()
                .subscribe(event->{
                    GroupChatHistoryEntity val = new GroupChatHistoryEntity();
                    val.setGroupId(event.getGroup_id());
                    val.setUserId(event.getSender().getUser_id());
                    val.setMessage(event.getRaw_message());
                    val.setTime(System.currentTimeMillis());
                    historyPersistence.save(val);
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
                    ChatRequest chatRequest = new ChatRequest();
                    chatRequest.setModel("deepseek-v3-0324");
                    chatRequest.setStream(true);
                    var list = new ArrayList<ChatMessage>();
                    list.add(new ChatMessage("system", StaticPrompts.prompt.get(5)));
                    var history = historyPersistence.getAllMessagesByUser(event.getGroup_id(), event.getSender().getUser_id());
                    GroupChatHistoryEntity val = new GroupChatHistoryEntity();
                    val.setMessage(event.getRaw_message());
                    history.add(val);
                    list.add(new ChatMessage("user",String.join("\n",history.stream()
                            .map(GroupChatHistoryEntity::getMessage)
                            .map(message-> event.getSender().getUser_id()+":"+message)
                            .toList())));
                    chatRequest.setMessages(list);
                    log.info("send request:{}", JSONUtil.toJsonPrettyStr(chatRequest));
                    DsClient.invoke("",chatRequest, resp -> {
                        if(Math.random()<0.4){
                            var message = resp.replaceAll("\\(.*\\)", "").replaceAll("（.*）", "");
                            SovitsClient.ttsV4(message,5,fileUri->{
                                event.getClient().sendGroupMessage(event.getGroup_id(),MessageBuilder.builder()
                                        .record(fileUri)
                                        .build());
                            });
                            return;
                        }
                        if(resp.startsWith("@")){
                            String[] split = resp.split("#");
                            var id = split[0].replace("@","");
                            var message = split[1];
                            event.getClient().sendGroupMessage(event.getGroup_id(),MessageBuilder.builder()
                                    .at(Long.parseLong(id))
                                    .plainText(message)
                                    .build());
                        }else {
                            event.getClient().sendGroupMessage(event.getGroup_id(),MessageBuilder.builder()
                                .plainText(resp)
                                .build());
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
                            .thenCompose(taskId->nanoClient.waitForCompletionAsync(taskId,300000))
                            .thenAccept(response->{
                                if (response == null){
                                    event.getClient().sendGroupMessage(event.getGroup_id(),MessageBuilder.builder()
                                            .plainText("图像生成失败")
                                            .build());
                                    return;
                                }
                                log.info(response);
                                long time = (System.currentTimeMillis() - start)/1000;
                                group.setCredits(group.getCredits()-1);
                                groupPersistence.updateGroup(group);
                                event.getClient().sendGroupMessage(event.getGroup_id(),MessageBuilder.builder()
                                        .plainText("图像生成完成，耗时"+time+"秒，等待图像上传中")
                                        .build());
                                event.getClient().sendGroupMessage(event.getGroup_id(),MessageBuilder.builder()
                                        .at(event.getSender().getUser_id())
                                        .image(response)
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
}
