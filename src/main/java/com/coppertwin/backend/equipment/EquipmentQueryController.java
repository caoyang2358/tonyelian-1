package com.coppertwin.backend.equipment;

import com.coppertwin.backend.common.ApiResponse;
import com.coppertwin.backend.equipment.EquipmentModels.*;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

@RestController @RequestMapping("/api/v1/equipment")
public class EquipmentQueryController {
    private final EquipmentQueryService service;
    public EquipmentQueryController(EquipmentQueryService service) { this.service=service; }

    @GetMapping("/summary") @Operation(summary="查询设备状态统计")
    ApiResponse<EquipmentSummary> summary() { return ApiResponse.ok(service.summary()); }

    @GetMapping @Operation(summary="分页查询设备列表")
    ApiResponse<EquipmentPage> list(@RequestParam(required=false) String workshopCode,@RequestParam(required=false) String status,
                                    @RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int page,
                                    @RequestParam(defaultValue="20") int pageSize) {
        return ApiResponse.ok(service.list(workshopCode,status,keyword,page,pageSize));
    }

    @GetMapping("/{id}") @Operation(summary="查询设备基础信息和当前状态")
    ApiResponse<EquipmentDetail> detail(@PathVariable long id) { return ApiResponse.ok(service.detail(id)); }

    @GetMapping("/{id}/parameters/latest") @Operation(summary="查询设备测点最新值")
    ApiResponse<LatestParameters> latest(@PathVariable long id) { return ApiResponse.ok(service.latest(id)); }

    @GetMapping("/{id}/parameters/trends") @Operation(summary="查询设备多参数趋势")
    ApiResponse<EquipmentTrends> trends(@PathVariable long id,@RequestParam(required=false) String parameterCodes,
                                        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
                                        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime,
                                        @RequestParam(defaultValue="minute") String granularity) {
        return ApiResponse.ok(service.trends(id,csv(parameterCodes),startTime,endTime,granularity));
    }

    private static List<String> csv(String value) { return value==null||value.isBlank()?List.of():Arrays.stream(value.split(",")).map(String::trim).filter(s->!s.isBlank()).toList(); }
}
