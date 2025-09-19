# jpa-spec-builder

JPA Specification을 위한 유틸리티 라이브러리. Spring Data JPA에서 동적 쿼리를 쉽게 생성할 수 있도록 설계되었습니다.

## 요구사항

- **JDK**: 17 이상
- **Spring Data JPA**: 3.5.4 이상
- **의존성**: Apache Commons Lang3, SLF4J

## 핵심 기능

- **빌더 패턴**: Fluent API로 복잡한 쿼리 조건을 체이닝 방식으로 작성
- **Metamodel 지원**: EntityManager를 통한 타입 안정성 및 검증
- **타입 변환**: 자동 타입 변환 (LocalDate ↔ ZonedDateTime, 숫자 타입 등)
- **복잡한 조인**: 다양한 조인 타입과 조건 지원
- **중첩 조건**: AND/OR 중첩을 통한 복잡한 논리 표현
- **정렬 및 페이징**: ORDER BY, LIMIT, OFFSET 지원 (v1.2.0+)
- **쿼리 로깅**: 디버깅을 위한 쿼리 빌드 정보 출력 (v1.2.0+)

## 사용법

### 1. 정적 메서드 사용

```java
// 개별 조건 생성
Specification<WorkspaceInvite> equalSpec = SpecificationQueryBuilder.equal("email", "test@example.com");
Specification<WorkspaceInvite> isNullSpec = SpecificationQueryBuilder.isNull("deletedAt");
Specification<WorkspaceInvite> likeSpec = SpecificationQueryBuilder.likeIgnoreCase("name", "김");
Specification<WorkspaceInvite> equalJoinIdSpec = SpecificationQueryBuilder.equalJoinId("members", "userId", "user123");
Specification<WorkspaceInvite> equalEnumSpec = SpecificationQueryBuilder.equalEnum("status", Status.PENDING);
Specification<WorkspaceInvite> dateRangeSpec = SpecificationQueryBuilder.dateRangeBetween(
    "createdAt", "2025-09-01", "2025-09-30", DateParser.defaultParser());

// 조건 조합
Specification<WorkspaceInvite> combinedSpec = equalSpec.and(isNullSpec).and(likeSpec);

// 생성되는 SQL:
// SELECT w.* FROM workspace_invite w
// WHERE w.email = 'test@example.com'
//   AND w.deleted_at IS NULL
//   AND LOWER(w.name) LIKE '%김%'

// 조인 조건 생성
Specification<WorkspaceInvite> joinSpec = SpecificationQueryBuilder.joinWithConditions("members", List.of(
    JoinCondition.equal("userId", "user123"),
    JoinCondition.like("name", "김"),
    JoinCondition.in("status", List.of("ACTIVE", "PENDING")),
    JoinCondition.between("createdAt", startDate, endDate)
), JoinType.LEFT);

// 생성되는 SQL:
// SELECT DISTINCT w.* FROM workspace_invite w
// LEFT JOIN member m ON w.id = m.invite_id
// WHERE m.user_id = 'user123'
//   AND m.name LIKE '%김%'
//   AND m.status IN ('ACTIVE', 'PENDING')
//   AND m.created_at BETWEEN '2025-01-01T00:00:00Z' AND '2025-12-31T23:59:59Z'
```

### 2. 기본 빌더 패턴

```java
// Metamodel 지원 빌더 (권장)
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .equal("email", "test@example.com")           // WHERE email = 'test@example.com'
    .notEqual("role", "GUEST")                    // AND role != 'GUEST'
    .like("name", "김")                           // AND name LIKE '%김%'
    .notLike("description", "test")               // AND description NOT LIKE '%test%'
    .likeStart("email", "user@")                  // AND email LIKE 'user@%'
    .likeEnd("phone", "1234")                     // AND phone LIKE '%1234'
    .likeIgnoreCase("description", "test")        // AND LOWER(description) LIKE '%test%'
    .build();

// 생성되는 SQL:
// SELECT w.* FROM workspace_invite w
// WHERE w.email = 'test@example.com'
//   AND w.role != 'GUEST'
//   AND w.name LIKE '%김%'
//   AND w.description NOT LIKE '%test%'
//   AND w.email LIKE 'user@%'
//   AND w.phone LIKE '%1234'
//   AND LOWER(w.description) LIKE '%test%'

// 간단한 빌더 (Metamodel 미사용)
Specification<WorkspaceInvite> simpleSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create()
    .equal("email", "test@example.com")
    .like("name", "김")
    .build();

// 생성되는 SQL:
// SELECT w.* FROM workspace_invite w
// WHERE w.email = 'test@example.com'
//   AND w.name LIKE '%김%'
```

