# AI 서비스 핵심 로직 및 코드 가이드 (Core Logic & Code Explanation)

OnMeet의 AI 서비스(`ai-service`)는 실시간 화상회의 음성 데이터를 텍스트로 변환하고, LLM을 이용해 최종 회의록을 요약하는 **비동기 이벤트 기반 파이프라인**으로 작동합니다.
이 문서에서는 주요 기능의 동작 흐름과 핵심 코드 단위를 한 줄씩 자세히 분석합니다.

---

## 1. 실시간 오디오 분석 및 STT 변환 (`AudioChunkProcessor.java`)

회의 중 업로드된 오디오 조각(Chunk)을 감지하고, "말소리가 있는 구간"만 찾아 텍스트로 변환하는 가장 앞단의 역할을 수행합니다.

### 📌 코드 단계별 분석

**1. 이벤트 감지 및 오디오 로드**
```java
@KafkaListener(topics = "audio.chunk.ready")
public void onAudioChunk(AudioChunkEvent event) {
    // Media 릴레이 서버 등에서 오디오를 저장했다는 이벤트를 수신합니다.
    // event.getFileId()를 이용해 S3 (또는 내부 저장소)에서 오디오의 순수 바이트 배열을 메모리로 불러옵니다.
    byte[] audioBytes = storageClient.readBytes(event.getFileId());
```
> **설명**: 파일 처리 지연을 막고자 Kafka 메시지 기반 비동기 워커로 즉시 분산 처리됩니다.

<br>

**2. VAD(무음 필터링)를 통한 최적화**
```java
    // 오디오 원본 덩어리 안에는 아무 말도 하지 않는 '공백 시간'이 포함되어 있습니다.
    // VAD(Voice Activity Detection) 엔진이 사람 목소리 파형을 감지하여 실 발화 구간(Segment)으로만 배열을 쪼개 반환합니다.
    List<AudioSegment> segments = vadEngine.segment(audioBytes);
```
> **설명**: OpenAI Whisper와 같은 외부 STT 엔진에 "빈 소리"를 전송하면 토큰(비용)과 처리 시간만 낭비됩니다. VAD를 통해 핵심 구간만 추출하여 효율성을 극대화합니다.

<br>

**3. STT 변환 및 실시간 발행**
```java
    for (AudioSegment seg : segments) {
        // 잘라낸 1~3초 가량의 음성 버퍼를 OpenAI Whisper에 넘겨 한국어/다국어 텍스트 문장으로 받아냅니다.
        String text = openAiSttClient.transcribe(seg.getBytes());
        
        // 변환된 텍스트 조각은 해당 발화의 상대적 시간 정보(시작/끝)와 묶여
        // 회의 종료 후 전체 대본(Transcript) 조립을 위한 기초 데이터로 사용되도록 Kafka 이벤트로 발행합니다.
        publishVoiceSegment(event.getRoomId(), text, seg.getStartTime(), seg.getEndTime());
    }
}
```
> **설명**: 갓 변환된 이 텍스트 조각(Voice Segment) 이벤트는 곧바로 임시 캐시(Redis 등)에 차곡차곡 쌓이게 되며, 이는 회의 종료 시점에 하나의 완벽한 회의 대본 스크립트를 조립하기 위한 핵심 재료가 됩니다.

### 🔍 아키텍처 도입 결정 배경

- **Why Kafka Event-Driven?**: 수십~수백 명의 화상회의 동시 접속자가 1~3초 간격으로 오디오를 쏟아낼 때, 이를 REST API(동기 방식)로 받으면 서버 스레드가 순식간에 고갈됩니다. 때문에 오디오 청크를 스토리지에 먼저 던져놓고, "파일이 준비되었다"는 가벼운 이벤트표만 Kafka로 발행하여 메시지 유실 없이 안정적이고 비동기적인 STT 처리를 보장하기 위해 도입되었습니다.
- **Why Silero VAD?**: 클라우드 API 기반의 벤더 STT 엔진들은 전송하는 "오디오 길이(초)"만큼 과금이 적용됩니다. 사내 시스템에서 경량화된 모델을 우선 가동해 무음 구간을 폐기하고 진로를 방해하는 노이즈를 먼저 걸러냄으로써, 타사 API 호출 비용을 획기적으로 낮추고 전사된 텍스트의 품질을 끌어올립니다.

