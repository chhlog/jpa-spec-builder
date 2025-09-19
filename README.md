# jpa-spec-builder

JPA Specification을 위한 유틸리티 라이브러리. Spring Data JPA에서 동적 쿼리를 쉽게 생성할 수 있도록 설계되었습니다.

## 요구사항

- **JDK**: 17 이상 (Amazon Corretto 17 권장).
- **Spring Data JPA**: 3.5.4 이상.
- **의존성**: Apache Commons Lang3, SLF4J.

## Core Pursuits

jpa-spec-builder의 핵심 추구점은 Spring Data JPA의 Specification API를 더 사용자 친화적으로 만드는 것입니다. 주요 포커스:

- **Readability (가독성)**: Fluent builder 패턴을 통해 쿼리 조건을 체이닝 방식으로 작성할 수 있습니다. 예: `builder.equal("email", "test@example.com").and(and -> and.equal("status", "PENDING")).build()`. 이는 코드의 가독성을 높여 유지보수성을 강화합니다.
- **Complex Nested Conditions (복잡한 중첩 조건)**: `and(Consumer<Builder<T>>)`와 `or(Consumer<Builder<T>>)` 메서드로 중첩된 AND/OR 조건을 지원합니다. 예: `(A OR B) AND (C OR D)` 같은 복잡한 논리를 쉽게 표현할 수 있습니다. 이는 실무에서 자주 발생하는 다중 필터링 요구사항을 충족합니다.
- **Joins with Conditions (조인 지원)**: `joinWithConditions` 메서드로 복잡한 조인(LEFT, INNER 등)과 조건 조합을 지원합니다. `JoinCondition`을 통해 조인된 엔티티의 필터링(예: `JoinCondition.equal("userId", "user123")`)을 간단히 처리합니다. 이는 관계형 데이터베이스 쿼리의 복잡성을 줄여줍니다.

이 라이브러리는 타입 안정성(Metamodel 검증), 타입 변환(TypeConverter)과 함께 이러한 추구점을 통해 개발 생산성을 높입니다.

## 기능

- 빌더 패턴으로 동적 쿼리 생성.
- 타입 변환 및 Metamodel 기반 검증.

## 🚀 빠른 시작

### Gradle

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.gitlab.chhyuk:jpa-spec-builder:1.0.0'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.gitlab.chhyuk</groupId>
    <artifactId>jpa-spec-builder</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 사용 예시

아래는 SpecificationQueryBuilder의 다양한 사용 예시입니다. WorkspaceInvite 엔티티를 가정하고, 기본 쿼리, 복잡한 중첩 쿼리, 조인, 타입 변환 등을 커버합니다. EntityManager는 Spring Data JPA 환경에서 @Autowired로 주입받아 사용합니다.

### 정적 메서드 사용 (Static Methods)

정적 메서드는 개별 조건을 생성할 때 사용합니다:

```java
import io.gitlab.chhyuk.jpa.querybuilder.SpecificationQueryBuilder;

@Autowired
private EntityManager entityManager;

// 개별 조건 생성
Specification<WorkspaceInvite> equalSpec = SpecificationQueryBuilder.equal("email", "test@example.com");
Specification<WorkspaceInvite> isNullSpec = SpecificationQueryBuilder.isNull("deletedAt");
Specification<WorkspaceInvite> likeIgnoreCaseSpec = SpecificationQueryBuilder.likeIgnoreCase("name", "김");
Specification<WorkspaceInvite> equalJoinIdSpec = SpecificationQueryBuilder.equalJoinId("members", "userId", "user123");
Specification<WorkspaceInvite> equalEnumSpec = SpecificationQueryBuilder.equalEnum("status", Status.PENDING);
Specification<WorkspaceInvite> dateRangeSpec = SpecificationQueryBuilder.dateRangeBetween(
    "createdAt", "2025-09-01", "2025-09-30", DateParser.defaultParser());

// 조건들을 조합
Specification<WorkspaceInvite> combinedSpec = equalSpec.and(isNullSpec).and(likeIgnoreCaseSpec);
```

### 빌더 패턴 사용 (Builder Pattern)

빌더 패턴은 체이닝 방식으로 복잡한 쿼리를 구성할 때 사용합니다:

```java
import io.gitlab.chhyuk.jpa.querybuilder.SpecificationQueryBuilder;

@Autowired
private EntityManager entityManager;

// Metamodel 지원 빌더 (권장)
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder.<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
        .equal("email", "test@example.com") // WHERE email = 'test@example.com'
        .notEqual("role", "GUEST") // AND role != 'GUEST'
        .like("name", "김") // AND name LIKE '%김%'
        .notLike("description", "test") // AND description NOT LIKE '%test%'
        .likeStart("email", "user@") // AND email LIKE 'user@%'
        .likeEnd("phone", "1234") // AND phone LIKE '%1234'
        .likeIgnoreCase("name", "김") // AND LOWER(name) LIKE '%김%'
        .build();

// Metamodel 미사용 빌더 (간단한 경우)
Specification<WorkspaceInvite> simpleSpec = SpecificationQueryBuilder.Builder.<WorkspaceInvite>create()
        .equal("email", "test@example.com")
        .like("name", "김")
        .build();
```

### 비교 쿼리 (GREATER_THAN, BETWEEN 등)

