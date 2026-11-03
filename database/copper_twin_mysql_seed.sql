-- Copper Twin MySQL 5.7 demo seed data
-- 先执行 copper_twin_mysql_schema.sql，再执行本文件。
USE copper_twin;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS=0;

INSERT INTO dim_workshop(workshop_code,workshop_name,workshop_type) VALUES
('WS-ROD','车间1 · 铜杆生产','熔炼'),('WS-WIRE','车间2 · 铜丝拉制','拉丝');

INSERT INTO dim_unit(unit_code,unit_name,unit_category,base_unit_code,conversion_factor) VALUES
('C','摄氏度','TEMPERATURE','C',1),('Pa','帕','PRESSURE','Pa',1),('kPa','千帕','PRESSURE','Pa',1000),('PCT','百分比','RATIO','PCT',1),
('M_PER_MIN','米每分钟','SPEED','M_PER_MIN',1),('M_PER_SEC','米每秒','SPEED','M_PER_MIN',60),('N','牛','FORCE','N',1),('MM','毫米','LENGTH','MM',1),
('KWH','千瓦时','ENERGY','KWH',1),('KW','千瓦','POWER','KW',1),('M3','立方米','VOLUME','M3',1),('M3_PER_H','立方米每小时','FLOW','M3_PER_H',1),
('NM3','标准立方米','STANDARD_VOLUME','NM3',1),('NM3_PER_H','标准立方米每小时','STANDARD_FLOW','NM3_PER_H',1),('TCE','吨标准煤','STANDARD_COAL','TCE',1),('CNY','人民币元','CURRENCY','CNY',1),('T','吨','MASS','T',1);

INSERT INTO dim_process(process_code,process_name,workshop_id,sequence_no,description)
SELECT x.code,x.name,w.workshop_id,x.seq,x.descr FROM dim_workshop w JOIN (
SELECT 'WS-ROD' wc,'ROD-FEED' code,'铜板上料' name,1 seq,'铜板上料与升降' descr UNION ALL SELECT 'WS-ROD','ROD-MELT','熔化炉熔化',2,'铜板熔化为铜液' UNION ALL SELECT 'WS-ROD','ROD-HOLD','保温炉保温',3,'铜液输送与保温' UNION ALL SELECT 'WS-ROD','ROD-CAST','结晶机水冷',4,'结晶冷却' UNION ALL SELECT 'WS-ROD','ROD-DRAW','牵引粗铜棒',5,'粗铜坯牵引' UNION ALL SELECT 'WS-ROD','ROD-ROLL','多道轧制',6,'轧制至8mm' UNION ALL SELECT 'WS-ROD','ROD-COOL','冷却清洗',7,'冷却清洗还原' UNION ALL SELECT 'WS-ROD','ROD-COIL','铜杆收卷',8,'铜杆收卷' UNION ALL SELECT 'WS-WIRE','WIRE-RECEIVE','Φ8铜杆卷接收',1,'接收车间1成品' UNION ALL SELECT 'WS-WIRE','WIRE-PAYOFF','上卷放线',2,'上卷放线' UNION ALL SELECT 'WS-WIRE','WIRE-DRAW','多道次冷拉',3,'客户规格冷拉' UNION ALL SELECT 'WS-WIRE','WIRE-GAUGE','在线测径',4,'在线测量线径' UNION ALL SELECT 'WS-WIRE','WIRE-COIL','成品收卷',5,'细铜丝收卷' UNION ALL SELECT 'WS-WIRE','WIRE-INSPECT','客户规格检验',6,'成品规格检验'
) x ON x.wc=w.workshop_code;

