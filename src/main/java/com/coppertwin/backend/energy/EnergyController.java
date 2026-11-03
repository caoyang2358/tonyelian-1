package com.coppertwin.backend.energy;

import com.coppertwin.backend.common.ApiResponse;
import com.coppertwin.backend.energy.EnergyModels.*;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController @RequestMapping("/api/v1")
public class EnergyController {
    private final EnergyService service;
    private final EnergyAggregationService aggregation;
    public EnergyController(EnergyService service,EnergyAggregationService aggregation){this.service=service;this.aggregation=aggregation;}

    @GetMapping("/energy/overview") @Operation(summary="综合能源总览")
    ApiResponse<EnergyOverview> overview(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date){return ApiResponse.ok(service.overview(date));}
    @GetMapping("/energy/workshops/comparison") @Operation(summary="车间能源对比")
    ApiResponse<WorkshopComparison> workshops(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date){return ApiResponse.ok(service.workshops(date));}
    @GetMapping("/energy/equipment/ranking") @Operation(summary="设备能耗排行")
    ApiResponse<EquipmentRanking> ranking(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam(required=false) Integer limit){return ApiResponse.ok(service.equipmentRanking(date,limit));}
    @GetMapping("/energy/equipment/benchmark") @Operation(summary="同类设备能效对标")
    ApiResponse<EquipmentRanking> benchmark(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam(required=false) String equipmentCode){return ApiResponse.ok(service.benchmark(date,equipmentCode));}
    @GetMapping("/energy/flow") @Operation(summary="能源总分表树和实时流向")
    ApiResponse<EnergyFlow> flow(@RequestParam(defaultValue="ELECTRICITY") String energyCode){return ApiResponse.ok(service.flow(energyCode));}
    @GetMapping("/energy/balance") @Operation(summary="能源总分表平衡")
    ApiResponse<EnergyBalance> balance(@RequestParam(defaultValue="ELECTRICITY") String energyCode,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date){return ApiResponse.ok(service.balance(energyCode,date));}
    @GetMapping("/energy/workshops/{id}/dashboard") @Operation(summary="车间能源看板")
    ApiResponse<WorkshopDashboard> dashboard(@PathVariable long id,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date){return ApiResponse.ok(service.dashboard(id,date));}
    @PostMapping("/energy/aggregations/{date}:rebuild") @Operation(summary="按测点原始数据重算能源小时和日汇总")
    ApiResponse<AggregationResult> rebuild(@PathVariable @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date){return ApiResponse.ok(aggregation.rebuildDay(date));}
    @GetMapping("/equipment/{id}/energy") @Operation(summary="查询设备独立计量能耗")
    ApiResponse<EquipmentEnergy> equipmentEnergy(@PathVariable long id,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date){return ApiResponse.ok(service.equipmentEnergy(id,date));}
}
