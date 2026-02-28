package com.tutorialq.systemdesign.processor.config;

import com.tutorialq.systemdesign.processor.job.AdClickStreamJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration class that wires Flink StreamExecutionEnvironment as a Spring bean
 * and manages the Flink job lifecycle via Spring.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class FlinkJobConfiguration {

    private final FlinkProperties flinkProperties;

    @Bean
    public StreamExecutionEnvironment streamExecutionEnvironment() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Configure parallelism from Spring properties
        env.setParallelism(flinkProperties.getParallelism());

        // Enable checkpointing
        env.enableCheckpointing(flinkProperties.getCheckpointInterval());

        // Configure state backend if needed
        // env.setStateBackend(new HashMapStateBackend());
        // env.getCheckpointConfig().setCheckpointStorage(flinkProperties.getCheckpointDir());

        log.info("Flink StreamExecutionEnvironment configured with parallelism: {}",
                flinkProperties.getParallelism());

        return env;
    }

    /**
     * Start the Flink job when Spring Boot application starts.
     * Use @Profile to control when this runs (e.g., not during tests).
     */
    @Bean
    @Profile("!test")
    public CommandLineRunner flinkJobRunner(
            StreamExecutionEnvironment env,
            AdClickStreamJob streamJob) {
        return args -> {
            log.info("Starting Flink job: {}", flinkProperties.getJobName());
            try {
                streamJob.execute(env);
                log.info("Flink job submitted successfully");
            } catch (Exception e) {
                log.error("Failed to start Flink job", e);
                throw new RuntimeException("Flink job startup failed", e);
            }
        };
    }
}
