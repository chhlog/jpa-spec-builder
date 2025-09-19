package io.gitlab.chhyuk.jpa.querybuilder.funtion;

/**
 * 지원되는 조건 타입.
 *
 * @since 1.0.0
 */
public enum ConditionType {
    EQUAL,
    NOT_EQUAL,
    LIKE,
    NOT_LIKE,
    LIKE_IGNORE_CASE,
    LIKE_START,
    LIKE_END,
    GREATER_THAN,
    GREATER_EQUAL,
    LESS_THAN,
    LESS_EQUAL,
    IN,
    NOT_IN,
    IS_NULL,
    IS_NOT_NULL,
    BETWEEN
}
