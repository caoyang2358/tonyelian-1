INSERT INTO ads_parameter_monitor(sensor_id,parameter_id,workshop_id,equipment_id,unit_code,latest_value,latest_value_text,
 quality_status,limit_status,latest_collect_time,refresh_time)
SELECT s.sensor_id,sp.parameter_id,e.workshop_id,e.equipment_id,sp.unit_code,x.value_num,NULL,'GOOD',x.limit_status,
 CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM (VALUES
 ('TEMP-001','temperature',1176.0,'NORMAL'),('TEMP-002','temperature',1182.0,'NORMAL'),
 ('PRESS-001','pressure',-18.0,'NORMAL'),
 ('LEVEL-001','level',72.6,'NORMAL'),('SPEED-001','speed',32.4,'NORMAL'),
 ('DIAM-ROD-001','diameter',8.00,'NORMAL'),('SPEED-003','speed',23.4,'NORMAL'),
 ('TENSION-003','tension',25.0,'WARNING'),('DIAM-WIRE-002','diameter',1.840,'ALARM')
) x(sensor_code,parameter_code,value_num,limit_status)
JOIN dim_sensor s ON s.sensor_code=x.sensor_code
JOIN dim_sensor_parameter sp ON sp.sensor_id=s.sensor_id AND sp.parameter_code=x.parameter_code
JOIN dim_equipment e ON e.equipment_id=s.equipment_id;

INSERT INTO dws_sensor_parameter_minute(bucket_time,workshop_id,equipment_id,sensor_id,parameter_id,unit_code,
 reading_count,avg_value,min_value,max_value,last_value,last_collect_time,abnormal_count,refresh_time)
SELECT CURRENT_TIMESTAMP - INTERVAL '10' MINUTE,e.workshop_id,e.equipment_id,s.sensor_id,sp.parameter_id,sp.unit_code,
 1,x.value_num-2,x.value_num-2,x.value_num-2,x.value_num-2,CURRENT_TIMESTAMP - INTERVAL '10' MINUTE,0,CURRENT_TIMESTAMP
FROM (VALUES ('TEMP-001','temperature',1176.0),('TEMP-002','temperature',1182.0),('TEMP-003','temperature',1169.0),
 ('PRESS-001','pressure',-18.0),('LEVEL-001','level',72.6),('TENSION-003','tension',25.0)) x(sensor_code,parameter_code,value_num)
JOIN dim_sensor s ON s.sensor_code=x.sensor_code JOIN dim_sensor_parameter sp ON sp.sensor_id=s.sensor_id AND sp.parameter_code=x.parameter_code
JOIN dim_equipment e ON e.equipment_id=s.equipment_id;

INSERT INTO dws_sensor_parameter_minute(bucket_time,workshop_id,equipment_id,sensor_id,parameter_id,unit_code,
 reading_count,avg_value,min_value,max_value,last_value,last_collect_time,abnormal_count,refresh_time)
SELECT CURRENT_TIMESTAMP - INTERVAL '5' MINUTE,e.workshop_id,e.equipment_id,s.sensor_id,sp.parameter_id,sp.unit_code,
 1,x.value_num-1,x.value_num-1,x.value_num-1,x.value_num-1,CURRENT_TIMESTAMP - INTERVAL '5' MINUTE,0,CURRENT_TIMESTAMP
FROM (VALUES ('TEMP-001','temperature',1176.0),('TEMP-002','temperature',1182.0),('TEMP-003','temperature',1169.0),
 ('PRESS-001','pressure',-18.0),('LEVEL-001','level',72.6),('TENSION-003','tension',25.0)) x(sensor_code,parameter_code,value_num)
JOIN dim_sensor s ON s.sensor_code=x.sensor_code JOIN dim_sensor_parameter sp ON sp.sensor_id=s.sensor_id AND sp.parameter_code=x.parameter_code
JOIN dim_equipment e ON e.equipment_id=s.equipment_id;
