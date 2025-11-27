package fortune.haengunseserver.domain.message.controller;

import fortune.haengunseserver.domain.message.dto.ItemResponseDto;
import fortune.haengunseserver.domain.message.dto.MessageResponseDto;
import fortune.haengunseserver.domain.message.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@AllArgsConstructor
@RestController
@RequestMapping("/api/message")
public class MessageController {

    private final MessageService messageService;

    @Operation(summary = "질문 뽑기")
    @ApiResponse(
            responseCode = "200",
            description = "질문 정상 반환",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MessageResponseDto.class))
    )
    @GetMapping("/random")
    public Mono<MessageResponseDto> getRandomQuestion() {
        return Mono.fromSupplier(messageService::getRandomQuestion);
    }

    @Operation(summary = "포춘쿠키 뽑기")
    @ApiResponse(
            responseCode = "200",
            description = "포춘쿠키 정상 반환",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MessageResponseDto.class))
    )
    @GetMapping("/cookie")
    public Mono<MessageResponseDto> getFortuneCookie() {
        return Mono.fromSupplier(messageService::getFortuneCookie);
    }

    @Operation(summary = "행운의 아이템 뽑기")
    @ApiResponse(
            responseCode = "200",
            description = "행운의 아이템 정상 반환",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ItemResponseDto.class))
    )
    @GetMapping("/item")
    public Mono<ItemResponseDto> getLuckyItem() {
        return Mono.fromSupplier(messageService::getLuckyItem);
    }
}
