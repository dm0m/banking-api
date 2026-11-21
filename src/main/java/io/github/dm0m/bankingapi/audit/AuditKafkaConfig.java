package io.github.dm0m.bankingapi.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import com.fasterxml.jackson.core.JsonProcessingException;

@Configuration
public class AuditKafkaConfig {

    /**
     * Picked up by Spring Boot for every @KafkaListener: a failed record is retried twice, one second apart,
     * then published unchanged to {@code <topic>-dlt}. A message that isn't valid JSON for the contract
     * fails the same way every time, so it goes to the dead-letter topic straight away.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        // Spring Boot's template: its String serializer republishes the raw message unchanged
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(kafkaTemplate), new FixedBackOff(1000L, 2L));
        errorHandler.addNotRetryableExceptions(JsonProcessingException.class);
        return errorHandler;
    }
}
