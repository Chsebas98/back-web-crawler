package com.web.crawler.web_crawler.web.exception;

import com.web.crawler.web_crawler.infrastructure.crawler.exception.HackerNewsScraperException;
import com.web.crawler.web_crawler.web.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.webflux.autoconfigure.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.webflux.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;
import reactor.core.publisher.Mono;

/**
 * Replaces Spring Boot's default WebFlux error handler so that literally
 * every response the app can produce - a successful controller result,
 * a validation failure, an upstream crawling failure, or even a request to a
 * route that does not exist (404) or an unsupported HTTP method (405) - uses
 * the same {@link ApiResponse} envelope.
 *
 * This is the single funnel for errors: there is deliberately no
 * {@code @RestControllerAdvice} in this app. A {@code @RestControllerAdvice}
 * only intercepts exceptions thrown by a matched controller method; a 404 for
 * an unmapped route never reaches one, because no controller was matched in
 * the first place. Extending {@link AbstractErrorWebExceptionHandler} - the
 * same extension point Spring Boot's own default handler uses - catches
 * every exception in the reactive pipeline, including that one, in one place.
 *
 * Registered at the same precedence Spring Boot uses for its own default
 * handler ({@code @Order(-1)}); since Boot only creates its default when no
 * other {@code ErrorWebExceptionHandler} bean is present, this one replaces
 * it entirely rather than competing with it.
 */
@Component
@Order(-1)
public class GlobalErrorWebExceptionHandler extends AbstractErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorWebExceptionHandler.class);

    private static final String GENERIC_BAD_REQUEST_DETAIL =
            "The information provided is incomplete or invalid. Please try again.";
    private static final String GENERIC_UPSTREAM_DETAIL =
            "We could not complete your request. Please try again later.";
    private static final String GENERIC_SERVER_ERROR_DETAIL =
            "An unexpected error occurred. Please try again later.";

    public GlobalErrorWebExceptionHandler(
            ErrorAttributes errorAttributes,
            WebProperties webProperties,
            ApplicationContext applicationContext,
            ServerCodecConfigurer serverCodecConfigurer) {
        super(errorAttributes, webProperties.getResources(), applicationContext);
        setMessageWriters(serverCodecConfigurer.getWriters());
        setMessageReaders(serverCodecConfigurer.getReaders());
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::renderResponse);
    }

    private Mono<ServerResponse> renderResponse(ServerRequest request) {
        Throwable error = getError(request);
        ErrorMapping mapping = mapError(error);

        if (mapping.status().is5xxServerError()) {
            log.error("Unhandled error on {} {}: {}", request.method(), request.path(), error.getMessage(), error);
        } else {
            log.warn("Rejected {} {}: {}", request.method(), request.path(), error.getMessage());
        }

        ApiResponse<Object> body = ApiResponse.failure(mapping.status().value(), mapping.message(), mapping.errorDetail());
        return ServerResponse.status(mapping.status())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body);
    }

    /**
     * Maps every exception this app can encounter - our own domain
     * exceptions, Spring's framework-level exceptions (missing/invalid
     * parameters, unmapped routes, unsupported methods), and anything
     * unexpected - to a status and to safe, generic, front-end-facing text.
     * The original exception message is deliberately never forwarded to the
     * caller: only this method's log statement (above) sees it.
     */
    private ErrorMapping mapError(Throwable error) {
        if (error instanceof InvalidFilterException) {
            return new ErrorMapping(HttpStatus.BAD_REQUEST, "Information incomplete", GENERIC_BAD_REQUEST_DETAIL);
        }
        if (error instanceof HackerNewsScraperException) {
            return new ErrorMapping(HttpStatus.BAD_GATEWAY, "Information unavailable", GENERIC_UPSTREAM_DETAIL);
        }
        if (error instanceof UnsupportedMediaTypeStatusException) {
            return new ErrorMapping(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Information incomplete", GENERIC_BAD_REQUEST_DETAIL);
        }
        if (error instanceof ServerWebInputException) {
            return new ErrorMapping(HttpStatus.BAD_REQUEST, "Information incomplete", GENERIC_BAD_REQUEST_DETAIL);
        }
        if (error instanceof org.springframework.web.server.ResponseStatusException responseStatusException) {
            return mapByStatusCode(responseStatusException.getStatusCode().value());
        }
        return new ErrorMapping(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong", GENERIC_SERVER_ERROR_DETAIL);
    }

    /** Covers framework-raised failures identified only by status code, e.g. a 404 for an unmapped route. */
    private ErrorMapping mapByStatusCode(int statusCode) {
        return switch (statusCode) {
            case 404 -> new ErrorMapping(HttpStatus.NOT_FOUND, "Not found", "The requested resource does not exist.");
            case 405 -> new ErrorMapping(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed",
                    "This operation is not supported for the requested resource.");
            default -> {
                HttpStatus resolved = HttpStatus.resolve(statusCode);
                yield (resolved != null && resolved.is4xxClientError())
                        ? new ErrorMapping(resolved, "Information incomplete", GENERIC_BAD_REQUEST_DETAIL)
                        : new ErrorMapping(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong", GENERIC_SERVER_ERROR_DETAIL);
            }
        };
    }

    private record ErrorMapping(HttpStatus status, String message, String errorDetail) {
    }
}
