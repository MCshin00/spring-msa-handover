# Spring 마이크로서비스 인수인계 도구

문서도, 인수인계해 줄 사람도 없이 마이크로서비스 프로젝트를 넘겨받을 때 쓰는 도구다. 서비스는 떠 있는데 문서가 없어 API를 쓰는 쪽(프론트엔드 개발자, 에이전트)이 요청·응답과 흐름을 알 수 없는 경우와, 저장소만 남아 서비스를 다시 띄워야 하는 경우를 다룬다. Spring Boot·Spring Cloud 마이크로서비스 저장소의 고정 커밋에서 API, 서비스 간 호출, 메시지 흐름, 설정, 배포 정의를 뽑고, 서버가 떠 있으면 실제 응답과 맞춰 본 뒤, 문서 페이지·검색·질의응답·MCP로 제공한다. 모든 값에는 근거 `파일:줄`과 확인 상태가 붙는다.

## 하는 일

| | 서비스가 떠 있고 문서가 없음 | 저장소만 남음 |
|---|---|---|
| 쓰는 사람 | 서비스 API를 쓰는 사람과 에이전트. 코드를 갖지 않았거나 읽지 않는다 | 서비스를 다시 띄울 개발자 |
| 필요한 것 | API 요청·응답, 서비스 사이 흐름, 각각 어디까지 확인됐는지 | 띄우는 데 필요한 환경변수·외부 자원, 이어지지 않는 연결, 운영 함정 |
| 보는 곳 | 문서 페이지, 질의응답, MCP | 문서 페이지, 되살리기 페이지 |

분석은 두 경우가 같다. 저장소를 읽어 추출하고, 서버가 떠 있으면 실제 응답으로 확인 범위를 가른다.

```
대상 저장소 @고정 커밋
  --> AI 세션이 Skill로 추출 (서비스, 관계, 배포·모니터링, 업무 규칙·함정)
  --> verify: Java 검증기가 근거 줄과 연결을 대조해 상태를 정함
  --> serve: 서버 하나
        +--> 문서 페이지: 서비스·엔드포인트·토픽·배포·되살리기
        +--> 검색·질의응답
        +--> MCP (Claude Code·Codex에서 도구로)
```

단계마다 무엇을 읽고 쓰는지, 상태를 어떻게 정하는지는 [추출 방식](docs/extraction.md)에 있다.

- **문서 페이지**: 엔드포인트마다 요청·응답 필드, 이어지는 HTTP 호출과 메시지 발행·구독, 오류 응답, 업무 규칙·함정이 나온다.
- **되살리기 페이지**: 저장소만 남았을 때 띄우는 데 필요한 것(외부 자원·외부 API, 채워야 할 환경변수, 저장소 밖 비밀값, 컨테이너, 띄울 때 확인할 연결, 운영 함정)을 한곳에 모은다.
- **검색·질의응답**: 문서를 BM25와 임베딩으로 찾고, 찾은 조각 안에서만 답한다. 근거가 없으면 답하지 않고 보류한다.
- **MCP**: 문서 검색·페이지 읽기에 더해, 엔드포인트에서 HTTP 호출·메시지 흐름을 따라가는 도구를 Claude Code·Codex에 연다.

| 첫 화면 | 엔드포인트 페이지 | 되살리기 페이지 |
|---|---|---|
| ![첫 화면](docs/images/home.png) | ![엔드포인트 페이지](docs/images/endpoint.png) | ![되살리기 페이지](docs/images/revival.png) |

예시 HotDealWave의 화면이다. 근거 표시를 펼치면 고정 커밋의 해당 줄로 가는 링크가 나온다.

| 상태 | 뜻 |
|---|---|
| 확인됨 | 저장소 안의 코드·설정으로 확정된다 |
| 확인 안 됨 | 저장소 안에서 연결을 확정할 수 없다(예: 발행·구독 쪽 토픽 환경변수 이름이 다름) |
| 저장소에 근거 없음 | 저장소 밖에만 있는 사실이다(예: 환경변수 값, 클라우드 콘솔 설정) |

업무 규칙·함정은 AI가 코드를 읽고 쓴 해석이라 출처를 "AI 해석"으로 달고, 개발자가 확인한 것만 확인 상태를 단다.

## 빠른 시작

JDK 17 이상, Git, 코딩 에이전트 하나(Claude Code 또는 Codex)가 필요하다.

1. **받기와 빌드**:
   ```bash
   git clone https://github.com/MCshin00/spring-msa-handover.git
   cd spring-msa-handover
   ./gradlew assemble
   ```
2. **대상 받기**: 작업 폴더의 `source/`에 대상을 받아 그 커밋으로 고정하고, AI 세션용 지시 파일을 쓴다.
   ```bash
   java -jar build/handover.jar init --repo <경로 또는 GitHub 주소> --commit <커밋> --workspace <작업 폴더>
   ```
