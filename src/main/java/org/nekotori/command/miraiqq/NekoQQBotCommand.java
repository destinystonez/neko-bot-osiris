package org.nekotori.command.miraiqq;

import net.mamoe.mirai.event.Event;
import net.mamoe.mirai.event.events.MessageEvent;
import org.nekotori.bot.NekoBot;

public interface NekoQQBotCommand {

    void handle(NekoBot<Event, MessageEvent> bot);
}
