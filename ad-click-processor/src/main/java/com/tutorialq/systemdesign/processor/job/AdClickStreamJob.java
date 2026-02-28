package com.tutorialq.systemdesign.processor.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorialq.systemdesign.processor.config.FlinkProperties;
import com.tutorialq.systemdesign.processor.model.AdClickEventRecord;
import com.tutorialq.systemdesign.processor.model.ClickAggregation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Spring-managed Flink streaming job that processes ad click events.
 * Reads JSON events from Kafka, performs windowed aggregations,
 * and sinks results to a Kafka aggregations topic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdClickStreamJob {

    private final FlinkProperties flinkProperties;

    public void execute(StreamExecutionEnvironment env) throws Exception {

        // Configure Kafka source reading raw JSON strings
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(flinkProperties.getKafka().getBootstrapServers())
                .setTopics(flinkProperties.getKafka().getSourceTopic())
                .setGroupId(flinkProperties.getKafka().getConsumerGroup())
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        ObjectMapper objectMapper = new ObjectMapper();

        // Parse JSON strings into AdClickEventRecord POJOs
        DataStream<AdClickEventRecord> eventStream = env
                .fromSource(kafkaSource,
                        WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                                .withIdleness(Duration.ofMinutes(1)),
                        "Kafka Source")
                .map((MapFunction<String, AdClickEventRecord>) json ->
                        objectMapper.readValue(json, AdClickEventRecord.class))
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<AdClickEventRecord>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                                .withTimestampAssigner((SerializableTimestampAssigner<AdClickEventRecord>)
                                        (event, recordTimestamp) -> event.getTimestamp())
                                .withIdleness(Duration.ofMinutes(1))
                );

        // Windowed aggregation: count events per campaignId+eventType in 1-minute tumbling windows
        DataStream<ClickAggregation> aggregations = eventStream
                .keyBy(event -> event.getCampaignId() + "|" + event.getEventType())
                .window(TumblingEventTimeWindows.of(Time.minutes(1)))
                .aggregate(new ClickCountAggregator(), new ClickCountWindowFunction());

        // Log aggregations for observability
        aggregations.map((MapFunction<ClickAggregation, ClickAggregation>) agg -> {
            log.info("Aggregation: {}", agg);
            return agg;
        });

        // Kafka sink for aggregation results
        KafkaSink<String> kafkaSink = KafkaSink.<String>builder()
                .setBootstrapServers(flinkProperties.getKafka().getBootstrapServers())
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(flinkProperties.getKafka().getSinkTopic())
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .build();

        aggregations
                .map((MapFunction<ClickAggregation, String>) agg -> objectMapper.writeValueAsString(agg))
                .sinkTo(kafkaSink);

        // Execute the job (non-blocking in embedded mode)
        env.executeAsync(flinkProperties.getJobName());

        log.info("Flink job '{}' started", flinkProperties.getJobName());
    }
}