INSERT INTO dim_equipment(equipment_code,equipment_name,workshop_id,responsible_person,equipment_usage)
SELECT x.code,x.name,w.workshop_id,'待确认','PRODUCTION' FROM dim_workshop w JOIN (
SELECT 'WS-ROD' wc,'SMELT-FUR-01' code,'1#熔化炉' name UNION ALL SELECT 'WS-ROD','HOLD-FUR-01','1#保温炉' UNION ALL SELECT 'WS-ROD','CAST-01','1#结晶牵引机' UNION ALL SELECT 'WS-ROD','ROLL-01','1#多道轧机组' UNION ALL SELECT 'WS-WIRE','DRAW-01','1#拉丝机' UNION ALL SELECT 'WS-WIRE','DRAW-02','2#拉丝机' UNION ALL SELECT 'WS-WIRE','DRAW-03','3#拉丝机' UNION ALL SELECT 'WS-WIRE','GAUGE-02','2#测径仪'
) x ON x.wc=w.workshop_code;

INSERT INTO dim_sensor(sensor_code,sensor_name,equipment_id,sensor_type,data_source)
SELECT x.code,x.name,e.equipment_id,x.typ,'SIMULATOR' FROM dim_equipment e JOIN (
SELECT 'SMELT-FUR-01' ec,'TEMP-001' code,'炉膛上部温度' name,'温度' typ UNION ALL SELECT 'SMELT-FUR-01','TEMP-002','炉膛中部温度','温度' UNION ALL SELECT 'SMELT-FUR-01','TEMP-003','炉膛底部温度','温度' UNION ALL SELECT 'SMELT-FUR-01','PRESS-001','炉压','压力' UNION ALL SELECT 'SMELT-FUR-01','LEVEL-001','铜液液位','液位' UNION ALL SELECT 'CAST-01','SPEED-001','牵引速度','速度' UNION ALL SELECT 'ROLL-01','DIAM-ROD-001','轧制出口线径','线径' UNION ALL SELECT 'DRAW-03','SPEED-003','拉丝线速度','速度' UNION ALL SELECT 'DRAW-03','TENSION-003','拉丝张力','张力' UNION ALL SELECT 'GAUGE-02','DIAM-WIRE-002','出线线径','线径'
) x ON x.ec=e.equipment_code;

INSERT INTO dim_sensor_parameter(sensor_id,parameter_code,parameter_name,data_type,unit_code,decimal_places,normal_lower_limit,normal_upper_limit,alarm_lower_limit,alarm_upper_limit,sampling_frequency_seconds)
SELECT s.sensor_id,x.pc,x.pn,'DECIMAL',x.uc,x.dp,x.nl,x.nu,x.al,x.au,10 FROM dim_sensor s JOIN (
SELECT 'TEMP-001' sc,'temperature' pc,'上部温度' pn,'C' uc,0 dp,1100 nl,1220 nu,1060 al,1260 au UNION ALL SELECT 'TEMP-002','temperature','中部温度','C',0,1100,1220,1060,1260 UNION ALL SELECT 'TEMP-003','temperature','底部温度','C',0,1090,1210,1050,1250 UNION ALL SELECT 'PRESS-001','pressure','炉压','Pa',0,-25,-10,-35,0 UNION ALL SELECT 'LEVEL-001','level','铜液液位','PCT',1,60,85,50,92 UNION ALL SELECT 'SPEED-001','speed','牵引速度','M_PER_MIN',1,28,36,24,40 UNION ALL SELECT 'DIAM-ROD-001','diameter','铜杆线径','MM',2,7.95,8.05,7.90,8.10 UNION ALL SELECT 'SPEED-003','speed','拉丝线速度','M_PER_SEC',1,20,26,18,28 UNION ALL SELECT 'TENSION-003','tension','拉丝张力','N',1,17,24,14,27 UNION ALL SELECT 'DIAM-WIRE-002','diameter','出线线径','MM',3,1.815,1.825,1.810,1.830
) x ON x.sc=s.sensor_code;

