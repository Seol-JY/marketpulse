# CLAUDE.md

실시간 주식 시세를 Kafka 로 흘려보내고 대시보드로 보여주는 모노레포, 문서와 커밋 메시지는 한국어

## 스택

Java 25 (LTS) · Spring Boot 4.1.1 · Gradle Kotlin DSL · Kafka 4.3 (Strimzi) · React

Spring Boot 4.1.1 이 `kafka-clients 4.2.1` 을 관리한다. 브로커가 4.3 이지만 클라이언트는
API 버전을 협상하므로 맞추지 않는다. `kafka.version` 을 임의로 올리지 말 것.

JSON 은 Jackson 3(`tools.jackson`)이다. Jackson 2 의 `com.fasterxml.jackson` 을 쓰지 않는다.
단 애노테이션은 Jackson 3 에서도 `com.fasterxml.jackson.annotation` 에 있다.

## 구조

```
common/            이벤트 스키마·직렬화·토픽 상수. 다른 모듈에 의존하지 않음
producer/          토스 Open API WebSocket → Kafka
consumer-candle/   체결 → 1분봉 집계
consumer-relay/    체결·캔들·알림 → SSE
consumer-anomaly/  체결 → 이상 탐지 → 알림 발행
consumer-archiver/ 체결·호가 → ClickHouse
consumer-notifier/ 알림 → Discord
web/               React. Gradle 빌드 대상 아님, 자체 package.json
docs/              설계 노트
```

- 모듈 하나가 이미지 하나이고 배포 하나다. 모노레포지만 모놀리스가 아니다
- 모듈끼리 직접 참조하지 않는다. 공유하는 것은 `common` 과 Kafka 토픽뿐이다
- 컨슈머를 추가할 때는 `consumer-<역할>/` 로 만들고 `settings.gradle.kts` 에 등록한다

## 명령

```bash
./gradlew build                 # 전체 빌드 + 테스트
./gradlew spotlessApply         # 포맷 자동 교정
./gradlew :producer:bootRun     # 단일 모듈 실행
./gradlew checkstyleMain        # 정적 분석
cd web && npm run dev           # 프론트
```

## 계층

계층 수는 모듈마다 다르다. **판단 기준은 하나다.**

> Kafka 없이 단위 테스트하고 싶은 로직이 있는가

있으면 4계층, 없으면 2계층이다. **빈 패키지를 만들지 않는다.**

### 4계층 — 도메인 판단이 있는 모듈

`producer`, `consumer-candle`, `consumer-anomaly`

```
presentation/     WebSocket 클라이언트, Kafka 리스너, SSE 컨트롤러
application/      유스케이스 + 나가는 쪽 인터페이스
domain/           순수 로직. 정규화, 집계, 판정
infrastructure/   Kafka 발행, ClickHouse, 외부 API
```

의존 방향은 안쪽을 향한다. **나가는 쪽 인터페이스는 `application` 이 소유하고
`infrastructure` 가 구현한다.**

```
presentation ──▶ application ──▶ domain
                      ▲
infrastructure ───────┘
```

```java
// application/  인터페이스를 여기가 소유
public interface MarketEventPublisher {
    void publish(MarketEvent event);
}

// infrastructure/  구현이 안쪽을 향해 들어옴
@Component
class KafkaMarketEventPublisher implements MarketEventPublisher { ... }
```

- `application` 은 Kafka 를 모른다. 가짜 구현을 넘겨 큐 드롭 정책을 검증할 수 있다
- `domain` 은 아무것도 참조하지 않는다. I/O 없음, 시계도 직접 보지 않는다
- `presentation` 과 `infrastructure` 는 얇다. 로직을 두지 않는다
- 진입 포트(`port/in`)는 만들지 않는다. 구현체가 하나면 이름만 늘어난다

### 2계층 — 옮기기만 하는 모듈

`consumer-relay`, `consumer-archiver`, `consumer-notifier`

```
presentation/     Kafka 리스너
infrastructure/   SSE · ClickHouse · Discord
```

위임만 하는 유스케이스와 빈 도메인 패키지를 만들지 않는다.
나중에 판단이 생기면 그때 4계층으로 올린다.

`common` 은 타입 라이브러리라 계층을 두지 않는다.

## 설계 원칙

### 스키마의 단일 출처는 `common`

프로듀서가 필드를 바꾸면 컨슈머 컴파일이 깨져야 한다. 이벤트 정의를 모듈에 복사하지 않는다.

### 프로듀서는 가볍게

시세 소스가 LOSSY 라 프로듀서가 막히면 데이터가 사라진다.
집계·필터링·DB 접근·외부 API 호출을 넣지 않는다.

## 코드 규칙

