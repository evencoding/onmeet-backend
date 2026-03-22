package com.onmeet.ai.controller;

import com.onmeet.ai.dto.request.MinutesPatchRequest;
import com.onmeet.ai.dto.request.MinutesRegenerateRequest;
import com.onmeet.ai.dto.response.MinutesResponse;
import com.onmeet.ai.dto.response.TranscriptResponse;
import com.onmeet.ai.service.MinutesService;
import com.onmeet.common.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

// CHECK [ai-담당자]: MinutesController URL 패턴이 /v1/rooms/{roomId}/minutes로 변경됨.
// Frontend에서 /ai/v1/rooms/{roomId}/minutes로 호출하면 Gateway가 /ai/v1 strip 후
// /rooms/{roomId}/minutes → 이 컨트롤러로 매핑됨. Gateway strip prefix 동작 확인 필요.

// CHECK [frontend-담당자]: AI API URL 패턴이 Backend에서 Frontend 계약에 맞게 변경됨.
// Frontend의 /ai/v1/rooms/{roomId}/minutes 호출이 정상 동작하는지 E2E 테스트 필요.
@Tag(name = "Minutes", description = "회의록 조회/수정/재생성 API")
@RestController
@RequestMapping("/v1/rooms")
public class MinutesController {

    private final MinutesService minutesService;

    public MinutesController(MinutesService minutesService) {
        this.minutesService = minutesService;
    }