INSERT INTO dim_energy_type(energy_code,energy_name,standard_unit_code,tce_factor,factor_version,effective_from) VALUES
('ELECTRICITY','电力','KWH',0.0001229,'GB-T2589-2020','2026-01-01'),('WATER','水','M3',0.0000857,'ENTERPRISE-2026','2026-01-01'),('COAL_GAS','煤气','NM3',0.0005714,'ENTERPRISE-2026','2026-01-01');
INSERT INTO dim_energy_tariff(energy_type_id,period_type,start_minute,end_minute,unit_price,effective_from)
SELECT et.energy_type_id,x.pt,x.sm,x.em,x.price,'2026-01-01' FROM dim_energy_type et JOIN (
SELECT 'ELECTRICITY' ec,'VALLEY' pt,0 sm,420 em,.38 price UNION ALL SELECT 'ELECTRICITY','FLAT',420,600,.68 UNION ALL SELECT 'ELECTRICITY','PEAK',600,900,1.08 UNION ALL SELECT 'ELECTRICITY','FLAT',900,1080,.68 UNION ALL SELECT 'ELECTRICITY','PEAK',1080,1320,1.08 UNION ALL SELECT 'ELECTRICITY','VALLEY',1320,1440,.38 UNION ALL SELECT 'WATER','STANDARD',0,1440,3.20 UNION ALL SELECT 'COAL_GAS','STANDARD',0,1440,2.45
) x ON x.ec=et.energy_code;

-- 能源表计设备、测点和参数
INSERT INTO dim_equipment(equipment_code,equipment_name,workshop_id,responsible_person,equipment_usage)
SELECT x.code,x.name,w.workshop_id,'能源管理员','ENERGY_METER' FROM dim_workshop w JOIN (
SELECT 'WS-ROD' wc,'ENERGY-SITE' code,'全厂能源总表柜' name UNION ALL SELECT 'WS-ROD','ENERGY-ROD','车间1能源表柜' UNION ALL SELECT 'WS-WIRE','ENERGY-WIRE','车间2能源表柜'
) x ON x.wc=w.workshop_code;
INSERT INTO dim_sensor(sensor_code,sensor_name,equipment_id,sensor_type,data_source)
SELECT x.sc,x.sn,e.equipment_id,'能源表计','SIMULATOR' FROM dim_equipment e JOIN (
SELECT 'ENERGY-SITE' ec,'EM-E-SITE' sc,'全厂总电表' sn UNION ALL SELECT 'ENERGY-ROD','EM-E-ROD','车间1总电表' UNION ALL SELECT 'ENERGY-WIRE','EM-E-WIRE','车间2总电表' UNION ALL SELECT 'SMELT-FUR-01','EM-E-SMELT','熔化炉电表' UNION ALL SELECT 'HOLD-FUR-01','EM-E-HOLD','保温炉电表' UNION ALL SELECT 'DRAW-01','EM-E-DRAW1','1#拉丝机电表' UNION ALL SELECT 'DRAW-02','EM-E-DRAW2','2#拉丝机电表' UNION ALL SELECT 'ENERGY-SITE','EM-W-SITE','全厂总水表' UNION ALL SELECT 'ENERGY-ROD','EM-W-ROD','车间1总水表' UNION ALL SELECT 'ENERGY-WIRE','EM-W-WIRE','车间2总水表' UNION ALL SELECT 'CAST-01','EM-W-CAST','结晶冷却水表' UNION ALL SELECT 'DRAW-03','EM-W-DRAW','拉丝冷却水表' UNION ALL SELECT 'ENERGY-SITE','EM-G-SITE','全厂总煤气表' UNION ALL SELECT 'ENERGY-ROD','EM-G-ROD','车间1总煤气表' UNION ALL SELECT 'SMELT-FUR-01','EM-G-SMELT','熔化炉煤气表' UNION ALL SELECT 'HOLD-FUR-01','EM-G-HOLD','保温炉煤气表'
) x ON x.ec=e.equipment_code;
INSERT INTO dim_sensor_parameter(sensor_id,parameter_code,parameter_name,data_type,unit_code,decimal_places,sampling_frequency_seconds)
SELECT s.sensor_id,x.pc,x.pn,'DECIMAL',x.uc,2,10 FROM dim_sensor s JOIN (
SELECT 'EM-E-SITE' sc,'cumulative' pc,'累计电量' pn,'KWH' uc UNION ALL SELECT 'EM-E-SITE','instantaneous','实时功率','KW' UNION ALL SELECT 'EM-E-ROD','cumulative','累计电量','KWH' UNION ALL SELECT 'EM-E-ROD','instantaneous','实时功率','KW' UNION ALL SELECT 'EM-E-WIRE','cumulative','累计电量','KWH' UNION ALL SELECT 'EM-E-WIRE','instantaneous','实时功率','KW' UNION ALL SELECT 'EM-E-SMELT','cumulative','累计电量','KWH' UNION ALL SELECT 'EM-E-SMELT','instantaneous','实时功率','KW' UNION ALL SELECT 'EM-E-HOLD','cumulative','累计电量','KWH' UNION ALL SELECT 'EM-E-HOLD','instantaneous','实时功率','KW' UNION ALL SELECT 'EM-E-DRAW1','cumulative','累计电量','KWH' UNION ALL SELECT 'EM-E-DRAW1','instantaneous','实时功率','KW' UNION ALL SELECT 'EM-E-DRAW2','cumulative','累计电量','KWH' UNION ALL SELECT 'EM-E-DRAW2','instantaneous','实时功率','KW' UNION ALL SELECT 'EM-W-SITE','cumulative','累计水量','M3' UNION ALL SELECT 'EM-W-SITE','instantaneous','瞬时流量','M3_PER_H' UNION ALL SELECT 'EM-W-ROD','cumulative','累计水量','M3' UNION ALL SELECT 'EM-W-ROD','instantaneous','瞬时流量','M3_PER_H' UNION ALL SELECT 'EM-W-WIRE','cumulative','累计水量','M3' UNION ALL SELECT 'EM-W-WIRE','instantaneous','瞬时流量','M3_PER_H' UNION ALL SELECT 'EM-W-CAST','cumulative','累计水量','M3' UNION ALL SELECT 'EM-W-CAST','instantaneous','瞬时流量','M3_PER_H' UNION ALL SELECT 'EM-W-DRAW','cumulative','累计水量','M3' UNION ALL SELECT 'EM-W-DRAW','instantaneous','瞬时流量','M3_PER_H' UNION ALL SELECT 'EM-G-SITE','cumulative','累计煤气量','NM3' UNION ALL SELECT 'EM-G-SITE','instantaneous','瞬时流量','NM3_PER_H' UNION ALL SELECT 'EM-G-ROD','cumulative','累计煤气量','NM3' UNION ALL SELECT 'EM-G-ROD','instantaneous','瞬时流量','NM3_PER_H' UNION ALL SELECT 'EM-G-SMELT','cumulative','累计煤气量','NM3' UNION ALL SELECT 'EM-G-SMELT','instantaneous','瞬时流量','NM3_PER_H' UNION ALL SELECT 'EM-G-HOLD','cumulative','累计煤气量','NM3' UNION ALL SELECT 'EM-G-HOLD','instantaneous','瞬时流量','NM3_PER_H'
) x ON x.sc=s.sensor_code;

