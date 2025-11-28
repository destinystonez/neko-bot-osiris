package org.nekotori.bot;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.nekotori.config.FileBasedBotConfiguration;
import org.nekotori.event.NapCatMessageEvent;
import org.nekotori.event.NekoMessageEvent;
import org.nekotori.napcat.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

@Slf4j
public class NapcatBot implements NekoBot<Event, MessageEvent>{

    private NapCatMessageClient client;
    @Getter
    private FileBasedBotConfiguration config;

    public NapcatBot(){
        try {
            init("bot/config.yaml");
        }catch (URISyntaxException e){
            log.error("NapcatBot init failed",e);
        }
    }

    private void init(String path) throws URISyntaxException {
        config = FileBasedBotConfiguration.resolveFile(new File(path));
        var uri = new URI(config.getOnebot().getHost());
        client = new NapCatMessageClient(uri, config.getOnebot().getToken());
    }


    @Override
    public <T extends Event> Flux<T> onEvent(Class<T> eventType) {
        return client.subscribeOn(eventType)
                .publishOn(Schedulers.boundedElastic())
                .doOnComplete(()->log.info("NapcatBot onEvent complete"))
                .doOnError(throwable -> log.error("NapcatBot onEvent failed",throwable));
    }

    @Override
    public <T extends MessageEvent> NekoMessageEvent<T> onMessageEvent(Class<T> eventType) {
        return NapCatMessageEvent.of(onEvent(eventType));
    }

    @Override
    public String getId() {
        return config.getQq().getAccount().toString();
    }

}
