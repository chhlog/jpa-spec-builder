package io.gitlab.chhyuk.jpa.querybuilder.funtion;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 날짜 파싱을 위한 인터페이스 (프로젝트별 커스터마이징 가능).
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface DateParser {
    /**
     * 기본 날짜 파서 (yyyy-MM-dd 포맷, UTC 시간대).
     *
     * @return DateParser 구현체
     * @since 1.0.0
     */
    static DateParser defaultParser() {
        return defaultParser(ZoneId.of("UTC"));
    }

    /**
     * 지정된 시간대를 사용하는 날짜 파서.
     *
     * @param zoneId 시간대
     * @return DateParser 구현체
     * @since 1.0.0
     */
    static DateParser defaultParser(ZoneId zoneId) {
        if (zoneId == null) {
            throw new IllegalArgumentException("ZoneId cannot be null");
        }
        return date -> {
            if (date == null || date.trim().isEmpty()) {
                throw new DateTimeParseException("Date string cannot be null or empty", date, 0);
            }
            return ZonedDateTime.parse(
                    date.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zoneId));
        };
    }

    /**
     * 날짜 문자열을 ZonedDateTime으로 파싱.
     *
     * @param date 날짜 문자열 (예: "2025-09-18")
     * @return 파싱된 ZonedDateTime 객체
     * @throws DateTimeParseException 잘못된 포맷일 경우
     * @since 1.0.0
     */
    ZonedDateTime parse(String date) throws DateTimeParseException;
}