-- 绑定能源属性（MySQL 5.7 不支持 UPDATE ... FROM，使用相关子查询）
UPDATE dim_sensor SET energy_type_id=(SELECT energy_type_id FROM dim_energy_type WHERE energy_code=CASE WHEN sensor_code LIKE 'EM-E-%' THEN 'ELECTRICITY' WHEN sensor_code LIKE 'EM-W-%' THEN 'WATER' ELSE 'COAL_GAS' END), energy_scope_type=CASE WHEN sensor_code LIKE '%-SITE' THEN 'SITE' WHEN sensor_code IN ('EM-E-ROD','EM-E-WIRE','EM-W-ROD','EM-W-WIRE','EM-G-ROD') THEN 'WORKSHOP' ELSE 'EQUIPMENT' END, energy_workshop_id=CASE WHEN sensor_code IN ('EM-E-ROD','EM-W-ROD','EM-G-ROD') THEN (SELECT workshop_id FROM dim_workshop WHERE workshop_code='WS-ROD') WHEN sensor_code IN ('EM-E-WIRE','EM-W-WIRE') THEN (SELECT workshop_id FROM dim_workshop WHERE workshop_code='WS-WIRE') ELSE NULL END, energy_multiplier=1, configured_loss_rate=CASE WHEN sensor_code='EM-E-SITE' THEN .018 WHEN sensor_code='EM-W-SITE' THEN .020 WHEN sensor_code='EM-G-SITE' THEN .015 WHEN sensor_code LIKE '%-ROD' OR sensor_code LIKE '%-WIRE' THEN .010 ELSE .005 END WHERE sensor_code LIKE 'EM-E-%' OR sensor_code LIKE 'EM-W-%' OR sensor_code LIKE 'EM-G-%';
UPDATE dim_sensor AS child JOIN dim_sensor AS parent ON parent.sensor_code='EM-E-SITE' SET child.parent_energy_sensor_id=parent.sensor_id WHERE child.sensor_code IN ('EM-E-ROD','EM-E-WIRE');
UPDATE dim_sensor AS child JOIN dim_sensor AS parent ON parent.sensor_code='EM-E-ROD' SET child.parent_energy_sensor_id=parent.sensor_id WHERE child.sensor_code IN ('EM-E-SMELT','EM-E-HOLD');
UPDATE dim_sensor AS child JOIN dim_sensor AS parent ON parent.sensor_code='EM-E-WIRE' SET child.parent_energy_sensor_id=parent.sensor_id WHERE child.sensor_code IN ('EM-E-DRAW1','EM-E-DRAW2');
UPDATE dim_sensor AS child JOIN dim_sensor AS parent ON parent.sensor_code='EM-W-SITE' SET child.parent_energy_sensor_id=parent.sensor_id WHERE child.sensor_code IN ('EM-W-ROD','EM-W-WIRE');
UPDATE dim_sensor AS child JOIN dim_sensor AS parent ON parent.sensor_code='EM-W-ROD' SET child.parent_energy_sensor_id=parent.sensor_id WHERE child.sensor_code='EM-W-CAST';
UPDATE dim_sensor AS child JOIN dim_sensor AS parent ON parent.sensor_code='EM-W-WIRE' SET child.parent_energy_sensor_id=parent.sensor_id WHERE child.sensor_code='EM-W-DRAW';
UPDATE dim_sensor AS child JOIN dim_sensor AS parent ON parent.sensor_code='EM-G-SITE' SET child.parent_energy_sensor_id=parent.sensor_id WHERE child.sensor_code='EM-G-ROD';
UPDATE dim_sensor AS child JOIN dim_sensor AS parent ON parent.sensor_code='EM-G-ROD' SET child.parent_energy_sensor_id=parent.sensor_id WHERE child.sensor_code IN ('EM-G-SMELT','EM-G-HOLD');

