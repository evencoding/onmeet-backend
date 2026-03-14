package com.onmeet.notification.type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class NotificationTypeWaitingRoomTest {

    @Test
    @DisplayName("WAITING_ROOM_ADMITTED enum 값이 존재한다")
    void waitingRoomAdmitted_enumExists() {
        assertThatCode(() -> NotificationType.valueOf("WAITING_ROOM_ADMITTED"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("WAITING_ROOM_REJECTED enum 값이 존재한다")
    void waitingRoomRejected_enumExists() {
        assertThatCode(() -> NotificationType.valueOf("WAITING_ROOM_REJECTED"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("WAITING_ROOM_ADMITTED 타입에 대응하는 NotificationTemplate이 존재한다")
    void waitingRoomAdmitted_templateExists() {
        NotificationTemplate template = NotificationTemplate.fromType(NotificationType.WAITING_ROOM_ADMITTED);

        assertThat(template).isNotNull();
        assertThat(template.name()).isEqualTo("WAITING_ROOM_ADMITTED");
        assertThat(template.getDefaultTitle()).isNotBlank();
        assertThat(template.getBodyTemplate()).isNotBlank();
    }

    @Test
    @DisplayName("WAITING_ROOM_REJECTED 타입에 대응하는 NotificationTemplate이 존재한다")
    void waitingRoomRejected_templateExists() {
        NotificationTemplate template = NotificationTemplate.fromType(NotificationType.WAITING_ROOM_REJECTED);

        assertThat(template).isNotNull();
        assertThat(template.name()).isEqualTo("WAITING_ROOM_REJECTED");
        assertThat(template.getDefaultTitle()).isNotBlank();
        assertThat(template.getBodyTemplate()).isNotBlank();
    }

    @Test
    @DisplayName("WAITING_ROOM_ADMITTED 템플릿 메시지에 승인 관련 문구가 포함된다")
    void waitingRoomAdmitted_templateContainsApprovalMessage() {
        NotificationTemplate template = NotificationTemplate.fromType(NotificationType.WAITING_ROOM_ADMITTED);

        assertThat(template.getBodyTemplate()).contains("승인");
    }

    @Test
    @DisplayName("WAITING_ROOM_REJECTED 템플릿 메시지에 거절 관련 문구가 포함된다")
    void waitingRoomRejected_templateContainsRejectionMessage() {
        NotificationTemplate template = NotificationTemplate.fromType(NotificationType.WAITING_ROOM_REJECTED);

        assertThat(template.getBodyTemplate()).contains("거절");
    }
}
