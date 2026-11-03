package com.coppertwin.backend.energy;

import com.coppertwin.backend.common.BusinessException;
import com.coppertwin.backend.common.JdbcTimes;
import com.coppertwin.backend.energy.EnergyModels.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
public class EnergyService {
    private static final ZoneId SHANGHAI=ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    public EnergyService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public EnergyOverview overview(LocalDate date) {
        LocalDate day=date==null?LocalDate.now(SHANGHAI):date;
        List<EnergyMetric> metrics=metrics("SITE",null,day);
        BigDecimal totalCost=sum(metrics,EnergyMetric::cost),totalTce=sum(metrics,EnergyMetric::tceAmount);
        BigDecimal production=siteProduction(day);
        BigDecimal electricity=metrics.stream().filter(x->"ELECTRICITY".equals(x.energyCode())).map(EnergyMetric::consumption).findFirst().orElse(BigDecimal.ZERO);
        BigDecimal unit=divide(electricity,production);
        List<CompositionItem> composition=metrics.stream().map(x->new CompositionItem(x.energyCode(),x.energyName(),x.tceAmount(),percentage(x.tceAmount(),totalTce))).toList();
        PeakFlatValley pfv=jdbc.query("""
            SELECT COALESCE(SUM(d.peak_amount),0),COALESCE(SUM(d.flat_amount),0),COALESCE(SUM(d.valley_amount),0)
             FROM dws_energy_day d JOIN dim_sensor s ON s.sensor_id=d.sensor_id JOIN dim_energy_type et ON et.energy_type_id=s.energy_type_id
             WHERE s.energy_scope_type='SITE' AND et.energy_code='ELECTRICITY' AND d.statistic_date=?
            """,rs->{rs.next();return new PeakFlatValley(rs.getBigDecimal(1),rs.getBigDecimal(2),rs.getBigDecimal(3));},day);
        return new EnergyOverview(day,metrics,totalCost,totalTce,production,unit,composition,pfv,trend(day),OffsetDateTime.now(SHANGHAI));
    }

    public WorkshopComparison workshops(LocalDate date) {
        LocalDate day=date==null?LocalDate.now(SHANGHAI):date;
        List<Long> ids=jdbc.query("SELECT workshop_id FROM dim_workshop WHERE enabled=TRUE AND workshop_code IN ('WS-ROD','WS-WIRE') ORDER BY workshop_id",(rs,n)->rs.getLong(1));
        return new WorkshopComparison(day,ids.stream().map(id->workshopRow(id,day)).toList(),OffsetDateTime.now(SHANGHAI));
    }

    public EquipmentRanking equipmentRanking(LocalDate date,Integer limit) {
        LocalDate day=date==null?LocalDate.now(SHANGHAI):date; int max=limit==null?8:Math.max(1,Math.min(limit,100));
        List<EquipmentEnergy> items=jdbc.query("""
            SELECT e.equipment_id,e.equipment_code,e.equipment_name,w.workshop_name,et.energy_code,et.standard_unit_code,
             d.consumption,d.production_quantity,d.cost,d.quality_status,a.latest_value
            FROM dws_energy_day d JOIN dim_sensor s ON s.sensor_id=d.sensor_id AND s.energy_scope_type='EQUIPMENT'
             JOIN dim_energy_type et ON et.energy_type_id=s.energy_type_id JOIN dim_equipment e ON e.equipment_id=s.equipment_id
             JOIN dim_workshop w ON w.workshop_id=e.workshop_id
             LEFT JOIN dim_sensor_parameter ip ON ip.sensor_id=s.sensor_id AND ip.parameter_code='instantaneous'
             LEFT JOIN ads_parameter_monitor a ON a.sensor_id=s.sensor_id AND a.parameter_id=ip.parameter_id
            WHERE d.statistic_date=? AND et.energy_code='ELECTRICITY' ORDER BY d.consumption DESC
            """,(rs,n)->equipment(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBigDecimal(11),
                rs.getBigDecimal(7),rs.getBigDecimal(9),rs.getBigDecimal(8),rs.getString(6),rs.getString(10)),day);
        return new EquipmentRanking(day,items.stream().limit(max).toList(),OffsetDateTime.now(SHANGHAI));
    }

