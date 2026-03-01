# onMeet Common Module Guide

`onmeet-common` 모듈은 서비스 간 통신(Inter-service Communication) 및 공통 구성을 위한 공유 라이브러리입니다.

## 1. 개요
이 모듈은 서비스 간 API 호출 시 인증 정보를 유지하고, 공통적인 네트워크 설정을 관리하기 위해 만들어졌습니다.

## 2. 주요 구성 요소

### BaseServiceClient (`com.onmeet.common.client`)
모든 외부/내부 서비스 클라이언트를 위한 추상 클래스입니다.
- **쿠키 포워딩**: 현재 브라우저(또는 클라이언트)에서 넘어온 쿠키를 내부 API 호출 시 자동으로 포함합니다.
- **에러 처리**: 기본적인 API 요청 실패에 대한 로깅과 예외 처리를 제공합니다.

### RestTemplateConfig (`com.onmeet.common.config`)
- `RestTemplate` 빈을 설정합니다.
- 타임아웃(Timeout) 설정: Connect 5초, Read 5초 (기본값).

### ErrorResponse (`com.onmeet.common.dto`)
표준화된 에러 응답 규격입니다.
- **필드**: `status` (HTTP 상태 코드), `message` (에러 메시지), `timestamp` (발생 시각).
- Java 호환성을 위해 `@JvmOverloads`가 적용되어 있습니다.

### Security Components (`com.onmeet.common.security`)
서비스 간 인증 및 보안을 위한 공통 컴포넌트입니다.
- `GatewayPreAuthFilter`: Gateway에서 전달된 인증 헤더(X-User-Id, X-User-Roles) 및 보안 시크릿(X-Gateway-Secret)을 검증하여 SecurityContext를 설정합니다.
- `UserContext`: 현재 요청 쓰레드의 인증 정보(UserId, Email, Roles)를 쉽게 가져올 수 있는 유틸리티입니다.
- `JwtConstants`: JWT 관련 공통 상수(헤더 키, 쿠키 명 등)를 정의합니다.

### Common Exceptions (`com.onmeet.common.exception`)
전 서비스에서 공통으로 발생하는 예외들입니다.
- `BaseException`: 모든 커스텀 예외의 최상위 클래스.
- `EntityNotFoundException`: 리소스를 찾을 수 없을 때 (404).
- `InsufficientPermissionException`: 권한이 부족할 때 (403).

### BaseGlobalExceptionHandler (`com.onmeet.common.exception`)
전역 예외 처리를 위한 추상 클래스입니다.
- 각 서비스의 `GlobalExceptionHandler`에서 이 클래스를 상속받아 사용합니다.
- `IllegalArgumentException`, `MethodArgumentNotValidException` 등 공통 Spring 예외와 위 공통 예외들에 대한 핸들러가 포함되어 있습니다.

## 3. 사용 방법

### 새 모듈에 의존성 추가
사용하고자 하는 서비스의 `build.gradle`에 다음과 같이 추가합니다.
```gradle
dependencies {
    implementation project(':onmeet-common')
}
```

### 전역 예외 처리기 적용
`BaseGlobalExceptionHandler`를 상속받아 구현합니다.
```kotlin
@RestControllerAdvice
class GlobalExceptionHandler : BaseGlobalExceptionHandler() {
    // 해당 서비스만의 특화된 예외 핸들러가 필요한 경우 여기에 추가
}
```

### 서비스 클라이언트 구현 예시
`BaseServiceClient`를 상속받아 구현합니다.
```kotlin
@Component
class MyServiceClient(
    restTemplate: RestTemplate,
    @Value("${service.url}") private val serviceUrl: String
) : BaseServiceClient(restTemplate) {

    fun getData(id: Long): MyResponse? {
        return getWithAuth("$serviceUrl/api/v1/data/$id", MyResponse::class.java)
    }
}
```

### Java 서비스 클라이언트 구현 예시
Java 서비스에서도 `BaseServiceClient`를 상속받아 동일하게 사용할 수 있습니다.
```java
@Component
public class MyJavaServiceClient extends BaseServiceClient {

    private final String serviceUrl;

    public MyJavaServiceClient(RestTemplate restTemplate, 
                               @Value("${service.url}") String serviceUrl) {
        super(restTemplate);
        this.serviceUrl = serviceUrl;
    }

    public MyResponse getData(Long id) {
        String url = serviceUrl + "/api/v1/data/" + id;
        return getWithAuth(url, MyResponse.class);
    }
}
```

## 4. 주의 사항
- `BaseServiceClient`는 `RequestContextHolder`를 사용하므로, REST Controller 요청 쓰레드 내에서 호출될 때 쿠키가 정상적으로 전파됩니다.
- 비동기(@Async) 또는 별도 쓰레드에서 호출 시 인증 정보를 직접 전달해야 할 수 있습니다.
- 새로운 공통 예외가 필요한 경우 `onmeet-common` 모듈의 `CommonExceptions.kt`에 추가하여 공유합니다.