### 3. 비교 연산

```java
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .greaterThan("age", 18)                       // WHERE age > 18
    .greaterEqual("salary", 50000)                // AND salary >= 50000
    .lessThan("createdAt", ZonedDateTime.now())   // AND createdAt < now
    .between("birthDate", startDate, endDate)     // AND birthDate BETWEEN start AND end
    .build();

// 생성되는 SQL:
// SELECT w.* FROM workspace_invite w
// WHERE w.age > 18
//   AND w.salary >= 50000
//   AND w.created_at < '2025-09-19T10:30:00Z'
//   AND w.birth_date BETWEEN '1990-01-01T00:00:00Z' AND '2005-01-01T00:00:00Z'
```

### 4. 리스트 및 NULL 조건

```java
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .in("status", List.of("ACTIVE", "PENDING"))   // WHERE status IN ('ACTIVE', 'PENDING')
    .notIn("department", List.of("HR", "SALES"))  // AND department NOT IN ('HR', 'SALES')
    .isNull("deletedAt")                          // AND deletedAt IS NULL
    .isNotNull("lastLogin")                       // AND lastLogin IS NOT NULL
    .build();

// 생성되는 SQL:
// SELECT w.* FROM workspace_invite w
// WHERE w.status IN ('ACTIVE', 'PENDING')
//   AND w.department NOT IN ('HR', 'SALES')
//   AND w.deleted_at IS NULL
//   AND w.last_login IS NOT NULL
```

### 5. 조인 쿼리

```java
// 단순 조인
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .equalJoinId("members", "userId", "user123")  // WHERE members.userId = 'user123'
    .build();

// 생성되는 SQL:
// SELECT DISTINCT w.* FROM workspace_invite w
// LEFT JOIN member m ON w.id = m.invite_id
// WHERE m.user_id = 'user123'

// 복잡한 조인 조건
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .joinWithConditions("members", List.of(
        JoinCondition.equal("userId", "user123"),
        JoinCondition.like("name", "김"),
        JoinCondition.in("status", List.of("ACTIVE", "PENDING")),
        JoinCondition.between("createdAt", startDate, endDate)
    ), JoinType.INNER)
    .build();

// 생성되는 SQL:
// SELECT DISTINCT w.* FROM workspace_invite w
// INNER JOIN member m ON w.id = m.invite_id
// WHERE m.user_id = 'user123'
//   AND m.name LIKE '%김%'
//   AND m.status IN ('ACTIVE', 'PENDING')
//   AND m.created_at BETWEEN '2025-01-01T00:00:00Z' AND '2025-12-31T23:59:59Z'
```

### 조인문 작성 가이드

빌더를 사용할 때 조인문을 생각하고 작성하는 방법:

```java
// 1. 기본 조인 패턴
// "members"는 WorkspaceInvite 엔티티의 @OneToMany 관계 필드명
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .joinWithConditions("members", List.of(
        JoinCondition.equal("userId", "user123")        // member.userId = 'user123'
    ), JoinType.INNER)
    .build();

// 2. 조인 타입별 사용법
// INNER JOIN: 양쪽 테이블에 모두 데이터가 있는 경우만
Specification<WorkspaceInvite> innerJoinSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .joinWithConditions("members", List.of(
        JoinCondition.equal("status", "ACTIVE")
    ), JoinType.INNER)
    .build();

// LEFT JOIN: 왼쪽 테이블 기준, 오른쪽에 데이터가 없어도 포함
Specification<WorkspaceInvite> leftJoinSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .joinWithConditions("members", List.of(
        JoinCondition.isNull("deletedAt")
    ), JoinType.LEFT)
    .build();

// 3. 조인 조건 조합
Specification<WorkspaceInvite> complexJoinSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .joinWithConditions("members", List.of(
        JoinCondition.equal("userId", "user123"),           // member.userId = 'user123'
        JoinCondition.like("name", "김"),                    // member.name LIKE '%김%'
        JoinCondition.greaterThan("age", 18),               // member.age > 18
        JoinCondition.in("status", List.of("ACTIVE", "PENDING")), // member.status IN ('ACTIVE', 'PENDING')
        JoinCondition.between("createdAt", startDate, endDate),   // member.createdAt BETWEEN start AND end
        JoinCondition.isNull("deletedAt")                   // member.deletedAt IS NULL
    ), JoinType.INNER)
    .build();

// 4. 조인과 메인 조건 조합
Specification<WorkspaceInvite> combinedSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .equal("email", "test@example.com")                     // w.email = 'test@example.com'
    .joinWithConditions("members", List.of(
        JoinCondition.equal("userId", "user123")            // AND member.userId = 'user123'
    ), JoinType.INNER)
    .isNull("deletedAt")                                    // AND w.deletedAt IS NULL
    .build();
```

