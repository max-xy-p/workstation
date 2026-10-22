package com.mike.workstation.assembler;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** 把数据库使用的本地时间转换成带 UTC Offset 的 API 时间。 */
public final class ApiTime {
    private ApiTime() {}

    public static OffsetDateTime toOffset(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
