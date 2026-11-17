package dev.codearena.realtime.internal;

import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * Browsers may only listen. Without this, any client could SEND a fake verdict or
 * scoreboard to a /topic destination and every subscriber would receive it.
 */
class SubscribeOnlyInterceptor implements ChannelInterceptor {

    static final Pattern ALLOWED = Pattern.compile(
            "^/topic/(submissions/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|contests/\\d+/scoreboard)$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.SEND) {
            throw new MessageDeliveryException("Sending messages is not allowed.");
        }
        if (command == StompCommand.SUBSCRIBE && !isAllowed(accessor.getDestination())) {
            throw new MessageDeliveryException("Unknown destination.");
        }
        return message;
    }

    static boolean isAllowed(String destination) {
        return destination != null && ALLOWED.matcher(destination).matches();
    }
}
