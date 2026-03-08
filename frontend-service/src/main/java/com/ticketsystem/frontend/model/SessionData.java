package com.ticketsystem.frontend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionData {

    private Long actorId;
    private String actorType;
    private String actorName;
    private String email;

    public boolean isLoggedIn() {
        return actorId != null && actorType != null;
    }

    public boolean isMerchant() {
        return "MERCHANT".equalsIgnoreCase(actorType);
    }

    public boolean isUser() {
        return "USER".equalsIgnoreCase(actorType);
    }
}
