# 추출 방식

대상 저장소에서 서비스 구조를 뽑아 문서 페이지·MCP가 쓰는 검증 출력을 만들기까지의 과정이다. 결과 JSON의 필드는 [형식 문서](format/README.md)에, `verify`의 검사 목록은 [개발 문서](development.md#verify가-하는-일)에 있다.

## 1. 흐름

```
init        대상을 작업 폴더 source/에 받아 커밋을 고정하고, AI 세션용 지시 파일(AGENTS.md)을 쓴다
  |
  | AI 세션이 지시 파일 순서대로
  v
서비스      services/<spring.application.name>.json   서비스마다 하나. 서로 독립이라 동시에 돌릴 수 있다
관계        relations.json                            서비스 파일이 다 있을 때. 메시지 연결, 핸들러가 닿는 호출·예외
저장소 수준 repository.json                           배포 파이프라인, 컨테이너, 메트릭·로그·모니터링
  |
  v
verify      Java 검증기가 근거 줄과 양쪽 연결을 대조해 상태를 정한다 -> out/analysis.json
  |
  | (선택) 테스트 서버가 있으면 응답 확인 -> responses/, 다시 verify
  | 업무 규칙·함정 -> knowledge/, 다시 verify
  v
serve       문서 페이지, 검색·질의응답, MCP
```

| 단계 | 맡는 쪽 | 읽는 것 | 쓰는 것 |
|---|---|---|---|
| 서비스 | AI ([추출 Skill](../.claude/skills/extract-msa-java-spring/SKILL.md)) | 그 서비스의 코드·설정, 공통 모듈, compose | 엔드포인트·DTO, HTTP 클라이언트·호출, 게이트웨이 경로·허용 목록, 메시지 선언·발행·구독, 환경변수·외부 자원, 오류 응답, `unsupported` |
| 관계 | AI (같은 Skill) | 모든 서비스 파일과 핸들러 코드 | `messageLinks`, `calls`, `throws` |
| 저장소 수준 | AI (같은 Skill) | 워크플로·compose·Dockerfile·모니터링 설정 | 역할별 항목(배포 파이프라인, 컨테이너, 메트릭 수집, 로그 전송, 도구 설정, 모니터링 도구) |
| 검증 | 검증기 (`verify`) | 위 결과와 `source/`의 원본 줄 | 상태가 붙은 `out/analysis.json`, 경고와 이유 코드 |
| 응답 확인 | AI ([응답 확인 Skill](../.claude/skills/probe-msa-java-spring/SKILL.md)) | 테스트 서버 응답 | `responses/`. 서비스 파일은 고치지 않고 `verify`가 추출 필드와 비교한다 |
| 업무 규칙·함정 | AI ([업무 규칙 Skill](../.claude/skills/rules-msa-java-spring/SKILL.md)) | 코드, 추출 결과, 저장된 응답 | `knowledge/`. 출처는 "AI 해석"이고 개발자가 확인하기 전까지 미확인 |

## 2. AI는 사실을 쓰고, 상태는 검증기가 정한다

AI는 코드를 읽어 값과 그 값이 적힌 `파일:줄`만 쓴다. 상태 필드는 쓰지 않는다. 검증기가 근거 줄을 다시 열어 아래를 대조하고 상태를 붙인다.

- 근거 줄에 그 값이 실제로 있는가(예: 매핑 어노테이션 줄에 그 경로가 있는가)
- 첫 근거가 그 종류의 선언 줄인가(예: Feign 호출이면 `@FeignClient` 인터페이스의 매핑 줄)
- 양쪽이 이어지는가(예: 호출 대상 엔드포인트가 같은 서비스 이름·HTTP 메서드·경로로 하나 있는가)

| 상태 | 붙는 경우 |
|---|---|
| 확인됨 | 근거 줄과 양쪽 대조가 모두 맞는다 |
| 확인 안 됨 | 근거 줄이 값을 뒷받침하지 않거나, 저장소 안에서 상대를 찾지 못한다 |
| 저장소에 근거 없음 | 값이 저장소 밖(환경변수 값, 비밀값)에서 정해진다 |

이렇게 나눈 이유는 AI가 그럴듯한 연결을 만들어 내도 "확인됨"이 되지 않게 하기 위해서다. 추출이 틀리면 결과에 "확인 안 됨"과 경고가 남고, 틀린 값이 확인된 사실처럼 나가지 않는다. 배열마다 "확인됨"이 무엇을 확인했다는 뜻인지는 출력의 `confirmed`에 있고, 페이지 범례와 MCP 결과 머리에도 나온다.

## 3. 예시 두 개

### HTTP 호출이 확인되는 경우

HotDealWave의 주문 서비스가 쿠폰 서비스를 부르는 Feign 호출이다.

```java
// order/.../infrastructure/client/CouponClient.java
13  @FeignClient(name = "coupon-service", configuration = CouponClientConfig.class)
15      @PostMapping("/api/v1/coupons/{couponId}/validate")
```

1. **서비스 추출**: AI가 `httpCalls`에 `order-service.CouponClient.validateCoupon`을 쓴다. HTTP 메서드 `POST`와 경로 `/api/v1/coupons/{couponId}/validate`의 근거는 15줄, 클라이언트 `name`의 근거는 13줄이다. 대상은 쿠폰 서비스 컨트롤러를 직접 읽어 `coupon-service.CouponController.validateCoupon`으로 쓴다.
2. **검증**: 검증기가 확인하는 것은 세 가지다.
   - 15줄이 매핑 어노테이션이고 거기서 `POST`와 경로가 나오는가
   - `coupon-service`가 `coupon/src/main/resources/application.yml:3`의 `spring.application.name`인가
   - 쿠폰 서비스에 `POST /api/v1/coupons/{couponId}/validate` 엔드포인트가 하나 있는가. 클래스 매핑 `CouponController.java:23`과 메서드 매핑 `:63`을 합쳐 경로를 맞춘다.

   셋 다 맞아 메서드·경로·대상이 모두 "확인됨"이다.
3. **페이지**: 주문 생성 엔드포인트 페이지의 흐름에 쿠폰 검증 호출이 이어지고, 근거 표시를 펼치면 위 줄들로 가는 링크가 나온다. MCP `trace_flow`도 같은 단계를 낸다.

### 메시지 연결이 확인되지 않는 경우

주문 서비스의 Kafka 발행 `order-service.requestProduct`다.

```yaml
# order/src/main/resources/application.yml:15
      request-product: ${KAFKA_REQUEST_PRODUCT_TOPIC}
# docker-compose.yml:156
      - KAFKA_REQUEST_PRODUCT_TOPIC=${KAFKA_REQUEST_PRODUCT_TOPIC}
```

1. **서비스 추출**: AI가 발행 코드의 설정 키 `spring.kafka.topics.request-product`에서 설정 파일, compose `environment`까지 따라가 환경변수 이름 `KAFKA_REQUEST_PRODUCT_TOPIC`을 쓴다. 토픽의 실제 값은 쓰지 않는다.
2. **관계**: 같은 환경변수나 같은 토픽 이름을 쓰는 구독을 찾는다. 저장소 안에는 없다.
3. **검증**: 환경변수 이름은 근거 줄에 있어 "확인됨"이다. 토픽 값은 "저장소에 근거 없음"이고, 구독자는 "확인 안 됨"이다. 경고 이유 코드는 `outside-value`(저장소 밖 값)이고, 할 일은 "배포 환경에서 양쪽 값을 확인한다"이다.
4. **페이지**: 흐름 단계가 이 발행에서 멈추고 이유를 보인다. 첫 화면의 "확인 안 된 연결"과 되살리기 페이지의 "띄울 때 확인할 연결"에도 같은 항목이 나온다.

## 4. 다루는 범위

추출 Skill의 가정 표가 범위다. 표에 없는 호출·메시지 방식(gRPC, GraphQL, WebSocket, 회로 차단기 fallback 등)은 항목으로 만들지 않고 그 서비스 파일의 `unsupported`에 근거 줄과 함께 남긴다. 서비스 페이지와 흐름 결과에 나오므로, 보는 사람이 그 서비스의 흐름이 어디까지 완전한지 안다.

| 부분 | 읽는 것 |
|---|---|
| 서비스 | 빌드 파일(`build.gradle`, `build.gradle.kts`, `pom.xml`)과 `spring.application.name`이 있는 모듈. 설정은 `application*.yml`·`yaml`·`properties` |
| 엔드포인트·DTO | Spring MVC 매핑 어노테이션, Lombok·getter·public 필드, record, `@JsonProperty` |
| HTTP 호출 | OpenFeign, RestTemplate, RestClient, WebClient, HTTP 인터페이스(`@HttpExchange`) |
| 메시지 | Spring Kafka, Spring AMQP(RabbitMQ), Spring Cloud Stream |
| 게이트웨이 | Spring Cloud Gateway의 YAML·Java DSL 경로와 `RewritePath`·`StripPrefix`, 앞부분 일치 허용 목록 또는 Spring Security `permitAll` |
| 메시지 목적지 | 코드·설정에 적힌 이름, 또는 설정 키 → 설정 파일의 `${환경변수}` → compose `environment` |
| 오류 응답 | 오류 코드 enum, `@RestControllerAdvice`의 `@ExceptionHandler`, 게이트웨이 필터의 거부 응답 |
| 저장소 수준 | 배포 파이프라인, 컨테이너 실행·빌드 정의, 메트릭 수집·로그 전송·모니터링 도구 설정 |

## 5. 설계 결정과 이유

| 결정 | 이유 | 기록 |
|---|---|---|
| 추출은 AI 세션과 Skill로 한다 | 프레임워크가 달라도 지시문으로 따라간다. 사용자의 세션 작업이라 별도 유료 호출이 없다 | [언어와 라이브러리](decisions.md#언어와-라이브러리) |
| AI는 값과 근거만 쓰고 상태는 검증기가 정한다 | AI가 그럴듯한 연결을 만들어도 "확인됨"이 되지 않게 한다(2절) | [검증](decisions.md#검증) |
| 사람이 정답표를 미리 쓰지 않고 코드와 대조한다 | 넘겨받는 사람도 구조를 다 알지 못한 채 시작한다 | [검증](decisions.md#검증) |
| 검증 규칙은 "근거 줄에 이름이 있는가" 수준으로 둔다 | 특정 대상의 작성 방식에 묶이지 않게 한다 | [검증](decisions.md#검증) |
| 검증기를 남긴다 | 추출이 실행마다 흔들려도 코드와 맞는 값만 "확인됨"이 되고, 1차 추출의 체계적 표기 오류를 한 번에 드러냈다. 답의 정확도에 얼마나 기여했는지는 검증기 없는 조건으로 재지 않았다 | [검증](decisions.md#검증) |
| 범위는 근거가 어디 있는지로 가른다 | 넘겨받는 사람이 가진 것만으로 확인할 수 있는 것과 없는 것을 보여 주는 도구다. 저장소 안에 근거가 있으면 API든 배포 설정이든 내고, 저장소 밖에만 있는 것은 "저장소에 근거 없음"으로 밝힌다 | [문제 상황](decisions.md#문제-상황) |
| 메시지 목적지의 실제 값을 쓰지 않는다 | 저장소만 받은 사람은 그 값을 알 수 없다 | [Kafka 연결 규칙](decisions.md#kafka-연결-규칙) |
| 같은 환경변수 이름이나 같은 토픽 이름일 때만 메시지를 잇는다 | 한 compose 파일의 같은 `${변수}`는 같은 값으로 채워지고, 같은 이름은 같은 토픽이다. 이름이 비슷하다고 후보로 내지 않는다 | [Kafka 연결 규칙](decisions.md#kafka-연결-규칙), #58 |
| 지원하는 통신 방식은 Spring이 제공하는 방식으로 정한다 | 특정 대상이 아니라 다른 Spring MSA 저장소에서도 쓰게 한다 | [사용 범위와 흐름](decisions.md#사용-범위와-흐름), #62 |
| 형식에 없는 방식은 `unsupported`로 남긴다 | 완료 보고에만 남기면 결과를 보는 사람이 그 서비스의 흐름이 완전한지 알 수 없다 | [추출 방식](decisions.md#추출-방식) |
| 저장소 수준 배열은 도구 이름이 아니라 역할로 나눈다 | 다른 저장소의 도구도 같은 칸에 담는다 | #51 |
| 핸들러의 나가는 호출(`calls`)은 관계 파일에 둔다 | 이미 응답 비교를 거친 서비스 파일을 다시 추출하지 않는다 | [추출 방식](decisions.md#추출-방식) |
| 응답 확인 결과로 서비스 파일을 고치지 않는다 | 떠 있는 서버가 분석한 커밋과 같은 버전이라는 보장이 없다. 코드 값과 관찰 값을 나란히 보인다 | [응답 검증](decisions.md#배포된-서비스-응답-검증) |
| 응답 확인은 서버 주소와 테스트 계정만 쓴다 | 넘겨받는 사람은 로컬에 남은 설정 파일이나 팀의 기억을 갖지 못한다 | [응답 검증](decisions.md#배포된-서비스-응답-검증) |
| 업무 규칙·함정의 AI 해석은 개발자가 확인하기 전까지 미확인이다 | 검증기는 근거 줄이 있는지만 보고, 설명이 맞는지는 대조하지 않는다 | [업무 규칙과 함정](decisions.md#업무-규칙과-함정) |
| 함정은 쓰는 쪽이 겪는 결과로 가른다 | 도구는 넘겨받는 사람이 API를 잘못 쓰지 않게 하려는 것이다. "기대와 다른 동작" 기준으로는 취소 상태를 넣어도 재고·결제를 되돌리지 않는 관리자 주문 상태 수정 API가 업무 규칙으로 분류됐다 | [업무 규칙과 함정](decisions.md#업무-규칙과-함정) |
| 경고를 사용자에게 판정하거나 다시 추출하게 하지 않는다 | 경고는 "확인 안 됨"과 이유, 할 일로 결과에 나온다. 다시 추출하는 것은 도구를 고치는 사람의 일이다 | [검증](decisions.md#검증) |

## 6. 영역별 확인 정도

실제 저장소는 HotDealWave와 SpartaHub 두 개로 썼다.

| 영역 | 확인 정도 | 남은 것 |
|---|---|---|
| 서비스 찾기 | 실제 저장소 두 개 | |
| 엔드포인트·DTO | 실제 저장소 두 개 | 헤더 파라미터 `name`을 헤더 이름과 변수 이름 중 무엇으로 쓸지 형식에 규칙이 없다. 경로 인자가 없는 매핑의 빈 경로를 보일 줄이 없다 |
| HTTP 호출: OpenFeign, RestTemplate | 실제 저장소 두 개 | |
| HTTP 호출: RestClient, WebClient, HTTP 인터페이스 | 대표 테스트 | 실제 저장소로 써 보지 않았다 |
| 메시지: Kafka | 실제 저장소 | |
| 메시지: RabbitMQ, Stream | 대표 테스트 | 실제 저장소로 써 보지 않았다 |
| 저장소 수준 | 실제 저장소 두 개 | 이미지만 쓰는 컨테이너의 빌드 명령처럼 "해당 없음"을 담을 자리가 없다 |
| 업무 규칙·함정 | 실제 저장소 두 개 | AI 해석이라 개발자 확인 전까지 미확인 |
| 게이트웨이 경로 | 실제 저장소 두 개 | 두 저장소 모두 Spring Cloud Gateway였다 |
| 게이트웨이 토큰 판정 | HotDealWave 방식과 대표 테스트 | 허용 목록을 다른 방식으로 비교하면(예: 메서드를 보지 않음) 모든 엔드포인트가 "확인 안 됨"이다 |
| 메시지 목적지의 환경변수 | compose만 따라감 | Spring Cloud Config 서버, k8s·Helm 매니페스트의 값은 따라가지 않는다 |
| 오류 응답 | 실제 저장소 두 개 | 필드 이름과 메시지를 이어 붙인 조립 메시지는 출처 칸이 없다 |

엔드포인트는 매핑 어노테이션만 읽는다. WebFlux 함수형 라우팅(`RouterFunction`)으로 만든 엔드포인트는 항목에도 `unsupported`에도 남지 않는다.

다른 구성에 맞추려면 고칠 파일은 [README의 표](../README.md#다른-구성에-맞추려면-고칠-곳)에 있다. SpartaHub에서 경고를 원인별로 나눈 결과는 [10단계 보고서](reports/general-msa-spartahub.md)에, 남은 과제는 [같은 보고서 7절](reports/general-msa-spartahub.md#7-남은-과제)에 있다.
