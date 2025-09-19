package io.gitlab.chhyuk.jpa.querybuilder.funtion;

/**
 * 타입 변환을 위한 인터페이스.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface TypeConverter {
    /**
     * 값을 목표 타입으로 변환.
     *
     * @param value 변환할 값
     * @param targetType 목표 타입
     * @return 변환된 값, 실패 시 null
     * @since 1.0.0
     */
    <V> V convert(Object value, Class<V> targetType);
}
