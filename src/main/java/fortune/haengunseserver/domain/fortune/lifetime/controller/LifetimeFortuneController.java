package fortune.haengunseserver.domain.fortune.lifetime.controller;

import fortune.haengunseserver.domain.fortune.common.dto.request.SajuRequest;
import fortune.haengunseserver.domain.fortune.lifetime.dto.response.LifetimeSajuResponse;
import fortune.haengunseserver.domain.fortune.lifetime.service.LifetimeFortuneService;
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
@RequestMapping("/api/fortune/lifetime")
public class LifetimeFortuneController {

    private final LifetimeFortuneService lifetimeFortuneService;

    @Operation( summary = "평생운세 조회")
    @ApiResponse(
            responseCode = "200",
            description = "평생운세 정상 반환",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LifetimeSajuResponse.class))
    )
    @ApiErrorCodeExamples({ErrorCode.GPT_ERROR, ErrorCode.GPT_OVERLOADED, ErrorCode.PARSE_ERROR, ErrorCode.INVALID_MANSE_FORMAT})
    @PostMapping
    public ResponseEntity<LifetimeSajuResponse> getLifetimeFortune(@RequestBody SajuRequest request) {
        LifetimeSajuResponse fortune = lifetimeFortuneService.getFortune(request);
        return ResponseEntity.ok(fortune);
    }
}
