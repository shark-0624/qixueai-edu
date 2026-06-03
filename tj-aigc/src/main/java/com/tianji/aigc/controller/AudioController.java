package com.tianji.aigc.controller;

import com.tianji.aigc.service.AudioService;
import com.tianji.common.annotations.NoWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

@RestController
@RequestMapping("/audio")
@RequiredArgsConstructor
public class AudioController {

    private final AudioService audioService;

//    @NoWrapper
    @PostMapping(
            value = "/tts-stream",
            produces = "audio/mpeg"
    )
    public ResponseBodyEmitter ttsStream(
            @RequestBody String text
    ) {
        return this.audioService.ttsStream(text);
    }
}