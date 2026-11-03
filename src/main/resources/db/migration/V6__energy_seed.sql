INSERT INTO dim_energy_type(energy_code,energy_name,standard_unit_code,tce_factor,factor_version,effective_from) VALUES
 ('ELECTRICITY','电力','KWH',0.0001229,'GB-T2589-2020','2026-01-01'),
 ('WATER','水','M3',0.0000857,'ENTERPRISE-2026','2026-01-01'),
 ('COAL_GAS','煤气','NM3',0.0005714,'ENTERPRISE-2026','2026-01-01');

INSERT INTO dim_energy_tariff(energy_type_id,period_type,start_minute,end_minute,unit_price,effective_from)
SELECT et.energy_type_id,x.period_type,x.start_minute,x.end_minute,x.price,'2026-01-01'
FROM dim_energy_type et JOIN (VALUES
 ('ELECTRICITY','VALLEY',0,420,0.38),('ELECTRICITY','FLAT',420,600,0.68),
 ('ELECTRICITY','PEAK',600,900,1.08),('ELECTRICITY','FLAT',900,1080,0.68),
 ('ELECTRICITY','PEAK',1080,1320,1.08),('ELECTRICITY','VALLEY',1320,1440,0.38),
 ('WATER','STANDARD',0,1440,3.20),('COAL_GAS','STANDARD',0,1440,2.45)
) x(energy_code,period_type,start_minute,end_minute,price) ON et.energy_code=x.energy_code;

INSERT INTO dim_equipment(equipment_code,equipment_name,workshop_id,responsible_person,equipment_usage)
SELECT x.code,x.name,w.workshop_id,'能源管理员','ENERGY_METER' FROM dim_workshop w JOIN (VALUES
 ('WS-ROD','ENERGY-SITE','全厂能源总表柜'),('WS-ROD','ENERGY-ROD','车间1能源表柜'),
 ('WS-WIRE','ENERGY-WIRE','车间2能源表柜')
) x(workshop_code,code,name) ON w.workshop_code=x.workshop_code;

INSERT INTO dim_sensor(sensor_code,sensor_name,equipment_id,sensor_type,data_source)
SELECT x.sensor_code,x.sensor_name,e.equipment_id,'能源表计','SIMULATOR'
FROM dim_equipment e JOIN (VALUES
 ('ENERGY-SITE','EM-E-SITE','全厂总电表'),('ENERGY-ROD','EM-E-ROD','车间1总电表'),('ENERGY-WIRE','EM-E-WIRE','车间2总电表'),
 ('SMELT-FUR-01','EM-E-SMELT','熔化炉电表'),('HOLD-FUR-01','EM-E-HOLD','保温炉电表'),('DRAW-01','EM-E-DRAW1','1#拉丝机电表'),('DRAW-02','EM-E-DRAW2','2#拉丝机电表'),
 ('ENERGY-SITE','EM-W-SITE','全厂总水表'),('ENERGY-ROD','EM-W-ROD','车间1总水表'),('ENERGY-WIRE','EM-W-WIRE','车间2总水表'),
 ('CAST-01','EM-W-CAST','结晶冷却水表'),('DRAW-03','EM-W-DRAW','拉丝冷却水表'),
 ('ENERGY-SITE','EM-G-SITE','全厂总煤气表'),('ENERGY-ROD','EM-G-ROD','车间1总煤气表'),
 ('SMELT-FUR-01','EM-G-SMELT','熔化炉煤气表'),('HOLD-FUR-01','EM-G-HOLD','保温炉煤气表')
) x(equipment_code,sensor_code,sensor_name) ON e.equipment_code=x.equipment_code;

