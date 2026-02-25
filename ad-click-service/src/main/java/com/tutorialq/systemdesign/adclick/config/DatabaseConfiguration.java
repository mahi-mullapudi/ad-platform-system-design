package com.tutorialq.systemdesign.adclick.config;

import io.r2dbc.spi.ConnectionFactory;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;

/**
 * Configuration for R2DBC and Flyway.
 * Flyway uses JDBC for migrations at startup, R2DBC for reactive runtime access.
 */
@Configuration
@EnableR2dbcAuditing
@EnableConfigurationProperties(FlywayProperties.class)
public class DatabaseConfiguration {

    /**
     * Initialize Flyway migrations on startup.
     * Flyway runs on JDBC connection pool, separate from R2DBC.
     */
    @Bean(initMethod = "migrate")
    public Flyway flyway(FlywayProperties flywayProperties) {
        return Flyway.configure()
                .dataSource(
                        flywayProperties.getUrl(),
                        flywayProperties.getUser(),
                        flywayProperties.getPassword()
                )
                .locations(flywayProperties.getLocations().toArray(new String[0]))
                .baselineOnMigrate(flywayProperties.isBaselineOnMigrate())
                .load();
    }
}
