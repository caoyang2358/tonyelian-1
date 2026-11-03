INSERT INTO dim_workshop(workshop_code,workshop_name,workshop_type) VALUES
 ('WS-ROD','车间1 · 铜杆生产','熔炼'),('WS-WIRE','车间2 · 铜丝拉制','拉丝');

INSERT INTO dim_unit(unit_code,unit_name,unit_category,base_unit_code,conversion_factor) VALUES
 ('C','摄氏度','TEMPERATURE','C',1),('Pa','帕','PRESSURE','Pa',1),('kPa','千帕','PRESSURE','Pa',1000),
 ('PCT','百分比','RATIO','PCT',1),('M_PER_MIN','米每分钟','SPEED','M_PER_MIN',1),
 ('M_PER_SEC','米每秒','SPEED','M_PER_MIN',60),('N','牛','FORCE','N',1),('MM','毫米','LENGTH','MM',1);

INSERT INTO dim_process(process_code,process_name,workshop_id,sequence_no,description)
SELECT x.code,x.name,w.workshop_id,x.seq,x.description FROM dim_workshop w JOIN (
 VALUES
 ('WS-ROD','ROD-FEED','铜板上料',1,'铜板上料与升降'),('WS-ROD','ROD-MELT','熔化炉熔化',2,'铜板熔化为铜液'),
 ('WS-ROD','ROD-HOLD','保温炉保温',3,'铜液输送与保温'),('WS-ROD','ROD-CAST','结晶机水冷',4,'结晶冷却'),
 ('WS-ROD','ROD-DRAW','牵引粗铜棒',5,'粗铜坯牵引'),('WS-ROD','ROD-ROLL','多道轧制',6,'轧制至8mm'),
 ('WS-ROD','ROD-COOL','冷却清洗',7,'冷却清洗还原'),('WS-ROD','ROD-COIL','铜杆收卷',8,'铜杆收卷'),
 ('WS-WIRE','WIRE-RECEIVE','Φ8铜杆卷接收',1,'接收车间1成品'),('WS-WIRE','WIRE-PAYOFF','上卷放线',2,'上卷放线'),
 ('WS-WIRE','WIRE-DRAW','多道次冷拉',3,'客户规格冷拉'),('WS-WIRE','WIRE-GAUGE','在线测径',4,'在线测量线径'),
 ('WS-WIRE','WIRE-COIL','成品收卷',5,'细铜丝收卷'),('WS-WIRE','WIRE-INSPECT','客户规格检验',6,'成品规格检验')
) x(workshop_code,code,name,seq,description) ON w.workshop_code=x.workshop_code;

INSERT INTO dim_equipment(equipment_code,equipment_name,workshop_id,responsible_person)
SELECT x.code,x.name,w.workshop_id,'待确认' FROM dim_workshop w JOIN (VALUES
 ('WS-ROD','SMELT-FUR-01','1#熔化炉'),('WS-ROD','HOLD-FUR-01','1#保温炉'),
 ('WS-ROD','CAST-01','1#结晶牵引机'),('WS-ROD','ROLL-01','1#多道轧机组'),
 ('WS-WIRE','DRAW-01','1#拉丝机'),('WS-WIRE','DRAW-02','2#拉丝机'),
 ('WS-WIRE','DRAW-03','3#拉丝机'),('WS-WIRE','GAUGE-02','2#测径仪')
) x(workshop_code,code,name) ON w.workshop_code=x.workshop_code;

INSERT INTO dim_sensor(sensor_code,sensor_name,equipment_id,sensor_type,data_source)
SELECT x.code,x.name,e.equipment_id,x.type,'SIMULATOR' FROM dim_equipment e JOIN (VALUES
 ('SMELT-FUR-01','TEMP-001','炉膛上部温度','温度'),('SMELT-FUR-01','TEMP-002','炉膛中部温度','温度'),
 ('SMELT-FUR-01','TEMP-003','炉膛底部温度','温度'),('SMELT-FUR-01','PRESS-001','炉压','压力'),
 ('SMELT-FUR-01','LEVEL-001','铜液液位','液位'),('CAST-01','SPEED-001','牵引速度','速度'),
 ('ROLL-01','DIAM-ROD-001','轧制出口线径','线径'),('DRAW-03','SPEED-003','拉丝线速度','速度'),
 ('DRAW-03','TENSION-003','拉丝张力','张力'),('GAUGE-02','DIAM-WIRE-002','出线线径','线径')
) x(equipment_code,code,name,type) ON e.equipment_code=x.equipment_code;

