-- Restore the process/measurement mappings omitted by the MySQL seed.
UPDATE dim_sensor_parameter AS sp
JOIN dim_sensor AS s ON s.sensor_id=sp.sensor_id
JOIN (
  SELECT 'TEMP-001' AS sensor_code,'temperature' AS parameter_code,'ROD-MELT' AS process_code,0 AS primary_display,1 AS trend_display,1 AS display_order
  UNION ALL SELECT 'TEMP-002','temperature','ROD-MELT',1,1,2
  UNION ALL SELECT 'TEMP-003','temperature','ROD-HOLD',1,1,3
  UNION ALL SELECT 'LEVEL-001','level','ROD-CAST',1,0,0
  UNION ALL SELECT 'SPEED-001','speed','ROD-DRAW',1,1,1
  UNION ALL SELECT 'DIAM-ROD-001','diameter','ROD-ROLL',1,0,0
  UNION ALL SELECT 'SPEED-003','speed','WIRE-PAYOFF',1,0,0
  UNION ALL SELECT 'TENSION-003','tension','WIRE-DRAW',1,1,1
  UNION ALL SELECT 'DIAM-WIRE-002','diameter','WIRE-GAUGE',1,1,1
) AS mapping ON mapping.sensor_code=s.sensor_code AND mapping.parameter_code=sp.parameter_code
JOIN dim_process AS p ON p.process_code=mapping.process_code
SET sp.process_id=p.process_id,sp.primary_display=mapping.primary_display,
    sp.trend_display=mapping.trend_display,sp.display_order=mapping.display_order;