-- 工艺参数归属和前端显示配置
UPDATE dim_sensor_parameter p JOIN dim_sensor s ON s.sensor_id=p.sensor_id JOIN dim_process pr ON pr.process_code='ROD-MELT'
SET p.process_id=pr.process_id,p.trend_display=1,p.display_order=CASE s.sensor_code WHEN 'TEMP-001' THEN 1 WHEN 'TEMP-002' THEN 2 ELSE 3 END,
    p.primary_display=CASE WHEN s.sensor_code='TEMP-002' THEN 1 ELSE 0 END WHERE s.sensor_code IN ('TEMP-001','TEMP-002','TEMP-003');
UPDATE dim_sensor_parameter p JOIN dim_sensor s ON s.sensor_id=p.sensor_id JOIN dim_process pr ON pr.process_code='ROD-HOLD'
SET p.process_id=pr.process_id,p.primary_display=1 WHERE s.sensor_code='TEMP-003';
UPDATE dim_sensor_parameter p JOIN dim_sensor s ON s.sensor_id=p.sensor_id JOIN dim_process pr ON pr.process_code='ROD-CAST'
SET p.process_id=pr.process_id,p.primary_display=1 WHERE s.sensor_code='LEVEL-001';
UPDATE dim_sensor_parameter p JOIN dim_sensor s ON s.sensor_id=p.sensor_id JOIN dim_process pr ON pr.process_code='ROD-DRAW'
SET p.process_id=pr.process_id,p.primary_display=1,p.trend_display=1,p.display_order=1 WHERE s.sensor_code='SPEED-001';
UPDATE dim_sensor_parameter p JOIN dim_sensor s ON s.sensor_id=p.sensor_id JOIN dim_process pr ON pr.process_code='ROD-ROLL'
SET p.process_id=pr.process_id,p.primary_display=1 WHERE s.sensor_code='DIAM-ROD-001';
UPDATE dim_sensor_parameter p JOIN dim_sensor s ON s.sensor_id=p.sensor_id JOIN dim_process pr ON pr.process_code='WIRE-PAYOFF'
SET p.process_id=pr.process_id,p.primary_display=1 WHERE s.sensor_code='SPEED-003';
UPDATE dim_sensor_parameter p JOIN dim_sensor s ON s.sensor_id=p.sensor_id JOIN dim_process pr ON pr.process_code='WIRE-DRAW'
SET p.process_id=pr.process_id,p.primary_display=1,p.trend_display=1,p.display_order=1 WHERE s.sensor_code='TENSION-003';
UPDATE dim_sensor_parameter p JOIN dim_sensor s ON s.sensor_id=p.sensor_id JOIN dim_process pr ON pr.process_code='WIRE-GAUGE'
SET p.process_id=pr.process_id,p.primary_display=1,p.trend_display=1,p.display_order=1 WHERE s.sensor_code='DIAM-WIRE-002';

