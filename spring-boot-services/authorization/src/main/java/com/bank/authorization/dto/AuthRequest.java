package com.bank.authorization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {
    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("profileId")
    private Long profileId;

    @JsonProperty("password")
    private String password;
}