- 메서드 파라미터에 `final` (Lombok 이 생성하는 Builder 생성자는 예외)
- 이벤트·DTO 는 `record` + Bean Validation
- 매퍼는 정적 유틸리티 클래스 (`@NoArgsConstructor(access = PRIVATE)` + `static` 변환 메서드)
- 값 객체는 `record` + compact 생성자 검증. 컬렉션은 `List.copyOf` 로 방어적 복사
- 예외는 `PipelineException.from(ExceptionCode.XXX)`. 코드는 한 열거형에 모은다
- 조회 전용 메서드는 부수효과를 만들지 않는다
- 주석은 코드로 설명되지 않는 의사결정 맥락이 있을 때만 제한적으로, 개조식 한 줄로 작성
  (서술형 문장 금지). 설명은 필요한 경우 제한적으로 README 와 `docs/` 에 기재
- 로그는 구조화한다. 문자열을 이어붙이지 말고 `log.info("수신 {} 건, 심볼 {}", n, symbol)` 형태

## Kafka 규칙

**토픽은 여기서 만들지 않는다.** 토픽은 컨슈머 여럿이 공유하는 자원이라 앱 하나가 소유하지
않고, 파티션 수는 줄일 수 없어 자동 생성에 맡기면 브로커 기본값이 박힌다.
GitOps 레포의 `infra/kafka/topics.yaml` 이 단일 출처이고 `auto.create.topics.enable=false` 다.

```
market.trades.v1       파티션 6   키 = 시장:심볼
market.orderbook.v1    파티션 6   키 = 시장:심볼
market.candles.1m.v1   파티션 3   키 = 시장:심볼
alerts.raised.v1       파티션 1
```

- 키는 `US:NVDA`, `KR:005930` 형태. 같은 종목의 순서가 보장돼야 캔들이 틀어지지 않는다
- 프로듀서: `acks=all`, `enable.idempotence=true`, `compression.type=zstd`, `linger.ms=20`
- 컨슈머: 그룹 하나가 모듈 하나. 오프셋 정책이 다르면 그룹을 나눈다
- 스키마를 깨는 변경은 토픽 이름을 `.v2` 로 올린다. 기존 토픽을 덮어쓰지 않는다
- `FAIL_ON_UNKNOWN_PROPERTIES` 를 끈다. 프로듀서가 필드를 추가해도 구버전 컨슈머가 살아야 한다
- 중복은 데이터 특성이므로 제거하지 않는다. 받은 대로 합산한다 (`docs/` 참조)

## 설정

- 종목 목록·구독 배분은 ConfigMap 으로 주입한다. 이미지에 넣지 않는다
- 시크릿은 환경변수로만 받는다. 어떤 파일에도 토큰·키를 두지 않는다
- 프로듀서는 수평 확장이 불가능하다 (소스 계정당 연결 2개, 연결당 구독 100건).
  `replicas: 1` 과 `strategy: Recreate` 가 필수이고 HPA 를 붙이지 않는다

## 테스트

테스트는 **핵심 로직에 제한적으로** 작성한다. 커버리지 숫자를 목표로 삼지 않는다.

- `domain` 은 단위 테스트. Mockito 없이 직접 호출한다
- `presentation`·`infrastructure` 는 얇으므로 통합 테스트만, 개수를 적게 유지한다 (Testcontainers Kafka)
- 한글 `@DisplayName` + `@Nested` 로 성공·실패를 그룹핑
- Given-When-Then 주석 구분, AssertJ 사용, 여러 검증은 `assertSoftly`
- 테스트 메서드 이름은 한글 스네이크 케이스 (`역행_타임스탬프는_유예_안에_들어온다`)
- 계층 의존 방향과 모듈 경계는 ArchUnit 으로 검사한다. 규칙에 `because()` 로 이유를 남긴다
- 계층 규칙은 4계층 모듈에만 건다. 2계층 모듈에 걸면 규칙이 거짓말이 된다

## Git

- 커밋 메시지는 `type(scope): 설명` 한 줄 한국어
- type 은 feat/fix/docs/style/refactor/test/chore, scope 는 모듈명 (`producer`, `common`, `web`)
- 헤더 100자 제한
- 커밋과 푸시는 사용자가 검토 후 직접 수행, 파일 준비와 제안 커밋 메시지까지만

## 배포

배포는 GitOps 로 진행된다. 이 레포는 이미지까지만 만든다.

```
main 푸시 → GitHub Actions → ghcr.io/seol-jy/marketpulse-<모듈>:<커밋 SHA>
          → GitOps 레포의 kustomization.yaml 에서 newTag 갱신
```

태그는 항상 커밋 SHA 다. `latest` 를 쓰지 않는다.
변경된 모듈만 빌드한다. `common/` 이 바뀌면 전체를 빌드한다.

## 검증

```bash
./gradlew build
./gradlew spotlessApply
```
