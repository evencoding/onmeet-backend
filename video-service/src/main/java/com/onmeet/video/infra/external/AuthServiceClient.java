package com.onmeet.video.infra.external;

import java.util.List;

/**
 * Auth Service와 통신하기 위한 클라이언트 인터페이스
 */
public interface AuthServiceClient {

    /**
     * 단일 사용자 정보 조회
     *
     * @param userId 사용자 ID
     * @return 사용자 정보
     */
    UserInfo getUserInfo(Long userId);

    /**
     * 다중 사용자 정보 일괄 조회
     *
     * @param userIds 사용자 ID 목록
     * @return 사용자 정보 목록
     */
    List<UserInfo> getBatchUserInfo(List<Long> userIds);

    /**
     * 사용자 기본 정보
     */
    record UserInfo(
            Long userId,
            String name,
            String email,
            Long profileImageId
    ) {
    }

    /**
     * 다중 사용자 조회 요청
     */
    record BatchUserInfoRequest(
            List<Long> userIds
    ) {
    }

    /**
     * 다중 사용자 조회 응답
     */
    record BatchUserInfoResponse(
            List<UserInfo> users
    ) {
    }
}
