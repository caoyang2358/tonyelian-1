ALTER TABLE dim_sensor_parameter ADD COLUMN process_id BIGINT REFERENCES dim_process(process_id);
ALTER TABLE dim_sensor_parameter ADD COLUMN primary_display BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE dim_sensor_parameter ADD COLUMN trend_display BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE dim_sensor_parameter ADD COLUMN display_order INT NOT NULL DEFAULT 0;

UPDATE dim_sensor_parameter sp SET
 process_id=(SELECT MIN(b.process_id) FROM dim_process_sensor_binding b WHERE b.parameter_id=sp.parameter_id AND b.enabled=TRUE),
 primary_display=CASE WHEN EXISTS(SELECT 1 FROM dim_process_sensor_binding b WHERE b.parameter_id=sp.parameter_id AND b.enabled=TRUE AND b.display_role='PRIMARY') THEN TRUE ELSE FALSE END,
 trend_display=CASE WHEN EXISTS(SELECT 1 FROM dim_process_sensor_binding b WHERE b.parameter_id=sp.parameter_id AND b.enabled=TRUE AND b.display_role='TREND') THEN TRUE ELSE FALSE END,
 display_order=COALESCE((SELECT MIN(b.display_order) FROM dim_process_sensor_binding b WHERE b.parameter_id=sp.parameter_id AND b.enabled=TRUE AND b.display_role='TREND'),0);

DROP TABLE dim_process_sensor_binding;
