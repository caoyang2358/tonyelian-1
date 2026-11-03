package com.coppertwin.backend.energy;

import com.coppertwin.backend.common.JdbcTimes;
import com.coppertwin.backend.energy.EnergyModels.AggregationResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
public class EnergyAggregationService {
    private static final ZoneId SHANGHAI=ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    public EnergyAggregationService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public boolean isEnergySensor(String sensorCode) {
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM dim_sensor WHERE sensor_code=? AND energy_type_id IS NOT NULL AND enabled=TRUE",Integer.class,sensorCode);
        return count!=null&&count>0;
    }

    @Transactional
    public AggregationResult rebuildDay(LocalDate date) {
        List<Meter> meters=jdbc.query("""
            SELECT s.sensor_id,s.sensor_code,cp.parameter_code,ip.parameter_code,s.energy_multiplier,et.energy_code,et.tce_factor,et.factor_version
            FROM dim_sensor s
            LEFT JOIN dim_sensor_parameter cp ON cp.sensor_id=s.sensor_id AND cp.parameter_code='cumulative' AND cp.enabled=TRUE
            LEFT JOIN dim_sensor_parameter ip ON ip.sensor_id=s.sensor_id AND ip.parameter_code='instantaneous' AND ip.enabled=TRUE
            JOIN dim_energy_type et ON et.energy_type_id=s.energy_type_id WHERE s.enabled=TRUE
            """,(rs,n)->new Meter(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBigDecimal(5),
                rs.getString(6),rs.getBigDecimal(7),rs.getString(8)));
        int good=0,partial=0,noData=0;
        for (Meter meter:meters) {
            BigDecimal dayConsumption=BigDecimal.ZERO,dayCost=BigDecimal.ZERO,dayPeak=BigDecimal.ZERO,dayFlat=BigDecimal.ZERO,dayValley=BigDecimal.ZERO;
            String dayQuality="GOOD";
            for (int hour=0;hour<24;hour++) {
                ZonedDateTime start=date.atStartOfDay(SHANGHAI).plusHours(hour),end=start.plusHours(1);
                Calculation c=calculate(meter,start.toOffsetDateTime(),end.toOffsetDateTime());
                BigDecimal cost=c.amount.multiply(tariff(meter.energyCode,start.toLocalTime(),date)).setScale(6,RoundingMode.HALF_UP);
                String period=period(meter.energyCode,start.toLocalTime(),date);
                upsertHour(meter,start.toOffsetDateTime(),c,cost,period);
                dayConsumption=dayConsumption.add(c.amount); dayCost=dayCost.add(cost);
                if ("PEAK".equals(period)) dayPeak=dayPeak.add(c.amount);
                else if ("VALLEY".equals(period)) dayValley=dayValley.add(c.amount); else dayFlat=dayFlat.add(c.amount);
                if ("NO_DATA".equals(c.quality)) dayQuality="PARTIAL";
            }
            if (dayConsumption.signum()==0) { dayQuality="NO_DATA"; noData++; }
            else if ("PARTIAL".equals(dayQuality)) partial++; else good++;
            upsertDay(meter,date,dayConsumption,dayCost,dayPeak,dayFlat,dayValley,dayQuality);
        }
        return new AggregationResult(date,meters.size(),good,partial,noData);
    }

    private Calculation calculate(Meter meter,OffsetDateTime start,OffsetDateTime end) {
        List<Point> cumulative=pointsWithPrevious(meter.sensorCode,meter.cumulativeParameter,start,end);
        if (cumulative.size()>=2) {
            BigDecimal amount=BigDecimal.ZERO;
            for (int i=1;i<cumulative.size();i++) {
                BigDecimal diff=cumulative.get(i).value.subtract(cumulative.get(i-1).value);
                amount=amount.add(diff.signum()>=0?diff:cumulative.get(i).value.max(BigDecimal.ZERO));
            }
            return new Calculation(amount.multiply(meter.multiplier),"CUMULATIVE_DIFF","GOOD");
        }
        List<Point> instant=pointsWithPrevious(meter.sensorCode,meter.instantParameter,start,end);
        if (instant.size()>=2) {
            BigDecimal amount=BigDecimal.ZERO;
            for (int i=1;i<instant.size();i++) {
                double hours=Duration.between(instant.get(i-1).time,instant.get(i).time).toMillis()/3600000d;
                amount=amount.add(instant.get(i-1).value.add(instant.get(i).value).divide(BigDecimal.valueOf(2),8,RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(hours)));
            }
            return new Calculation(amount.multiply(meter.multiplier),"INSTANT_INTEGRAL","PARTIAL");
        }
        return new Calculation(BigDecimal.ZERO,"NO_DATA","NO_DATA");
    }

