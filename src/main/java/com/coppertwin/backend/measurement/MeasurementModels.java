package com.coppertwin.backend.measurement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class MeasurementModels {
    private MeasurementModels() {}

    public record ReadingBatchRequest(
            @NotBlank @Size(max=64) String sourceSystem,
            @NotEmpty @Size(max=5000) List<@Valid ReadingInput> readings) {}
    public record ReadingInput(
            @Size(max=128) String sourceRecordId,
            @NotBlank @Size(max=128) String sensorCode,
            @NotBlank @Size(max=64) String parameterCode,
            @NotNull OffsetDateTime collectTime,
            BigDecimal valueNum,
            @Size(max=500) String valueText,
            @Size(max=32) String sourceUnit,
            @Size(max=32) String qualityCode) {}
    public record IngestItemResult(int index,String sourceRecordId,String status,String errorCode,String message) {}
    public record BatchResult(int receivedCount,int acceptedCount,int failedCount,String status,List<IngestItemResult> items) {}

    public record SensorView(long sensorId,String sensorCode,String sensorName,String sensorType,String dataSource,
                             long equipmentId,String equipmentCode,String equipmentName,long workshopId,String workshopCode,
                             List<ParameterView> parameters) {}
    public record ParameterView(long parameterId,String parameterCode,String parameterName,String dataType,String unitCode,
                                int decimalPlaces,BigDecimal normalLowerLimit,BigDecimal normalUpperLimit,
                                BigDecimal alarmLowerLimit,BigDecimal alarmUpperLimit,Integer samplingFrequencySeconds) {}

    public record LatestValue(String processCode,String sensorCode,String sensorName,String parameterCode,String parameterName,
                              BigDecimal value,String valueText,String unitCode,OffsetDateTime collectTime,
                              String quality,String dataStatus,String limitStatus,String displayStatus) {}
    public record LatestResult(List<LatestValue> items,OffsetDateTime dataAsOfTime,OffsetDateTime refreshTime,String dataStatus) {}
    public record TrendPoint(OffsetDateTime time,BigDecimal value,String quality) {}
    public record TrendSeries(String sensorCode,String parameterCode,String name,String unitCode,String granularity,
                              List<TrendPoint> points,OffsetDateTime dataAsOfTime,OffsetDateTime refreshTime,String dataStatus) {}
    public record TrendOption(String sensorCode,String parameterCode,String name,String unitCode,int displayOrder) {}
    public record ProcessNode(long processId,String processCode,String processName,int sequenceNo,LatestValue primaryMeasurement,
                              List<TrendOption> trendOptions,String displayStatus) {}
    public record WorkshopFlow(long workshopId,String workshopCode,String workshopName,List<ProcessNode> processes) {}
    public record ProcessOverview(List<WorkshopFlow> workshops,OffsetDateTime dataAsOfTime,OffsetDateTime refreshTime,String dataStatus) {}
}