### 6. 중첩 조건 (AND/OR)

```java
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
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

// 생성되는 SQL:
// SELECT w.* FROM workspace_invite w
// WHERE (w.email = 'test@example.com' OR w.status = 'PENDING')
//   AND (w.role = 'MEMBER' OR w.role = 'ADMIN')
```

### 중첩 조건과 조인문 조합

```java
// 1. 조인과 중첩 조건 조합
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .and(and -> and
        .or(or -> or
            .equal("email", "kim@example.com")
            .equal("email", "choi@example.com")
        )
        .joinWithConditions("members", List.of(
            JoinCondition.equal("status", "ACTIVE"),
            JoinCondition.like("name", "김")
        ), JoinType.INNER)
    )
    .build();

// 생성되는 SQL:
// SELECT DISTINCT w.* FROM workspace_invite w
// INNER JOIN member m ON w.id = m.invite_id
// WHERE (w.email = 'kim@example.com' OR w.email = 'choi@example.com')
//   AND m.status = 'ACTIVE'
//   AND m.name LIKE '%김%'

// 2. 복잡한 OR 중첩 (조인 포함)
Specification<WorkspaceInvite> complexSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .or(or -> or
        .and(and -> and
            .equal("email", "kim@example.com")
            .joinWithConditions("members", List.of(
                JoinCondition.equal("userId", "kim-user-001")
            ), JoinType.INNER)
        )
        .and(and -> and
            .equal("email", "choi@example.com")
            .joinWithConditions("members", List.of(
                JoinCondition.equal("userId", "choi-user-001")
            ), JoinType.LEFT)
        )
    )
    .build();

// 생성되는 SQL (JPA가 복잡한 OR 중첩을 EXISTS로 변환):
// SELECT DISTINCT w.* FROM workspace_invite w
// WHERE ((w.email = 'kim@example.com'
//         AND EXISTS (SELECT 1 FROM member m WHERE w.id = m.invite_id AND m.user_id = 'kim-user-001'))
//     OR (w.email = 'choi@example.com'
//         AND EXISTS (SELECT 1 FROM member m WHERE w.id = m.invite_id AND m.user_id = 'choi-user-001')))
```

### 7. 날짜 범위 쿼리

```java
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .dateRangeBetween("createdAt", "2025-09-01", "2025-09-30",
        DateParser.defaultParser())               // WHERE createdAt >= '2025-09-01' AND createdAt < '2025-10-01'
    .build();

// 생성되는 SQL:
// SELECT w.* FROM workspace_invite w
// WHERE w.created_at >= '2025-09-01T00:00:00Z'
//   AND w.created_at < '2025-10-01T00:00:00Z'

// 다른 시간대 사용
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .dateRangeBetween("createdAt", "2025-09-01", "2025-09-30",
        DateParser.defaultParser(ZoneId.of("Asia/Seoul")))
    .build();

// 생성되는 SQL (한국 시간대):
// SELECT w.* FROM workspace_invite w
// WHERE w.created_at >= '2025-08-31T15:00:00Z'
//   AND w.created_at < '2025-09-30T15:00:00Z'
```

### 8. OR 조건 사용

