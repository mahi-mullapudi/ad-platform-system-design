package com.tutorialq.systemdesign.adclick.config;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration with JSON serialization.
 * Merges Spring Boot Kafka properties (including SASL/SSL for Azure Event Hubs)
 * with explicit producer settings.
 */
@Configuration
public class KafkaProducerConfiguration {

    private final KafkaProperties kafkaProperties;

    public KafkaProducerConfiguration(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Propagate SASL/SSL properties from Spring config (required for Azure Event Hubs)
        Map<String, String> springProps = kafkaProperties.getProperties();
        if (springProps.containsKey("security.protocol")) {
            configProps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, springProps.get("security.protocol"));
        }
        if (springProps.containsKey("sasl.mechanism")) {
            configProps.put(SaslConfigs.SASL_MECHANISM, springProps.get("sasl.mechanism"));
        }
        if (springProps.containsKey("sasl.jaas.config")) {
            configProps.put(SaslConfigs.SASL_JAAS_CONFIG, springProps.get("sasl.jaas.config"));
        }

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
