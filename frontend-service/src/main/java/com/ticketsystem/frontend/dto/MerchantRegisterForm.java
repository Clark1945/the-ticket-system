package com.ticketsystem.frontend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantRegisterForm {
    private String name;
    private String email;
    private String password;
    private String address;
    private String phone;
}
