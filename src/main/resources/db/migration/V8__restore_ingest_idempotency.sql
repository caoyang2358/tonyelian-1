DELETE FROM ods_sensor_data d
WHERE d.source_record_id IS NOT NULL
  AND EXISTS (
    SELECT 1 FROM ods_sensor_data earlier
    WHERE earlier.source_system=d.source_system
      AND earlier.source_record_id=d.source_record_id
      AND earlier.record_id<d.record_id
  );

ALTER TABLE ods_sensor_data
    ADD CONSTRAINT uk_ods_sensor_source_record UNIQUE(source_system,source_record_id);
