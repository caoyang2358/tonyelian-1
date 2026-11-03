package com.coppertwin.backend.measurement;

import com.coppertwin.backend.common.ApiResponse;
import com.coppertwin.backend.measurement.MeasurementModels.*;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

@RestController @RequestMapping("/api/v1")
public class MeasurementQueryController {
    private final MeasurementQueryService service;
    public MeasurementQueryController(MeasurementQueryService service) { this.service=service; }

    @GetMapping("/measurements/latest") @Operation(summary="批量查询测点最新标准值")
    ApiResponse<LatestResult> latest(@RequestParam(required=false) String processCode,@RequestParam(required=false) String sensorCodes,
                                     @RequestParam(required=false) String parameterCodes) {
        return ApiResponse.ok(service.latest(processCode,csv(sensorCodes),csv(parameterCodes)));
    }
    @GetMapping("/measurements/trends") @Operation(summary="查询数值测点趋势",description="时间范围左闭右开")
    ApiResponse<TrendSeries> trends(@RequestParam String sensorCode,@RequestParam String parameterCode,
                                    @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
                                    @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime,
                                    @RequestParam(defaultValue="minute") String granularity) {
        return ApiResponse.ok(service.trends(sensorCode,parameterCode,startTime,endTime,granularity));
    }
    @GetMapping("/production/process-overview/measurements") @Operation(summary="查询首屏工艺节点测点数据")
    ApiResponse<ProcessOverview> overview() { return ApiResponse.ok(service.overview()); }
    private static List<String> csv(String value) { return value==null||value.isBlank()?List.of():Arrays.stream(value.split(",")).map(String::trim).filter(s->!s.isBlank()).toList(); }
}
