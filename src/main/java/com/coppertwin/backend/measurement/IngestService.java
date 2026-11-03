package com.coppertwin.backend.measurement;

import com.coppertwin.backend.energy.EnergyAggregationService;
import com.coppertwin.backend.measurement.MeasurementModels.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.LinkedHashSet;

@Service
public class IngestService {
    private final JdbcTemplate jdbc;
    private final MeasurementDefinitionRepository definitions;
    private final MeasurementProcessor processor;
    private final EnergyAggregationService energyAggregation;
    private final boolean postgresql;
    public IngestService(JdbcTemplate jdbc,MeasurementDefinitionRepository definitions,MeasurementProcessor processor,
                         EnergyAggregationService energyAggregation) {
        this.jdbc=jdbc; this.definitions=definitions; this.processor=processor; this.energyAggregation=energyAggregation;
        this.postgresql=Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection ->
                connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql")));
    }

    @Transactional
    public BatchResult ingest(ReadingBatchRequest request) {
        int accepted=0,failed=0,index=0;
        var items=new ArrayList<IngestItemResult>();
        var affectedEnergyDays=new LinkedHashSet<LocalDate>();
        for (ReadingInput input:request.readings()) {
            String validation=validateValue(input);
            if (validation!=null) { failed++; items.add(failure(index,input,"INVALID_VALUE",validation)); index++; continue; }
            var definition=definitions.find(input.sensorCode(),input.parameterCode());
            if (definition.isEmpty()) { failed++; String msg="未知或停用的测点参数"; items.add(failure(index,input,"UNKNOWN_MEASUREMENT",msg)); index++; continue; }
            int inserted=insertRaw(request.sourceSystem(),input);
            if (inserted==0) {
                accepted++;
                items.add(new IngestItemResult(index,input.sourceRecordId(),"ACCEPTED",null,"重复记录已忽略"));
                index++;
                continue;
            }
            var processed=processor.process(definition.get(),input);
            if (processed.snapshotUpdated()) {
                accepted++; items.add(new IngestItemResult(index,input.sourceRecordId(),"ACCEPTED",null,""));
                if (energyAggregation.isEnergySensor(input.sensorCode()))
                    affectedEnergyDays.add(input.collectTime().atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDate());
            } else {
                failed++; items.add(failure(index,input,processed.status(),processed.message()));
            }
            index++;
        }
        affectedEnergyDays.forEach(energyAggregation::rebuildDay);
        String status=failed==0?"COMPLETED":accepted==0?"FAILED":"PARTIAL";
        return new BatchResult(request.readings().size(),accepted,failed,status,items);
    }
    private int insertRaw(String sourceSystem,ReadingInput input) {
        if (!postgresql) {
            Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM ods_sensor_data WHERE source_system=? AND source_record_id=?",
                    Integer.class,sourceSystem,input.sourceRecordId());
            if (count!=null&&count>0) return 0;
        }
        String conflict=postgresql?" ON CONFLICT (source_system,source_record_id) DO NOTHING":"";
        return jdbc.update("""
            INSERT INTO ods_sensor_data(source_system,source_record_id,sensor_code,parameter_code,collect_time,value_num,value_text,source_unit,quality_code)
            VALUES(?,?,?,?,?,?,?,?,?)
            """+conflict,sourceSystem,input.sourceRecordId(),input.sensorCode(),input.parameterCode(),input.collectTime(),
                input.valueNum(),input.valueText(),input.sourceUnit(),input.qualityCode());
    }
    private static String validateValue(ReadingInput input) {
        if ((input.valueNum()==null)==(input.valueText()==null)) return "valueNum 与 valueText 必须且只能提供一个";
        if (input.collectTime().isAfter(java.time.OffsetDateTime.now().plusMinutes(5))) return "采集时间不能超过当前时间5分钟";
        return null;
    }
    private IngestItemResult failure(int index,ReadingInput input,String code,String message) {
        return new IngestItemResult(index,input.sourceRecordId(),"REJECTED",code,message);
    }
}