---

## 2. 분산된 대화록 무결성 병합 (`TranscriptBuilderService.java`)

회의 진행 중 1~3초 단위로 쪼개져 캐싱되었던 수백 개의 텍스트 조각들을 회의가 종료된 시점에 모아 '완전한 대본(Transcript)'으로 조립합니다.

### 📌 코드 단계별 분석

**1. 회의 종료 이벤트 수신 및 정렬 조회**
```java
@KafkaListener(topics = "meeting.ended")
public void onMeetingEnded(MeetingEndedEvent event) {
    // 해당 Room(회의방)에서 발생했던 모든 발화 세그먼트 기록을 데이터베이스(또는 캐시)에서 조회합니다.
    // 이 때 실제 말한 순서대로 대본을 만들어야 하므로 반드시 시작 시간(StartAsc) 기준으로 쿼리합니다.
    List<VoiceSegment> allSegments = segmentRepository.findAllByRoomIdOrderByStartAsc(event.getRoomId());
```

<br>

**2. 합본 문단 구성 및 Redis(음성/채팅) 병합**
```java
    // 흩어져 있던 화자(Participant ID)와 시간 정보를 조합해 하나의 매끄러운 문서로 짓어낼 빌더 객체입니다.
    TranscriptDocument finalTranscript = new TranscriptDocument(event.getRoomId());
    
    // 이 과정에서 중요한 점은, 음성에서 나온 텍스트(STT)와 텍스트 타이핑(채팅) 메시지가
    // Redis에 각각의 스트림/캐시로 분리수집되고 있었다는 사실입니다.
    // 회의가 끝난 지금, Redis에 분리 보관되어있던 이 두 종류의 조각들을 모두 가져와 
    // 시간순으로 교차시키며 무결점의 하나의 "통합 스크립트"로 결합(Merge)합니다.
    finalTranscript.appendAll(allSegments);
    
    // 음성/채팅이 합쳐져 완벽해진 문서 객체를 향후 영구 보존에 유리하고 열람하기 쉬운 JSON 문자열 규격으로 직렬화합니다.
    String transcriptJson = objectMapper.writeValueAsString(finalTranscript);
```

<br>

**3. Object Storage(S3) 영구 보관**
```java
    // RDBMS 버퍼를 지키기 위해, 대용량 텍스트 스크립트는 S3의 고유 키를 채번받아 업로드 처리합니다.
    String s3Key = StorageKeyFactory.transcriptKey(event.getRoomId());
    storageClient.writeText(s3Key, transcriptJson, "application/json");

    // "원본 대본이 무사히 생성되었으니, 다음 단계인 'AI 요약'을 시작하라"는 신호를 쏩니다.
    eventPublisher.publishTranscriptFinalized(event.getRoomId(), s3Key);
}
```
> **설명**: 회의록과 대본의 원본 데이터 관리 주체를 철저히 분리하여 데이터 파이프라인의 안전함을 유지합니다.

### 🔍 아키텍처 도입 결정 배경

- **Why DB separation (S3 over RDBMS)?**: 1~2시간짜리 회의 대본은 수천~수만 줄의 엄청난 길이를 갖는 거대한 텍스트(JSON) 객체 속성을 띕니다. 이를 MySQL과 같은 관계형 데이터베이스의 TEXT 컬럼에 직접 밀어 넣으면, DB 엔진 버퍼 메모리에 병목이 생기고 조회 성능이 극도로 구려집니다. 따라서 값이 비싸고 한정적인 DB 자원 대신, 무제한적이고 저렴한 S3 블록 스토리지에 원본 본문을 보관하고, DB에는 그 위치를 가리키는 열쇠(S3 Key URL)만 쥐여주는 아키텍처 패턴을 택했습니다.

---

## 3. 맥락 기반 AI 요약 프로세스 (`SummaryWorkerService.java`)

위에서 최종 완성된 원본 대본을 읽어들이고, LLM(Claude)을 통해 압축/요약/키워드 추출을 진행한 뒤 DB의 각 컬럼으로 분배합니다.

