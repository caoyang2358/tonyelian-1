package com.coppertwin.backend.energy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class EnergyModels {
    private EnergyModels() {}

    public record EnergyMetric(String energyCode,String energyName,BigDecimal consumption,String unitCode,
                               BigDecimal cost,BigDecimal tceAmount,String qualityStatus) {}
    public record CompositionItem(String energyCode,String energyName,BigDecimal tceAmount,BigDecimal percentage) {}
    public record EnergyTrendPoint(OffsetDateTime time,BigDecimal electricity,BigDecimal water,BigDecimal coalGas) {}
    public record PeakFlatValley(BigDecimal peak,BigDecimal flat,BigDecimal valley) {}
    public record EnergyOverview(LocalDate statisticDate,List<EnergyMetric> metrics,BigDecimal totalCost,BigDecimal totalTce,
                                 BigDecimal productionQuantity,BigDecimal unitElectricityConsumption,List<CompositionItem> composition,
                                 PeakFlatValley peakFlatValley,List<EnergyTrendPoint> trend,OffsetDateTime refreshTime) {}

    public record WorkshopEnergy(long workshopId,String workshopCode,String workshopName,BigDecimal productionQuantity,
                                 BigDecimal electricity,BigDecimal water,BigDecimal coalGas,BigDecimal unitElectricityConsumption,
                                 BigDecimal totalCost,BigDecimal totalTce,String productionStage,String qualityStatus) {}
    public record WorkshopComparison(LocalDate statisticDate,List<WorkshopEnergy> workshops,OffsetDateTime refreshTime) {}

    public record EquipmentEnergy(long equipmentId,String equipmentCode,String equipmentName,String workshopName,
                                  BigDecimal currentLoad,BigDecimal consumption,BigDecimal unitConsumption,
                                  BigDecimal productionQuantity,BigDecimal cost,String unitCode,String qualityStatus,String benchmark) {}
    public record EquipmentRanking(LocalDate statisticDate,List<EquipmentEnergy> items,OffsetDateTime refreshTime) {}

    public record FlowNode(long meterId,String meterCode,String meterName,String scopeType,Long workshopId,Long equipmentId,
                           BigDecimal realtimeValue,String realtimeUnit,String dataStatus,List<FlowNode> children) {}
    public record EnergyFlow(String energyCode,String energyName,List<FlowNode> roots,OffsetDateTime refreshTime) {}
    public record BalanceItem(long meterId,String meterCode,String meterName,BigDecimal input,BigDecimal childConsumption,
                              BigDecimal configuredLoss,BigDecimal difference,BigDecimal differenceRate,String status) {}
    public record EnergyBalance(String energyCode,List<BalanceItem> items,BigDecimal collectionOnlineRate,OffsetDateTime refreshTime) {}

    public record WorkshopDashboard(long workshopId,String workshopCode,String workshopName,LocalDate statisticDate,
                                    List<EnergyMetric> metrics,BigDecimal currentElectricityLoad,BigDecimal productionQuantity,
                                    BigDecimal unitElectricityConsumption,BigDecimal totalCost,BigDecimal totalTce,
                                    List<EquipmentEnergy>重点设备,OffsetDateTime refreshTime) {}

    public record AggregationResult(LocalDate statisticDate,int meterCount,int goodCount,int partialCount,int noDataCount) {}
}
