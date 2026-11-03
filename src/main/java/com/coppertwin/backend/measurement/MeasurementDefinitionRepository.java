package com.coppertwin.backend.measurement;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public class MeasurementDefinitionRepository {
    public record Definition(long workshopId,long equipmentId,long sensorId,long parameterId,String dataType,
                             String targetUnit,BigDecimal targetFactor,String unitCategory,int decimalPlaces,
                             BigDecimal normalLow,BigDecimal normalHigh,BigDecimal alarmLow,BigDecimal alarmHigh,Integer samplingSeconds) {}
    private final JdbcTemplate jdbc;
    public MeasurementDefinitionRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public Optional<Definition> find(String sensorCode,String parameterCode) {
        var list=jdbc.query("""
            SELECT w.workshop_id,e.equipment_id,s.sensor_id,p.parameter_id,p.data_type,p.unit_code,u.conversion_factor,u.unit_category,
                   p.decimal_places,p.normal_lower_limit,p.normal_upper_limit,p.alarm_lower_limit,p.alarm_upper_limit,p.sampling_frequency_seconds
            FROM dim_sensor s JOIN dim_equipment e ON e.equipment_id=s.equipment_id
            JOIN dim_workshop w ON w.workshop_id=e.workshop_id
            JOIN dim_sensor_parameter p ON p.sensor_id=s.sensor_id
            LEFT JOIN dim_unit u ON u.unit_code=p.unit_code
            WHERE s.sensor_code=? AND p.parameter_code=? AND s.enabled=TRUE AND p.enabled=TRUE
            """,(rs,n)->new Definition(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getString(5),
                rs.getString(6),rs.getBigDecimal(7),rs.getString(8),rs.getInt(9),rs.getBigDecimal(10),rs.getBigDecimal(11),
                rs.getBigDecimal(12),rs.getBigDecimal(13),(Integer)rs.getObject(14)),sensorCode,parameterCode);
        return list.stream().findFirst();
    }

    public Optional<UnitDefinition> unit(String unitCode) {
        if (unitCode==null || unitCode.isBlank()) return Optional.empty();
        return jdbc.query("SELECT unit_category,conversion_factor FROM dim_unit WHERE unit_code=? AND enabled=TRUE",
                (rs,n)->new UnitDefinition(rs.getString(1),rs.getBigDecimal(2)),unitCode).stream().findFirst();
    }
    public record UnitDefinition(String category,BigDecimal factor) {}
}