INSERT INTO dim_sensor_parameter(sensor_id,parameter_code,parameter_name,data_type,unit_code,decimal_places,sampling_frequency_seconds)
SELECT s.sensor_id,x.parameter_code,x.parameter_name,'DECIMAL',x.unit_code,2,10
FROM dim_sensor s JOIN (VALUES
 ('EM-E-SITE','cumulative','累计电量','KWH'),('EM-E-SITE','instantaneous','实时功率','KW'),
 ('EM-E-ROD','cumulative','累计电量','KWH'),('EM-E-ROD','instantaneous','实时功率','KW'),
 ('EM-E-WIRE','cumulative','累计电量','KWH'),('EM-E-WIRE','instantaneous','实时功率','KW'),
 ('EM-E-SMELT','cumulative','累计电量','KWH'),('EM-E-SMELT','instantaneous','实时功率','KW'),
 ('EM-E-HOLD','cumulative','累计电量','KWH'),('EM-E-HOLD','instantaneous','实时功率','KW'),
 ('EM-E-DRAW1','cumulative','累计电量','KWH'),('EM-E-DRAW1','instantaneous','实时功率','KW'),
 ('EM-E-DRAW2','cumulative','累计电量','KWH'),('EM-E-DRAW2','instantaneous','实时功率','KW'),
 ('EM-W-SITE','cumulative','累计水量','M3'),('EM-W-SITE','instantaneous','瞬时流量','M3_PER_H'),
 ('EM-W-ROD','cumulative','累计水量','M3'),('EM-W-ROD','instantaneous','瞬时流量','M3_PER_H'),
 ('EM-W-WIRE','cumulative','累计水量','M3'),('EM-W-WIRE','instantaneous','瞬时流量','M3_PER_H'),
 ('EM-W-CAST','cumulative','累计水量','M3'),('EM-W-CAST','instantaneous','瞬时流量','M3_PER_H'),
 ('EM-W-DRAW','cumulative','累计水量','M3'),('EM-W-DRAW','instantaneous','瞬时流量','M3_PER_H'),
 ('EM-G-SITE','cumulative','累计煤气量','NM3'),('EM-G-SITE','instantaneous','瞬时流量','NM3_PER_H'),
 ('EM-G-ROD','cumulative','累计煤气量','NM3'),('EM-G-ROD','instantaneous','瞬时流量','NM3_PER_H'),
 ('EM-G-SMELT','cumulative','累计煤气量','NM3'),('EM-G-SMELT','instantaneous','瞬时流量','NM3_PER_H'),
 ('EM-G-HOLD','cumulative','累计煤气量','NM3'),('EM-G-HOLD','instantaneous','瞬时流量','NM3_PER_H')
) x(sensor_code,parameter_code,parameter_name,unit_code) ON s.sensor_code=x.sensor_code;

UPDATE dim_sensor SET
 energy_type_id=(SELECT energy_type_id FROM dim_energy_type WHERE energy_code=CASE
   WHEN sensor_code LIKE 'EM-E-%' THEN 'ELECTRICITY' WHEN sensor_code LIKE 'EM-W-%' THEN 'WATER' ELSE 'COAL_GAS' END),
 energy_scope_type=CASE WHEN sensor_code LIKE '%-SITE' THEN 'SITE' WHEN sensor_code IN ('EM-E-ROD','EM-E-WIRE','EM-W-ROD','EM-W-WIRE','EM-G-ROD') THEN 'WORKSHOP' ELSE 'EQUIPMENT' END,
 energy_workshop_id=CASE
   WHEN sensor_code IN ('EM-E-ROD','EM-W-ROD','EM-G-ROD') THEN (SELECT workshop_id FROM dim_workshop WHERE workshop_code='WS-ROD')
   WHEN sensor_code IN ('EM-E-WIRE','EM-W-WIRE') THEN (SELECT workshop_id FROM dim_workshop WHERE workshop_code='WS-WIRE')
   ELSE NULL END,
 energy_flow_direction='INPUT', energy_multiplier=1,
 configured_loss_rate=CASE WHEN sensor_code='EM-E-SITE' THEN 0.018 WHEN sensor_code='EM-W-SITE' THEN 0.020
   WHEN sensor_code='EM-G-SITE' THEN 0.015 WHEN sensor_code LIKE '%-ROD' OR sensor_code LIKE '%-WIRE' THEN 0.010 ELSE 0.005 END
