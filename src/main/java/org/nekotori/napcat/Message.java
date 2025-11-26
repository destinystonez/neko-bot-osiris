package org.nekotori.napcat;

import lombok.Data;

import java.util.Map;

@Data
public class Message {

    private String type;

    private Map<String,String> data;
}
