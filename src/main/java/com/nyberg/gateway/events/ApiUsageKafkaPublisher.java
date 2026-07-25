package com.nyberg.gateway.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "byz.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class ApiUsageKafkaPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${byz.kafka.topics.api-usage:byz.api.usage}")
    private String topic;

    /**
     * Best-effort publish. Never throws — gateway request path must not fail on Kafka.
     */
    public void publishAsync(ApiUsageEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            String key = event.tokenId() != null ? event.tokenId() : event.eventId();
            kafkaTemplate.send(topic, key, json).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.debug("Failed to publish {} tokenId={}: {}",
                            event.type(), event.tokenId(), ex.toString());
                }
            });
        } catch (JsonProcessingException e) {
            log.debug("Failed to serialize {}: {}", event.type(), e.toString());
        } catch (RuntimeException e) {
            log.debug("Kafka publish skipped for {}: {}", event.type(), e.toString());
        }
    }
}
