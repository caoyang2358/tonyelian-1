package com.coppertwin.backend.measurement;

import com.coppertwin.backend.common.ApiResponse;
import com.coppertwin.backend.measurement.MeasurementModels.*;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1/master")
public class MasterDataController {
    private final MasterDataService service;
    public MasterDataController(MasterDataService service) { this.service=service; }
    @GetMapping("/sensors") @Operation(summary="查询测点及参数定义")
    ApiResponse<List<SensorView>> sensors(@RequestParam(required=false) String workshopCode,@RequestParam(required=false) String processCode,
                                           @RequestParam(required=false) Boolean enabled) { return ApiResponse.ok(service.sensors(workshopCode,processCode,enabled)); }
}
