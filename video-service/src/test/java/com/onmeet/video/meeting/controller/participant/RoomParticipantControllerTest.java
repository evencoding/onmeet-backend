package com.onmeet.video.meeting.controller.participant;

import com.onmeet.video.meeting.dto.participant.RoomParticipantResponse;
import com.onmeet.video.meeting.entity.participant.DeviceType;
import com.onmeet.video.meeting.entity.participant.ParticipantRole;
import com.onmeet.video.meeting.entity.participant.ParticipantStatus;
import com.onmeet.video.meeting.service.participant.RoomParticipantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomParticipantController.class)
@ActiveProfiles("test")
class RoomParticipantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoomParticipantService participantService;

    private RoomParticipantResponse sampleParticipant() {
        return new RoomParticipantResponse(
                1L, 10L, 100L,
                ParticipantRole.HOST, ParticipantStatus.JOINED,
                Instant.now(), null, null, DeviceType.WEB
        );
    }

    // [ITEM-4] GET /api/rooms/{roomId}/participants/history → Page<RoomParticipantResponse>
    @Test
    @WithMockUser
    void listHistory_returnsPage() throws Exception {
        Page<RoomParticipantResponse> page = new PageImpl<>(
                List.of(sampleParticipant()), PageRequest.of(0, 20), 1);
        when(participantService.listHistory(anyLong(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/rooms/10/participants/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }
}
