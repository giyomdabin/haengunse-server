package fortune.haengunseserver.domain.fortune.today.controller;

import fortune.haengunseserver.domain.fortune.today.dto.response.agefortune.AgeResponseDto;
import fortune.haengunseserver.domain.fortune.today.dto.response.starfortune.StarResponseDto;
import fortune.haengunseserver.domain.fortune.today.service.agefortune.AgeFortuneStore;
import fortune.haengunseserver.domain.fortune.today.service.dream.DreamService;
import fortune.haengunseserver.domain.fortune.today.service.starfortune.StarFortuneStore;
import fortune.haengunseserver.domain.fortune.common.dto.request.SajuRequest;
import fortune.haengunseserver.domain.fortune.today.dto.response.dream.DreamResponse;
import fortune.haengunseserver.domain.fortune.today.dto.response.sajufortune.TodaySajuResponse;
import fortune.haengunseserver.domain.fortune.today.service.todaysaju.TodaySajuService;
import fortune.haengunseserver.global.exception.code.ErrorCode;
import fortune.haengunseserver.global.swagger.ApiErrorCodeExample;
import fortune.haengunseserver.global.swagger.ApiErrorCodeExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/fortune/today")
public class TodayFortuneController {

    private final TodaySajuService todayLuckyService;
    private final StarFortuneStore starFortuneStore;
    private final AgeFortuneStore ageFortuneStore;
    private final DreamService dreamService;

    @Operation( summary = "오늘의 운세 조회", description = "사용자의 사주 정보를 기반으로 오늘의 운세를 반환")
    @ApiResponse(
            responseCode = "200",
            description = "운세 데이터 정상 반환",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TodaySajuResponse.class))
    )
    @ApiErrorCodeExamples({ErrorCode.GPT_ERROR, ErrorCode.GPT_OVERLOADED, ErrorCode.PARSE_ERROR, ErrorCode.INVALID_MANSE_FORMAT})
    @PostMapping("/saju")
    public ResponseEntity<TodaySajuResponse> getTodaySaju(@RequestBody SajuRequest request) {
        TodaySajuResponse response = todayLuckyService.getFortune(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "오늘의 별자리 운세 조회")
    @ApiResponse(
            responseCode = "200",
            description = "별자리 운세 데이터 정상 반환",
            content = @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = StarResponseDto.class))
            )
    )
    @ApiErrorCodeExample(ErrorCode.SERVER_ERROR)
    @GetMapping("/star")
    public ResponseEntity<List<StarResponseDto>> getStarFortune() {
        List<StarResponseDto> fortunes = starFortuneStore.get();

        if (fortunes == null || fortunes.isEmpty()) {
            return ResponseEntity.noContent().build(); // 204 No Content
        }

        return ResponseEntity.ok(fortunes);
    }

    @Operation(summary = "오늘의 띠 운세 조회")
    @ApiResponse(
            responseCode = "200",
            description = "띠 운세 데이터 정상 반환",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = AgeResponseDto.class))
            )
    )
    @ApiErrorCodeExample(ErrorCode.SERVER_ERROR)
    @GetMapping("/age")
    public ResponseEntity<List<AgeResponseDto>> getAgeFortune() {
        List<AgeResponseDto> fortunes = ageFortuneStore.get();

        if (fortunes == null || fortunes.isEmpty()) {
            return ResponseEntity.noContent().build(); // 204 No Content
        }

        return ResponseEntity.ok(fortunes);
    }

    @Operation(summary = "꿈 해몽")
    @ApiResponse(
            responseCode = "200",
            description = "꿈 해몽 정상 반환",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DreamResponse.class))
    )
    @ApiErrorCodeExamples({ErrorCode.GPT_ERROR, ErrorCode.GPT_OVERLOADED, ErrorCode.PARSE_ERROR, ErrorCode.TOO_MANY_QUESTIONS})
    @PostMapping("/dream")
    public ResponseEntity<DreamResponse> getDreamLucky(@RequestBody List<String> request) {
        DreamResponse response = dreamService.getFortune(request);
        return ResponseEntity.ok(response);
    }
}