    private List<Point> points(String sensor,String parameter,OffsetDateTime start,OffsetDateTime end) {
        if (parameter==null) return List.of();
        return jdbc.query("""
            SELECT collect_time,value_num FROM ods_sensor_data WHERE sensor_code=? AND parameter_code=? AND collect_time>=? AND collect_time<?
             AND value_num IS NOT NULL AND is_deleted=FALSE AND UPPER(COALESCE(quality_code,'GOOD')) IN ('GOOD','VALID','OK','192') ORDER BY collect_time
            """,(rs,n)->new Point(JdbcTimes.offsetDateTime(rs,1),rs.getBigDecimal(2)),sensor,parameter,start,end);
    }
    private List<Point> pointsWithPrevious(String sensor,String parameter,OffsetDateTime start,OffsetDateTime end) {
        if (parameter==null) return List.of();
        List<Point> result=new ArrayList<>(jdbc.query("""
            SELECT collect_time,value_num FROM ods_sensor_data WHERE sensor_code=? AND parameter_code=? AND collect_time>=? AND collect_time<?
             AND value_num IS NOT NULL AND is_deleted=FALSE AND UPPER(COALESCE(quality_code,'GOOD')) IN ('GOOD','VALID','OK','192')
             ORDER BY collect_time DESC,record_id DESC LIMIT 1
            """,(rs,n)->new Point(JdbcTimes.offsetDateTime(rs,1),rs.getBigDecimal(2)),sensor,parameter,start.minusHours(1),start));
        result.addAll(points(sensor,parameter,start,end));
        return result;
    }

    private BigDecimal tariff(String energyCode,LocalTime time,LocalDate date) {
        int minute=time.getHour()*60+time.getMinute();
        return jdbc.query("""
            SELECT t.unit_price FROM dim_energy_tariff t JOIN dim_energy_type et ON et.energy_type_id=t.energy_type_id
             WHERE et.energy_code=? AND t.start_minute<=? AND t.end_minute>? AND t.effective_from<=?
             AND (t.effective_to IS NULL OR t.effective_to>=?) ORDER BY t.effective_from DESC
            """,(rs,n)->rs.getBigDecimal(1),energyCode,minute,minute,date,date).stream().findFirst().orElse(BigDecimal.ZERO);
    }
    private String period(String energyCode,LocalTime time,LocalDate date) {
        int minute=time.getHour()*60+time.getMinute();
        return jdbc.query("""
            SELECT t.period_type FROM dim_energy_tariff t JOIN dim_energy_type et ON et.energy_type_id=t.energy_type_id
             WHERE et.energy_code=? AND t.start_minute<=? AND t.end_minute>? AND t.effective_from<=?
             AND (t.effective_to IS NULL OR t.effective_to>=?) ORDER BY t.effective_from DESC
            """,(rs,n)->rs.getString(1),energyCode,minute,minute,date,date).stream().findFirst().orElse("FLAT");
    }

    private void upsertHour(Meter m,ZonedDateTime start,Calculation c,BigDecimal cost,String period) { upsertHour(m,start.toOffsetDateTime(),c,cost,period); }
    private void upsertHour(Meter m,OffsetDateTime start,Calculation c,BigDecimal cost,String period) {
        BigDecimal peak="PEAK".equals(period)?c.amount:BigDecimal.ZERO,flat="FLAT".equals(period)||"STANDARD".equals(period)?c.amount:BigDecimal.ZERO,valley="VALLEY".equals(period)?c.amount:BigDecimal.ZERO;
        BigDecimal tce=c.amount.multiply(m.tceFactor).setScale(10,RoundingMode.HALF_UP);
        int updated=jdbc.update("""
            UPDATE dws_energy_hour SET consumption=?,cost=?,tce_amount=?,peak_amount=?,flat_amount=?,valley_amount=?,calculation_method=?,quality_status=?,factor_version=?,refresh_time=CURRENT_TIMESTAMP
             WHERE sensor_id=? AND bucket_time=?
            """,c.amount,cost,tce,peak,flat,valley,c.method,c.quality,m.factorVersion,m.id,start);
        if (updated==0) jdbc.update("""
            INSERT INTO dws_energy_hour(sensor_id,bucket_time,consumption,cost,tce_amount,peak_amount,flat_amount,valley_amount,calculation_method,quality_status,factor_version)
             VALUES(?,?,?,?,?,?,?,?,?,?,?)
            """,m.id,start,c.amount,cost,tce,peak,flat,valley,c.method,c.quality,m.factorVersion);
    }
    private void upsertDay(Meter m,LocalDate date,BigDecimal amount,BigDecimal cost,BigDecimal peak,BigDecimal flat,BigDecimal valley,String quality) {
        BigDecimal tce=amount.multiply(m.tceFactor).setScale(10,RoundingMode.HALF_UP);
        int updated=jdbc.update("""
            UPDATE dws_energy_day SET consumption=?,cost=?,tce_amount=?,peak_amount=?,flat_amount=?,valley_amount=?,quality_status=?,factor_version=?,refresh_time=CURRENT_TIMESTAMP
             WHERE sensor_id=? AND statistic_date=?
            """,amount,cost,tce,peak,flat,valley,quality,m.factorVersion,m.id,date);
        if (updated==0) jdbc.update("""
            INSERT INTO dws_energy_day(sensor_id,statistic_date,consumption,cost,tce_amount,peak_amount,flat_amount,valley_amount,quality_status,factor_version)
             VALUES(?,?,?,?,?,?,?,?,?,?)
            """,m.id,date,amount,cost,tce,peak,flat,valley,quality,m.factorVersion);
    }

    private record Meter(long id,String sensorCode,String cumulativeParameter,String instantParameter,BigDecimal multiplier,
                         String energyCode,BigDecimal tceFactor,String factorVersion) {}
    private record Point(OffsetDateTime time,BigDecimal value) {}
    private record Calculation(BigDecimal amount,String method,String quality) {}
}