-- 工艺最新值
INSERT INTO ads_parameter_monitor(sensor_id,parameter_id,workshop_id,equipment_id,unit_code,latest_value,latest_value_text,quality_status,limit_status,latest_collect_time,refresh_time)
SELECT s.sensor_id,p.parameter_id,e.workshop_id,e.equipment_id,p.unit_code,x.val,NULL,'GOOD',x.stat,NOW(),NOW() FROM dim_sensor s JOIN dim_sensor_parameter p ON p.sensor_id=s.sensor_id JOIN dim_equipment e ON e.equipment_id=s.equipment_id JOIN (
SELECT 'TEMP-001' sc,1176.0 val,'NORMAL' stat UNION ALL SELECT 'TEMP-002',1182.0,'NORMAL' UNION ALL SELECT 'TEMP-003',1169.0,'NORMAL' UNION ALL SELECT 'PRESS-001',-18.0,'NORMAL' UNION ALL SELECT 'LEVEL-001',72.6,'NORMAL' UNION ALL SELECT 'SPEED-001',32.4,'NORMAL' UNION ALL SELECT 'DIAM-ROD-001',8.00,'NORMAL' UNION ALL SELECT 'SPEED-003',23.4,'NORMAL' UNION ALL SELECT 'TENSION-003',25.0,'WARNING' UNION ALL SELECT 'DIAM-WIRE-002',1.840,'ALARM'
) x ON x.sc=s.sensor_code AND x.sc NOT LIKE 'EM-%';

-- 能源实时累计值和瞬时值
INSERT INTO ads_parameter_monitor(sensor_id,parameter_id,workshop_id,equipment_id,unit_code,latest_value,latest_value_text,quality_status,limit_status,latest_collect_time,refresh_time)
SELECT s.sensor_id,p.parameter_id,e.workshop_id,e.equipment_id,p.unit_code,
       CASE WHEN p.parameter_code='cumulative' THEN x.total_val ELSE x.instant_val END,NULL,'GOOD','NORMAL',NOW(),NOW()
