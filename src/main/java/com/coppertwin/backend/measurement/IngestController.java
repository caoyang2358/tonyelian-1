package com.coppertwin.backend.measurement;

import com.coppertwin.backend.common.ApiResponse;
import com.coppertwin.backend.measurement.MeasurementModels.BatchResult;
import com.coppertwin.backend.measurement.MeasurementModels.ReadingBatchRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/ingest")
public class IngestController {
    private final IngestService service;
    public IngestController(IngestService service) { this.service=service; }
    @PostMapping("/sensor-readings:batch")
    @Operation(summary="批量接收测点原始读数",description="单批最多5000条，直接保存原始数据并刷新最新快照")
    public ApiResponse<BatchResult> ingest(@Valid @RequestBody ReadingBatchRequest request) { return ApiResponse.ok(service.ingest(request)); }
}
