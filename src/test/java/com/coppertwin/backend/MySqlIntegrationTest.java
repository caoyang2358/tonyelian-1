package com.coppertwin.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MySQL 5.7/8.0 integration test. It is enabled when MYSQL_TEST_URL is set,
 * for example: DB_URL=jdbc:mysql://127.0.0.1:3306/copper_twin ... mvn test.
 */
@SpringBootTest(properties = {"spring.profiles.active=mysql", "spring.datasource.url=${MYSQL_TEST_URL}", "spring.datasource.username=${MYSQL_TEST_USERNAME:copper_twin}", "spring.datasource.password=${MYSQL_TEST_PASSWORD:}"})
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = ".+")
class MySqlIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void schemaAndSeedAreAvailable() {
        Integer workshops=jdbc.queryForObject("SELECT COUNT(*) FROM dim_workshop",Integer.class);
        Integer sensors=jdbc.queryForObject("SELECT COUNT(*) FROM dim_sensor",Integer.class);
        Integer energy=jdbc.queryForObject("SELECT COUNT(*) FROM dim_energy_type",Integer.class);
        assertNotNull(workshops); assertTrue(workshops>=2);
        assertNotNull(sensors); assertTrue(sensors>=10);
        assertEquals(3,energy);
    }

    @Test
    void processMeasurementsAndTrendBindingsAreAvailable() {
        Integer primary = jdbc.queryForObject("SELECT COUNT(*) FROM dim_sensor_parameter WHERE process_id IS NOT NULL AND primary_display=1", Integer.class);
        Integer trends = jdbc.queryForObject("SELECT COUNT(*) FROM dim_sensor_parameter WHERE process_id IS NOT NULL AND trend_display=1", Integer.class);
        assertEquals(8, primary);
        assertEquals(6, trends);
    }
    @Test
    void mysqlTimestampCanBeReadAndIsNonNull() {
        Timestamp latest=jdbc.queryForObject("SELECT MAX(latest_collect_time) FROM ads_parameter_monitor",Timestamp.class);
        assertNotNull(latest);
    }

    @Test
    void idempotencyUniqueKeyExists() {
        Integer duplicates=jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT source_system,source_record_id,COUNT(*) c FROM ods_sensor_data WHERE source_record_id IS NOT NULL GROUP BY source_system,source_record_id HAVING COUNT(*)>1) x",Integer.class);
        assertEquals(0,duplicates);
    }
}
