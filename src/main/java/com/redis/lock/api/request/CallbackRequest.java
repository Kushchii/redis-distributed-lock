package com.redis.lock.api.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CallbackRequest {

    @NotBlank
    private UUID id;

    @NotBlank
    private String status;
}
