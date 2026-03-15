package com.onmeet.video.meeting.controller.room;

import com.onmeet.video.meeting.dto.room.MeetingRoomDetailResponse;
import com.onmeet.video.meeting.dto.room.MeetingRoomResponse;
import com.onmeet.video.meeting.dto.room.RoomSettingsResponse;
import com.onmeet.video.meeting.entity.room.RoomAccessScope;
import com.onmeet.video.meeting.entity.room.RoomStatus;
import com.onmeet.video.meeting.entity.room.RoomType;
import com.onmeet.video.meeting.service.room.MeetingRoomService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeetingRoomController.class)
@ActiveProfiles("test")
class MeetingRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MeetingRoomService meetingRoomService;

    private MeetingRoomResponse sampleRoomResponse() {
        return new MeetingRoomResponse(
                1L, "ABC123", "Test Room", "desc",
                100L, RoomStatus.WAITING, RoomType.INSTANT,
                RoomAccessScope.ALL, null, 10, false,
                null, null, null, null, Instant.now()
        );
    }

    private MeetingRoomDetailResponse sampleDetailResponse() {
        RoomSettingsResponse settings = new RoomSettingsResponse(
                1L, 1L, true, true, true, true, false, false, false);
        return new MeetingRoomDetailResponse(
                1L, "ABC123", "Test Room", "desc",
                100L, RoomStatus.WAITING, RoomType.INSTANT,
                RoomAccessScope.ALL, null, 10, false,
                null, null, null, null,
                0, settings, List.of(), Instant.now()
        );
    }

    // [ITEM-4] GET /v1/rooms/scheduled → Page<MeetingRoomResponse>
    @Test
    @WithMockUser
    void listScheduled_returnsPage() throws Exception {
        Page<MeetingRoomResponse> page = new PageImpl<>(
                List.of(sampleRoomResponse()), PageRequest.of(0, 20), 1);
        when(meetingRoomService.listScheduled(anyLong(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/v1/rooms/scheduled")
                        .header("X-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    // [ITEM-4] GET /v1/rooms/history → Page<MeetingRoomResponse>
    @Test
    @WithMockUser
    void listHistory_returnsPage() throws Exception {
        Page<MeetingRoomResponse> page = new PageImpl<>(
                List.of(sampleRoomResponse()), PageRequest.of(0, 20), 1);
        when(meetingRoomService.listHistory(anyLong(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/v1/rooms/history")
                        .header("X-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    // [ITEM-4] GET /v1/rooms/favorites → Page<MeetingRoomResponse>
    @Test
    @WithMockUser
    void listFavorites_returnsPage() throws Exception {
        Page<MeetingRoomResponse> page = new PageImpl<>(
                List.of(sampleRoomResponse()), PageRequest.of(0, 20), 1);
        when(meetingRoomService.listFavorites(anyLong(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/v1/rooms/favorites")
                        .header("X-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    // [ITEM-4] GET /v1/rooms/tags/{tagName} → Page<MeetingRoomResponse>
    @Test
    @WithMockUser
    void searchByTag_returnsPage() throws Exception {
        Page<MeetingRoomResponse> page = new PageImpl<>(
                List.of(sampleRoomResponse()), PageRequest.of(0, 20), 1);
        when(meetingRoomService.searchByTag(anyString(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/v1/rooms/tags/important"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    // [ITEM-5] GET /v1/rooms/code/{roomCode} → MeetingRoomDetailResponse
    @Test
    @WithMockUser
    void findByCode_returnsMeetingRoomDetailResponse() throws Exception {
        when(meetingRoomService.findByCode(anyString())).thenReturn(sampleDetailResponse());

        mockMvc.perform(get("/v1/rooms/code/ABC123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings").exists())
                .andExpect(jsonPath("$.data.tags").isArray());
    }
}
