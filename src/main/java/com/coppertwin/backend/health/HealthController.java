package com.coppertwin.backend.health;

import com.coppertwin.backend.common.ApiResponse;
import com.coppertwin.backend.common.JdbcTimes;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

@RestController @RequestMapping("/api/v1")
public class HealthController {
    private static final ZoneId SHANGHAI=ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    public HealthController(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @GetMapping("/health/live") public ApiResponse<Map<String,Object>> live() {
        return ApiResponse.ok(Map.of("status","UP","time",OffsetDateTime.now(SHANGHAI)));
    }
    @GetMapping("/health/ready") public ResponseEntity<ApiResponse<Map<String,Object>>> ready() {
        try {
            jdbc.queryForObject("SELECT 1",Integer.class);
            return ResponseEntity.ok(ApiResponse.ok(Map.of("status","UP","databaseStatus","UP")));
        } catch (Exception ex) {
            return ResponseEntity.status(503).body(ApiResponse.error("DEPENDENCY_UNAVAILABLE","数据库不可用",null));
        }
    }
    @GetMapping("/system/data-service-status") public ApiResponse<Map<String,Object>> status() {
        OffsetDateTime now=OffsetDateTime.now(SHANGHAI);
        OffsetDateTime last=jdbc.query("SELECT MAX(latest_collect_time) last_time FROM ads_parameter_monitor", rs -> {
            if (!rs.next() || rs.getObject("last_time") == null) return null;
            return JdbcTimes.offsetDateTime(rs,"last_time");
        });
        var data=new java.util.LinkedHashMap<String,Object>();
        data.put("status","ONLINE"); data.put("databaseStatus","UP"); data.put("onlineRate",null);
        data.put("lastDataTime",last); data.put("dataAsOfTime",last); data.put("refreshTime",now);
        return ApiResponse.ok(data);
    }
}

