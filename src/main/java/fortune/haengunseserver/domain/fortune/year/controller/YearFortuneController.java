package fortune.haengunseserver.domain.fortune.year.controller;

import fortune.haengunseserver.domain.fortune.common.dto.request.SajuRequest;
import fortune.haengunseserver.domain.fortune.year.dto.response.YearlySajuResponse;
import fortune.haengunseserver.global.exception.code.ErrorCode;
import fortune.haengunseserver.global.swagger.ApiErrorCodeExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/api/fortune/year")
public class YearFortuneController {

    @Operation( summary = "신년사주 조회")
    @ApiResponse(
            responseCode = "200",
            description = "신년사주 정상 반환",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = YearlySajuResponse.class))
    )
    @ApiErrorCodeExamples({ErrorCode.GPT_ERROR, ErrorCode.GPT_OVERLOADED, ErrorCode.PARSE_ERROR})
    @PostMapping("/saju")
    public ResponseEntity<YearlySajuResponse> getYearlySaju(@RequestBody SajuRequest request) {
        return ResponseEntity.ok(null);
    }
}
