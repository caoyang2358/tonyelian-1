package com.coppertwin.backend.measurement;

import com.coppertwin.backend.common.JdbcTimes;
import com.coppertwin.backend.common.BusinessException;
import com.coppertwin.backend.measurement.MeasurementModels.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class MeasurementQueryService {
    private static final ZoneId SHANGHAI=ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final MeasurementDefinitionRepository definitions;
    public MeasurementQueryService(JdbcTemplate jdbc,NamedParameterJdbcTemplate named,MeasurementDefinitionRepository definitions) {
        this.jdbc=jdbc; this.named=named; this.definitions=definitions;
    }

    public LatestResult latest(String processCode,List<String> sensorCodes,List<String> parameterCodes) {
        StringBuilder sql=new StringBuilder("""
            SELECT pp.process_code,s.sensor_code,s.sensor_name,p.parameter_code,p.parameter_name,a.latest_value,a.latest_value_text,
             a.unit_code,a.latest_collect_time,a.quality_status,a.limit_status,p.sampling_frequency_seconds,a.refresh_time
            FROM ads_parameter_monitor a JOIN dim_sensor s ON s.sensor_id=a.sensor_id JOIN dim_sensor_parameter p ON p.parameter_id=a.parameter_id
            LEFT JOIN dim_process pp ON pp.process_id=p.process_id AND p.primary_display=TRUE WHERE 1=1
            """);
        var params=new MapSqlParameterSource();
        if (processCode!=null&&!processCode.isBlank()) { sql.append(" AND pp.process_code=:processCode"); params.addValue("processCode",processCode); }
        if (sensorCodes!=null&&!sensorCodes.isEmpty()) { sql.append(" AND s.sensor_code IN (:sensorCodes)"); params.addValue("sensorCodes",sensorCodes); }
        if (parameterCodes!=null&&!parameterCodes.isEmpty()) { sql.append(" AND p.parameter_code IN (:parameterCodes)"); params.addValue("parameterCodes",parameterCodes); }
        sql.append(" ORDER BY s.sensor_code,p.parameter_code");
        OffsetDateTime now=OffsetDateTime.now(SHANGHAI);
        var items=named.query(sql.toString(),params,(rs,n)->toLatest(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),
                rs.getBigDecimal(6),rs.getString(7),rs.getString(8),JdbcTimes.offsetDateTime(rs,9),rs.getString(10),rs.getString(11),
                (Integer)rs.getObject(12),now));
        OffsetDateTime asOf=items.stream().map(LatestValue::collectTime).max(Comparator.naturalOrder()).orElse(null);
        return new LatestResult(items,asOf,now,items.isEmpty()?"NO_DATA":"AVAILABLE");
    }

    public TrendSeries trends(String sensorCode,String parameterCode,OffsetDateTime start,OffsetDateTime end,String granularity) {
        if (!start.isBefore(end)) throw validation("startTime 必须早于 endTime");
        String g=granularity==null?"minute":granularity.toLowerCase(Locale.ROOT);
        long maxHours=switch(g) { case "raw"->2; case "minute"->24*7; case "hour"->24*90; case "day"->24*366; default->throw validation("granularity 必须为 raw、minute、hour 或 day"); };
        if (Duration.between(start,end).compareTo(Duration.ofHours(maxHours))>0) throw validation("查询时间跨度超过 "+g+" 粒度上限");
        var d=definitions.find(sensorCode,parameterCode).orElseThrow(()->new BusinessException("RESOURCE_NOT_FOUND","测点参数不存在",HttpStatus.NOT_FOUND));
        if (!List.of("DECIMAL","INTEGER").contains(d.dataType())) throw validation("趋势仅支持数值型参数");
        List<TrendPoint> raw=rawTrend(sensorCode,parameterCode,start,end,d);
        List<TrendPoint> points="raw".equals(g)?raw:aggregate(raw.stream().filter(x->"GOOD".equals(x.quality())).toList(),g);
        OffsetDateTime asOf=points.isEmpty()?null:points.get(points.size()-1).time();
        return new TrendSeries(sensorCode,parameterCode,parameterCode,d.targetUnit(),g,points,asOf,OffsetDateTime.now(SHANGHAI),points.isEmpty()?"NO_DATA":"AVAILABLE");
    }

    private List<TrendPoint> rawTrend(String sensorCode,String parameterCode,OffsetDateTime start,OffsetDateTime end,
                                      MeasurementDefinitionRepository.Definition d) {
        var rows=jdbc.query("""
            SELECT collect_time,value_num,source_unit,quality_code FROM ods_sensor_data
             WHERE sensor_code=? AND parameter_code=? AND collect_time>=? AND collect_time<? AND value_num IS NOT NULL AND is_deleted=FALSE
             ORDER BY collect_time,record_id
            """,(rs,n)->new Object[]{JdbcTimes.offsetDateTime(rs,1),rs.getBigDecimal(2),rs.getString(3),rs.getString(4)},sensorCode,parameterCode,start,end);
        List<TrendPoint> result=new ArrayList<>();
        for (Object[] row:rows) {
            String sourceUnit=(String)row[2]; if (sourceUnit==null||sourceUnit.isBlank()) sourceUnit=d.targetUnit();
            var source=definitions.unit(sourceUnit);
            if (source.isEmpty()||d.unitCategory()!=null&&!d.unitCategory().equals(source.get().category())) continue;
            BigDecimal value=((BigDecimal)row[1]).multiply(source.get().factor()).divide(d.targetFactor(),Math.max(6,d.decimalPlaces()),RoundingMode.HALF_UP)
                    .setScale(d.decimalPlaces(),RoundingMode.HALF_UP);
            result.add(new TrendPoint(((OffsetDateTime)row[0]).atZoneSameInstant(SHANGHAI).toOffsetDateTime(),value,MeasurementProcessor.normalizeQuality((String)row[3])));
        }
        return result;
    }
    private List<TrendPoint> aggregate(List<TrendPoint> source,String granularity) {
        record Acc(BigDecimal sum,int count) {}
        Map<OffsetDateTime,Acc> grouped=new TreeMap<>();
        for (TrendPoint p:source) {
            OffsetDateTime key=switch(granularity) {
                case "minute" -> p.time().truncatedTo(ChronoUnit.MINUTES);
                case "hour" -> p.time().truncatedTo(ChronoUnit.HOURS);
                case "day" -> p.time().toLocalDate().atStartOfDay(SHANGHAI).toOffsetDateTime();
                default -> throw validation("granularity 必须为 raw、minute、hour 或 day");
            };
            Acc a=grouped.get(key); grouped.put(key,a==null?new Acc(p.value(),1):new Acc(a.sum().add(p.value()),a.count()+1));
        }
        return grouped.entrySet().stream().map(e->new TrendPoint(e.getKey(),e.getValue().sum().divide(BigDecimal.valueOf(e.getValue().count()),6,RoundingMode.HALF_UP),"GOOD")).toList();
    }

    public ProcessOverview overview() {
        OffsetDateTime now=OffsetDateTime.now(SHANGHAI);
        var workshops=jdbc.query("SELECT workshop_id,workshop_code,workshop_name FROM dim_workshop WHERE enabled=TRUE ORDER BY workshop_id",
                (rs,n)->new Object[]{rs.getLong(1),rs.getString(2),rs.getString(3)});
        List<WorkshopFlow> flows=new ArrayList<>(); OffsetDateTime max=null;
        for (Object[] w:workshops) {
            var nodes=jdbc.query("SELECT process_id,process_code,process_name,sequence_no FROM dim_process WHERE workshop_id=? AND enabled=TRUE ORDER BY sequence_no",
                    (rs,n)->node(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getInt(4),now),(Long)w[0]);
            for (ProcessNode node:nodes) if (node.primaryMeasurement()!=null && (max==null||node.primaryMeasurement().collectTime().isAfter(max))) max=node.primaryMeasurement().collectTime();
            flows.add(new WorkshopFlow((Long)w[0],(String)w[1],(String)w[2],nodes));
        }
        return new ProcessOverview(flows,max,now,max==null?"NO_DATA":"AVAILABLE");
    }
    private ProcessNode node(long id,String code,String name,int sequence,OffsetDateTime now) {
        var latest=jdbc.query("""
            SELECT s.sensor_code,s.sensor_name,sp.parameter_code,sp.parameter_name,a.latest_value,a.latest_value_text,a.unit_code,a.latest_collect_time,
             a.quality_status,a.limit_status,sp.sampling_frequency_seconds FROM dim_sensor_parameter sp
             JOIN dim_sensor s ON s.sensor_id=sp.sensor_id
             LEFT JOIN ads_parameter_monitor a ON a.sensor_id=s.sensor_id AND a.parameter_id=sp.parameter_id
             WHERE sp.process_id=? AND sp.primary_display=TRUE AND sp.enabled=TRUE
            """,(rs,n)->rs.getObject(8)==null?null:toLatest(code,rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBigDecimal(5),rs.getString(6),
                rs.getString(7),JdbcTimes.offsetDateTime(rs,8),rs.getString(9),rs.getString(10),(Integer)rs.getObject(11),now),id).stream().filter(Objects::nonNull).findFirst().orElse(null);
        var options=jdbc.query("""
            SELECT s.sensor_code,sp.parameter_code,sp.parameter_name,sp.unit_code,sp.display_order FROM dim_sensor_parameter sp
             JOIN dim_sensor s ON s.sensor_id=sp.sensor_id
             WHERE sp.process_id=? AND sp.trend_display=TRUE AND sp.enabled=TRUE ORDER BY sp.display_order,sp.parameter_id
            """,(rs,n)->new TrendOption(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getInt(5)),id);
        return new ProcessNode(id,code,name,sequence,latest,options,latest==null?"NO_DATA":latest.displayStatus());
    }

    private LatestValue toLatest(String processCode,String sensorCode,String sensorName,String parameterCode,String parameterName,BigDecimal value,
                                 String valueText,String unit,OffsetDateTime collect,String quality,String limit,Integer sampling,OffsetDateTime now) {
        int offline=sampling==null?300:Math.max(sampling*3,60);
        String dataStatus=collect.plusSeconds(offline).isBefore(now)?"OFFLINE":"GOOD";
        if (!"GOOD".equals(quality)) dataStatus="DATA_INVALID";
        String display="OFFLINE".equals(dataStatus)?"OFFLINE":"DATA_INVALID".equals(dataStatus)?"DATA_INVALID":
                "ALARM".equals(limit)?"ALARM":"WARNING".equals(limit)?"WARNING":"NORMAL";
        return new LatestValue(processCode,sensorCode,sensorName,parameterCode,parameterName,value,valueText,unit,
                collect.atZoneSameInstant(SHANGHAI).toOffsetDateTime(),quality,dataStatus,limit,display);
    }
    private BusinessException validation(String message) { return new BusinessException("VALIDATION_ERROR",message,HttpStatus.BAD_REQUEST); }
}
