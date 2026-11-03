package com.coppertwin.backend.measurement;

import com.coppertwin.backend.measurement.MeasurementModels.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MasterDataService {
    private final JdbcTemplate jdbc;
    public MasterDataService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public List<SensorView> sensors(String workshopCode,String processCode,Boolean enabled) {
        StringBuilder sql=new StringBuilder("""
            SELECT DISTINCT s.sensor_id,s.sensor_code,s.sensor_name,s.sensor_type,s.data_source,e.equipment_id,e.equipment_code,e.equipment_name,
             w.workshop_id,w.workshop_code FROM dim_sensor s JOIN dim_equipment e ON e.equipment_id=s.equipment_id
             JOIN dim_workshop w ON w.workshop_id=e.workshop_id
            """);
        List<Object> args=new ArrayList<>();
        sql.append(" WHERE 1=1 ");
        if (workshopCode!=null) { sql.append(" AND w.workshop_code=? "); args.add(workshopCode); }
        if (processCode!=null) { sql.append(" AND EXISTS (SELECT 1 FROM dim_sensor_parameter sp JOIN dim_process pr ON pr.process_id=sp.process_id WHERE sp.sensor_id=s.sensor_id AND pr.process_code=?) "); args.add(processCode); }
        if (enabled!=null) { sql.append(" AND s.enabled=? "); args.add(enabled); }
        sql.append(" ORDER BY w.workshop_id,e.equipment_id,s.sensor_id");
        return jdbc.query(sql.toString(),(rs,n)->new SensorView(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),
                rs.getLong(6),rs.getString(7),rs.getString(8),rs.getLong(9),rs.getString(10),parameters(rs.getLong(1))),args.toArray());
    }
    private List<ParameterView> parameters(long sensorId) {
        return jdbc.query("""
            SELECT parameter_id,parameter_code,parameter_name,data_type,unit_code,decimal_places,normal_lower_limit,normal_upper_limit,
             alarm_lower_limit,alarm_upper_limit,sampling_frequency_seconds FROM dim_sensor_parameter WHERE sensor_id=? AND enabled=TRUE ORDER BY parameter_id
            """,(rs,n)->new ParameterView(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getInt(6),
                rs.getBigDecimal(7),rs.getBigDecimal(8),rs.getBigDecimal(9),rs.getBigDecimal(10),(Integer)rs.getObject(11)),sensorId);
    }
}
