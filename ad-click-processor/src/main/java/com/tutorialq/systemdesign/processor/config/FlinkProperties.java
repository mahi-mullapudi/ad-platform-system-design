package com.tutorialq.systemdesign.processor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "flink")
@Data
public class FlinkProperties {

    private String jobName = "ad-click-processor";
    private int parallelism = 4;
    private String checkpointDir = "file:///tmp/flink-checkpoints";
    private long checkpointInterval = 60000L; // 1 minute

    private KafkaProperties kafka = new KafkaProperties();
    private PinotProperties pinot = new PinotProperties();

    @Data
    public static class KafkaProperties {
        private String bootstrapServers = "localhost:9092";
        private String schemaRegistryUrl = "http://localhost:8081";
        private String sourceTopic = "ad-click-events";
        private String sinkTopic = "ad-click-aggregations";
        private String consumerGroup = "ad-click-processor";
    }

    @Data
    public static class PinotProperties {
        private String brokerUrl = "localhost:8000";
        private String tableName = "ad_click_analytics";
        private int batchSize = 1000;
        private long batchIntervalMs = 10000L; // 10 seconds
    }
}