```java
// OR 리스트 방식
Specification<WorkspaceInvite> orSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .or(List.of(
        SpecificationQueryBuilder.equal("email", "test@example.com"),
        SpecificationQueryBuilder.equalEnum("status", Status.PENDING),
        SpecificationQueryBuilder.like("name", "김")
    ))
    .build();

// 생성되는 SQL:
// SELECT w.* FROM workspace_invite w
// WHERE (w.email = 'test@example.com'
//     OR w.status = 'PENDING'
//     OR w.name LIKE '%김%')

// OR 중첩 빌더 방식
Specification<WorkspaceInvite> orNestedSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .or(or -> or
        .equal("email", "test@example.com")
        .equalEnum("status", Status.PENDING)
        .like("name", "김")
    )
    .build();

// 생성되는 SQL:
// SELECT w.* FROM workspace_invite w
// WHERE (w.email = 'test@example.com'
//     OR w.status = 'PENDING'
//     OR w.name LIKE '%김%')

// 복잡한 OR 중첩 (OR 안에 AND)
Specification<WorkspaceInvite> complexOrSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .or(or -> or
        .and(and -> and
            .equal("email", "kim@example.com")
            .joinWithConditions("members", List.of(
                JoinCondition.equal("userId", "kim-user-001")
            ), JoinType.INNER)
        )
        .and(and -> and
            .equal("email", "choi@example.com")
            .joinWithConditions("members", List.of(
                JoinCondition.equal("userId", "choi-user-001")
            ), JoinType.LEFT)
        )
    )
    .build();

// 생성되는 SQL (JPA가 복잡한 OR 중첩을 EXISTS로 변환):
// SELECT DISTINCT w.* FROM workspace_invite w
// WHERE ((w.email = 'kim@example.com'
//         AND EXISTS (SELECT 1 FROM member m WHERE w.id = m.invite_id AND m.user_id = 'kim-user-001'))
//     OR (w.email = 'choi@example.com'
//         AND EXISTS (SELECT 1 FROM member m WHERE w.id = m.invite_id AND m.user_id = 'choi-user-001')))
```

### 9. 정적 메서드 사용

```java
// 개별 조건 생성
Specification<WorkspaceInvite> equalSpec = SpecificationQueryBuilder.equal("email", "test@example.com");
Specification<WorkspaceInvite> isNullSpec = SpecificationQueryBuilder.isNull("deletedAt");
Specification<WorkspaceInvite> likeSpec = SpecificationQueryBuilder.likeIgnoreCase("name", "김");

// 조건 조합
Specification<WorkspaceInvite> combinedSpec = equalSpec.and(isNullSpec).and(likeSpec);

// 생성되는 SQL:
// SELECT w.* FROM workspace_invite w
// WHERE w.email = 'test@example.com'
//   AND w.deleted_at IS NULL
//   AND LOWER(w.name) LIKE '%김%'

// OR 조건 리스트
Specification<WorkspaceInvite> orSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .or(List.of(
        SpecificationQueryBuilder.equal("email", "test@example.com"),
        SpecificationQueryBuilder.equalEnum("status", Status.PENDING),
        SpecificationQueryBuilder.like("name", "김")
    ))
    .build();

// 생성되는 SQL:
// SELECT w.* FROM workspace_invite w
// WHERE (w.email = 'test@example.com'
//     OR w.status = 'PENDING'
//     OR w.name LIKE '%김%')
```

### 10. 타입 변환 지원

```java
// 자동 타입 변환
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .greaterThan("createdAt", LocalDate.now())    // LocalDate → ZonedDateTime 자동 변환
    .between("age", 18, 65)                       // Integer → Long 자동 변환
    .build();

// 생성되는 SQL:
// SELECT w.* FROM workspace_invite w
// WHERE w.created_at > '2025-09-19T00:00:00Z'
//   AND w.age BETWEEN 18 AND 65
```

### 11. 빈 조건 처리

```java
// null, 빈 문자열은 자동으로 무시됨
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .equal("email", "test@example.com")
    .equal("name", null)                          // 무시됨
    .like("description", "")                      // 무시됨
    .like("description", "   ")                   // 무시됨
    .build();

// 생성되는 SQL:
// SELECT w.* FROM workspace_invite w
// WHERE w.email = 'test@example.com'
// (다른 조건들은 null/빈 값이므로 무시됨)
```

## JoinCondition 사용법

### 모든 JoinCondition 타입 지원