FROM dim_sensor s JOIN dim_sensor_parameter p ON p.sensor_id=s.sensor_id JOIN dim_equipment e ON e.equipment_id=s.equipment_id JOIN (
SELECT 'EM-E-SITE' sc,128600 total_val,2846 instant_val UNION ALL SELECT 'EM-E-ROD',78600,1776 UNION ALL SELECT 'EM-E-WIRE',48200,1070 UNION ALL SELECT 'EM-E-SMELT',32600,1286 UNION ALL SELECT 'EM-E-HOLD',14600,286 UNION ALL SELECT 'EM-E-DRAW1',12600,368 UNION ALL SELECT 'EM-E-DRAW2',11800,300 UNION ALL SELECT 'EM-W-SITE',23860,38.6 UNION ALL SELECT 'EM-W-ROD',10260,14.6 UNION ALL SELECT 'EM-W-WIRE',13600,24 UNION ALL SELECT 'EM-W-CAST',8860,11.2 UNION ALL SELECT 'EM-W-DRAW',9200,18.6 UNION ALL SELECT 'EM-G-SITE',638420,426 UNION ALL SELECT 'EM-G-ROD',632840,426 UNION ALL SELECT 'EM-G-SMELT',398420,286 UNION ALL SELECT 'EM-G-HOLD',234420,140
) x ON x.sc=s.sensor_code;

-- 最近一小时工艺历史数据，用于趋势图
INSERT INTO ods_sensor_data(source_system,source_record_id,sensor_code,parameter_code,collect_time,value_num,source_unit,quality_code)
SELECT 'MYSQL_SEED',CONCAT('seed-',x.sc,'-',x.mins),x.sc,x.pc,DATE_SUB(NOW(),INTERVAL x.mins MINUTE),x.val,x.uc,'GOOD' FROM (
SELECT 'TEMP-001' sc,'temperature' pc,'C' uc,50 mins,1171 val UNION ALL SELECT 'TEMP-001','temperature','C',40,1174 UNION ALL SELECT 'TEMP-001','temperature','C',30,1178 UNION ALL SELECT 'TEMP-001','temperature','C',20,1176 UNION ALL SELECT 'TEMP-001','temperature','C',10,1180 UNION ALL
SELECT 'TEMP-002','temperature','C',50,1178 UNION ALL SELECT 'TEMP-002','temperature','C',40,1181 UNION ALL SELECT 'TEMP-002','temperature','C',30,1185 UNION ALL SELECT 'TEMP-002','temperature','C',20,1182 UNION ALL SELECT 'TEMP-002','temperature','C',10,1186 UNION ALL
SELECT 'PRESS-001','pressure','Pa',50,-20 UNION ALL SELECT 'PRESS-001','pressure','Pa',40,-19 UNION ALL SELECT 'PRESS-001','pressure','Pa',30,-18 UNION ALL SELECT 'PRESS-001','pressure','Pa',20,-19 UNION ALL SELECT 'PRESS-001','pressure','Pa',10,-18 UNION ALL
SELECT 'LEVEL-001','level','PCT',50,70.5 UNION ALL SELECT 'LEVEL-001','level','PCT',40,71.8 UNION ALL SELECT 'LEVEL-001','level','PCT',30,72.6 UNION ALL SELECT 'LEVEL-001','level','PCT',20,73.2 UNION ALL SELECT 'LEVEL-001','level','PCT',10,72.8 UNION ALL
SELECT 'SPEED-003','speed','M_PER_SEC',50,22.8 UNION ALL SELECT 'SPEED-003','speed','M_PER_SEC',40,23.1 UNION ALL SELECT 'SPEED-003','speed','M_PER_SEC',30,23.5 UNION ALL SELECT 'SPEED-003','speed','M_PER_SEC',20,23.2 UNION ALL SELECT 'SPEED-003','speed','M_PER_SEC',10,23.4 UNION ALL
SELECT 'TENSION-003','tension','N',50,21.2 UNION ALL SELECT 'TENSION-003','tension','N',40,22.1 UNION ALL SELECT 'TENSION-003','tension','N',30,23.0 UNION ALL SELECT 'TENSION-003','tension','N',20,24.2 UNION ALL SELECT 'TENSION-003','tension','N',10,23.6
) x;

