package org.nekotori.napcat;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MessageBuilder {

    private final List<Message> messages = new ArrayList<>();

    public static MessageBuilder builder() {
        return new MessageBuilder();
    }

    public MessageBuilder plainText(String text) {
        Message message = new Message();
        message.setType("text");
        message.setData(Map.of("text", text));
        messages.add(message);
        return this;
    }

    public MessageBuilder record(String fileUrl){
        Message message = new Message();
        message.setType("record");
        message.setData(Map.of("file", fileUrl));
        messages.add(message);
        return this;
    }

    public MessageBuilder image(String url) {
        Message message = new Message();
        message.setType("image");
        message.setData(Map.of("url", url));
        messages.add(message);
        return this;
    }

    public MessageBuilder at(String qq) {
        Message message = new Message();
        message.setType("at");
        message.setData(Map.of("qq", qq));
        messages.add(message);
        return this;
    }

    public MessageBuilder at(Long qq){
        return at(String.valueOf(qq));
    }

    public MessageBuilder reply(String id) {
        Message message = new Message();
        message.setType("reply");
        message.setData(Map.of("id", id));
        messages.add(message);
        return this;
    }

    public List<Message> build() {
        return messages;
    }

    public static List<Message> parse(MessageEvent event){
        List<Message> messages = new ArrayList<>();
        List<MessageEvent.MessageElement> message = event.getMessage();
        message.forEach(messageElement -> {
            if (messageElement.getType().equals("reply")){
                MessageEvent innerMessage = NapCatMessageClient.getMessage(messageElement.getData().getId());
                innerMessage.getMessage().forEach(messageElement1 -> {
                    Message message1 = new Message();
                    message1.setType(messageElement1.getType());
                    message1.setData(JSONUtil.toBean(JSONUtil.toJsonStr(messageElement1.getData()),
                            new TypeReference<Map<String,String>>(){},true));
                    messages.add(message1);
                });
            }else {
                Message message1 = new Message();
                message1.setType(messageElement.getType());
                message1.setData(JSONUtil.toBean(JSONUtil.toJsonStr(messageElement.getData()),
                        new TypeReference<Map<String,String>>(){},true));
                messages.add(message1);
            }
        });
        return messages;
    }

}
