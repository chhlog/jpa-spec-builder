package io.gitlab.chhyuk.jpa.querybuilder;

import io.gitlab.chhyuk.jpa.querybuilder.function.ConditionType;
import io.gitlab.chhyuk.jpa.querybuilder.function.DateParser;
import io.gitlab.chhyuk.jpa.querybuilder.function.DefaultTypeConverter;
import io.gitlab.chhyuk.jpa.querybuilder.function.JoinCondition;
import io.gitlab.chhyuk.jpa.querybuilder.function.TypeConverter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA Specification을 위한 유틸리티 클래스. 다양한 프로젝트에서 재사용 가능하도록 설계됨. 모든 검색 조건은 null 또는 공백 입력 시 기본적으로 조건을
 * 무시(conjunction)하며, 명시적 NULL 검색을 위해 별도 메서드 제공.
 *
 * @since 1.0.0
 */
public class SpecificationQueryBuilder {

  private static final Logger log = LoggerFactory.getLogger(SpecificationQueryBuilder.class);

  /**
   * 지정된 필드와 값이 동일한 조건.
   *
   * @param propertyName 필드 이름
   * @param value 비교 값 (null 시 조건 무시)
   * @return Specification 객체
   * @since 1.0.0
   */
  public static <T> Specification<T> equal(String propertyName, Object value) {
    return (root, query, builder) -> {
      if (value == null) {
        log.debug("Skipping equal condition for property: {}, value is null", propertyName);
        return builder.conjunction();
      }
      return builder.equal(root.get(propertyName), value);
    };
  }

  // ========== 빌더 클래스 ==========

  /**
   * 지정된 필드가 NULL인 조건 (IS NULL).
   *
   * @param propertyName 필드 이름
   * @return Specification 객체
   * @since 1.0.0
   */
  public static <T> Specification<T> isNull(String propertyName) {
    return (root, query, builder) -> {
      log.debug("Applying IS NULL condition for property: {}", propertyName);
      return builder.isNull(root.get(propertyName));
    };
  }

  /**
   * 대소문자를 무시하는 LIKE 조건 (공백, null 체크 포함).
   *
   * @param propertyName 검색 대상 필드
   * @param value 검색 값 (null, "", 공백만 포함 시 조건 무시)
   * @return Specification 객체
   * @since 1.0.0
   */
  public static <T> Specification<T> likeIgnoreCase(String propertyName, String value) {
    return (root, query, builder) -> {
      if (StringUtils.isBlank(value)) {
        log.debug("Skipping LIKE condition for property: {}, value is blank", propertyName);
        return builder.conjunction();
      }
      Expression<String> field = builder.lower(root.get(propertyName).as(String.class));
      String pattern = StringUtils.wrap(value.trim(), "%");
      return builder.like(field, pattern);
    };
  }

  /**
   * 조인된 엔티티의 ID로 필터링.
   *
   * @param joinProperty 조인 대상 필드
   * @param idProperty ID 필드 이름
   * @param value 비교 값 (null 시 조건 무시)
   * @return Specification 객체
   * @since 1.0.0
   */
  public static <T> Specification<T> equalJoinId(
      String joinProperty, String idProperty, Object value) {
    return (root, query, builder) -> {
      if (value == null) {
        log.debug("Skipping equalJoinId condition for join: {}, id: {}", joinProperty, idProperty);
        return builder.conjunction();
      }
      try {
        return builder.equal(root.get(joinProperty).get(idProperty), value);
      } catch (IllegalArgumentException e) {
        log.warn("Invalid join property: {} or id property: {}", joinProperty, idProperty, e);
        return builder.conjunction();
      }
    };
  }

  /**
   * Enum을 문자열로 변환하여 비교.
   *
   * @param propertyName 필드 이름
   * @param enumValue Enum 값 (null 시 조건 무시)
   * @return Specification 객체
   * @since 1.0.0
   */
  public static <T> Specification<T> equalEnum(String propertyName, Enum<?> enumValue) {
    return (root, query, builder) -> {
      if (enumValue == null) {
        log.debug("Skipping equalEnum condition for property: {}, value is null", propertyName);
        return builder.conjunction();
      }
      return builder.equal(root.get(propertyName), enumValue.name());
    };
  }

  /**
   * 날짜 범위 조건 (포함 범위: startDate &lt;= x &lt; endDate + 1).
   *
   * @param propertyName 날짜 필드
   * @param startDate 시작 날짜 (예: "2025-09-01", null 시 무시)
   * @param endDate 종료 날짜 (예: "2025-09-30", null 시 무시)
   * @param dateParser 프로젝트별 날짜 파서 (기본: yyyy-MM-dd, 시스템 시간대)
   * @return Specification 객체
   * @throws DateTimeParseException 잘못된 날짜 포맷일 경우
   * @since 1.0.0
   */
  public static <T> Specification<T> dateRangeBetween(
      String propertyName, String startDate, String endDate, DateParser dateParser) {
    return (root, query, builder) -> {
      List<Predicate> predicates = new ArrayList<>();

      try {
        if (StringUtils.isNotBlank(startDate)) {
          ZonedDateTime start = dateParser.parse(startDate);
          predicates.add(builder.greaterThanOrEqualTo(root.get(propertyName), start));
        }
        if (StringUtils.isNotBlank(endDate)) {
          ZonedDateTime end = dateParser.parse(endDate);
          // 종료 날짜는 exclusive이므로 하루를 추가
          predicates.add(builder.lessThan(root.get(propertyName), end.plusDays(1)));
        }
      } catch (DateTimeParseException e) {
        log.warn(
            "Invalid date format for property: {}, startDate: {}, endDate: {}",
            propertyName,
            startDate,
            endDate,
            e);
        return builder.conjunction();
      }

      return predicates.isEmpty()
          ? builder.conjunction()
          : builder.and(predicates.toArray(new Predicate[0]));
    };
  }

