package com.ticketsystem.frontend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeResponse {
    private Long actorId;
    private String actorType;
    private String actorName;
    private String email;
}
