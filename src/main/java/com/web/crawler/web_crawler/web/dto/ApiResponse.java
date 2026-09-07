package com.web.crawler.web_crawler.web.dto;

/**
 * The single response envelope for every endpoint in this API, success or
 * failure. The frontend can always read the same shape instead of branching
 * on HTTP status: {@code response} says whether the call succeeded,
 * {@code message} is a short title meant for an alert/toast, {@code result}
 * carries the payload (object, array, or null), and {@code errorDetail}
 * carries a safe, generic description of what went wrong - never the raw
 * exception message, which could leak internal implementation details.
 */
public record ApiResponse<T>(boolean response, int statusCode, String message, T result, String errorDetail) {

    public static <T> ApiResponse<T> success(int statusCode, String message, T result) {
        return new ApiResponse<>(true, statusCode, message, result, null);
    }

    public static <T> ApiResponse<T> failure(int statusCode, String message, String errorDetail) {
        return new ApiResponse<>(false, statusCode, message, null, errorDetail);
    }
}