    @Operation(summary = "회의록 조회", description = "roomId로 해당 회의의 회의록을 조회합니다. AI가 생성한 요약 및 원본 트랜스크립트 정보를 포함합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "회의록 조회 성공"),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필요 - 유효한 JWT 토큰이 없거나 만료된 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"GATEWAY_001\",\"status\":401,\"message\":\"인증 토큰이 없습니다.\",\"timestamp\":1710000000000}"))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "해당 회의실의 회의록을 찾을 수 없는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "AI_001 - 회의록 없음",
                    value = "{\"code\":\"AI_001\",\"status\":404,\"message\":\"해당 회의실의 회의록을 찾을 수 없습니다\",\"timestamp\":1710000000000}"))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류 - 데이터 읽기 실패 또는 파싱 실패",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "AI_002 - 데이터 읽기 실패",
                        value = "{\"code\":\"AI_002\",\"status\":500,\"message\":\"데이터를 읽는 중 오류가 발생했습니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "AI_003 - 파싱 실패",
                        value = "{\"code\":\"AI_003\",\"status\":500,\"message\":\"트랜스크립트 JSON 파싱 중 오류가 발생했습니다\",\"timestamp\":1710000000000}")
                })
        )
    })
    @GetMapping("/{roomId}/minutes")
    public MinutesResponse get(@PathVariable Long roomId) {
        return minutesService.get(roomId);
    }

    @Operation(summary = "회의록 내용 검색", description = "회의 요약의 설명, 키워드, 결정사항, 작업 목록에서 특정 키워드가 포함된 회의록을 검색합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "검색 성공", content = @Content(mediaType = "application/json"))
    })
    @GetMapping("/minutes/search")
    public java.util.List<MinutesResponse> search(@RequestParam("q") String keyword) {
        return minutesService.search(keyword);
    }

    @Operation(summary = "회의록 재생성", description = "기존 트랜스크립트를 기반으로 AI가 회의록을 다시 생성합니다. 프롬프트 커스터마이징이 가능합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "회의록 재생성 성공"),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필요",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"GATEWAY_001\",\"status\":401,\"message\":\"인증 토큰이 없습니다.\",\"timestamp\":1710000000000}"))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "해당 회의실의 회의록을 찾을 수 없는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "AI_001 - 회의록 없음",
                    value = "{\"code\":\"AI_001\",\"status\":404,\"message\":\"해당 회의실의 회의록을 찾을 수 없습니다\",\"timestamp\":1710000000000}"))
        ),
        @ApiResponse(
            responseCode = "422",
            description = "트랜스크립트가 비어 있어 처리할 수 없는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "AI_004 - 트랜스크립트 없음",
                    value = "{\"code\":\"AI_004\",\"status\":422,\"message\":\"트랜스크립트가 비어 있어 처리할 수 없습니다\",\"timestamp\":1710000000000}"))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류 - AI 요약 처리 실패 또는 파일 서버 저장 실패",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "AI_005 - AI 요약 실패",
                        value = "{\"code\":\"AI_005\",\"status\":500,\"message\":\"AI 요약 처리 중 오류가 발생했습니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "AI_006 - 파일 저장 실패",
                        value = "{\"code\":\"AI_006\",\"status\":500,\"message\":\"파일 서버에 저장하는 중 오류가 발생했습니다\",\"timestamp\":1710000000000}")
                })
        ),
        @ApiResponse(
            responseCode = "502",
            description = "외부 AI API(Claude/OpenAI) 호출 실패",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "AI_020 - Claude API 실패",
                    value = "{\"code\":\"AI_020\",\"status\":502,\"message\":\"Claude API 호출 중 오류가 발생했습니다\",\"timestamp\":1710000000000}"))
        ),
        @ApiResponse(
            responseCode = "504",
            description = "외부 AI API 타임아웃",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "AI_021 - Claude API 타임아웃",
                    value = "{\"code\":\"AI_021\",\"status\":504,\"message\":\"Claude API 연결 시간이 초과되었습니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PostMapping("/{roomId}/minutes/regenerate")
    public MinutesResponse regenerate(
            @PathVariable Long roomId,
            @RequestBody(required = false) MinutesRegenerateRequest req
    ) {
        return minutesService.regenerate(roomId, req);
    }

    @Operation(summary = "회의록 수정", description = "회의록의 제목, 요약 텍스트 등을 수정합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필요",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"GATEWAY_001\",\"status\":401,\"message\":\"인증 토큰이 없습니다.\",\"timestamp\":1710000000000}"))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "해당 회의실의 회의록을 찾을 수 없는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "AI_001 - 회의록 없음",
                    value = "{\"code\":\"AI_001\",\"status\":404,\"message\":\"해당 회의실의 회의록을 찾을 수 없습니다\",\"timestamp\":1710000000000}"))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "회의록 DB 저장 실패",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "AI_012 - DB 저장 실패",
                    value = "{\"code\":\"AI_012\",\"status\":500,\"message\":\"회의록 데이터베이스 저장 중 오류가 발생했습니다\",\"timestamp\":1710000000000}"))
        )
    })
    // CHECK [ai-담당자]: updateMinutes HTTP 메서드가 PATCH → PUT으로 변경됨.
    // Frontend PUT /ai/v1/rooms/{roomId}/minutes 호출로 수정 필요.
    @PutMapping("/{roomId}/minutes")
    public MinutesResponse patch(
            @PathVariable Long roomId,
            @RequestBody MinutesPatchRequest req
    ) {
        return minutesService.patch(roomId, req);
    }

    @Operation(summary = "트랜스크립트 원본 조회", description = "회의의 원본 트랜스크립트를 DTO로 반환합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "트랜스크립트 조회 성공"),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필요",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"GATEWAY_001\",\"status\":401,\"message\":\"인증 토큰이 없습니다.\",\"timestamp\":1710000000000}"))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "회의록 데이터 또는 파일이 없는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "AI_001 - 회의록 없음",
                        value = "{\"code\":\"AI_001\",\"status\":404,\"message\":\"해당 회의실의 회의록을 찾을 수 없습니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "AI_027 - 파일 없음",
                        value = "{\"code\":\"AI_027\",\"status\":404,\"message\":\"원문 파일을 찾을 수 없습니다\",\"timestamp\":1710000000000}")
                })
        ),
        @ApiResponse(
            responseCode = "500",
            description = "데이터 읽기 오류 또는 파싱 실패",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "AI_002 - 데이터 읽기 실패",
                        value = "{\"code\":\"AI_002\",\"status\":500,\"message\":\"데이터 읽기 중 오류가 발생했습니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "AI_016 - 직렬화 실패",
                        value = "{\"code\":\"AI_016\",\"status\":500,\"message\":\"트랜스크립트 JSON 직렬화 중 오류가 발생했습니다\",\"timestamp\":1710000000000}")
                })
        )
    })
    @GetMapping("/{roomId}/transcript")
    public TranscriptResponse transcript(@PathVariable Long roomId) {
        return minutesService.getTranscript(roomId);
    }

    @Operation(summary = "회의록 삭제", description = "특정 회의의 회의록 및 관련 데이터(트랜스크립트, 요약본)를 삭제합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "회의록 삭제 성공 (내용 없음)"),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필요",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"GATEWAY_001\",\"status\":401,\"message\":\"인증 토큰이 없습니다.\",\"timestamp\":1710000000000}"))
        )
    })
    @DeleteMapping("/{roomId}/minutes")
    public org.springframework.http.ResponseEntity<Void> delete(@PathVariable Long roomId) {
        minutesService.delete(roomId);
        return org.springframework.http.ResponseEntity.noContent().build();
    }
}
