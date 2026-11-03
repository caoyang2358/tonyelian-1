package com.coppertwin.backend.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String code, String message, T data, Object details, String traceId) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>("OK", "", data, null, TraceContext.id()); }
    public static <T> ApiResponse<T> error(String code, String message, Object details) {
        return new ApiResponse<>(code, message, null, details, TraceContext.id());
    }
}