```java
// 기본 조건들
List<JoinCondition> conditions = List.of(
    JoinCondition.equal("userId", "user123"),                    // m.user_id = 'user123'
    JoinCondition.notEqual("role", "GUEST"),                     // m.role != 'GUEST'
    JoinCondition.like("name", "김"),                            // m.name LIKE '%김%'
    JoinCondition.notLike("description", "test"),                // m.description NOT LIKE '%test%'
    JoinCondition.likeIgnoreCase("description", "test"),         // LOWER(m.description) LIKE '%test%'
    JoinCondition.likeStart("email", "user@"),                   // m.email LIKE 'user@%'
    JoinCondition.likeEnd("phone", "1234"),                      // m.phone LIKE '%1234'
    JoinCondition.greaterThan("age", 18),                        // m.age > 18
    JoinCondition.greaterEqual("salary", 50000),                 // m.salary >= 50000
    JoinCondition.lessThan("createdAt", ZonedDateTime.now()),    // m.created_at < now
    JoinCondition.lessEqual("updatedAt", ZonedDateTime.now()),   // m.updated_at <= now
    JoinCondition.between("createdAt", startDate, endDate),      // m.created_at BETWEEN start AND end
    JoinCondition.in("status", List.of("ACTIVE", "PENDING")),    // m.status IN ('ACTIVE', 'PENDING')
    JoinCondition.notIn("department", List.of("HR", "SALES")),   // m.department NOT IN ('HR', 'SALES')
    JoinCondition.isNull("deletedAt"),                           // m.deleted_at IS NULL
    JoinCondition.isNotNull("lastLogin")                         // m.last_login IS NOT NULL
);

Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .joinWithConditions("members", conditions, JoinType.INNER)
    .build();

// 생성되는 SQL:
// SELECT DISTINCT w.* FROM workspace_invite w
// INNER JOIN member m ON w.id = m.invite_id
// WHERE m.user_id = 'user123'
//   AND m.role != 'GUEST'
//   AND m.name LIKE '%김%'
//   AND m.description NOT LIKE '%test%'
//   AND LOWER(m.description) LIKE '%test%'
//   AND m.email LIKE 'user@%'
//   AND m.phone LIKE '%1234'
//   AND m.age > 18
//   AND m.salary >= 50000
//   AND m.created_at < '2025-09-19T10:30:00Z'
//   AND m.updated_at <= '2025-09-19T10:30:00Z'
//   AND m.created_at BETWEEN '2025-01-01T00:00:00Z' AND '2025-12-31T23:59:59Z'
//   AND m.status IN ('ACTIVE', 'PENDING')
//   AND m.department NOT IN ('HR', 'SALES')
//   AND m.deleted_at IS NULL
//   AND m.last_login IS NOT NULL
```

### 조인 타입별 사용법

```java
// LEFT JOIN (기본값)
Specification<WorkspaceInvite> leftJoinSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .joinWithConditions("members", List.of(
        JoinCondition.equal("userId", "user123")
    ), JoinType.LEFT)
    .build();

// INNER JOIN
Specification<WorkspaceInvite> innerJoinSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .joinWithConditions("members", List.of(
        JoinCondition.equal("userId", "user123")
    ), JoinType.INNER)
    .build();

// RIGHT JOIN
Specification<WorkspaceInvite> rightJoinSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .joinWithConditions("members", List.of(
        JoinCondition.equal("userId", "user123")
    ), JoinType.RIGHT)
    .build();
```

## DateParser 사용법

### 기본 사용법

```java
// 기본 파서 (시스템 시간대)
DateParser defaultParser = DateParser.defaultParser();

// 특정 시간대 파서
DateParser koreaParser = DateParser.defaultParser(ZoneId.of("Asia/Seoul"));
DateParser utcParser = DateParser.defaultParser(ZoneId.of("UTC"));
DateParser jstParser = DateParser.defaultParser(ZoneId.of("Asia/Tokyo"));
DateParser estParser = DateParser.defaultParser(ZoneId.of("America/New_York"));

// 사용 예시
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .dateRangeBetween("createdAt", "2025-09-01", "2025-09-30", koreaParser)
    .build();

// 생성되는 SQL (한국 시간대):
// SELECT w.* FROM workspace_invite w
// WHERE w.created_at >= '2025-08-31T15:00:00Z'
//   AND w.created_at < '2025-09-30T15:00:00Z'
```

### 시간대별 비교