WHERE sensor_code LIKE 'EM-E-%' OR sensor_code LIKE 'EM-W-%' OR sensor_code LIKE 'EM-G-%';

UPDATE dim_sensor SET parent_energy_sensor_id=(SELECT sensor_id FROM dim_sensor WHERE sensor_code='EM-E-SITE') WHERE sensor_code IN ('EM-E-ROD','EM-E-WIRE');
UPDATE dim_sensor SET parent_energy_sensor_id=(SELECT sensor_id FROM dim_sensor WHERE sensor_code='EM-E-ROD') WHERE sensor_code IN ('EM-E-SMELT','EM-E-HOLD');
UPDATE dim_sensor SET parent_energy_sensor_id=(SELECT sensor_id FROM dim_sensor WHERE sensor_code='EM-E-WIRE') WHERE sensor_code IN ('EM-E-DRAW1','EM-E-DRAW2');
UPDATE dim_sensor SET parent_energy_sensor_id=(SELECT sensor_id FROM dim_sensor WHERE sensor_code='EM-W-SITE') WHERE sensor_code IN ('EM-W-ROD','EM-W-WIRE');
UPDATE dim_sensor SET parent_energy_sensor_id=(SELECT sensor_id FROM dim_sensor WHERE sensor_code='EM-W-ROD') WHERE sensor_code='EM-W-CAST';
UPDATE dim_sensor SET parent_energy_sensor_id=(SELECT sensor_id FROM dim_sensor WHERE sensor_code='EM-W-WIRE') WHERE sensor_code='EM-W-DRAW';
UPDATE dim_sensor SET parent_energy_sensor_id=(SELECT sensor_id FROM dim_sensor WHERE sensor_code='EM-G-SITE') WHERE sensor_code='EM-G-ROD';
UPDATE dim_sensor SET parent_energy_sensor_id=(SELECT sensor_id FROM dim_sensor WHERE sensor_code='EM-G-ROD') WHERE sensor_code IN ('EM-G-SMELT','EM-G-HOLD');

INSERT INTO ads_parameter_monitor(sensor_id,parameter_id,workshop_id,equipment_id,unit_code,latest_value,latest_value_text,quality_status,limit_status,latest_collect_time,refresh_time)
SELECT s.sensor_id,sp.parameter_id,e.workshop_id,e.equipment_id,sp.unit_code,
 CASE WHEN sp.parameter_code='cumulative' THEN x.total_value ELSE x.instant_value END,NULL,'GOOD','NORMAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM (VALUES
 ('EM-E-SITE',128600.0,2846.0),('EM-E-ROD',78600.0,1776.0),('EM-E-WIRE',48200.0,1070.0),
 ('EM-E-SMELT',32600.0,1286.0),('EM-E-HOLD',14600.0,286.0),('EM-E-DRAW1',12600.0,368.0),('EM-E-DRAW2',11800.0,300.0),
 ('EM-W-SITE',23860.0,38.6),('EM-W-ROD',10260.0,14.6),('EM-W-WIRE',13600.0,24.0),('EM-W-CAST',8860.0,11.2),('EM-W-DRAW',9200.0,18.6),
 ('EM-G-SITE',638420.0,426.0),('EM-G-ROD',632840.0,426.0),('EM-G-SMELT',398420.0,286.0),('EM-G-HOLD',234420.0,140.0)
) x(sensor_code,total_value,instant_value)
JOIN dim_sensor s ON s.sensor_code=x.sensor_code JOIN dim_sensor_parameter sp ON sp.sensor_id=s.sensor_id
JOIN dim_equipment e ON e.equipment_id=s.equipment_id;

