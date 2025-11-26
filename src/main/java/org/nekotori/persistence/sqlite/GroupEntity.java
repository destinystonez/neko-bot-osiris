package org.nekotori.persistence.sqlite;

import lombok.Data;

@Data
public class GroupEntity {
    private Long groupId;
    private String groupName;
    private boolean isVoiceChat;
    private boolean isBlackList;
    private Integer credits;
    private String nanoApiKey;
    private String features;
}