  /**
   * 복잡한 조인 조건 처리 (LEFT JOIN 기본값).
   *
   * @param joinProperty 조인 대상 필드
   * @param conditions 조인 조건 리스트
   * @return Specification 객체
   * @since 1.0.0
   */
  public static <T> Specification<T> joinWithConditions(
      String joinProperty, List<JoinCondition> conditions) {
    return joinWithConditions(joinProperty, conditions, JoinType.LEFT);
  }

  /**
   * 정렬 조건을 위한 정적 메서드 (Direction 사용). 예: orderBy("createdAt", Direction.DESC)
   *
   * @param field 정렬할 필드명
   * @param direction 정렬 방향
   * @return Specification 객체
   * @since 1.2.0
   */
  public static <T> Specification<T> orderBy(String field, Direction direction) {
    return (root, query, builder) -> {
      if (StringUtils.isNotBlank(field)) {
        Path<?> path = root.get(field);
        Order order = direction == Direction.DESC ? builder.desc(path) : builder.asc(path);
        query.orderBy(order);
        log.debug("Applied ORDER BY condition: {} {}", field, direction.name());
      } else {
        log.debug("Skipping ORDER BY condition, field is blank");
      }
      return builder.conjunction();
    };
  }

  /**
   * 복잡한 조인 조건 처리 (JOIN 타입 지정 가능). 예: joinWithConditions("members", List.of(
   * JoinCondition.equal("userId", "user123"), JoinCondition.like("name", "김"),
   * JoinCondition.likeIgnoreCase("description", "test"), JoinCondition.in("status",
   * List.of("ACTIVE", "PENDING")), JoinCondition.isNull("deletedAt"),
   * JoinCondition.between("createdAt", ZonedDateTime.now().minusDays(30), ZonedDateTime.now()) ))
   *
   * @param joinProperty 조인 대상 필드
   * @param conditions 조인 조건 리스트
   * @param joinType 조인 타입 (INNER, LEFT, RIGHT)
   * @return Specification 객체
   * @since 1.0.0
   */
  public static <T> Specification<T> joinWithConditions(
      String joinProperty, List<JoinCondition> conditions, JoinType joinType) {
    return (root, query, builder) -> {
      if (conditions == null || conditions.isEmpty()) {
        log.debug("No conditions provided for join on property: {}, skipping join", joinProperty);
        return builder.conjunction();
      }

      List<JoinCondition> validConditions =
          conditions.stream()
              .filter(
                  c ->
                      c.getValue() != null
                          || c.getType() == ConditionType.IS_NULL
                          || c.getType() == ConditionType.IS_NOT_NULL)
              .toList();

      if (validConditions.isEmpty()) {
        log.debug("No valid conditions for join on property: {}, skipping join", joinProperty);
        return builder.conjunction();
      }

      try {
        Join<T, ?> join = root.join(joinProperty, joinType);
        query.distinct(true); // 중복 제거
        List<Predicate> predicates =
            validConditions.stream().map(c -> createPredicate(join, c, builder)).toList();
        return builder.and(predicates.toArray(new Predicate[0]));
      } catch (IllegalArgumentException e) {
        log.warn("Invalid join property: {}", joinProperty, e);
        return builder.conjunction();
      }
    };
  }