INSERT INTO dws_energy_day(sensor_id,statistic_date,consumption,cost,tce_amount,peak_amount,flat_amount,valley_amount,production_quantity,production_stage,quality_status,factor_version)
SELECT s.sensor_id,CURRENT_DATE,x.consumption,x.cost,x.tce,x.peak,x.flat,x.valley,x.production,x.stage,'GOOD',et.factor_version
FROM (VALUES
 ('E-SITE',28600.0,20220.0,3.51494,8866.0,12584.0,7150.0,151.2,'SITE_TOTAL'),
 ('E-ROD',17800.0,12640.0,2.18762,5696.0,7654.0,4450.0,86.4,'COPPER_ROD'),
 ('E-WIRE',10800.0,7580.0,1.32732,3170.0,4930.0,2700.0,64.8,'COPPER_WIRE'),
 ('E-SMELT',9800.0,7020.0,1.20442,3200.0,4100.0,2500.0,86.4,'COPPER_ROD'),
 ('E-HOLD',3600.0,2490.0,0.44244,1100.0,1600.0,900.0,86.4,'COPPER_ROD'),
 ('E-DRAW1',4200.0,2960.0,0.51618,1220.0,1980.0,1000.0,32.4,'COPPER_WIRE'),
 ('E-DRAW2',3900.0,2740.0,0.47931,1180.0,1720.0,1000.0,32.4,'COPPER_WIRE'),
 ('W-SITE',386.0,1235.2,0.0330802,0.0,386.0,0.0,151.2,'SITE_TOTAL'),
 ('W-ROD',146.0,467.2,0.0125122,0.0,146.0,0.0,86.4,'COPPER_ROD'),
 ('W-WIRE',240.0,768.0,0.0205680,0.0,240.0,0.0,64.8,'COPPER_WIRE'),
 ('W-CAST',112.0,358.4,0.0095984,0.0,112.0,0.0,86.4,'COPPER_ROD'),
 ('W-DRAW',186.0,595.2,0.0159402,0.0,186.0,0.0,64.8,'COPPER_WIRE'),
 ('G-SITE',3842.0,9412.9,2.1953188,0.0,3842.0,0.0,86.4,'COPPER_ROD'),
 ('G-ROD',3784.0,9270.8,2.1627776,0.0,3784.0,0.0,86.4,'COPPER_ROD'),
 ('G-SMELT',2584.0,6330.8,1.4764976,0.0,2584.0,0.0,86.4,'COPPER_ROD'),
 ('G-HOLD',1200.0,2940.0,0.6856800,0.0,1200.0,0.0,86.4,'COPPER_ROD')
) x(meter_code,consumption,cost,tce,peak,flat,valley,production,stage)
JOIN dim_sensor s ON s.sensor_code=CONCAT('EM-',x.meter_code) JOIN dim_energy_type et ON et.energy_type_id=s.energy_type_id;

INSERT INTO dws_energy_hour(sensor_id,bucket_time,consumption,cost,tce_amount,peak_amount,flat_amount,valley_amount,calculation_method,quality_status,factor_version)
SELECT s.sensor_id,CURRENT_TIMESTAMP - x.hours_ago * INTERVAL '1' HOUR,
 x.ratio,x.ratio*x.price,x.ratio*et.tce_factor,
 CASE WHEN x.period='PEAK' THEN x.ratio ELSE 0 END,CASE WHEN x.period='FLAT' THEN x.ratio ELSE 0 END,CASE WHEN x.period='VALLEY' THEN x.ratio ELSE 0 END,
 'CUMULATIVE_DIFF','GOOD',et.factor_version
FROM dim_sensor s JOIN dim_energy_type et ON et.energy_type_id=s.energy_type_id JOIN (VALUES
 (5,0.75,'VALLEY',0.38),(4,0.86,'FLAT',0.68),(3,1.08,'PEAK',1.08),(2,1.14,'PEAK',1.08),(1,0.98,'FLAT',0.68),(0,1.00,'FLAT',0.68)
) x(hours_ago,ratio,period,price) ON 1=1
WHERE s.energy_scope_type='SITE';
