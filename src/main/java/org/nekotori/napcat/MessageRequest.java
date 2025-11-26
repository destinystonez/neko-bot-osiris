package org.nekotori.napcat;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class MessageRequest {

    private String action;

    private String echo;

    private Map<String,Object> params;
}
