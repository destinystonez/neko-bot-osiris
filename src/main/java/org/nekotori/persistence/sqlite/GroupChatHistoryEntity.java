package org.nekotori.persistence.sqlite;

import lombok.Data;

@Data
public class GroupChatHistoryEntity {
    private Integer id;
    private Long groupId;
    private Long userId;
    private String message;
    private Long time;
}
