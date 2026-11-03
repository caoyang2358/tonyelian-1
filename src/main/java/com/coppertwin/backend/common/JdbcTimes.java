package com.coppertwin.backend.common;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;

public final class JdbcTimes {
    private static final ZoneId SHANGHAI=ZoneId.of("Asia/Shanghai");
    private JdbcTimes() {}

    public static OffsetDateTime offsetDateTime(ResultSet rs,int column) throws SQLException {
        return convert(rs.getObject(column));
    }

    public static OffsetDateTime offsetDateTime(ResultSet rs,String column) throws SQLException {
        return convert(rs.getObject(column));
    }

    private static OffsetDateTime convert(Object value) {
        if (value==null) return null;
        if (value instanceof OffsetDateTime v) return v.atZoneSameInstant(SHANGHAI).toOffsetDateTime();
        if (value instanceof ZonedDateTime v) return v.withZoneSameInstant(SHANGHAI).toOffsetDateTime();
        if (value instanceof Instant v) return v.atZone(SHANGHAI).toOffsetDateTime();
        if (value instanceof Timestamp v) return v.toInstant().atZone(SHANGHAI).toOffsetDateTime();
        if (value instanceof LocalDateTime v) return v.atZone(SHANGHAI).toOffsetDateTime();
        throw new IllegalArgumentException("不支持的数据库时间类型: "+value.getClass().getName());
    }
}
