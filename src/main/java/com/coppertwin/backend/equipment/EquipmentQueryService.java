package com.coppertwin.backend.equipment;

import com.coppertwin.backend.common.JdbcTimes;
import com.coppertwin.backend.common.BusinessException;
import com.coppertwin.backend.equipment.EquipmentModels.*;
import com.coppertwin.backend.measurement.MeasurementQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class EquipmentQueryService {
    private static final ZoneId SHANGHAI=ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final MeasurementQueryService measurements;

    public EquipmentQueryService(JdbcTemplate jdbc,MeasurementQueryService measurements) {
        this.jdbc=jdbc; this.measurements=measurements;
    }

    public EquipmentSummary summary() {
        List<EquipmentItem> items=allItems();
        int sensors=items.stream().mapToInt(EquipmentItem::sensorCount).sum();
        int online=items.stream().mapToInt(EquipmentItem::onlineSensorCount).sum();
        BigDecimal rate=sensors==0?BigDecimal.ZERO:BigDecimal.valueOf(online*100L)
                .divide(BigDecimal.valueOf(sensors),1,RoundingMode.HALF_UP);
        return new EquipmentSummary(items.size(),count(items,"RUNNING"),count(items,"OFFLINE"),
                count(items,"WARNING"),count(items,"ALARM"),rate,OffsetDateTime.now(SHANGHAI));
    }

    public EquipmentPage list(String workshopCode,String status,String keyword,int page,int pageSize) {
        if (page<1||pageSize<1||pageSize>100) throw validation("page 必须大于等于 1，pageSize 必须在 1-100 之间");
        String normalized=status==null?null:status.trim().toUpperCase(Locale.ROOT);
        if (normalized!=null&&!normalized.isBlank()&&!List.of("RUNNING","OFFLINE","WARNING","ALARM").contains(normalized))
            throw validation("status 必须为 RUNNING、OFFLINE、WARNING 或 ALARM");
        String key=keyword==null?null:keyword.trim().toLowerCase(Locale.ROOT);
        List<EquipmentItem> filtered=allItems().stream()
                .filter(x->workshopCode==null||workshopCode.isBlank()||x.workshopCode().equalsIgnoreCase(workshopCode))
                .filter(x->normalized==null||normalized.isBlank()||x.status().equals(normalized))
                .filter(x->key==null||key.isBlank()||x.equipmentCode().toLowerCase(Locale.ROOT).contains(key)||x.equipmentName().toLowerCase(Locale.ROOT).contains(key))
                .toList();
        int from=Math.min((page-1)*pageSize,filtered.size());
        int to=Math.min(from+pageSize,filtered.size());
        return new EquipmentPage(filtered.subList(from,to),page,pageSize,filtered.size());
    }

    public EquipmentDetail detail(long equipmentId) {
        EquipmentItem item=allItems().stream().filter(x->x.equipmentId()==equipmentId).findFirst().orElseThrow(this::notFound);
        String process=jdbc.query("""
            SELECT DISTINCT p.process_name FROM dim_process p
            JOIN dim_sensor_parameter sp ON sp.process_id=p.process_id AND sp.enabled=TRUE
            JOIN dim_sensor s ON s.sensor_id=sp.sensor_id
            WHERE s.equipment_id=? ORDER BY p.process_name
            """,(rs,n)->rs.getString(1),equipmentId).stream().findFirst().orElse(null);
        return new EquipmentDetail(item.equipmentId(),item.equipmentCode(),item.equipmentName(),item.workshopId(),
                item.workshopCode(),item.workshopName(),process,item.responsiblePerson(),item.status(),item.sensorCount(),
                item.onlineSensorCount(),item.warningCount(),item.alarmCount(),item.latestCollectTime());
    }

    public LatestParameters latest(long equipmentId) {
        detail(equipmentId);
        OffsetDateTime now=OffsetDateTime.now(SHANGHAI);
        List<EquipmentParameter> items=jdbc.query("""
            SELECT s.sensor_id,s.sensor_code,s.sensor_name,s.sensor_type,sp.parameter_id,sp.parameter_code,sp.parameter_name,
             a.latest_value,a.latest_value_text,sp.unit_code,sp.decimal_places,a.latest_collect_time,a.quality_status,
             a.limit_status,sp.sampling_frequency_seconds
            FROM dim_sensor s JOIN dim_sensor_parameter sp ON sp.sensor_id=s.sensor_id AND sp.enabled=TRUE
            LEFT JOIN ads_parameter_monitor a ON a.sensor_id=s.sensor_id AND a.parameter_id=sp.parameter_id
            WHERE s.equipment_id=? AND s.enabled=TRUE AND COALESCE(s.sensor_type,'')<>'能源表计'
            ORDER BY s.sensor_id,sp.parameter_id
            """,(rs,n)->{
            OffsetDateTime collect=JdbcTimes.offsetDateTime(rs,12);
            String quality=rs.getString(13), limit=rs.getString(14);
            int sampling=rs.getObject(15)==null?300:rs.getInt(15);
            String data=dataStatus(collect,quality,sampling,now);
            String display=displayStatus(data,limit);
            int index=n%6;
            return new EquipmentParameter(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getLong(5),
                    rs.getString(6),rs.getString(7),rs.getBigDecimal(8),rs.getString(9),rs.getString(10),rs.getInt(11),
                    collect==null?null:collect.atZoneSameInstant(SHANGHAI).toOffsetDateTime(),quality,data,limit==null?"UNKNOWN":limit,
                    display,index%2==0?16:62,14+(index/2)*31);
        },equipmentId);
        OffsetDateTime asOf=items.stream().map(EquipmentParameter::collectTime).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        return new LatestParameters(items,asOf,now,items.isEmpty()?"NO_DATA":"AVAILABLE");
    }

    public EquipmentTrends trends(long equipmentId,List<String> parameterCodes,OffsetDateTime start,OffsetDateTime end,String granularity) {
        List<EquipmentParameter> available=latest(equipmentId).items();
        Set<String> requested=parameterCodes==null?Set.of():new LinkedHashSet<>(parameterCodes);
        var selected=available.stream().filter(x->requested.isEmpty()||requested.contains(x.parameterCode())).limit(6).toList();
        if (selected.isEmpty()) throw validation("设备没有可查询的参数");
        var series=selected.stream().map(x->measurements.trends(x.sensorCode(),x.parameterCode(),start,end,granularity)).toList();
        return new EquipmentTrends(series,OffsetDateTime.now(SHANGHAI));
    }

    private List<EquipmentItem> allItems() {
        OffsetDateTime now=OffsetDateTime.now(SHANGHAI);
        record Row(long id,String code,String name,long workshopId,String workshopCode,String workshopName,String owner,
                   Long sensorId,OffsetDateTime collect,String quality,String limit,Integer sampling) {}
        List<Row> rows=jdbc.query("""
            SELECT e.equipment_id,e.equipment_code,e.equipment_name,w.workshop_id,w.workshop_code,w.workshop_name,e.responsible_person,
             s.sensor_id,a.latest_collect_time,a.quality_status,a.limit_status,sp.sampling_frequency_seconds
            FROM dim_equipment e JOIN dim_workshop w ON w.workshop_id=e.workshop_id
            LEFT JOIN dim_sensor s ON s.equipment_id=e.equipment_id AND s.enabled=TRUE AND COALESCE(s.sensor_type,'')<>'能源表计'
            LEFT JOIN dim_sensor_parameter sp ON sp.sensor_id=s.sensor_id AND sp.enabled=TRUE
            LEFT JOIN ads_parameter_monitor a ON a.sensor_id=s.sensor_id AND a.parameter_id=sp.parameter_id
            WHERE e.enabled=TRUE AND COALESCE(e.equipment_usage,'PRODUCTION')='PRODUCTION'
            ORDER BY e.equipment_id,s.sensor_id,sp.parameter_id
            """,(rs,n)->new Row(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getString(6),
                rs.getString(7),(Long)rs.getObject(8),JdbcTimes.offsetDateTime(rs,9),rs.getString(10),rs.getString(11),(Integer)rs.getObject(12)));
        Map<Long,List<Row>> grouped=new LinkedHashMap<>();
        for (Row row:rows) grouped.computeIfAbsent(row.id(),k->new ArrayList<>()).add(row);
        List<EquipmentItem> result=new ArrayList<>();
        for (List<Row> group:grouped.values()) {
            Row base=group.get(0); Set<Long> sensors=new HashSet<>(),onlineSensors=new HashSet<>(); int warning=0,alarm=0; OffsetDateTime latest=null;
            for (Row row:group) if (row.sensorId()!=null) {
                sensors.add(row.sensorId()); String data=dataStatus(row.collect(),row.quality(),row.sampling()==null?300:row.sampling(),now);
                if ("GOOD".equals(data)) onlineSensors.add(row.sensorId());
                if ("WARNING".equals(row.limit())&&"GOOD".equals(data)) warning++;
                if ("ALARM".equals(row.limit())&&"GOOD".equals(data)) alarm++;
                if (row.collect()!=null&&(latest==null||row.collect().isAfter(latest))) latest=row.collect();
            }
            String status=alarm>0?"ALARM":warning>0?"WARNING":!sensors.isEmpty()&&!onlineSensors.isEmpty()?"RUNNING":"OFFLINE";
            result.add(new EquipmentItem(base.id(),base.code(),base.name(),base.workshopId(),base.workshopCode(),base.workshopName(),
                    base.owner(),status,sensors.size(),onlineSensors.size(),warning,alarm,latest));
        }
        result.sort(Comparator.comparingInt((EquipmentItem x)->severity(x.status())).thenComparing(EquipmentItem::equipmentCode));
        return result;
    }

    private static int count(List<EquipmentItem> items,String status) { return (int)items.stream().filter(x->x.status().equals(status)).count(); }
    private static int severity(String status) { return switch(status) { case "ALARM"->0; case "WARNING"->1; case "OFFLINE"->2; default->3; }; }
    private static String dataStatus(OffsetDateTime collect,String quality,int sampling,OffsetDateTime now) {
        if (collect==null) return "NO_DATA";
        if (!"GOOD".equals(quality)) return "DATA_INVALID";
        return collect.plusSeconds(Math.max(sampling*3,60)).isBefore(now)?"OFFLINE":"GOOD";
    }
    private static String displayStatus(String data,String limit) {
        if ("ALARM".equals(limit)&&"GOOD".equals(data)) return "ALARM";
        if ("WARNING".equals(limit)&&"GOOD".equals(data)) return "WARNING";
        return "GOOD".equals(data)?"NORMAL":data;
    }
    private BusinessException notFound() { return new BusinessException("RESOURCE_NOT_FOUND","设备不存在",HttpStatus.NOT_FOUND); }
    private BusinessException validation(String message) { return new BusinessException("VALIDATION_ERROR",message,HttpStatus.BAD_REQUEST); }
}