INSERT INTO dim_sensor_parameter(sensor_id,parameter_code,parameter_name,data_type,unit_code,decimal_places,
 normal_lower_limit,normal_upper_limit,alarm_lower_limit,alarm_upper_limit,sampling_frequency_seconds)
SELECT s.sensor_id,x.parameter_code,x.parameter_name,'DECIMAL',x.unit_code,x.places,x.normal_low,x.normal_high,x.alarm_low,x.alarm_high,10
FROM dim_sensor s JOIN (VALUES
 ('TEMP-001','temperature','上部温度','C',0,1100,1220,1060,1260),
 ('TEMP-002','temperature','中部温度','C',0,1100,1220,1060,1260),
 ('TEMP-003','temperature','底部温度','C',0,1090,1210,1050,1250),
 ('PRESS-001','pressure','炉压','Pa',0,-25,-10,-35,0),
 ('LEVEL-001','level','铜液液位','PCT',1,60,85,50,92),
 ('SPEED-001','speed','牵引速度','M_PER_MIN',1,28,36,24,40),
 ('DIAM-ROD-001','diameter','铜杆线径','MM',2,7.95,8.05,7.90,8.10),
 ('SPEED-003','speed','拉丝线速度','M_PER_SEC',1,20,26,18,28),
 ('TENSION-003','tension','拉丝张力','N',1,17,24,14,27),
 ('DIAM-WIRE-002','diameter','出线线径','MM',3,1.815,1.825,1.810,1.830)
) x(sensor_code,parameter_code,parameter_name,unit_code,places,normal_low,normal_high,alarm_low,alarm_high)
ON s.sensor_code=x.sensor_code;

INSERT INTO dim_process_sensor_binding(process_id,sensor_id,parameter_id,display_role,display_order,enabled,primary_process_id)
SELECT p.process_id,s.sensor_id,sp.parameter_id,'PRIMARY',0,TRUE,p.process_id
FROM (VALUES
 ('ROD-MELT','TEMP-002','temperature'),('ROD-HOLD','TEMP-003','temperature'),('ROD-CAST','LEVEL-001','level'),
 ('ROD-DRAW','SPEED-001','speed'),('ROD-ROLL','DIAM-ROD-001','diameter'),
 ('WIRE-PAYOFF','SPEED-003','speed'),('WIRE-DRAW','TENSION-003','tension'),('WIRE-GAUGE','DIAM-WIRE-002','diameter')
) x(process_code,sensor_code,parameter_code)
JOIN dim_process p ON p.process_code=x.process_code JOIN dim_sensor s ON s.sensor_code=x.sensor_code
JOIN dim_sensor_parameter sp ON sp.sensor_id=s.sensor_id AND sp.parameter_code=x.parameter_code;

INSERT INTO dim_process_sensor_binding(process_id,sensor_id,parameter_id,display_role,display_order,enabled,primary_process_id)
SELECT p.process_id,s.sensor_id,sp.parameter_id,'TREND',x.ord,TRUE,NULL
FROM (VALUES
 ('ROD-MELT','TEMP-001','temperature',1),('ROD-MELT','TEMP-002','temperature',2),('ROD-MELT','TEMP-003','temperature',3),
 ('ROD-DRAW','SPEED-001','speed',1),('WIRE-DRAW','TENSION-003','tension',1),('WIRE-GAUGE','DIAM-WIRE-002','diameter',1)
) x(process_code,sensor_code,parameter_code,ord)
JOIN dim_process p ON p.process_code=x.process_code JOIN dim_sensor s ON s.sensor_code=x.sensor_code
JOIN dim_sensor_parameter sp ON sp.sensor_id=s.sensor_id AND sp.parameter_code=x.parameter_code;