    public EquipmentRanking benchmark(LocalDate date,String equipmentCode) {
        EquipmentRanking ranking=equipmentRanking(date,100);
        List<EquipmentEnergy> comparable=ranking.items();
        if (equipmentCode!=null&&!equipmentCode.isBlank()) {
            String prefix=equipmentCode.contains("-")?equipmentCode.substring(0,equipmentCode.indexOf('-')):equipmentCode;
            comparable=comparable.stream().filter(x->x.equipmentCode().startsWith(prefix)).toList();
        }
        return new EquipmentRanking(ranking.statisticDate(),comparable,ranking.refreshTime());
    }

    public EnergyFlow flow(String energyCode) {
        String code=energyCode(energyCode);
        List<NodeRow> rows=jdbc.query("""
            SELECT s.sensor_id,s.sensor_code,s.sensor_name,s.energy_scope_type,COALESCE(s.energy_workshop_id,e.workshop_id),
             CASE WHEN s.energy_scope_type='EQUIPMENT' THEN s.equipment_id ELSE NULL END,s.parent_energy_sensor_id,
             a.latest_value,ip.unit_code,a.quality_status,a.latest_collect_time
            FROM dim_sensor s JOIN dim_energy_type et ON et.energy_type_id=s.energy_type_id
             LEFT JOIN dim_equipment e ON e.equipment_id=s.equipment_id
             LEFT JOIN dim_sensor_parameter ip ON ip.sensor_id=s.sensor_id AND ip.parameter_code='instantaneous'
             LEFT JOIN ads_parameter_monitor a ON a.sensor_id=s.sensor_id AND a.parameter_id=ip.parameter_id
            WHERE et.energy_code=? AND s.enabled=TRUE ORDER BY s.sensor_id
            """,(rs,n)->new NodeRow(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),(Long)rs.getObject(5),(Long)rs.getObject(6),
                (Long)rs.getObject(7),rs.getBigDecimal(8),rs.getString(9),rs.getString(10),JdbcTimes.offsetDateTime(rs,11)),code);
        validateAcyclic(rows);
        Map<Long,List<NodeRow>> children=new HashMap<>();
        for (NodeRow row:rows) if (row.parentId!=null) children.computeIfAbsent(row.parentId,k->new ArrayList<>()).add(row);
        List<FlowNode> roots=rows.stream().filter(x->x.parentId==null).map(x->node(x,children,new HashSet<>())).toList();
        String name=jdbc.queryForObject("SELECT energy_name FROM dim_energy_type WHERE energy_code=?",String.class,code);
        return new EnergyFlow(code,name,roots,OffsetDateTime.now(SHANGHAI));
    }

    public EnergyBalance balance(String energyCode,LocalDate date) {
        String code=energyCode(energyCode); LocalDate day=date==null?LocalDate.now(SHANGHAI):date;
        List<BalanceItem> items=jdbc.query("""
            SELECT p.sensor_id,p.sensor_code,p.sensor_name,COALESCE(pd.consumption,0),
             COALESCE(SUM(cd.consumption),0),p.configured_loss_rate
            FROM dim_sensor p JOIN dim_energy_type et ON et.energy_type_id=p.energy_type_id
             LEFT JOIN dws_energy_day pd ON pd.sensor_id=p.sensor_id AND pd.statistic_date=?
             LEFT JOIN dim_sensor c ON c.parent_energy_sensor_id=p.sensor_id AND c.enabled=TRUE
             LEFT JOIN dws_energy_day cd ON cd.sensor_id=c.sensor_id AND cd.statistic_date=?
            WHERE et.energy_code=? AND p.enabled=TRUE GROUP BY p.sensor_id,p.sensor_code,p.sensor_name,pd.consumption,p.configured_loss_rate
             HAVING COUNT(c.sensor_id)>0 ORDER BY p.sensor_id
            """,(rs,n)->{
            BigDecimal input=rs.getBigDecimal(4),child=rs.getBigDecimal(5),loss=input.multiply(rs.getBigDecimal(6));
            BigDecimal difference=input.subtract(child).subtract(loss),rate=percentage(difference.abs(),input);
            return new BalanceItem(rs.getLong(1),rs.getString(2),rs.getString(3),input,child,loss,difference,rate,rate.compareTo(BigDecimal.valueOf(5))>0?"WARNING":"NORMAL");
        },day,day,code);
        BigDecimal online=jdbc.query("""
            SELECT COUNT(a.sensor_id),COUNT(s.sensor_id) FROM dim_sensor s JOIN dim_energy_type et ON et.energy_type_id=s.energy_type_id
             LEFT JOIN dim_sensor_parameter ip ON ip.sensor_id=s.sensor_id AND ip.parameter_code='instantaneous'
             LEFT JOIN ads_parameter_monitor a ON a.sensor_id=s.sensor_id AND a.parameter_id=ip.parameter_id AND a.quality_status='GOOD' AND a.latest_collect_time>=?
             WHERE et.energy_code=? AND s.enabled=TRUE
            """,rs->{rs.next(); return percentage(rs.getBigDecimal(1),rs.getBigDecimal(2));},OffsetDateTime.now(SHANGHAI).minusMinutes(5),code);
        return new EnergyBalance(code,items,online,OffsetDateTime.now(SHANGHAI));
    }

    public WorkshopDashboard dashboard(long workshopId,LocalDate date) {
        LocalDate day=date==null?LocalDate.now(SHANGHAI):date; WorkshopEnergy row=workshopRow(workshopId,day);
        List<EnergyMetric> metrics=metrics("WORKSHOP",workshopId,day);
        BigDecimal load=jdbc.query("""
            SELECT a.latest_value FROM dim_sensor s JOIN dim_energy_type et ON et.energy_type_id=s.energy_type_id
             LEFT JOIN dim_sensor_parameter ip ON ip.sensor_id=s.sensor_id AND ip.parameter_code='instantaneous'
             LEFT JOIN ads_parameter_monitor a ON a.sensor_id=s.sensor_id AND a.parameter_id=ip.parameter_id
             WHERE s.energy_scope_type='WORKSHOP' AND s.energy_workshop_id=? AND et.energy_code='ELECTRICITY'
            """,(rs,n)->rs.getBigDecimal(1),workshopId).stream().findFirst().orElse(BigDecimal.ZERO);
        List<EquipmentEnergy> equipment=equipmentRanking(day,100).items().stream().filter(x->x.workshopName().equals(row.workshopName())).limit(5).toList();
        return new WorkshopDashboard(row.workshopId(),row.workshopCode(),row.workshopName(),day,metrics,load,row.productionQuantity(),
                row.unitElectricityConsumption(),row.totalCost(),row.totalTce(),equipment,OffsetDateTime.now(SHANGHAI));
    }

    public EquipmentEnergy equipmentEnergy(long equipmentId,LocalDate date) {
        LocalDate day=date==null?LocalDate.now(SHANGHAI):date;
        return jdbc.query("""
            SELECT e.equipment_id,e.equipment_code,e.equipment_name,w.workshop_name,ip.unit_code,a.latest_value,d.consumption,d.cost,d.production_quantity,d.quality_status
             FROM dim_sensor s JOIN dim_equipment e ON e.equipment_id=s.equipment_id JOIN dim_workshop w ON w.workshop_id=e.workshop_id
             JOIN dim_energy_type et ON et.energy_type_id=s.energy_type_id
             LEFT JOIN dim_sensor_parameter ip ON ip.sensor_id=s.sensor_id AND ip.parameter_code='instantaneous'
             LEFT JOIN ads_parameter_monitor a ON a.sensor_id=s.sensor_id AND a.parameter_id=ip.parameter_id
             LEFT JOIN dws_energy_day d ON d.sensor_id=s.sensor_id AND d.statistic_date=?
             WHERE s.energy_scope_type='EQUIPMENT' AND s.equipment_id=? AND et.energy_code='ELECTRICITY'
            """,(rs,n)->equipment(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBigDecimal(6),rs.getBigDecimal(7),rs.getBigDecimal(8),rs.getBigDecimal(9),rs.getString(5),rs.getString(10)),day,equipmentId)
                .stream().findFirst().orElseThrow(()->notFound("该设备暂无独立能源计量"));
    }

    private List<EnergyMetric> metrics(String scope,Long workshopId,LocalDate day) {
        String workshop=workshopId==null?"":" AND s.energy_workshop_id=?"; List<Object> args=new ArrayList<>(); if(workshopId!=null)args.add(workshopId); args.add(day);
        return jdbc.query("""
            SELECT et.energy_code,et.energy_name,et.standard_unit_code,COALESCE(d.consumption,0),COALESCE(d.cost,0),COALESCE(d.tce_amount,0),COALESCE(d.quality_status,'NO_DATA')
             FROM dim_energy_type et LEFT JOIN dim_sensor s ON s.energy_type_id=et.energy_type_id AND s.energy_scope_type='"""+scope+"'"+workshop+"""
             LEFT JOIN dws_energy_day d ON d.sensor_id=s.sensor_id AND d.statistic_date=? WHERE et.enabled=TRUE ORDER BY et.energy_type_id
            """,(rs,n)->new EnergyMetric(rs.getString(1),rs.getString(2),rs.getBigDecimal(4),rs.getString(3),rs.getBigDecimal(5),rs.getBigDecimal(6),rs.getString(7)),args.toArray());
    }

    private WorkshopEnergy workshopRow(long workshopId,LocalDate day) {
        String[] workshop=jdbc.query("SELECT workshop_code,workshop_name FROM dim_workshop WHERE workshop_id=?",(rs,n)->new String[]{rs.getString(1),rs.getString(2)},workshopId).stream().findFirst().orElseThrow(()->notFound("车间不存在"));
        List<EnergyMetric> metrics=metrics("WORKSHOP",workshopId,day);
        BigDecimal production=jdbc.query("""
            SELECT MAX(d.production_quantity) FROM dws_energy_day d JOIN dim_sensor s ON s.sensor_id=d.sensor_id
             WHERE s.energy_scope_type='WORKSHOP' AND s.energy_workshop_id=? AND d.statistic_date=?
            """,rs->{rs.next();return rs.getBigDecimal(1);},workshopId,day); if(production==null)production=BigDecimal.ZERO;
        String stage=jdbc.query("""
            SELECT d.production_stage FROM dws_energy_day d JOIN dim_sensor s ON s.sensor_id=d.sensor_id
             WHERE s.energy_scope_type='WORKSHOP' AND s.energy_workshop_id=? AND d.statistic_date=? AND d.production_stage IS NOT NULL
            """,(rs,n)->rs.getString(1),workshopId,day).stream().findFirst().orElse("NO_PRODUCTION_STAGE");
        BigDecimal electricity=value(metrics,"ELECTRICITY"),water=value(metrics,"WATER"),gas=value(metrics,"COAL_GAS");
        String quality=metrics.stream().anyMatch(x->!"GOOD".equals(x.qualityStatus()))?"PARTIAL":"GOOD";
        return new WorkshopEnergy(workshopId,workshop[0],workshop[1],production,electricity,water,gas,divide(electricity,production),sum(metrics,EnergyMetric::cost),sum(metrics,EnergyMetric::tceAmount),stage,quality);
    }

    private List<EnergyTrendPoint> trend(LocalDate day) {
        Map<OffsetDateTime,BigDecimal[]> grouped=new TreeMap<>();
        jdbc.query("""
            SELECT h.bucket_time,et.energy_code,h.consumption FROM dws_energy_hour h JOIN dim_sensor s ON s.sensor_id=h.sensor_id
             JOIN dim_energy_type et ON et.energy_type_id=s.energy_type_id WHERE s.energy_scope_type='SITE' AND h.bucket_time>=? AND h.bucket_time<? ORDER BY h.bucket_time
            """,rs->{OffsetDateTime t=JdbcTimes.offsetDateTime(rs,1); BigDecimal[] v=grouped.computeIfAbsent(t,k->new BigDecimal[]{BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO});
                switch(rs.getString(2)){case "ELECTRICITY"->v[0]=rs.getBigDecimal(3);case "WATER"->v[1]=rs.getBigDecimal(3);case "COAL_GAS"->v[2]=rs.getBigDecimal(3);} },day.atStartOfDay(SHANGHAI).toOffsetDateTime(),day.plusDays(1).atStartOfDay(SHANGHAI).toOffsetDateTime());
        return grouped.entrySet().stream().map(e->new EnergyTrendPoint(e.getKey(),e.getValue()[0],e.getValue()[1],e.getValue()[2])).toList();
    }
    private BigDecimal siteProduction(LocalDate day) { BigDecimal v=jdbc.query("""
        SELECT MAX(d.production_quantity) FROM dws_energy_day d JOIN dim_sensor s ON s.sensor_id=d.sensor_id WHERE s.energy_scope_type='SITE' AND d.statistic_date=?
        """,rs->{rs.next();return rs.getBigDecimal(1);},day); return v==null?BigDecimal.ZERO:v; }
    private FlowNode node(NodeRow row,Map<Long,List<NodeRow>> children,Set<Long> path) {
        if (!path.add(row.id)) throw new BusinessException("ENERGY_TREE_CYCLE","能源表计层级存在环",HttpStatus.CONFLICT);
        List<FlowNode> nested=children.getOrDefault(row.id,List.of()).stream().map(x->node(x,children,new HashSet<>(path))).toList();
        String status=row.latestTime==null?"NO_DATA":!"GOOD".equals(row.quality)?"DATA_INVALID":row.latestTime.plusMinutes(5).isBefore(OffsetDateTime.now(SHANGHAI))?"OFFLINE":"GOOD";
        return new FlowNode(row.id,row.code,row.name,row.scope,row.workshopId,row.equipmentId,row.value,row.unit,status,nested);
    }
    private void validateAcyclic(List<NodeRow> rows) { Map<Long,Long> parent=new HashMap<>(); rows.forEach(x->parent.put(x.id,x.parentId)); for(Long id:parent.keySet()){Set<Long> seen=new HashSet<>();Long p=id;while(p!=null){if(!seen.add(p))throw new BusinessException("ENERGY_TREE_CYCLE","能源表计层级存在环",HttpStatus.CONFLICT);p=parent.get(p);}} }
    private EquipmentEnergy equipment(long id,String code,String name,String workshop,BigDecimal load,BigDecimal consumption,BigDecimal cost,BigDecimal production,String unit,String quality) {
        BigDecimal per=divide(consumption,production); String benchmark=per.signum()==0?"NO_DATA":per.compareTo(BigDecimal.valueOf(180))<=0?"EXCELLENT":per.compareTo(BigDecimal.valueOf(210))<=0?"GOOD":"HIGH";
        return new EquipmentEnergy(id,code,name,workshop,nvl(load),nvl(consumption),per,nvl(production),nvl(cost),unit,quality==null?"NO_DATA":quality,benchmark);
    }
    private String energyCode(String code) { String normalized=code==null?"ELECTRICITY":code.trim().toUpperCase(Locale.ROOT); if(!List.of("ELECTRICITY","WATER","COAL_GAS").contains(normalized))throw validation("energyCode 必须为 ELECTRICITY、WATER 或 COAL_GAS"); return normalized; }
    private static BigDecimal value(List<EnergyMetric> metrics,String code){return metrics.stream().filter(x->code.equals(x.energyCode())).map(EnergyMetric::consumption).findFirst().orElse(BigDecimal.ZERO);}
    private static <T> BigDecimal sum(List<T> list,java.util.function.Function<T,BigDecimal> fn){return list.stream().map(fn).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private static BigDecimal divide(BigDecimal a,BigDecimal b){return b==null||b.signum()==0?BigDecimal.ZERO:nvl(a).divide(b,2,RoundingMode.HALF_UP);}
    private static BigDecimal percentage(BigDecimal a,BigDecimal b){return b==null||b.signum()==0?BigDecimal.ZERO:nvl(a).multiply(BigDecimal.valueOf(100)).divide(b,2,RoundingMode.HALF_UP);}
    private static BigDecimal nvl(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
    private BusinessException validation(String message){return new BusinessException("VALIDATION_ERROR",message,HttpStatus.BAD_REQUEST);}
    private BusinessException notFound(String message){return new BusinessException("RESOURCE_NOT_FOUND",message,HttpStatus.NOT_FOUND);}
    private record NodeRow(long id,String code,String name,String scope,Long workshopId,Long equipmentId,Long parentId,BigDecimal value,String unit,String quality,OffsetDateTime latestTime){}
}
