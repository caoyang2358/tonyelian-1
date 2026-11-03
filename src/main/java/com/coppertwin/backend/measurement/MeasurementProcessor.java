package com.coppertwin.backend.measurement;

import com.coppertwin.backend.measurement.MeasurementDefinitionRepository.Definition;
import com.coppertwin.backend.measurement.MeasurementModels.ReadingInput;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

@Service
public class MeasurementProcessor {
    public record ProcessingResult(boolean snapshotUpdated,String status,String message) {}
    private final JdbcTemplate jdbc;
    private final MeasurementDefinitionRepository definitions;
    private final boolean postgresql;
    public MeasurementProcessor(JdbcTemplate jdbc,MeasurementDefinitionRepository definitions) {
        this.jdbc=jdbc; this.definitions=definitions;
        this.postgresql=Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection ->
                connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql")));
    }

    public ProcessingResult process(Definition d,ReadingInput input) {
        String quality=normalizeQuality(input.qualityCode());
        BigDecimal value=input.valueNum();
        if (value!=null) {
            String sourceUnit=input.sourceUnit()==null || input.sourceUnit().isBlank()?d.targetUnit():input.sourceUnit();
            var source=definitions.unit(sourceUnit);
            if (d.targetUnit()!=null && (source.isEmpty() || !source.get().category().equals(d.unitCategory())))
                return new ProcessingResult(false,"RAW_ONLY","来源单位无法转换为参数标准单位");
            if (source.isPresent() && d.targetFactor()!=null)
                value=value.multiply(source.get().factor()).divide(d.targetFactor(),Math.max(6,d.decimalPlaces()),RoundingMode.HALF_UP);
            value=value.setScale(d.decimalPlaces(),RoundingMode.HALF_UP);
        }
        String limit=limitStatus(d,value);
        OffsetDateTime now=OffsetDateTime.now();
        updateSnapshot(d,input,value,quality,limit,now);
        return new ProcessingResult(true,"ACCEPTED","");
    }

    private void updateSnapshot(Definition d,ReadingInput input,BigDecimal value,String quality,String limit,OffsetDateTime now) {
        int updated=jdbc.update("""
            UPDATE ads_parameter_monitor SET unit_code=?,latest_value=?,latest_value_text=?,quality_status=?,limit_status=?,
              latest_collect_time=?,refresh_time=? WHERE sensor_id=? AND parameter_id=? AND latest_collect_time<=?
            """,d.targetUnit(),value,input.valueText(),quality,limit,input.collectTime(),now,d.sensorId(),d.parameterId(),input.collectTime());
        if (updated==0) {
            int inserted;
            if (postgresql) inserted=jdbc.update("""
                INSERT INTO ads_parameter_monitor(sensor_id,parameter_id,workshop_id,equipment_id,unit_code,latest_value,latest_value_text,
                 quality_status,limit_status,latest_collect_time,refresh_time) VALUES(?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (sensor_id,parameter_id) DO NOTHING
                """,d.sensorId(),d.parameterId(),d.workshopId(),d.equipmentId(),d.targetUnit(),value,input.valueText(),quality,limit,input.collectTime(),now);
            else {
                Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM ads_parameter_monitor WHERE sensor_id=? AND parameter_id=?",
                        Integer.class,d.sensorId(),d.parameterId());
                inserted=count!=null&&count>0?0:jdbc.update("""
                    INSERT INTO ads_parameter_monitor(sensor_id,parameter_id,workshop_id,equipment_id,unit_code,latest_value,latest_value_text,
                     quality_status,limit_status,latest_collect_time,refresh_time) VALUES(?,?,?,?,?,?,?,?,?,?,?)
                    """,d.sensorId(),d.parameterId(),d.workshopId(),d.equipmentId(),d.targetUnit(),value,input.valueText(),quality,limit,input.collectTime(),now);
            }
            if (inserted==0) jdbc.update("""
                UPDATE ads_parameter_monitor SET unit_code=?,latest_value=?,latest_value_text=?,quality_status=?,limit_status=?,
                  latest_collect_time=?,refresh_time=? WHERE sensor_id=? AND parameter_id=? AND latest_collect_time<=?
                """,d.targetUnit(),value,input.valueText(),quality,limit,input.collectTime(),now,d.sensorId(),d.parameterId(),input.collectTime());
        }
    }

    public static String normalizeQuality(String raw) {
        if (raw==null || raw.isBlank()) return "GOOD";
        return switch(raw.trim().toUpperCase()) {
            case "GOOD","VALID","OK","192" -> "GOOD";
            case "UNCERTAIN","QUESTIONABLE","64" -> "UNCERTAIN";
            default -> "BAD";
        };
    }
    private static String limitStatus(Definition d,BigDecimal v) {
        if (v==null) return "UNKNOWN";
        if ((d.alarmLow()!=null&&v.compareTo(d.alarmLow())<0)||(d.alarmHigh()!=null&&v.compareTo(d.alarmHigh())>0)) return "ALARM";
        if ((d.normalLow()!=null&&v.compareTo(d.normalLow())<0)||(d.normalHigh()!=null&&v.compareTo(d.normalHigh())>0)) return "WARNING";
        return "NORMAL";
    }
}
