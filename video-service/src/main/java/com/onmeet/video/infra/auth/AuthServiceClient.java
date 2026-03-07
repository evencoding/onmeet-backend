package com.onmeet.video.infra.auth;

import java.util.List;

public interface AuthServiceClient {

    UserInfo getUserInfo(Long userId);

    List<UserInfo> getBatchUserInfo(List<Long> userIds);

    boolean userExists(Long userId);

    java.util.Map<Long, Boolean> batchUserExists(List<Long> userIds);

    boolean teamExists(Long teamId);

    boolean isTeamMember(Long teamId, Long userId);

    record UserInfo(
            Long userId,
            String name,
            String email,
            Long profileImageId
    ) {
    }

    record BatchUserInfoRequest(
            List<Long> userIds
    ) {
    }

    record BatchUserInfoResponse(
            List<UserInfo> users
    ) {
    }

    record UserExistsResponse(
            Long userId,
            Boolean exists
    ) {
    }

    record BatchUserExistsRequest(
            List<Long> userIds
    ) {
    }

    record BatchUserExistsResponse(
            List<UserExistsResponse> users
    ) {
    }

    record TeamExistsResponse(
            Long teamId,
            Boolean exists
    ) {
    }

    record TeamMembershipRequest(
            Long teamId,
            Long userId
    ) {
    }

    record TeamMembershipResponse(
            Long teamId,
            Long userId,
            Boolean isMember
    ) {
    }
}
