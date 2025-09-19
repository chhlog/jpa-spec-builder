package io.gitlab.chhyuk.jpa.querybuilder.funtion;

import java.util.Collection;
import org.apache.commons.lang3.StringUtils;

/**
 * 조인 조건을 정의하는 클래스.
 *
 * @since 1.0.0
 */
public class JoinCondition {
    private final String field;
    private final ConditionType type;
    private final Object value;
    private final Object secondValue;

    private JoinCondition(String field, ConditionType type, Object value, Object secondValue) {
        if (StringUtils.isBlank(field)) {
            throw new IllegalArgumentException("Field name cannot be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Condition type cannot be null");
        }

        // 특정 조건 타입에 대한 값 검증
        validateConditionValues(type, value, secondValue);

        this.field = field.trim();
        this.type = type;
        this.value = value;
        this.secondValue = secondValue;
    }

    /**
     * EQUAL 조건 생성. 예: JoinCondition.equal("userId", "user123")
     *
     * @param field 필드 이름
     * @param value 비교 값
     * @return JoinCondition 객체
     * @since 1.0.0
     */
    public static JoinCondition equal(String field, Object value) {
        return new JoinCondition(field, ConditionType.EQUAL, value, null);
    }

    /**
     * NOT_EQUAL 조건 생성. 예: JoinCondition.notEqual("role", "GUEST")
     *
     * @param field 필드 이름
     * @param value 비교 값
     * @return JoinCondition 객체
     * @since 1.0.0
     */
    public static JoinCondition notEqual(String field, Object value) {
        return new JoinCondition(field, ConditionType.NOT_EQUAL, value, null);
    }

    /**
     * LIKE 조건 생성 (%value% 패턴). 예: JoinCondition.like("name", "김")
     *
     * @param field 필드 이름
     * @param value 검색 문자열
     * @return JoinCondition 객체
     * @since 1.0.0
     */
    public static JoinCondition like(String field, String value) {
        return new JoinCondition(field, ConditionType.LIKE, value, null);
    }

    /**
     * NOT_LIKE 조건 생성 (%value% 패턴). 예: JoinCondition.notLike("description", "test")
     *
     * @param field 필드 이름
     * @param value 검색 문자열
     * @return JoinCondition 객체
     * @since 1.0.0
     */
    public static JoinCondition notLike(String field, String value) {
        return new JoinCondition(field, ConditionType.NOT_LIKE, value, null);
    }

    /**
     * LIKE_IGNORE_CASE 조건 생성 (%value% 패턴, 대소문자 무시). 예: JoinCondition.likeIgnoreCase("description",
     * "test")
     *
     * @param field 필드 이름
     * @param value 검색 문자열
     * @return JoinCondition 객체
     * @since 1.0.0
     */
    public static JoinCondition likeIgnoreCase(String field, String value) {
        return new JoinCondition(field, ConditionType.LIKE_IGNORE_CASE, value, null);
    }

    /**
     * LIKE_START 조건 생성 (value% 패턴). 예: JoinCondition.likeStart("email", "user@")
     *
     * @param field 필드 이름
     * @param value 검색 문자열
     * @return JoinCondition 객체
     * @since 1.0.0
     */
    public static JoinCondition likeStart(String field, String value) {
        return new JoinCondition(field, ConditionType.LIKE_START, value, null);
    }

    /**
     * LIKE_END 조건 생성 (%value 패턴). 예: JoinCondition.likeEnd("phone", "1234")
     *
     * @param field 필드 이름
     * @param value 검색 문자열
     * @return JoinCondition 객체
     * @since 1.0.0
     */
    public static JoinCondition likeEnd(String field, String value) {
        return new JoinCondition(field, ConditionType.LIKE_END, value, null);
    }

    /**
     * GREATER_THAN 조건 생성. 예: JoinCondition.greaterThan("age", 18)
     *
     * @param field 필드 이름
     * @param value 비교 값 (Comparable 구현 필요)
     * @param <T> Comparable 타입
     * @return JoinCondition 객체
     * @since 1.0.0
     */
    public static <T extends Comparable<? super T>> JoinCondition greaterThan(String field, T value) {
        return new JoinCondition(field, ConditionType.GREATER_THAN, value, null);
    }

    /**
     * GREATER_EQUAL 조건 생성. 예: JoinCondition.greaterEqual("salary", 50000)
     *
     * @param field 필드 이름
     * @param value 비교 값 (Comparable 구현 필요)
     * @param <T> Comparable 타입
     * @return JoinCondition 객체
     * @since 1.0.0
     */
    public static <T extends Comparable<? super T>> JoinCondition greaterEqual(String field, T value) {
        return new JoinCondition(field, ConditionType.GREATER_EQUAL, value, null);
    }

