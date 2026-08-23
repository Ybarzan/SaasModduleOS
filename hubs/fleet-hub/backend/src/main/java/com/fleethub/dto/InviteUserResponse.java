package com.fleethub.dto;

public record InviteUserResponse(
        UserDto user,
        String inviteUrl
) {}
