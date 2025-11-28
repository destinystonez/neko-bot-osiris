package org.nekotori.openai;

import lombok.Data;

@Data
public class AnalysisMessage {
    private String user;

    private Long userId;

    private String content;
}