3. **추출**: 작업 폴더에서 AI 세션을 열고 "지시 파일대로 끝까지 해 줘"라고 한다.
4. **결과 보기**: `http://localhost:8080`에서 본다. 첫 화면에서 서비스·엔드포인트·메시지 목적지·배포 페이지로 들어가고, 띄우는 데 필요한 것은 되살리기 페이지(`/revival`)에 모여 있다.
   ```bash
   java -jar <도구 폴더>/build/handover.jar serve --workspace <작업 폴더>
   ```
5. **MCP 등록**(서버를 띄운 채):
   ```bash
   claude mcp add --transport http handover http://localhost:8080/mcp
   ```
   ```bash
   codex mcp add handover --url http://localhost:8080/mcp
   ```

질의응답은 `OPENAI_API_KEY` 환경변수가 있어야 하고, 질문 한 번에 유료 호출이 두 번(질문 임베딩, 답) 든다. 페이지·검색·MCP는 모델을 부르지 않는다.

## 구조

```
spring-msa-handover/
|
+-- examples/hotdealwave/   # 예시 작업 폴더 (추출 결과, 응답, 업무 규칙·함정, 검증 출력)
+-- .claude/skills/         # 추출·응답 확인·업무 규칙 Skill
|
+-- cli/                    # init·verify·serve. 검증기와 서버를 build/handover.jar 하나로 묶음
+-- verifier/               # 검증기 (Java 17) -> out/analysis.json, 상태·경고
+-- server/                 # Spring Boot 서버: 페이지, /api/search, /api/ask, /mcp
|
+-- docs/                   # 결정 기록, 형식, 진행 상황, 단계 보고서
+-- eval/                   # 최종 질문, 결과, 채점
```

```
[MCP]      질문 -> 클라이언트의 모델이 /mcp 도구를 고름 -> 도구가 문서 본문·흐름 단계를 상태·근거와 함께 냄 -> 모델이 답함
[질의응답] 질문 -> 문서 조각 검색(BM25 + 임베딩, RRF) -> 외부 모델 한 번 -> 인용 검사 -> 답 또는 보류
```

| MCP 도구 | 하는 일 |
|---|---|
| `find_endpoint` | 경로·ID·핸들러 이름으로 엔드포인트 본문(요청·응답 필드, 호출, 검증 범위, 업무 규칙·함정)을 낸다 |
| `trace_flow` | 엔드포인트·메시지 발행·구독에서 HTTP 호출·메시지 발행·구독을 따라간 단계와 부르는 곳을 낸다. 이어지지 않는 곳은 상태와 이유에서 멈춘다 |
| `find_subscribers` | 토픽 이름으로 발행·구독과 그 연결을 담은 토픽 본문을 낸다 |
| `search_docs` | 본문을 BM25로 찾아 상위 5개 절의 페이지 주소·절 제목·점수·미리보기를 낸다 |
| `get_page` | 페이지 주소로 본문 전체를 낸다 |

```
find_endpoint("POST /api/v1/orders")
trace_flow("order-service.OrderController.createOrder")
find_subscribers("KAFKA_REQUEST_PRODUCT_TOPIC")
search_docs("쿠폰 복구")
get_page("/revival")
```

## 지원 범위

| | 실제 저장소로 확인 | 대표 테스트로 확인 | 다루지 않음 |
|---|---|---|---|
| HTTP 호출 | OpenFeign, RestTemplate | RestClient, WebClient, HTTP 인터페이스 | gRPC, GraphQL, WebSocket |
| 메시지 | Kafka | RabbitMQ, Spring Cloud Stream | |
| 게이트웨이 | Spring Cloud Gateway, Eureka | Spring Security `permitAll` | |
| 그 밖 | | | 회로 차단기 fallback, 재시도(`unsupported`로 남음) |

