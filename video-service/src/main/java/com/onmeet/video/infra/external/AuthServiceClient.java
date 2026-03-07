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
     * 사용자 존재 여부 확인
     *
     * @param userId 사용자 ID
     * @return 존재 여부
     */
    boolean userExists(Long userId);

    /**
     * 다중 사용자 존재 여부 일괄 확인
     *
     * @param userIds 사용자 ID 목록
     * @return 사용자 ID와 존재 여부 맵
     */
    java.util.Map<Long, Boolean> batchUserExists(List<Long> userIds);

    /**
     * 팀 존재 여부 확인
     *
     * @param teamId 팀 ID
     * @return 존재 여부
     */
    boolean teamExists(Long teamId);

    /**
     * 팀 멤버십 확인
     *
     * @param teamId 팀 ID
     * @param userId 사용자 ID
     * @return 팀 멤버 여부
     */
    boolean isTeamMember(Long teamId, Long userId);

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

    /**
     * 사용자 존재 여부 확인 응답
     */
    record UserExistsResponse(
            Long userId,
            Boolean exists
    ) {
    }

    /**
     * 다중 사용자 존재 여부 확인 요청
     */
    record BatchUserExistsRequest(
            List<Long> userIds
    ) {
    }

    /**
     * 다중 사용자 존재 여부 확인 응답
     */
    record BatchUserExistsResponse(
            List<UserExistsResponse> users
    ) {
    }

    /**
     * 팀 존재 여부 확인 응답
     */
    record TeamExistsResponse(
            Long teamId,
            Boolean exists
    ) {
    }

    /**
     * 팀 멤버십 확인 요청
     */
    record TeamMembershipRequest(
            Long teamId,
            Long userId
    ) {
    }

    /**
     * 팀 멤버십 확인 응답
     */
    record TeamMembershipResponse(
            Long teamId,
            Long userId,
            Boolean isMember
    ) {
    }
}