```java
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder.<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
        .greaterThan("age", 18) // WHERE age > 18
        .greaterEqual("salary", 50000) // AND salary >= 50000
        .lessThan("createdAt", ZonedDateTime.now()) // AND createdAt < now
        .lessEqual("updatedAt", ZonedDateTime.now()) // AND updatedAt <= now
        .between("birthDate", ZonedDateTime.now().minusYears(30), ZonedDateTime.now().minusYears(18)) // AND birthDate BETWEEN (now - 30y) AND (now - 18y)
        .build();
```

### 리스트 및 NULL 쿼리 (IN, NOT_IN, IS_NULL, IS_NOT_NULL)

```java
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder.<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
        .in("status", List.of("ACTIVE", "PENDING")) // WHERE status IN ('ACTIVE', 'PENDING')
        .notIn("department", List.of("HR", "SALES")) // AND department NOT IN ('HR', 'SALES')
        .isNull("deletedAt") // AND deletedAt IS NULL
        .isNotNull("lastLogin") // AND lastLogin IS NOT NULL
        .build();
```

### Enum 및 날짜 범위 쿼리

```java
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder.<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
        .equalEnum("status", Status.PENDING) // WHERE status = 'PENDING'
        .dateRangeBetween("createdAt", "2025-09-01", "2025-09-30", DateParser.defaultParser()) // WHERE createdAt >= '2025-09-01' AND createdAt < '2025-10-01'
        .build();
```

### 조인 쿼리 (equalJoinId, joinWithConditions)

```java
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder.<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
        .equalJoinId("members", "userId", "user123") // WHERE members.userId = 'user123'
        .joinWithConditions("members", List.of(
            JoinCondition.equal("userId", "user123"),
            JoinCondition.like("name", "김"),
            JoinCondition.likeIgnoreCase("description", "test"),
            JoinCondition.in("status", List.of("ACTIVE", "PENDING")),
            JoinCondition.isNull("deletedAt"),
            JoinCondition.between("createdAt", ZonedDateTime.now().minusDays(30), ZonedDateTime.now())
        ), JoinType.INNER) // INNER JOIN members ON ... WITH (userId = 'user123' AND name LIKE '%김%' AND LOWER(description) LIKE '%test%' AND status IN ('ACTIVE', 'PENDING') AND deletedAt IS NULL AND createdAt BETWEEN (now - 30d) AND now)
        .build();
```

### 복잡한 중첩 쿼리 (AND/OR 중첩)

```java
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder.<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
        .and(and -> and
            .or(or -> or
                .equal("email", "test@example.com")
                .equalEnum("status", Status.PENDING)
            )
            .or(or -> or
                .equal("role", "MEMBER")
                .equal("role", "ADMIN")
            )
        )
        .build();
// WHERE (email = 'test@example.com' OR status = 'PENDING') AND (role = 'MEMBER' OR role = 'ADMIN')
```

### 타입 변환 지원

```java
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder.<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
        .greaterThan("createdAt", ZonedDateTime.now().toLocalDate()) // LocalDate로 자동 변환
        .build();
// WHERE createdAt > (converted LocalDate)
```

### 조인과 중첩 조건 결합

```java
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder.<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
        .joinWithConditions("members", List.of(
            JoinCondition.equal("userId", "user123"),
            JoinCondition.likeIgnoreCase("description", "test")
        ), JoinType.INNER)
        .and(and -> and
            .or(or -> or
                .equal("email", "test@example.com")
                .equalEnum("status", Status.PENDING)
            )
        )
        .build();
// WHERE (members.userId = 'user123' AND LOWER(members.description) LIKE '%test%') AND (email = 'test@example.com' OR status = 'PENDING')
```

### 정적 메서드로 조인 조건 생성

```java
// 정적 메서드로 개별 조인 조건 생성
Specification<WorkspaceInvite> joinSpec = SpecificationQueryBuilder.joinWithConditions("members", List.of(
    JoinCondition.equal("userId", "user123"),
    JoinCondition.like("name", "김"),
    JoinCondition.in("status", List.of("ACTIVE", "PENDING")),
    JoinCondition.between("createdAt", ZonedDateTime.now().minusDays(30), ZonedDateTime.now())
), JoinType.LEFT);

// 다른 조건과 조합
Specification<WorkspaceInvite> combinedSpec = joinSpec
    .and(SpecificationQueryBuilder.equal("email", "test@example.com"))
    .and(SpecificationQueryBuilder.isNull("deletedAt"));
```

### OR 조건을 리스트로 추가

```java
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder.<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
        .or(List.of(
            SpecificationQueryBuilder.equal("email", "test@example.com"),
            SpecificationQueryBuilder.equalEnum("status", Status.PENDING),
            SpecificationQueryBuilder.like("name", "김")
        ))
        .build();
// WHERE (email = 'test@example.com' OR status = 'PENDING' OR name LIKE '%김%')
```

### 빈 조건 처리

```java
// null 값은 자동으로 무시됨
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder.<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
        .equal("email", "test@example.com")
        .equal("name", null) // 이 조건은 무시됨
        .like("description", "") // 빈 문자열도 무시됨
        .like("description", "   ") // 공백만 있는 문자열도 무시됨
        .build();
// 결과: WHERE email = 'test@example.com' (다른 조건들은 무시됨)
```

### 명시적 NULL 검색

```java
// NULL 값을 명시적으로 검색하려면 isNull, isNotNull 사용
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder.<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
        .isNull("deletedAt") // WHERE deletedAt IS NULL
        .isNotNull("lastLogin") // AND lastLogin IS NOT NULL
        .build();
```

## 📜 라이센스

이 프로젝트는 [MIT License](LICENSE)를 따릅니다.