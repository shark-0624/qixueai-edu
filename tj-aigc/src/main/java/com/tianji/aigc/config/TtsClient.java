package com.tianji.aigc.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TtsClient {

    private final String apiKey;
    private static final String TTS_URL = "https://dashscope.aliyuncs.com/api/v1/services/audio/text-to-speech/generation";

    public Flux<byte[]> streamTts(String text, String model, String voice, String format) {
        WebClient webClient = WebClient.builder()
                .baseUrl(TTS_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-DashScope-SSE", "enable")
                .build();

        Map<String, Object> requestBody = Map.of(
                "model", model != null ? model : "tts-1",
                "input", Map.of("text", text),
                "parameters", Map.of(
                        "voice", voice != null ? voice : "alloy",
                        "format", format != null ? format : "mp3",
                        "sample_rate", 48000
                )
        );

        return webClient.post()
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(byte[].class)
                .doOnError(error -> log.error("TTS 合成失败: {}", error.getMessage()))
                .onErrorResume(e -> {
                    log.error("TTS 调用异常", e);
                    return Flux.empty();
                });
    }
}