  /**
   * JoinCondition을 Predicate로 변환.
   *
   * @since 1.0.0
   */
  @SuppressWarnings("unchecked")
  private static <T> Predicate createPredicate(
      Join<T, ?> join, JoinCondition condition, CriteriaBuilder builder) {
    String field = condition.getField();
    Object value = condition.getValue();

    try {
      return switch (condition.getType()) {
        case EQUAL -> builder.equal(join.get(field), value);
        case NOT_EQUAL -> builder.notEqual(join.get(field), value);
        case LIKE ->
            builder.like(join.get(field).as(String.class), StringUtils.wrap((String) value, "%"));
        case NOT_LIKE ->
            builder.notLike(
                join.get(field).as(String.class), StringUtils.wrap((String) value, "%"));
        case LIKE_IGNORE_CASE -> {
          if (StringUtils.isBlank((String) value)) {
            log.debug("Skipping LIKE_IGNORE_CASE condition for field: {}, value is blank", field);
            yield builder.conjunction();
          }
          Expression<String> fieldExpr = builder.lower(join.get(field).as(String.class));
          String pattern = StringUtils.wrap(((String) value).trim().toLowerCase(), "%");
          yield builder.like(fieldExpr, pattern);
        }
        case LIKE_START -> builder.like(join.get(field).as(String.class), value + "%");
        case LIKE_END -> builder.like(join.get(field).as(String.class), "%" + value);
        case GREATER_THAN -> {
          Path<?> path = join.get(field);
          if (value != null && !path.getJavaType().isInstance(value)) {
            log.warn(
                "Type mismatch for field: {}, expected: {}, got: {}",
                field,
                path.getJavaType().getName(),
                value.getClass().getName());
            yield builder.conjunction();
          }
          if (!(value instanceof Comparable)) {
            log.warn(
                "Value for GREATER_THAN must be Comparable, field: {}, value: {}", field, value);
            yield builder.conjunction();
          }
          Comparable<Object> comparableValue = (Comparable<Object>) value;
          yield builder.greaterThan(join.get(field), comparableValue);
        }
        case GREATER_EQUAL -> {
          Path<?> path = join.get(field);
          if (value != null && !path.getJavaType().isInstance(value)) {
            log.warn(
                "Type mismatch for field: {}, expected: {}, got: {}",
                field,
                path.getJavaType().getName(),
                value.getClass().getName());
            yield builder.conjunction();
          }
          if (!(value instanceof Comparable)) {
            log.warn(
                "Value for GREATER_EQUAL must be Comparable, field: {}, value: {}", field, value);
            yield builder.conjunction();
          }
          Comparable<Object> comparableValue = (Comparable<Object>) value;
          yield builder.greaterThanOrEqualTo(join.get(field), comparableValue);
        }
        case LESS_THAN -> {
          Path<?> path = join.get(field);
          if (value != null && !path.getJavaType().isInstance(value)) {
            log.warn(
                "Type mismatch for field: {}, expected: {}, got: {}",
                field,
                path.getJavaType().getName(),
                value.getClass().getName());
            yield builder.conjunction();
          }
          if (!(value instanceof Comparable)) {
            log.warn("Value for LESS_THAN must be Comparable, field: {}, value: {}", field, value);
            yield builder.conjunction();
          }
          Comparable<Object> comparableValue = (Comparable<Object>) value;
          yield builder.lessThan(join.get(field), comparableValue);
        }
        case LESS_EQUAL -> {
          Path<?> path = join.get(field);
          if (value != null && !path.getJavaType().isInstance(value)) {
            log.warn(
                "Type mismatch for field: {}, expected: {}, got: {}",
                field,
                path.getJavaType().getName(),
                value.getClass().getName());
            yield builder.conjunction();
          }
          if (!(value instanceof Comparable)) {
            log.warn("Value for LESS_EQUAL must be Comparable, field: {}, value: {}", field, value);
            yield builder.conjunction();
          }
          Comparable<Object> comparableValue = (Comparable<Object>) value;
          yield builder.lessThanOrEqualTo(join.get(field), comparableValue);
        }
        case IN -> {
          CriteriaBuilder.In<Object> inClause = builder.in(join.get(field));
          if (value instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
              log.debug("Skipping IN condition for field: {}, values are empty", field);
              yield builder.conjunction();
            }
            collection.forEach(inClause::value);
          } else if (value.getClass().isArray()) {
            Object[] array = (Object[]) value;
            if (array.length == 0) {
              log.debug("Skipping IN condition for field: {}, values are empty", field);
              yield builder.conjunction();
            }
            Arrays.stream(array).forEach(inClause::value);
          } else {
            inClause.value(value);
          }
          yield inClause;
        }
        case NOT_IN -> {
          CriteriaBuilder.In<Object> inClause = builder.in(join.get(field));
          if (value instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
              log.debug("Skipping NOT_IN condition for field: {}, values are empty", field);
              yield builder.conjunction();
            }
            collection.forEach(inClause::value);
          } else if (value.getClass().isArray()) {
            Object[] array = (Object[]) value;
            if (array.length == 0) {
              log.debug("Skipping NOT_IN condition for field: {}, values are empty", field);
              yield builder.conjunction();
            }
            Arrays.stream(array).forEach(inClause::value);
          } else {
            inClause.value(value);
          }
          yield builder.not(inClause);
        }
        case IS_NULL -> builder.isNull(join.get(field));
        case IS_NOT_NULL -> builder.isNotNull(join.get(field));
        case BETWEEN -> {
          Object secondValue = condition.getSecondValue();
          Path<?> path = join.get(field);
          if (value != null && !path.getJavaType().isInstance(value)) {
            log.warn(
                "Type mismatch for BETWEEN value in field: {}, expected: {}, got: {}",
                field,
                path.getJavaType().getName(),
                value.getClass().getName());
            yield builder.conjunction();
          }
          if (secondValue != null && !path.getJavaType().isInstance(secondValue)) {
            log.warn(
                "Type mismatch for BETWEEN secondValue in field: {}, expected: {}, got: {}",
                field,
                path.getJavaType().getName(),
                secondValue.getClass().getName());
            yield builder.conjunction();
          }
          if (!(value instanceof Comparable)
              || !(condition.getSecondValue() instanceof Comparable)) {
            log.warn("Values for BETWEEN must be Comparable, field: {}, value: {}", field, value);
            yield builder.conjunction();
          }
          Comparable<Object> startValue = (Comparable<Object>) value;
          Comparable<Object> endValue = (Comparable<Object>) condition.getSecondValue();
          yield builder.between(join.get(field), startValue, endValue);
        }
      };
    } catch (IllegalArgumentException e) {
      log.warn("Failed to create predicate for field: {}, type: {}", field, condition.getType(), e);
      return builder.conjunction();
    }
  }

  /** 정렬 정보를 담는 내부 클래스. */
  private static class OrderInfo {
    private final String field;
    private final String direction;
    private final Direction sortDirection;

    public OrderInfo(String field, String direction) {
      this.field = field;
      this.direction = direction != null ? direction.toUpperCase() : "ASC";
      this.sortDirection = "DESC".equals(this.direction) ? Direction.DESC : Direction.ASC;
    }

    public OrderInfo(String field, Direction direction) {
      this.field = field;
      this.sortDirection = direction != null ? direction : Direction.ASC;
      this.direction = this.sortDirection.name();
    }

    public String getField() {
      return field;
    }

    public String getDirection() {
      return direction;
    }

    public Direction getSortDirection() {
      return sortDirection;
    }

    public boolean isAscending() {
      return sortDirection == Direction.ASC;
    }
  }

  /**
   * 특정 엔티티 타입에 대한 Specification을 체이닝 방식으로 생성하는 빌더.
   *
   * @param <T> 엔티티 타입
   * @since 1.0.0
   */
  public static class Builder<T> {
    private final List<Specification<T>> specifications;
    private final Class<T> entityClass;
    private final EntityManager entityManager;
    private final TypeConverter typeConverter = new DefaultTypeConverter();

    // Phase 1: 정렬, 페이징, 로깅을 위한 필드
    private final List<OrderInfo> orderByList = new ArrayList<>();
    private Integer limitValue;
    private Integer offsetValue;
    private boolean enableQueryLogging = false;

    private Builder(Class<T> entityClass, EntityManager entityManager) {
      this.entityClass = entityClass;
      this.entityManager = entityManager;
      this.specifications = new ArrayList<>();
    }

    /**
     * 새 빌더 인스턴스 생성 (Metamodel 지원). 예: Builder.create(WorkspaceInvite.class,
     * entityManager).equal("email", "test@example.com").build()
     *
     * @param entityClass 엔티티 클래스
     * @param entityManager EntityManager (Metamodel 접근용)
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public static <T> Builder<T> create(Class<T> entityClass, EntityManager entityManager) {
      return new Builder<>(entityClass, entityManager);
    }

    /**
     * 새 빌더 인스턴스 생성 (기존 방식, Metamodel 미사용). 타입 변환이나 Metamodel 검증이 필요 없는 경우 사용. 예:
     * Builder.&lt;User&gt;create().equal("email", "test@example.com").build()
     *
     * @param <T> 엔티티 타입
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public static <T> Builder<T> create() {
      return new Builder<>(null, null);
    }

    /**
     * EQUAL 조건 추가. 예: builder.equal("email", "test@example.com")
     *
     * @param propertyName 필드 이름
     * @param value 비교 값 (null인 경우 조건 무시)
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public Builder<T> equal(String propertyName, Object value) {
      specifications.add(
          (root, query, builder) -> {
            if (value == null) {
              log.debug("Skipping equal condition for property: {}, value is null", propertyName);
              return builder.conjunction();
            }
            return builder.equal(root.get(propertyName), value);
          });
      return this;
    }

    /**
     * NOT_EQUAL 조건 추가. 예: builder.notEqual("role", "GUEST")
     *
     * @param propertyName 필드 이름
     * @param value 비교 값
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public Builder<T> notEqual(String propertyName, Object value) {
      specifications.add(
          (root, query, builder) -> {
            if (value == null) {
              log.debug(
                  "Skipping notEqual condition for property: {}, value is null", propertyName);
              return builder.conjunction();
            }
            return builder.notEqual(root.get(propertyName), value);
          });
      return this;
    }

    /**
     * LIKE 조건 추가 (%value% 패턴). 예: builder.like("name", "김")
     *
     * @param propertyName 필드 이름
     * @param value 검색 문자열
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public Builder<T> like(String propertyName, String value) {
      specifications.add(
          (root, query, builder) -> {
            if (StringUtils.isBlank(value)) {
              log.debug("Skipping LIKE condition for property: {}, value is blank", propertyName);
              return builder.conjunction();
            }
            return builder.like(
                root.get(propertyName).as(String.class), StringUtils.wrap(value, "%"));
          });
      return this;
    }

    /**
     * NOT_LIKE 조건 추가 (%value% 패턴). 예: builder.notLike("description", "test")
     *
     * @param propertyName 필드 이름
     * @param value 검색 문자열
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public Builder<T> notLike(String propertyName, String value) {
      specifications.add(
          (root, query, builder) -> {
            if (StringUtils.isBlank(value)) {
              log.debug(
                  "Skipping NOT_LIKE condition for property: {}, value is blank", propertyName);
              return builder.conjunction();
            }
            return builder.notLike(
                root.get(propertyName).as(String.class), StringUtils.wrap(value, "%"));
          });
      return this;
    }

    /**
     * LIKE_IGNORE_CASE 조건 추가 (%value% 패턴, 대소문자 무시). 예: builder.likeIgnoreCase("name", "김")
     *
     * @param propertyName 필드 이름
     * @param value 검색 문자열
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public Builder<T> likeIgnoreCase(String propertyName, String value) {
      specifications.add(
          (root, query, builder) -> {
            if (StringUtils.isBlank(value)) {
              log.debug(
                  "Skipping LIKE_IGNORE_CASE condition for property: {}, value is blank",
                  propertyName);
              return builder.conjunction();
            }
            Expression<String> field = builder.lower(root.get(propertyName).as(String.class));
            String pattern = StringUtils.wrap(value.trim().toLowerCase(), "%");
            return builder.like(field, pattern);
          });
      return this;
    }

    /**
     * LIKE_START 조건 추가 (value% 패턴). 예: builder.likeStart("email", "user@")
     *
     * @param propertyName 필드 이름
     * @param value 검색 문자열
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public Builder<T> likeStart(String propertyName, String value) {
      specifications.add(
          (root, query, builder) -> {
            if (StringUtils.isBlank(value)) {
              log.debug(
                  "Skipping LIKE_START condition for property: {}, value is blank", propertyName);
              return builder.conjunction();
            }
            return builder.like(root.get(propertyName).as(String.class), value + "%");
          });
      return this;
    }

    /**
     * LIKE_END 조건 추가 (%value 패턴). 예: builder.likeEnd("phone", "1234")
     *
     * @param propertyName 필드 이름
     * @param value 검색 문자열
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public Builder<T> likeEnd(String propertyName, String value) {
      specifications.add(
          (root, query, builder) -> {
            if (StringUtils.isBlank(value)) {
              log.debug(
                  "Skipping LIKE_END condition for property: {}, value is blank", propertyName);
              return builder.conjunction();
            }
            return builder.like(root.get(propertyName).as(String.class), "%" + value);
          });
      return this;
    }

    /**
     * GREATER_THAN 조건 추가. 예: builder.greaterThan("age", 18) 타입 불일치 시 변환 시도 후 조건 무시 (로그 기록).
     *
     * @param propertyName 필드 이름
     * @param value 비교 값 (Comparable 구현 필요)
     * @param <V> Comparable 타입
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public <V extends Comparable<? super V>> Builder<T> greaterThan(
        String propertyName, Object value) {
      specifications.add(
          (root, query, builder) -> {
            if (value == null) {
              log.debug(
                  "Skipping GREATER_THAN condition for property: {}, value is null", propertyName);
              return builder.conjunction();
            }
            Class<?> targetType;
            if (entityManager != null && entityClass != null) {
              Metamodel metamodel = entityManager.getMetamodel();
              EntityType<T> entity = metamodel.entity(entityClass);
              Attribute<? super T, ?> attribute;
              try {
                attribute = entity.getAttribute(propertyName);
              } catch (IllegalArgumentException e) {
                log.warn(
                    "Invalid property: {} for entity: {}", propertyName, entityClass.getName());
                return builder.conjunction();
              }
              targetType = attribute.getJavaType();
            } else {
              Path<?> path = root.get(propertyName);
              targetType = path.getJavaType();
            }
            Object convertedValue = typeConverter.convert(value, targetType);
            if (!targetType.isInstance(convertedValue)) {
              log.warn(
                  "Type mismatch for property: {}, expected: {}, got: {}",
                  propertyName,
                  targetType.getName(),
                  value.getClass().getName());
              return builder.conjunction();
            }
            if (!(convertedValue instanceof Comparable)) {
              log.warn(
                  "Value for GREATER_THAN must be Comparable, property: {}, value: {}",
                  propertyName,
                  value);
              return builder.conjunction();
            }
            @SuppressWarnings("unchecked")
            Comparable<Object> comparableValue = (Comparable<Object>) convertedValue;
            return builder.greaterThan(root.get(propertyName), comparableValue);
          });
      return this;
    }

    /**
     * GREATER_EQUAL 조건 추가. 예: builder.greaterEqual("salary", 50000) 타입 불일치 시 변환 시도 후 조건 무시 (로그 기록).
     *
     * @param propertyName 필드 이름
     * @param value 비교 값 (Comparable 구현 필요)
     * @param <V> Comparable 타입
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public <V extends Comparable<? super V>> Builder<T> greaterEqual(
        String propertyName, Object value) {
      specifications.add(
          (root, query, builder) -> {
            if (value == null) {
              log.debug(
                  "Skipping GREATER_EQUAL condition for property: {}, value is null", propertyName);
              return builder.conjunction();
            }
            Class<?> targetType;
            if (entityManager != null && entityClass != null) {
              Metamodel metamodel = entityManager.getMetamodel();
              EntityType<T> entity = metamodel.entity(entityClass);
              Attribute<? super T, ?> attribute;
              try {
                attribute = entity.getAttribute(propertyName);
              } catch (IllegalArgumentException e) {
                log.warn(
                    "Invalid property: {} for entity: {}", propertyName, entityClass.getName());
                return builder.conjunction();
              }
              targetType = attribute.getJavaType();
            } else {
              Path<?> path = root.get(propertyName);
              targetType = path.getJavaType();
            }
            Object convertedValue = typeConverter.convert(value, targetType);
            if (!targetType.isInstance(convertedValue)) {
              log.warn(
                  "Type mismatch for property: {}, expected: {}, got: {}",
                  propertyName,
                  targetType.getName(),
                  value.getClass().getName());
              return builder.conjunction();
            }
            if (!(convertedValue instanceof Comparable)) {
              log.warn(
                  "Value for GREATER_EQUAL must be Comparable, property: {}, value: {}",
                  propertyName,
                  value);
              return builder.conjunction();
            }
            @SuppressWarnings("unchecked")
            Comparable<Object> comparableValue = (Comparable<Object>) convertedValue;
            return builder.greaterThanOrEqualTo(root.get(propertyName), comparableValue);
          });
      return this;
    }

    /**
     * LESS_THAN 조건 추가. 예: builder.lessThan("createdAt", ZonedDateTime.now()) 타입 불일치 시 변환 시도 후 조건 무시
     * (로그 기록).
     *
     * @param propertyName 필드 이름
     * @param value 비교 값 (Comparable 구현 필요)
     * @param <V> Comparable 타입
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public <V extends Comparable<? super V>> Builder<T> lessThan(
        String propertyName, Object value) {
      specifications.add(
          (root, query, builder) -> {
            if (value == null) {
              log.debug(
                  "Skipping LESS_THAN condition for property: {}, value is null", propertyName);
              return builder.conjunction();
            }
            Class<?> targetType;
            if (entityManager != null && entityClass != null) {
              Metamodel metamodel = entityManager.getMetamodel();
              EntityType<T> entity = metamodel.entity(entityClass);
              Attribute<? super T, ?> attribute;
              try {
                attribute = entity.getAttribute(propertyName);
              } catch (IllegalArgumentException e) {
                log.warn(
                    "Invalid property: {} for entity: {}", propertyName, entityClass.getName());
                return builder.conjunction();
              }
              targetType = attribute.getJavaType();
            } else {
              Path<?> path = root.get(propertyName);
              targetType = path.getJavaType();
            }
            Object convertedValue = typeConverter.convert(value, targetType);
            if (!targetType.isInstance(convertedValue)) {
              log.warn(
                  "Type mismatch for property: {}, expected: {}, got: {}",
                  propertyName,
                  targetType.getName(),
                  value.getClass().getName());
              return builder.conjunction();
            }
            if (!(convertedValue instanceof Comparable)) {
              log.warn(
                  "Value for LESS_THAN must be Comparable, property: {}, value: {}",
                  propertyName,
                  value);
              return builder.conjunction();
            }
            @SuppressWarnings("unchecked")
            Comparable<Object> comparableValue = (Comparable<Object>) convertedValue;
            return builder.lessThan(root.get(propertyName), comparableValue);
          });
      return this;
    }

    /**
     * LESS_EQUAL 조건 추가. 예: builder.lessEqual("updatedAt", ZonedDateTime.now()) 타입 불일치 시 변환 시도 후 조건
     * 무시 (로그 기록).
     *
     * @param propertyName 필드 이름
     * @param value 비교 값 (Comparable 구현 필요)
     * @param <V> Comparable 타입
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public <V extends Comparable<? super V>> Builder<T> lessEqual(
        String propertyName, Object value) {
      specifications.add(
          (root, query, builder) -> {
            if (value == null) {
              log.debug(
                  "Skipping LESS_EQUAL condition for property: {}, value is null", propertyName);
              return builder.conjunction();
            }
            Class<?> targetType;
            if (entityManager != null && entityClass != null) {
              Metamodel metamodel = entityManager.getMetamodel();
              EntityType<T> entity = metamodel.entity(entityClass);
              Attribute<? super T, ?> attribute;
              try {
                attribute = entity.getAttribute(propertyName);
              } catch (IllegalArgumentException e) {
                log.warn(
                    "Invalid property: {} for entity: {}", propertyName, entityClass.getName());
                return builder.conjunction();
              }
              targetType = attribute.getJavaType();
            } else {
              Path<?> path = root.get(propertyName);
              targetType = path.getJavaType();
            }
            Object convertedValue = typeConverter.convert(value, targetType);
            if (!targetType.isInstance(convertedValue)) {
              log.warn(
                  "Type mismatch for property: {}, expected: {}, got: {}",
                  propertyName,
                  targetType.getName(),
                  value.getClass().getName());
              return builder.conjunction();
            }
            if (!(convertedValue instanceof Comparable)) {
              log.warn(
                  "Value for LESS_EQUAL must be Comparable, property: {}, value: {}",
                  propertyName,
                  value);
              return builder.conjunction();
            }
            @SuppressWarnings("unchecked")
            Comparable<Object> comparableValue = (Comparable<Object>) convertedValue;
            return builder.lessThanOrEqualTo(root.get(propertyName), comparableValue);
          });
      return this;
    }

    /**
     * BETWEEN 조건 추가. 예: builder.between("birthDate", ZonedDateTime.now().minusYears(30),
     * ZonedDateTime.now().minusYears(18)) 타입 불일치 시 변환 시도 후 조건 무시 (로그 기록).
     *
     * @param propertyName 필드 이름
     * @param start 시작 값 (Comparable 구현 필요)
     * @param end 종료 값 (Comparable 구현 필요)
     * @param <V> Comparable 타입
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public <V extends Comparable<? super V>> Builder<T> between(
        String propertyName, Object start, Object end) {
      specifications.add(
          (root, query, builder) -> {
            if (start == null || end == null) {
              log.debug(
                  "Skipping BETWEEN condition for property: {}, start or end is null",
                  propertyName);
              return builder.conjunction();
            }
            Class<?> targetType;
            if (entityManager != null && entityClass != null) {
              Metamodel metamodel = entityManager.getMetamodel();
              EntityType<T> entity = metamodel.entity(entityClass);
              Attribute<? super T, ?> attribute;
              try {
                attribute = entity.getAttribute(propertyName);
              } catch (IllegalArgumentException e) {
                log.warn(
                    "Invalid property: {} for entity: {}", propertyName, entityClass.getName());
                return builder.conjunction();
              }
              targetType = attribute.getJavaType();
            } else {
              Path<?> path = root.get(propertyName);
              targetType = path.getJavaType();
            }
            Object convertedStart = typeConverter.convert(start, targetType);
            Object convertedEnd = typeConverter.convert(end, targetType);
            if (!targetType.isInstance(convertedStart) || !targetType.isInstance(convertedEnd)) {
              log.warn(
                  "Type mismatch for property: {}, expected: {}, got start: {}, end: {}",
                  propertyName,
                  targetType.getName(),
                  start.getClass().getName(),
                  end.getClass().getName());
              return builder.conjunction();
            }
            if (!(convertedStart instanceof Comparable) || !(convertedEnd instanceof Comparable)) {
              log.warn(
                  "Values for BETWEEN must be Comparable, property: {}, start: {}, end: {}",
                  propertyName,
                  start,
                  end);
              return builder.conjunction();
            }
            @SuppressWarnings("unchecked")
            Comparable<Object> startValue = (Comparable<Object>) convertedStart;
            @SuppressWarnings("unchecked")
            Comparable<Object> endValue = (Comparable<Object>) convertedEnd;
            return builder.between(root.get(propertyName), startValue, endValue);
          });
      return this;
    }

    /**
     * IN 조건 추가. 예: builder.in("status", List.of("ACTIVE", "PENDING"))
     *
     * @param propertyName 필드 이름
     * @param values 값 컬렉션
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public Builder<T> in(String propertyName, Collection<?> values) {
      specifications.add(
          (root, query, builder) -> {
            if (values == null || values.isEmpty()) {
              log.debug(
                  "Skipping IN condition for property: {}, values are null or empty", propertyName);
              return builder.conjunction();
            }
            CriteriaBuilder.In<Object> inClause = builder.in(root.get(propertyName));
            values.forEach(inClause::value);
            return inClause;
          });
      return this;
    }

    /**
     * NOT_IN 조건 추가. 예: builder.notIn("department", List.of("HR", "SALES"))
     *
     * @param propertyName 필드 이름
     * @param values 값 컬렉션
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public Builder<T> notIn(String propertyName, Collection<?> values) {
      specifications.add(
          (root, query, builder) -> {
            if (values == null || values.isEmpty()) {
              log.debug(
                  "Skipping NOT_IN condition for property: {}, values are null or empty",
                  propertyName);
              return builder.conjunction();
            }
            CriteriaBuilder.In<Object> inClause = builder.in(root.get(propertyName));
            values.forEach(inClause::value);
            return builder.not(inClause);
          });
      return this;
    }

    /**
     * IS_NULL 조건 추가. 예: builder.isNull("deletedAt")
     *
     * @param propertyName 필드 이름
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public Builder<T> isNull(String propertyName) {
      specifications.add(
          (root, query, builder) -> {
            log.debug("Applying IS_NULL condition for property: {}", propertyName);
            return builder.isNull(root.get(propertyName));
          });
      return this;
    }

    /**
     * IS_NOT_NULL 조건 추가. 예: builder.isNotNull("lastLogin")
     *
     * @param propertyName 필드 이름
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public Builder<T> isNotNull(String propertyName) {
      specifications.add(
          (root, query, builder) -> {
            log.debug("Applying IS_NOT_NULL condition for property: {}", propertyName);
            return builder.isNotNull(root.get(propertyName));
          });
      return this;
    }

    /**
     * ENUM EQUAL 조건 추가. 예: builder.equalEnum("status", Status.PENDING)
     *
     * @param propertyName 필드 이름
     * @param enumValue Enum 값
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public Builder<T> equalEnum(String propertyName, Enum<?> enumValue) {
      specifications.add(SpecificationQueryBuilder.equalEnum(propertyName, enumValue));
      return this;
    }

    /**
     * 날짜 범위 조건 추가. 예: builder.dateRangeBetween("createdAt", "2025-09-01", "2025-09-30",
     * DateParser.defaultParser())
     *
     * @param propertyName 필드 이름
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @param dateParser 날짜 파서
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public Builder<T> dateRangeBetween(
        String propertyName, String startDate, String endDate, DateParser dateParser) {
      specifications.add(
          SpecificationQueryBuilder.dateRangeBetween(propertyName, startDate, endDate, dateParser));
      return this;
    }

    /**
     * 조인된 엔티티의 ID로 필터링 조건 추가. 예: builder.equalJoinId("members", "userId", "user123")
     *
     * @param joinProperty 조인 대상 필드
     * @param idProperty ID 필드 이름
     * @param value 비교 값
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public Builder<T> equalJoinId(String joinProperty, String idProperty, Object value) {
      specifications.add(SpecificationQueryBuilder.equalJoinId(joinProperty, idProperty, value));
      return this;
    }

    /**
     * 복잡한 조인 조건 추가. 예: builder.joinWithConditions("members", List.of(JoinCondition.equal("userId",
     * "user123")), JoinType. INNER)
     *
     * @param joinProperty 조인 대상 필드
     * @param conditions 조인 조건 리스트
     * @param joinType 조인 타입
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public Builder<T> joinWithConditions(
        String joinProperty, List<JoinCondition> conditions, JoinType joinType) {
      specifications.add(
          SpecificationQueryBuilder.joinWithConditions(joinProperty, conditions, joinType));
      return this;
    }

    /**
     * OR 조건 추가 (복수 조건). 예: builder.or(List.of(SpecificationQueryBuilder.equal("email",
     * "test@example.com"), SpecificationQueryBuilder.equalEnum("status", Status. PENDING)))
     *
     * @param conditions OR로 결합할 조건 리스트
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public Builder<T> or(List<Specification<T>> conditions) {
      specifications.add(
          (root, query, builder) -> {
            if (conditions == null || conditions.isEmpty()) {
              log.debug("Skipping OR condition, conditions are null or empty");
              return builder.conjunction();
            }
            Predicate[] predicates =
                conditions.stream()
                    .map(spec -> spec.toPredicate(root, query, builder))
                    .toArray(Predicate[]::new);
            if (predicates.length == 0) {
              log.debug("Skipping OR condition, no valid predicates");
              return builder.conjunction();
            }
            return builder.or(predicates);
          });
      return this;
    }

    /**
     * OR 조건 추가 (중첩 빌더). 예: builder.or(or -> or.equal("email",
     * "test@example.com").equalEnum("status", Status.PENDING))
     *
     * @param orBuilder OR 조건을 정의하는 빌더
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public Builder<T> or(Consumer<Builder<T>> orBuilder) {
      Builder<T> subBuilder = Builder.create(entityClass, entityManager);
      orBuilder.accept(subBuilder);
      specifications.add(
          (root, query, builder) -> {
            Specification<T> subSpec = subBuilder.build();
            Predicate predicate = subSpec.toPredicate(root, query, builder);
            if (predicate.equals(builder.conjunction())) {
              log.debug("Skipping OR condition, sub-builder has no valid conditions");
              return builder.conjunction();
            }
            return builder.or(predicate);
          });
      return this;
    }

    /**
     * AND 조건 추가 (중첩 빌더). 예: builder.and(and -> and.equal("email",
     * "test@example.com").equalEnum("status", Status.PENDING))
     *
     * @param andBuilder AND 조건을 정의하는 빌더
     * @return 빌더 인스턴스
     * @since 1.0.0
     */
    public Builder<T> and(Consumer<Builder<T>> andBuilder) {
      Builder<T> subBuilder = Builder.create(entityClass, entityManager);
      andBuilder.accept(subBuilder);
      specifications.add(
          (root, query, builder) -> {
            Specification<T> subSpec = subBuilder.build();
            Predicate predicate = subSpec.toPredicate(root, query, builder);
            if (predicate.equals(builder.conjunction())) {
              log.debug("Skipping AND condition, sub-builder has no valid conditions");
              return builder.conjunction();
            }
            return predicate;
          });
      return this;
    }

    /**
     * 정렬 조건 추가. 예: builder.orderBy("createdAt", "DESC")
     *
     * @param field 정렬할 필드명
     * @param direction 정렬 방향 ("ASC" 또는 "DESC", null이면 "ASC")
     * @return 빌더 인스턴스
     * @since 1.2.0
     */
    public Builder<T> orderBy(String field, String direction) {
      if (StringUtils.isNotBlank(field)) {
        orderByList.add(new OrderInfo(field, direction));
        log.debug("Added ORDER BY condition: {} {}", field, direction != null ? direction : "ASC");
      } else {
        log.debug("Skipping ORDER BY condition, field is blank");
      }
      return this;
    }

    /**
     * 정렬 조건 추가 (기본값 ASC). 예: builder.orderBy("name")
     *
     * @param field 정렬할 필드명
     * @return 빌더 인스턴스
     * @since 1.2.0
     */
    public Builder<T> orderBy(String field) {
      return orderBy(field, "ASC");
    }

    /**
     * 정렬 조건 추가 (Direction 사용). 예: builder.orderBy("createdAt", Direction.DESC)
     *
     * @param field 정렬할 필드명
     * @param direction 정렬 방향 (Direction.ASC 또는 Direction.DESC)
     * @return 빌더 인스턴스
     * @since 1.2.0
     */
    public Builder<T> orderBy(String field, Direction direction) {
      if (StringUtils.isNotBlank(field)) {
        orderByList.add(new OrderInfo(field, direction));
        log.debug(
            "Added ORDER BY condition: {} {}", field, direction != null ? direction.name() : "ASC");
      } else {
        log.debug("Skipping ORDER BY condition, field is blank");
      }
      return this;
    }

    /**
     * LIMIT 조건 추가. 예: builder.limit(10)
     *
     * @param limit 최대 결과 수
     * @return 빌더 인스턴스
     * @since 1.2.0
     */
    public Builder<T> limit(Integer limit) {
      if (limit != null && limit > 0) {
        this.limitValue = limit;
        log.debug("Added LIMIT condition: {}", limit);
      } else {
        log.debug("Skipping LIMIT condition, limit is null or <= 0: {}", limit);
      }
      return this;
    }

    /**
     * OFFSET 조건 추가. 예: builder.offset(20)
     *
     * @param offset 건너뛸 결과 수
     * @return 빌더 인스턴스
     * @since 1.2.0
     */
    public Builder<T> offset(Integer offset) {
      if (offset != null && offset >= 0) {
        this.offsetValue = offset;
        log.debug("Added OFFSET condition: {}", offset);
      } else {
        log.debug("Skipping OFFSET condition, offset is null or < 0: {}", offset);
      }
      return this;
    }

    /**
     * 페이징 조건 추가. 예: builder.page(0, 10) - 첫 번째 페이지, 10개씩
     *
     * @param pageNumber 페이지 번호 (0부터 시작)
     * @param pageSize 페이지 크기
     * @return 빌더 인스턴스
     * @since 1.2.0
     */
    public Builder<T> page(int pageNumber, int pageSize) {
      if (pageNumber >= 0 && pageSize > 0) {
        this.offsetValue = pageNumber * pageSize;
        this.limitValue = pageSize;
        log.debug(
            "Added pagination: page={}, size={}, offset={}", pageNumber, pageSize, offsetValue);
      } else {
        log.debug(
            "Skipping pagination, invalid pageNumber: {} or pageSize: {}", pageNumber, pageSize);
      }
      return this;
    }

    /**
     * 쿼리 로깅 활성화. 예: builder.logQuery()
     *
     * @return 빌더 인스턴스
     * @since 1.2.0
     */
    public Builder<T> logQuery() {
      this.enableQueryLogging = true;
      log.debug("Enabled query logging");
      return this;
    }

    /**
     * Spring Data JPA Pageable 생성. 페이징 정보를 Pageable로 변환.
     *
     * @return Pageable 객체 (페이징 정보가 없으면 null)
     * @since 1.2.0
     */
    public Pageable toPageable() {
      if (limitValue != null && offsetValue != null) {
        int pageNumber = offsetValue / limitValue;
        return PageRequest.of(pageNumber, limitValue);
      } else if (limitValue != null) {
        return PageRequest.of(0, limitValue);
      }
      return null;
    }

    /**
     * 페이징 정보가 설정되었는지 확인.
     *
     * @return 페이징 정보가 설정되었으면 true
     * @since 1.2.0
     */
    public boolean hasPagination() {
      return limitValue != null || offsetValue != null;
    }

    /**
     * 정렬 정보가 설정되었는지 확인.
     *
     * @return 정렬 정보가 설정되었으면 true
     * @since 1.2.0
     */
    public boolean hasOrderBy() {
      return !orderByList.isEmpty();
    }

    /**
     * Specification 생성.
     *
     * @return 최종 Specification 객체
     * @since 1.0.0
     */
    public Specification<T> build() {
      return (root, query, builder) -> {
        // 쿼리 로깅 활성화
        if (enableQueryLogging) {
          log.info(
              "Building query with {} conditions, {} order by clauses, limit: {}, offset: {}",
              specifications.size(),
              orderByList.size(),
              limitValue,
              offsetValue);
        }

        // WHERE 조건 처리
        Predicate[] predicates =
            specifications.stream()
                .map(spec -> spec.toPredicate(root, query, builder))
                .toArray(Predicate[]::new);
        Predicate wherePredicate =
            predicates.length > 0 ? builder.and(predicates) : builder.conjunction();

        // ORDER BY 조건 처리
        if (!orderByList.isEmpty()) {
          List<Order> orders =
              orderByList.stream()
                  .map(
                      orderInfo -> {
                        Path<?> path = root.get(orderInfo.getField());
                        return orderInfo.isAscending() ? builder.asc(path) : builder.desc(path);
                      })
                  .toList();
          query.orderBy(orders);
        }

        // LIMIT/OFFSET 처리 (JPA Criteria API에서는 직접 지원하지 않으므로 로깅만)
        if (limitValue != null || offsetValue != null) {
          log.info(
              "LIMIT/OFFSET conditions set - limit: {}, offset: {}. "
                  + "These should be handled by Spring Data JPA Pageable or repository methods.",
              limitValue,
              offsetValue);
        }

        return wherePredicate;
      };
    }
  }
}
