package org.nekotori.napcat;


import lombok.Data;

@Data
public class Event {

    private long time;
    private long self_id;
    private String post_type;
    private String meta_event_type;
    private String sub_type;

    private NapCatMessageClient client;
}