```java
// UTC 시간대
Specification<WorkspaceInvite> utcSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .dateRangeBetween("createdAt", "2025-09-01", "2025-09-30",
        DateParser.defaultParser(ZoneId.of("UTC")))
    .build();

// 한국 시간대
Specification<WorkspaceInvite> koreaSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .dateRangeBetween("createdAt", "2025-09-01", "2025-09-30",
        DateParser.defaultParser(ZoneId.of("Asia/Seoul")))
    .build();

// UTC: 2025-09-01T00:00:00Z ~ 2025-10-01T00:00:00Z
// Korea: 2025-08-31T15:00:00Z ~ 2025-09-30T15:00:00Z (9시간 차이)
```

### 날짜 파싱 예외 처리

```java
// 잘못된 날짜 형식 처리
try {
    Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder
        .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
        .dateRangeBetween("createdAt", "invalid-date", "2025-09-30",
            DateParser.defaultParser())
        .build();
} catch (DateTimeParseException e) {
    // 잘못된 날짜 형식 시 예외 발생
    log.warn("Invalid date format", e);
}

// null/빈 문자열 처리
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .dateRangeBetween("createdAt", null, "", DateParser.defaultParser())
    .build();
// null/빈 값은 무시되어 모든 결과 반환
```

## Metamodel 지원

EntityManager를 사용한 Metamodel 지원으로 타입 안정성을 보장합니다:

### 기본 사용법

```java
// Metamodel 지원 빌더
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .equal("email", "test@example.com")           // 타입 검증됨
    .greaterThan("createdAt", LocalDate.now())    // 자동 타입 변환
    .build();

// 생성되는 SQL:
// SELECT w.* FROM workspace_invite w
// WHERE w.email = 'test@example.com'
//   AND w.created_at > '2025-09-19T00:00:00Z'
```

### 타입 검증 및 변환

```java
// 자동 타입 변환 (Metamodel 지원)
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .greaterThan("createdAt", LocalDate.now())    // LocalDate → ZonedDateTime
    .between("age", 18, 65)                       // Integer → Long
    .in("status", List.of("ACTIVE", "PENDING"))   // List<String> → Collection
    .build();

// 타입 불일치 시 자동 변환 시도
Specification<WorkspaceInvite> typeConversionSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .greaterThan("createdAt", "2025-09-01")       // String → ZonedDateTime 변환 시도
    .build();
```

### 잘못된 속성명 처리

```java
// 잘못된 속성명은 예외 발생
try {
    Specification<WorkspaceInvite> invalidSpec = SpecificationQueryBuilder.Builder
        .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
        .equal("invalidProperty", "value")        // IllegalArgumentException 발생
        .build();
} catch (IllegalArgumentException e) {
    log.warn("Invalid property name", e);
}

// 잘못된 조인 속성도 예외 발생
try {
    Specification<WorkspaceInvite> invalidJoinSpec = SpecificationQueryBuilder.Builder
        .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
        .equalJoinId("invalidJoin", "userId", "user123")  // IllegalArgumentException 발생
        .build();
} catch (IllegalArgumentException e) {
    log.warn("Invalid join property", e);
}
```

### Metamodel vs 비Metamodel 비교

```java
// Metamodel 지원 (권장)
Specification<WorkspaceInvite> metamodelSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .equal("email", "test@example.com")           // 타입 검증됨
    .greaterThan("createdAt", LocalDate.now())    // 자동 타입 변환
    .build();

// Metamodel 미지원 (간단한 경우)
Specification<WorkspaceInvite> simpleSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create()
    .equal("email", "test@example.com")           // 타입 검증 없음
    .greaterThan("createdAt", LocalDate.now())    // 타입 변환 없음
    .build();
```

## 실제 사용 예시

```java
@Service
public class WorkspaceInviteService {

    @Autowired
    private WorkspaceInviteRepository repository;

    @Autowired
    private EntityManager entityManager;

    public List<WorkspaceInvite> searchInvites(SearchRequest request) {
        Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder
            .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
            .equal("email", request.getEmail())
            .in("status", request.getStatuses())
            .dateRangeBetween("createdAt", request.getStartDate(), request.getEndDate(),
                DateParser.defaultParser())
            .joinWithConditions("members", List.of(
                JoinCondition.equal("userId", request.getUserId()),
                JoinCondition.like("name", request.getName())
            ), JoinType.INNER)
            .and(and -> and
                .or(or -> or
                    .equal("role", "MEMBER")
                    .equal("role", "ADMIN")
                )
            )
            .build();

        return repository.findAll(spec);
    }
}
```