### 📌 코드 단계별 분석

**1. 대본 다운로드 및 가독성 렌더링**
```java
public void processSummary(Long roomId, String transcriptS3Key) {
    // 방금 S3에 올라간 대본 JSON 원문(화자 맵핑, 타임스탬프 딕셔너리 정보 포함)을 다운받습니다.
    String transcriptJson = storageClient.readText(transcriptS3Key);
    
    // LLM 모델이 이해하기 쉽도록 불필요한 JSON 문법을 제거하고 "사람1: 안녕\n사람2: 네 반가워요" 같은 순수 줄글로 전처리합니다.
    String plainText = transcriptRenderer.toPlainText(transcriptJson);
```

<br>

**2. LLM 요약 실행 및 체인 프롬프팅**
```java
    // 전처리된 줄글 본문을 Anthropic Claude 로 보내며 백엔드에 사전에 주입된 시스템 프롬프트를 함께 태웁니다.
    // "한국어, 비즈니스 톤으로 회의록 상세 내용을 요약해줘 + JSON 형태를 반환해줘"
    String summaryJson = summarizerClient.summarize(plainText, "ko", "business", "claude-sonnet");
```

<br>

**3. DB 효율적 색인을 위한 객체 파싱 (ONMEET-57 핵심 기능)**
```java
    // Claude가 만들어준 거대한 문자열 텍스트를, Jackson 라이브러리를 통해 안전한 자바 객체(SummaryResult)로 파싱해 쪼갭니다.
    SummaryResult result = objectMapper.readValue(summaryJson, SummaryResult.class);

    // AI가 생성한 회의록 메타데이터를 저장하기 위해 Entity 인스턴스를 조립합니다.
    Minutes minutes = Minutes.createGenerated(
        roomId,
        transcriptS3Key,
        summaryS3Key,                       
        result.getDescription(),            // [검색용 DB 파싱] "회의 전체 설명" 분리 저장
        toJson(result.getKeywords()),       // [검색용 DB 파싱] "#회의주제" 등 배열을 리얼 JSON 텍스트로 전환
        toJson(result.getDecisions()),      // [검색용 DB 파싱] "의결 사항" 리스트
        toJson(result.getActionItems()),    // [검색용 DB 파싱] "다음 할 일" 리스트
        summaryJson                         // 만약을 대비해 파싱 전 원본도 계속 유지
    );
    
    // 최종적으로 JpaRepository를 통해 영구 물리 테이블(`minutes`)에 저장되어 사용자에게 노출됩니다.
    minutesRepository.save(minutes);
}
```
> **설명**: Claude가 내려주는 방대한 내용을 DB의 한 컬럼에 욱여넣지 않고 개별 필드 구조로 찢어(`Description`, `Keywords` 등) 적재함으로써, 프론트엔드의 화면 표시 유연성은 물론이고 **정교한 키워드/내용 기반 회의록 검색 쿼리 작성** 최적화를 이뤄냅니다.

### 🔍 아키텍처 도입 결정 배경

- **Why Claude Result DB Parsing?**: 과거 V1 모델에서는 LLM이 뱉어낸 통짜 마크다운 텍스트 덩어리를 통째로 텍스트 컬럼에 저장했습니다. 하지만 이렇게 처리하면 프론트엔드가 이를 디자인 화면에 입맛대로 배치하기도 힘들 뿐더러, 결정적으로 "이 키워드나 할 일이 나온 회의가 뭐였는지" 검색하고 싶을 때 DB 안의 수만 글자를 풀스캔해야 하는 쿼리 지연이 발생합니다. 이에 LLM 프롬프트 설계 단계부터 "반드시 JSON 구조로 대답해" 라고 트리거를 걸고, 이것을 백엔드가 분해하여 각각 인덱스(`Index`)를 태울 수 있는 컬럼들(키워드, 의결사항 등)에 각각 저장함으로써 해당 AI 파이프라인을 단순한 텍스트 뷰어가 아닌 **데이터 자산 검색 엔진**으로서의 성능을 보장할 수 있게 업그레이드하기 위해 이 구조를 사용했습니다.
