package com.onmeet.notification.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [TDD] 컨트롤러 이중 prefix 제거 검증
 *
 * context-path=/notification 이 이미 설정되어 있으므로
 * @RequestMapping에 /notification을 중복하면 실제 경로가 /notification/notification/...이 됨.
 */
class NotificationControllerPrefixTest {

    @Test
    @DisplayName("NotificationController @RequestMapping에 /notification prefix가 없다")
    void notificationController_noDoublePrefix() {
        RequestMapping mapping = NotificationController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping.value()[0])
                .as("context-path=/notification이 prefix를 이미 추가하므로 /notification 중복 불가")
                .isEqualTo("/v1/notifications")
                .doesNotStartWith("/notification/");
    }

    @Test
    @DisplayName("NotificationSettingController @RequestMapping에 /notification prefix가 없다")
    void notificationSettingController_noDoublePrefix() {
        RequestMapping mapping = NotificationSettingController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping.value()[0])
                .as("context-path=/notification이 prefix를 이미 추가하므로 /notification 중복 불가")
                .isEqualTo("/v1/settings")
                .doesNotStartWith("/notification/");
    }

    @Test
    @DisplayName("FcmTokenController @RequestMapping에 /notification prefix가 없다")
    void fcmTokenController_noDoublePrefix() {
        RequestMapping mapping = FcmTokenController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping.value()[0])
                .as("context-path=/notification이 prefix를 이미 추가하므로 /notification 중복 불가")
                .isEqualTo("/v1/fcm")
                .doesNotStartWith("/notification/");
    }
}
