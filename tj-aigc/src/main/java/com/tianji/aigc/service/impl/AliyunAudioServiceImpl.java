package com.tianji.aigc.service.impl;

import com.alibaba.cloud.ai.dashscope.audio.synthesis.SpeechSynthesisModel;
import com.alibaba.cloud.ai.dashscope.audio.synthesis.SpeechSynthesisPrompt;
import com.alibaba.cloud.ai.dashscope.audio.synthesis.SpeechSynthesisResponse;
import com.tianji.aigc.service.AudioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AliyunAudioServiceImpl implements AudioService {

    private final SpeechSynthesisModel speechSynthesisModel;

    @Override
    public ResponseBodyEmitter ttsStream(String text) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter();

        try {
            // 调用 DashScope 生成完整音频
            var response = speechSynthesisModel.call(new SpeechSynthesisPrompt(text));

            // 获取音频数据
            ByteBuffer audioBuffer = response.getResult().getOutput().getAudio();
            byte[] audioBytes = new byte[audioBuffer.remaining()];
            audioBuffer.get(audioBytes);

            // 发送给前端
            emitter.send(audioBytes);  // 注意：完整 MP3 数据一次性发送
            emitter.complete();
        } catch (Exception e) {
            log.error("TTS 合成失败", e);
            emitter.completeWithError(e);
        }

        return emitter;
    }
}