빌드는 Gradle(Groovy·Kotlin DSL)과 Maven, 설정은 `application*.yml`·`yaml`·`properties`, 코드는 Java를 읽는다. 저장소 하나에 서비스가 모인 구성을 전제한다. 영역별로 어디까지 확인했고 무엇이 남았는지는 [추출 방식](docs/extraction.md#5-영역별-확인-정도)에 있다.

### 다른 구성에 맞추려면 고칠 곳

| 부분 | 지금 가정 | 고칠 파일 |
|---|---|---|
| 서비스·설정 파일 | 빌드 파일(`build.gradle`, `build.gradle.kts`, `pom.xml`)과 `spring.application.name`이 있는 모듈, `application*.yml`·`yaml`·`properties`, Java 소스 | 추출 Skill 가정 표, `verifier/.../SourceText.java`(근거 파일 종류·모듈 찾기·설정 키) |
| 게이트웨이 | Spring Cloud Gateway의 YAML·Java DSL 경로, `RewritePath`·`StripPrefix`, 앞부분 일치 허용 목록 또는 Security `permitAll` | 추출 Skill, `CrossChecks.java`(경로 필터·토큰 판정), `EvidenceChecks.java`(필터 근거) |
| 내부 호출·메시지 | 위 지원 범위 표 | 추출 Skill의 종류별 읽는 법과 검증기의 선언 줄 규칙(`CommunicationRules.java`) |
| 오류 응답 | 오류 코드 enum, `@RestControllerAdvice`, 게이트웨이 필터 | 형식 문서와 스키마, 추출 Skill, `EvidenceChecks.java`·`ThrowChecks.java` |
| DTO JSON 이름 | `@JsonProperty`, record, Lombok·getter, public 필드 | 형식 문서, `EvidenceChecks.java`(`jsonName`) |
| 응답 확인 | 게이트웨이로 부르는 JSON API와 토큰 로그인 | 응답 확인 Skill, `ResponseComparison.java` |

## 두 저장소에서 써 본 결과

### HotDealWave: 저장소 없이 답하기

[HotDealWave](https://github.com/sparta-final-12th-9700/HotDealWave)는 서비스 8개, 엔드포인트 72개, Kafka 토픽을 쓰는 팀 프로젝트다. 서비스가 떠 있고 문서가 없는 경우를 재려고, 고정 커밋을 띄워 응답을 기록한 뒤 개발자가 실험 전에 고정한 질문 21개로 저장소 없이 이 도구로 답하는 쪽과 저장소를 가진 Codex를 비교했다.

| 답하는 쪽 | 맞음 | 부분 | 틀림 | 확인 안 된 것을 확인된 것처럼 말한 답 | 걸린 시간(중앙값) |
|---|---|---|---|---|---|
| Codex + 저장소 | 21 | 0 | 0 | 1 | 71초 |
| Codex + MCP(저장소 없음) | 12 | 9 | 0 | 0 | 49초 |
| 질의응답(`/api/ask`) | 5 | 11 | 5 | 0 | 4초 |

- MCP만 쓴 Codex는 틀린 답이 없었고, 확인 안 된 연결을 확인된 것처럼 말하지 않았다. 파일 인용 580개는 모두 고정 커밋에 있는 줄이었다.
- 질의응답이 답한 12개에는 틀린 답이 없다. 틀림 5개는 모두 근거가 부족해 답하지 않은 경우다.

자세한 결과는 [9단계 보고서](docs/reports/eval-hotdealwave.md)에 있다.

이 결과는 저장소에 커밋되어 있어 대상 코드 없이 띄워 볼 수 있다.

```bash
java -jar build/handover.jar serve --workspace examples/hotdealwave
```

### SpartaHub: 처음 쓰는 사람의 순서로

두 번째 대상 [13th-SpartaHub-Backend](https://github.com/13th-SpartaHub/13th-SpartaHub-Backend)(서비스 10개, Feign·RestTemplate, 회로 차단기)에 도구를 새로 받아 처음 쓰는 사람의 순서로 돌렸다.

| | 첫 시도 | 결함 수정 뒤 재추출 |
|---|---|---|
| 검증 경고 | 52 | 16 |
| 확인된 내부 호출 대상 | 2 | 20 |
| 업무 규칙·함정·설명 | 없음 | 171 |

- HotDealWave에 없던 패턴(매핑의 `headers` 속성, 설정값 주소의 외부 API, 메서드를 비교하지 않는 허용 목록)으로 드러난 도구 결함 네 가지를 고쳤다.
- 재추출은 서비스별 하위 에이전트로 동시에 해서 업무 규칙·함정까지 약 47분 걸렸다.
- 남은 경고 16개는 모두 원인을 판정했다(추출 잘못 7, 실제 코드 3, 도구가 모름 6).
- 결과 파일에 대상 저장소의 비밀값은 들어가지 않았다.

자세한 결과는 [10단계 보고서](docs/reports/general-msa-spartahub.md)에 있다.

## 개발

```bash
java -jar build/handover.jar init --repo https://github.com/sparta-final-12th-9700/HotDealWave --commit cc43016 --workspace examples/hotdealwave
./gradlew build
python scripts/check_shared_files.py
python -m unittest discover tests
```

`verify`의 검사 항목, 예시 검증 출력 다시 만들기, 비교 실험·채점 명령은 [개발 문서](docs/development.md)에 있다. 결정과 이유는 [결정 기록](docs/decisions.md), 진행 상황은 [진행 상황](docs/progress.md)에 있다.
