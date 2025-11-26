package org.nekotori.napcat;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import jakarta.websocket.*;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;


@ClientEndpoint(configurator = NapCatMessageClient.MyConfigurator.class)
@Slf4j
public class NapCatMessageClient {

    private Session session;

    public static String token;

    private final List<Consumer<Event>> eventListener = new ArrayList<>();


    private final List<Consumer<MessageEvent>> messageListener = new ArrayList<>();


    public void setMessageListener(Consumer<MessageEvent> listener) {
        messageListener.add(listener);
    }

    public void setEventListener(Consumer<Event> listener) {
        eventListener.add(listener);
    }


    // 自定义 Configurator 添加 Header
    public static class MyConfigurator extends ClientEndpointConfig.Configurator {
        @Override
        public void beforeRequest(Map<String, List<String>> headers) {
            headers.put("Authorization", List.of("Bearer "+token));
        }
    }


    public NapCatMessageClient(URI endpointURI,String token) {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            NapCatMessageClient.token = token;
            container.connectToServer(this, endpointURI);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        log.info("Connected to napcat server");
    }

    @OnMessage
    public void onMessage(String message) {
        try {
            log.info("receive message: {}", message);
            Event event = JSONUtil.toBean(message, Event.class);
            if ("message".equals(event.getPost_type())) {
                MessageEvent messageEvent = JSONUtil.toBean(message, MessageEvent.class);
                if("group".equals(messageEvent.getMessage_type())){
                    GroupMessageEvent groupMessageEvent = JSONUtil.toBean(message, GroupMessageEvent.class);
                    groupMessageEvent.setClient(this);
                    eventListener.forEach(listener->listener.accept(groupMessageEvent));
                }else if("private".equals(messageEvent.getMessage_type())){
                    PrivateMessageEvent privateMessageEvent = JSONUtil.toBean(message, PrivateMessageEvent.class);
                    privateMessageEvent.setClient(this);
                    eventListener.forEach(listener->listener.accept(privateMessageEvent));
                } else {
                    event.setClient(this);
                    eventListener.forEach(listener -> listener.accept(event));
                }
                messageListener.forEach(listener -> listener.accept(messageEvent));
            }
            //其他事件，需要实现类继承Event
            else if ("meta_event".equals(event.getPost_type())){
                MetaEvent metaEvent = JSONUtil.toBean(message, MetaEvent.class);
                metaEvent.setClient(this);
                eventListener.forEach(listener->listener.accept(metaEvent));
            }else {
                event.setClient(this);
                eventListener.forEach(listener->listener.accept(event));
            }
        }catch (Exception e){
            log.error("receive message error",e);
        }
    }

    public <T extends Event> Flux<T> subscribeOn(Class<T> clazz){
        return Flux.create(fluxSink -> {
            eventListener.add(event -> {
                if (clazz.isInstance(event)) {
                    fluxSink.next(clazz.cast(event));
                }
            });
        });
    }

    @OnClose
    public void onClose() {
        log.info("Napcat Connection closed");
    }

    @OnError
    public void onError(Throwable t) {
        log.error("Napcat Connection error",t);
    }

    public void sendCommand(String message) {
        if (session != null && session.isOpen()) {
            log.info("send message: {}", message);
            session.getAsyncRemote().sendText(message);
        }
    }

    public void sendGroupMessage(Long groupId,List<Message> message){
        MessageRequest messageRequest = new MessageRequest();
        messageRequest.setAction("send_group_msg");

        HashMap<String, Object> params = new HashMap<>();
        params.put("message",message);
        params.put("group_id", groupId);
        messageRequest.setParams(params);
        messageRequest.setEcho("1234567890");
        String jsonStr = JSONUtil.toJsonStr(messageRequest);
        sendCommand(jsonStr);
    }
    public void sendPrivateMessage(Long userId,List<Message> message){
        MessageRequest messageRequest = new MessageRequest();
        messageRequest.setAction("send_private_msg");

        HashMap<String, Object> params = new HashMap<>();
        params.put("message",message);
        params.put("user_id", userId);
        messageRequest.setParams(params);
        messageRequest.setEcho("1234567890");
        String jsonStr = JSONUtil.toJsonStr(messageRequest);
        sendCommand(jsonStr);
    }

    public static MessageEvent getMessage(String messageId){
        HashMap<String, Object> params = new HashMap<>();
        params.put("message_id", messageId);
        String resp = HttpUtil.createPost("http://localhost:3000/get_msg")
                .header("Authorization", "Bearer " + token)
                .body(JSONUtil.toJsonStr(params))
                .execute()
                .body();
        String data = JSONUtil.parseObj(resp).getStr("data");
        return JSONUtil.toBean(data, MessageEvent.class);
    }

}
