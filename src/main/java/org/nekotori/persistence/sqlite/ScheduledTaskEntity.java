package org.nekotori.persistence.sqlite;

import lombok.Data;

@Data
public class ScheduledTaskEntity {
    private Integer id;
    private Long groupId;
    private Long senderId;
    private String taskInfo;
    private Long time;
}