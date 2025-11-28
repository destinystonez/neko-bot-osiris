package org.nekotori.openai;

import lombok.Data;

@Data
public class OllamaResponse {
    //{
    //    "model": "deepseek-r1:8b",
    //    "created_at": "2025-11-26T02:34:00.3564742Z",
    //    "message": {
    //        "role": "assistant",
    //        "content": ""
    //    },
    //    "done": true,
    //    "done_reason": "stop",
    //    "total_duration": 1006004500,
    //    "load_duration": 118426600,
    //    "prompt_eval_count": 8,
    //    "prompt_eval_duration": 258066900,
    //    "eval_count": 13,
    //    "eval_duration": 626903300
    //}

    private String model;

    private String created_at;

    private ChatMessage message;

    private boolean done;

    private String done_reason;

    private long total_duration;

    private long load_duration;

    private long prompt_eval_count;

    private long prompt_eval_duration;

    private long eval_count;

    private long eval_duration;
}
