package org.nekotori.openai;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ChatRequest {

    List<ChatMessage> messages = new ArrayList<>();

    Boolean stream;

    String model;

    Boolean enable_search;
}