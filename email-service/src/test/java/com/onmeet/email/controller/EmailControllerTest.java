package com.onmeet.email.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmailController.class)
@AutoConfigureMockMvc(addFilters = false)
class EmailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    // [Essential] 이메일 서비스 헬스 체크 엔드포인트 검증
    void health_ShouldReturnServiceRunningMessage() throws Exception {
        // when & then
        mockMvc.perform(get("/email/v1/health")
                .contextPath("/email"))
            .andExpect(status().isOk())
            .andExpect(content().string("Email Service is running"));
    }

    @Test
    // [Essential] v1 경로 패턴 검증 - 새로운 API 버저닝 구조 확인
    void health_ShouldBeAccessibleViaV1Path() throws Exception {
        // when & then
        mockMvc.perform(get("/email/v1/health")
                .contextPath("/email"))
            .andExpect(status().isOk());
    }

    @Test
    // [Necessary Infrastructure] 잘못된 경로 요청 시 404 응답 확인
    void invalidPath_ShouldReturn404() throws Exception {
        // when & then
        mockMvc.perform(get("/email/v1/invalid")
                .contextPath("/email"))
            .andExpect(status().isNotFound());
    }
}
