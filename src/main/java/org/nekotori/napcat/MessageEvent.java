package org.nekotori.napcat;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.nekotori.event.NekoMessageEvent;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class MessageEvent extends Event{
    private long self_id;
    private long user_id;
    private long time;
    private long message_id;
    private long message_seq;
    private long real_id;
    private String real_seq;
    private String message_type; // group / private
    private Sender sender;
    private String raw_message;
    private int font;
    private String sub_type; // normal / friend / ...
    private List<MessageElement> message;
    private String message_format;
    private String post_type;
    private Long group_id;  // nullable
    private Long target_id; // nullable, 私聊专用
    // Getters & Setters

    @Data
    public static class Sender {
        private long user_id;
        private String nickname;
        private String card;
        private String role; // 群消息中有，私聊中可能缺失

        // Getters & Setters
    }

    @Data
    public static class MessageElement {
        private String type; // text / image / at / reply / ...
        private MessageData data;

        // Getters & Setters
    }

    @Data
    public static class MessageData {
        // image
        private String summary;
        private String file;
        private Integer sub_type;
        private String url;
        private String file_size;

        // reply / at
        private String id;
        private String qq;

        // text
        private String text;

        // Getters & Setters
    }
}
