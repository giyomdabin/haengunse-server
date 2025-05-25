package fortune.haengunseserver.global.exception.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 클라이언트 요청 오류
    TOO_MANY_QUESTIONS(HttpStatus.UNPROCESSABLE_ENTITY, "질문은 최대 3개까지만 입력할 수 있습니다."),
    INVALID_DATE_FORMAT(HttpStatus.BAD_REQUEST, "날짜 형식은 yyyy-MM-dd이어야 합니다."),
    INVALID_MANSE_FORMAT(HttpStatus.BAD_REQUEST, "만세정보는 8개의 항목이어야 합니다."),

    // 행운세 서버 오류
    SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    XML_PARSE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "양력 응답 파싱에 실패했습니다."),
    PARSE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "GPT 응답 파싱에 실패했습니다."),

    // KASI(달력 서버) 오류
    KASI_ERROR(HttpStatus.BAD_GATEWAY, "달력 서버에 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),

    // GPT API 오류
    GPT_OVERLOADED(HttpStatus.SERVICE_UNAVAILABLE, "GPT 서버에 요청이 몰리고 있습니다. 잠시 후 다시 시도해주세요."),
    GPT_ERROR(HttpStatus.BAD_GATEWAY, "GPT 서버에 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus httpStatus;
    private final String message;
}
