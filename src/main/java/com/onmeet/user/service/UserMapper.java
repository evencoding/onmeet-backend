package com.onmeet.user.service;

import com.onmeet.user.dto.UserResponse;
import com.onmeet.user.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponse toResponse(User user);
}
