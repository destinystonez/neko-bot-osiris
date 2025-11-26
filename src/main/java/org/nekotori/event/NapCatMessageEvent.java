package org.nekotori.event;

import org.nekotori.napcat.MessageEvent;
import reactor.core.publisher.Flux;

import java.util.List;

public class NapCatMessageEvent<T extends MessageEvent> extends NekoMessageEvent<T>{

    public static <E extends MessageEvent> NekoMessageEvent<E> of(Flux<E> flux){
        var eMessageEvent = new NapCatMessageEvent<E>();
        eMessageEvent.flux = flux;
        return eMessageEvent;
    }

    @Override
    public NekoMessageEvent<T> onCommand(String command) {
        this.flux = flux.filter(event ->{
            String rawMessage = event.getRaw_message();
            return rawMessage.contains("-"+command +" ");
        });
        return this;
    }

    @Override
    public NekoMessageEvent<T> onAt() {
        this.flux = flux.filter(event -> {
            List<MessageEvent.MessageElement> message = event.getMessage();
            for (MessageEvent.MessageElement element : message) {
                if (element.getType().equals("at")) {
                    var qq = element.getData().getQq();
                    if (qq.equals("" +event.getSelf_id())) {
                        return true;
                    }
                }
            }
            return false;
        });
        return this;
    }

    @Override
    public NekoMessageEvent<T> onVerify(Class<?> clazz) {
        this.flux = flux.filter(event -> event.getClass() == clazz);
        return this;
    }
}
