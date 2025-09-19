package io.gitlab.chhyuk.jpa.querybuilder.function;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 기본 타입 변환기 (날짜/시간, 숫자 타입 지원).
 *
 * @since 1.0.0
 */
public class DefaultTypeConverter implements TypeConverter {
    @SuppressWarnings("unchecked")
    public <V> V convert(Object value, Class<V> targetType) {
        if (value == null) return null;
        if (targetType == null) return null;
        if (targetType.isInstance(value)) return (V) value;

        try {
            // 날짜/시간 변환
            if (value instanceof ZonedDateTime zonedDateTime) {
                if (targetType == LocalDate.class) return (V) zonedDateTime.toLocalDate();
                if (targetType == LocalDateTime.class) return (V) zonedDateTime.toLocalDateTime();
            }
            if (value instanceof LocalDate localDate) {
                if (targetType == ZonedDateTime.class) return (V) localDate.atStartOfDay(ZoneId.of("UTC"));
                if (targetType == LocalDateTime.class) return (V) localDate.atStartOfDay();
            }
            if (value instanceof LocalDateTime localDateTime) {
                if (targetType == LocalDate.class) return (V) localDateTime.toLocalDate();
                if (targetType == ZonedDateTime.class) return (V) localDateTime.atZone(ZoneId.of("UTC"));
            }

            // 숫자 타입 변환
            if (value instanceof Number number) {
                if (targetType == Long.class) return (V) Long.valueOf(number.longValue());
                if (targetType == Integer.class) return (V) Integer.valueOf(number.intValue());
                if (targetType == Double.class) return (V) Double.valueOf(number.doubleValue());
                if (targetType == Float.class) return (V) Float.valueOf(number.floatValue());
            }

            // 문자열 변환
            if (value instanceof String str && !str.trim().isEmpty()) {
                if (targetType == Long.class) return (V) Long.valueOf(str);
                if (targetType == Integer.class) return (V) Integer.valueOf(str);
                if (targetType == Double.class) return (V) Double.valueOf(str);
                if (targetType == Float.class) return (V) Float.valueOf(str);
            }

        } catch (NumberFormatException e) {
            // 숫자 변환 실패 시 null 반환 (로깅은 호출자에서 처리)
            return null;
        } catch (IllegalArgumentException e) {
            // 기타 변환 실패 시 null 반환 (로깅은 호출자에서 처리)
            return null;
        }

        return null;
    }
}
