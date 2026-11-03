package com.coppertwin.backend.equipment;

import com.coppertwin.backend.measurement.MeasurementModels.TrendSeries;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class EquipmentModels {
    private EquipmentModels() {}

    public record EquipmentSummary(int totalCount,int runningCount,int offlineCount,int warningCount,int alarmCount,
                                   BigDecimal collectionOnlineRate,OffsetDateTime refreshTime) {}
    public record EquipmentItem(long equipmentId,String equipmentCode,String equipmentName,long workshopId,
                                String workshopCode,String workshopName,String responsiblePerson,String status,
                                int sensorCount,int onlineSensorCount,int warningCount,int alarmCount,
                                OffsetDateTime latestCollectTime) {}
    public record EquipmentPage(List<EquipmentItem> items,int page,int pageSize,long total) {}
    public record EquipmentDetail(long equipmentId,String equipmentCode,String equipmentName,long workshopId,
                                  String workshopCode,String workshopName,String processName,String responsiblePerson,
                                  String status,int sensorCount,int onlineSensorCount,int warningCount,int alarmCount,
                                  OffsetDateTime latestCollectTime) {}
    public record EquipmentParameter(long sensorId,String sensorCode,String sensorName,String sensorType,
                                     long parameterId,String parameterCode,String parameterName,BigDecimal value,
                                     String valueText,String unitCode,int decimalPlaces,OffsetDateTime collectTime,
                                     String quality,String dataStatus,String limitStatus,String displayStatus,
                                     int positionX,int positionY) {}
    public record LatestParameters(List<EquipmentParameter> items,OffsetDateTime dataAsOfTime,
                                   OffsetDateTime refreshTime,String dataStatus) {}
    public record EquipmentTrends(List<TrendSeries> series,OffsetDateTime refreshTime) {}
}
