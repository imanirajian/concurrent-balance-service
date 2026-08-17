package com.imanirajian.finance.cbs.domain;

import java.time.Instant;

/**
 * @author Iman Irajian
 * Date: 8/18/2026 12:16 AM
 */

public record ApiError(String code, String message, String path, Instant timestamp) {

    public static ApiError of(String code, String message, String path) {
        return new ApiError(code, message, path, Instant.now());
    }

}