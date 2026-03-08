package com.ticketsystem.auth.dto.response;

public record MeResponse(Long actorId, String actorType, String actorName, String email) {
}
