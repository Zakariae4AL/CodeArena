package dev.codearena.realtime.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

class SubscribeOnlyInterceptorTest {

    private final SubscribeOnlyInterceptor interceptor = new SubscribeOnlyInterceptor();

    private static Message<byte[]> frame(StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void allowsOnlyKnownTopics() {
        assertThat(SubscribeOnlyInterceptor.isAllowed("/topic/submissions/" + UUID.randomUUID())).isTrue();
        assertThat(SubscribeOnlyInterceptor.isAllowed("/topic/contests/12/scoreboard")).isTrue();
        assertThat(SubscribeOnlyInterceptor.isAllowed("/topic/submissions/../../admin")).isFalse();
        assertThat(SubscribeOnlyInterceptor.isAllowed("/topic/**")).isFalse();
        assertThat(SubscribeOnlyInterceptor.isAllowed(null)).isFalse();
    }

    @Test
    void clientsCannotPublish() {
        String topic = "/topic/submissions/" + UUID.randomUUID();
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, topic), null))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/anything"), null))
                .isInstanceOf(MessageDeliveryException.class);
        assertThat(interceptor.preSend(frame(StompCommand.SUBSCRIBE, topic), null)).isNotNull();
    }
}