    /**
     * LESS_THAN 조건 생성. 예: JoinCondition.lessThan("createdAt", ZonedDateTime.now())
     *
     * @param field 필드 이름
     * @param value 비교 값 (Comparable 구현 필요)
     * @param <T> Comparable 타입
     * @return JoinCondition 객체
     * @since 1.0.0
     */
    public static <T extends Comparable<? super T>> JoinCondition lessThan(String field, T value) {
        return new JoinCondition(field, ConditionType.LESS_THAN, value, null);
    }

    /**
     * LESS_EQUAL 조건 생성. 예: JoinCondition.lessEqual("updatedAt", ZonedDateTime.now())
     *
     * @param field 필드 이름
     * @param value 비교 값 (Comparable 구현 필요)
     * @param <T> Comparable 타입
     * @return JoinCondition 객체
     * @since 1.0.0
     */
    public static <T extends Comparable<? super T>> JoinCondition lessEqual(String field, T value) {
        return new JoinCondition(field, ConditionType.LESS_EQUAL, value, null);
    }

    /**
     * IN 조건 생성. 예: JoinCondition.in("status", List.of("ACTIVE", "PENDING"))
     *
     * @param field 필드 이름
     * @param values 값 컬렉션
     * @return JoinCondition 객체
     * @since 1.0.0
     */
    public static JoinCondition in(String field, Collection<?> values) {
        return new JoinCondition(field, ConditionType.IN, values, null);
    }

    /**
     * NOT_IN 조건 생성. 예: JoinCondition.notIn("department", List.of("HR", "SALES"))
     *
     * @param field 필드 이름
     * @param values 값 컬렉션
     * @return JoinCondition 객체
     * @since 1.0.0
     */
    public static JoinCondition notIn(String field, Collection<?> values) {
        return new JoinCondition(field, ConditionType.NOT_IN, values, null);
    }

    /**
     * IS_NULL 조건 생성. 예: JoinCondition.isNull("deletedAt")
     *
     * @param field 필드 이름
     * @return JoinCondition 객체
     * @since 1.0.0
     */
    public static JoinCondition isNull(String field) {
        return new JoinCondition(field, ConditionType.IS_NULL, null, null);
    }

    /**
     * IS_NOT_NULL 조건 생성. 예: JoinCondition.isNotNull("lastLogin")
     *
     * @param field 필드 이름
     * @return JoinCondition 객체
     * @since 1.0.0
     */
    public static JoinCondition isNotNull(String field) {
        return new JoinCondition(field, ConditionType.IS_NOT_NULL, null, null);
    }

    /**
     * BETWEEN 조건 생성. 예: JoinCondition.between("createdAt", ZonedDateTime.now().minusDays(30),
     * ZonedDateTime.now())
     *
     * @param field 필드 이름
     * @param start 시작 값 (Comparable 구현 필요)
     * @param end 종료 값 (Comparable 구현 필요)
     * @param <T> Comparable 타입
     * @return JoinCondition 객체
     * @since 1.0.0
     */
    public static <T extends Comparable<? super T>> JoinCondition between(String field, T start, T end) {
        return new JoinCondition(field, ConditionType.BETWEEN, start, end);
    }

    private void validateConditionValues(ConditionType type, Object value, Object secondValue) {
        switch (type) {
            case BETWEEN:
                if (value == null || secondValue == null) {
                    throw new IllegalArgumentException("BETWEEN condition requires both start and end values");
                }
                if (value instanceof Comparable<?> && secondValue instanceof Comparable<?>) {
                    // 타입이 같은지 확인
                    if (!value.getClass().equals(secondValue.getClass())) {
                        throw new IllegalArgumentException("BETWEEN values must be of the same type");
                    }
                }
                break;
            case IN, NOT_IN:
                if (value != null && value instanceof Collection<?> collection && collection.isEmpty()) {
                    throw new IllegalArgumentException("IN/NOT_IN condition cannot have empty collection");
                }
                break;
            case IS_NULL, IS_NOT_NULL:
                // NULL 조건은 값이 없어야 함
                break;
            default:
                // 다른 조건들은 IS_NULL, IS_NOT_NULL이 아닌 경우 값이 필요
                if (type != ConditionType.IS_NULL && type != ConditionType.IS_NOT_NULL && value == null) {
                    throw new IllegalArgumentException("Condition " + type + " requires a non-null value");
                }
        }
    }

    public String getField() {
        return field;
    }

    public ConditionType getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    public Object getSecondValue() {
        return secondValue;
    }
}
