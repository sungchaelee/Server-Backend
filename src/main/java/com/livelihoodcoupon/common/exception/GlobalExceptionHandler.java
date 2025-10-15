package com.livelihoodcoupon.common.exception;

import com.livelihoodcoupon.common.response.CustomApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // BusinessException 처리
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<CustomApiResponse<?>> handleBusinessException(BusinessException e) {
        log.error("BusinessException: {}", e.getMessage(), e);
        return ResponseEntity
            .status(e.getErrorCode().getStatus())
            .body(CustomApiResponse.error(e.getErrorCode(), e.getMessage()));
    }

    // KakaoApiException 처리
    @ExceptionHandler(KakaoApiException.class)
    protected ResponseEntity<CustomApiResponse<?>> handleKakaoApiException(KakaoApiException e) {
        log.error("KakaoApiException: {} (Status: {})", e.getMessage(), e.getStatusCode());
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(CustomApiResponse.error(ErrorCode.KAKAO_API_ERROR,
                "카카오 API 서비스가 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해주세요."));
    }

    // @Valid 또는 @Validated 에 의한 유효성 검사 실패 시 발생
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request) {

        log.error("MethodArgumentNotValidException: {}", ex.getMessage(), ex);

        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + (fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"))
            .findFirst()
            .orElse(ErrorCode.INVALID_INPUT_VALUE.getMessage());

        CustomApiResponse<?> body = CustomApiResponse.error(
            ErrorCode.INVALID_INPUT_VALUE, errorMessage);

        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    // @RequestParam 필수 파라미터 누락 시 발생
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
        MissingServletRequestParameterException ex,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request) {

        log.error("MissingServletRequestParameterException: {}", ex.getMessage(), ex);

        // 팀 공통 에러 바디 유지
        CustomApiResponse<?> body = CustomApiResponse.error(
            ErrorCode.INVALID_REQUEST_PARAM,
            "필수 파라미터 누락: " + ex.getParameterName()
        );

        // 항상 일관 포맷으로 반환
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    // @RequestParam 등에서 타입 불일치 시 발생
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<CustomApiResponse<?>> handleMethodArgumentTypeMismatchException(
        MethodArgumentTypeMismatchException e) {
        log.error("MethodArgumentTypeMismatchException: {}", e.getMessage(), e);
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(CustomApiResponse.error(ErrorCode.INVALID_REQUEST_PARAM, e.getMessage()));
    }

    // 그 외 모든 예외 처리
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<CustomApiResponse<?>> handleException(Exception e) {
        log.error("Exception: {}", e.getMessage(), e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(CustomApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}