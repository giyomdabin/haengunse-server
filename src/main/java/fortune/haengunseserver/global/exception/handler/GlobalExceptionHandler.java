package fortune.haengunseserver.global.exception.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fortune.haengunseserver.global.exception.CustomException;
import fortune.haengunseserver.global.exception.code.ErrorCode;
import fortune.haengunseserver.global.response.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Order(-2) // 기본 에러 핸들러보다 먼저 실행
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ErrorResponse errorResponse;
        HttpStatus status;

        if (ex instanceof CustomException customException) {
            ErrorCode errorCode = customException.getErrorCode();
            status = errorCode.getHttpStatus();
            errorResponse = new ErrorResponse(status.value(), errorCode.getMessage());
        } else if (ex instanceof WebExchangeBindException bindException) {
            // Validation 실패
            status = HttpStatus.BAD_REQUEST;
            String errorMessage = bindException.getFieldErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .findFirst()
                    .orElse("입력값이 올바르지 않습니다.");
            errorResponse = new ErrorResponse(status.value(), errorMessage);
        } else {
            // 기타 예외
            log.error("Unhandled exception: ", ex);
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            errorResponse = new ErrorResponse(status.value(), "서버 오류가 발생했습니다.");
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Error writing response: ", e);
            return Mono.error(e);
        }
    }
}