## 정렬 및 페이징 (v1.2.0+)

### 1. 기본 정렬

```java
// 단일 필드 정렬 (문자열)
Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .equal("status", Status.ACTIVE)
    .orderBy("createdAt", "DESC")                    // 생성일 내림차순
    .build();

// 단일 필드 정렬 (Direction enum)
Specification<WorkspaceInvite> spec2 = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .equal("status", Status.ACTIVE)
    .orderBy("createdAt", Direction.DESC)            // 생성일 내림차순
    .build();

// 다중 필드 정렬
Specification<WorkspaceInvite> multiSortSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .equal("status", Status.ACTIVE)
    .orderBy("status", Direction.ASC)                // 상태 오름차순
    .orderBy("createdAt", Direction.DESC)            // 생성일 내림차순
    .build();

// 정적 메서드 사용
Specification<WorkspaceInvite> staticSpec = SpecificationQueryBuilder.orderBy("createdAt", Direction.DESC);

// 생성되는 SQL:
// SELECT w.* FROM workspace_invite w
// WHERE w.status = 'ACTIVE'
// ORDER BY w.status ASC, w.created_at DESC
```

### 2. 페이징

```java
// LIMIT/OFFSET 방식
Specification<WorkspaceInvite> pagedSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .like("name", "김")
    .orderBy("createdAt", "DESC")
    .limit(10)                                       // 최대 10개
    .offset(20)                                      // 20개 건너뛰기
    .build();

// Spring Data JPA와 연동
Pageable pageable = PageRequest.of(2, 10, Sort.by("createdAt").descending());
Page<WorkspaceInvite> result = repository.findAll(pagedSpec, pageable);

// 생성되는 SQL:
// SELECT w.* FROM workspace_invite w
// WHERE w.name LIKE '%김%'
// ORDER BY w.created_at DESC
// OFFSET 20 ROWS FETCH FIRST 10 ROWS ONLY

// 페이지 기반 페이징
Specification<WorkspaceInvite> pageSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .equal("status", Status.ACTIVE)
    .page(0, 5)                                      // 첫 번째 페이지, 5개씩
    .build();
```

### 3. 쿼리 로깅

```java
// 디버깅을 위한 쿼리 로깅
Specification<WorkspaceInvite> loggedSpec = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .equal("email", "test@example.com")
    .like("name", "김")
    .orderBy("createdAt", "DESC")
    .limit(10)
    .logQuery()                                      // 쿼리 빌드 정보 로깅
    .build();

// 로그 출력 예시:
// INFO  - Building query with 2 conditions, 1 order by clauses, limit: 10, offset: null
// INFO  - LIMIT/OFFSET conditions set - limit: 10, offset: null. These should be handled by Spring Data JPA Pageable or repository methods.
```

### 4. 빌더 상태 확인

```java
// 빌더 상태 확인 메서드들
SpecificationQueryBuilder.Builder<WorkspaceInvite> builder = SpecificationQueryBuilder.Builder
    .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
    .equal("status", Status.ACTIVE)
    .orderBy("createdAt", "DESC")
    .limit(10)
    .offset(5);

// 상태 확인
boolean hasPagination = builder.hasPagination();     // true
boolean hasOrderBy = builder.hasOrderBy();           // true
Pageable pageable = builder.toPageable();            // PageRequest.of(0, 10)
```

### 5. 실무 예시

```java
@Service
public class WorkspaceInviteService {

    @Autowired
    private WorkspaceInviteRepository repository;

    @Autowired
    private EntityManager entityManager;

    public Page<WorkspaceInvite> searchInvites(InviteSearchRequest request) {
        Specification<WorkspaceInvite> spec = SpecificationQueryBuilder.Builder
            .<WorkspaceInvite>create(WorkspaceInvite.class, entityManager)
            .equal("status", request.getStatus())
            .like("name", request.getName())
            .dateRangeBetween("createdAt", request.getStartDate(), request.getEndDate(),
                DateParser.defaultParser())
            .orderBy("createdAt", "DESC")             // 최신순 정렬
            .page(request.getPage(), request.getSize()) // 페이징
            .logQuery()                               // 디버깅용 로깅
            .build();

        return repository.findAll(spec, PageRequest.of(request.getPage(), request.getSize()));
    }
}
```
