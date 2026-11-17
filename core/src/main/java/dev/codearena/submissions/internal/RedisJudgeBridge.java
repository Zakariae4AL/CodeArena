package dev.codearena.submissions.internal;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis Streams bridge to the judge workers: jobs go out on one stream, results come
 * back on another, read with a consumer group and acknowledged after they are applied.
 */
@Component
@ConditionalOnProperty(name = "codearena.judge.queue", havingValue = "redis", matchIfMissing = true)
class RedisJudgeBridge implements JudgeQueue, SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(RedisJudgeBridge.class);
    private static final long MAX_STREAM_LENGTH = 100_000;

    private final StringRedisTemplate redis;
    private final RedisConnectionFactory connectionFactory;
    private final ObjectMapper mapper;
    private final JudgeResultHandler handler;
    private final JudgeProperties props;
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private volatile boolean running;

    RedisJudgeBridge(StringRedisTemplate redis, RedisConnectionFactory connectionFactory, ObjectMapper mapper,
                     JudgeResultHandler handler, JudgeProperties props) {
        this.redis = redis;
        this.connectionFactory = connectionFactory;
        this.mapper = mapper;
        this.handler = handler;
        this.props = props;
    }

    @Override
    public void send(JudgeProtocol.Job job) {
        String payload = mapper.writeValueAsString(job);
        redis.opsForStream().add(StreamRecords.newRecord().in(props.jobsStream()).ofMap(Map.of("payload", payload)));
        redis.opsForStream().trim(props.jobsStream(), MAX_STREAM_LENGTH, true);
    }

    @Override
    public void start() {
        createGroup(props.resultsStream(), props.group());
        drainPending();
        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofSeconds(1))
                .batchSize(20)
                .build();
        container = StreamMessageListenerContainer.create(connectionFactory, options);
        var request = StreamMessageListenerContainer.StreamReadRequest
                .builder(StreamOffset.create(props.resultsStream(), ReadOffset.lastConsumed()))
                .consumer(Consumer.from(props.group(), props.consumerName()))
                .autoAcknowledge(false)
                .cancelOnError(error -> false)
                .errorHandler(error -> log.warn("Error while reading judge results: {}", error.getMessage()))
                .build();
        container.register(request, record -> apply(record.getId().getValue(), record.getValue().get("payload")));
        container.start();
        running = true;
        log.info("Listening for judge results on '{}' as '{}'", props.resultsStream(), props.consumerName());
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void apply(String recordId, String payload) {
        try {
            handler.handle(mapper.readValue(payload, JudgeProtocol.Result.class));
        } catch (RuntimeException e) {
            // Left unacknowledged: it is retried at the next start (see drainPending).
            log.error("Could not apply judge result {}", recordId, e);
            return;
        }
        redis.opsForStream().acknowledge(props.resultsStream(), props.group(), recordId);
    }

    /** Re-applies results this instance received but did not acknowledge before a restart. */
    private void drainPending() {
        for (int round = 0; round < 100; round++) {
            List<MapRecord<String, Object, Object>> pending = redis.opsForStream().read(
                    Consumer.from(props.group(), props.consumerName()),
                    StreamReadOptions.empty().count(100),
                    StreamOffset.create(props.resultsStream(), ReadOffset.from("0")));
            if (pending == null || pending.isEmpty()) {
                return;
            }
            for (MapRecord<String, Object, Object> record : pending) {
                Object payload = record.getValue().get("payload");
                if (payload == null) {
                    redis.opsForStream().acknowledge(props.resultsStream(), props.group(), record.getId());
                } else {
                    apply(record.getId().getValue(), payload.toString());
                }
            }
        }
    }

    private void createGroup(String stream, String group) {
        try {
            redis.execute((RedisCallback<Object>) connection -> connection.execute("XGROUP",
                    bytes("CREATE"), bytes(stream), bytes(group), bytes("0"), bytes("MKSTREAM")));
        } catch (DataAccessException e) {
            if (!String.valueOf(e.getMostSpecificCause().getMessage()).contains("BUSYGROUP")) {
                throw e;
            }
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