-- 当日能源统计（厂级、车间级、设备级）
INSERT INTO dws_energy_day(sensor_id,statistic_date,consumption,cost,tce_amount,peak_amount,flat_amount,valley_amount,production_quantity,production_stage,quality_status,factor_version)
SELECT s.sensor_id,CURDATE(),x.consumption,x.cost,x.tce,x.peak,x.flat,x.valley,x.production,x.stage,'GOOD',et.factor_version FROM dim_sensor s JOIN dim_energy_type et ON et.energy_type_id=s.energy_type_id JOIN (
SELECT 'EM-E-SITE' sc,28600 consumption,20220 cost,3.51494 tce,8866 peak,12584 flat,7150 valley,151.2 production,'SITE_TOTAL' stage UNION ALL SELECT 'EM-E-ROD',17800,12640,2.18762,5696,7654,4450,86.4,'COPPER_ROD' UNION ALL SELECT 'EM-E-WIRE',10800,7580,1.32732,3170,4930,2700,64.8,'COPPER_WIRE' UNION ALL SELECT 'EM-E-SMELT',9800,7020,1.20442,3200,4100,2500,86.4,'COPPER_ROD' UNION ALL SELECT 'EM-E-HOLD',3600,2490,.44244,1100,1600,900,86.4,'COPPER_ROD' UNION ALL SELECT 'EM-E-DRAW1',4200,2960,.51618,1220,1980,1000,32.4,'COPPER_WIRE' UNION ALL SELECT 'EM-E-DRAW2',3900,2740,.47931,1180,1720,1000,32.4,'COPPER_WIRE' UNION ALL SELECT 'EM-W-SITE',386,1235.2,.0330802,0,386,0,151.2,'SITE_TOTAL' UNION ALL SELECT 'EM-W-ROD',146,467.2,.0125122,0,146,0,86.4,'COPPER_ROD' UNION ALL SELECT 'EM-W-WIRE',240,768,.020568,0,240,0,64.8,'COPPER_WIRE' UNION ALL SELECT 'EM-W-CAST',112,358.4,.0095984,0,112,0,86.4,'COPPER_ROD' UNION ALL SELECT 'EM-W-DRAW',186,595.2,.0159402,0,186,0,64.8,'COPPER_WIRE' UNION ALL SELECT 'EM-G-SITE',3842,9412.9,2.1953188,0,3842,0,86.4,'SITE_TOTAL' UNION ALL SELECT 'EM-G-ROD',3784,9270.8,2.1627776,0,3784,0,86.4,'COPPER_ROD' UNION ALL SELECT 'EM-G-SMELT',2584,6330.8,1.4764976,0,2584,0,86.4,'COPPER_ROD' UNION ALL SELECT 'EM-G-HOLD',1200,2940,.68568,0,1200,0,86.4,'COPPER_ROD'
) x ON x.sc=s.sensor_code;

-- 厂级最近六小时趋势
INSERT INTO dws_energy_hour(sensor_id,bucket_time,consumption,cost,tce_amount,peak_amount,flat_amount,valley_amount,calculation_method,quality_status,factor_version)
SELECT s.sensor_id,DATE_SUB(DATE_FORMAT(NOW(),'%Y-%m-%d %H:00:00'),INTERVAL x.h HOUR),x.amount,x.amount*x.price,x.amount*et.tce_factor,
CASE WHEN x.period='PEAK' THEN x.amount ELSE 0 END,CASE WHEN x.period='FLAT' THEN x.amount ELSE 0 END,CASE WHEN x.period='VALLEY' THEN x.amount ELSE 0 END,'CUMULATIVE_DIFF','GOOD',et.factor_version
FROM dim_sensor s JOIN dim_energy_type et ON et.energy_type_id=s.energy_type_id JOIN (
SELECT 5 h,.75 amount,'VALLEY' period,.38 price UNION ALL SELECT 4,.86,'FLAT',.68 UNION ALL SELECT 3,1.08,'PEAK',1.08 UNION ALL SELECT 2,1.14,'PEAK',1.08 UNION ALL SELECT 1,.98,'FLAT',.68 UNION ALL SELECT 0,1,'FLAT',.68
) x WHERE s.energy_scope_type='SITE';

SET FOREIGN_KEY_CHECKS=1;
