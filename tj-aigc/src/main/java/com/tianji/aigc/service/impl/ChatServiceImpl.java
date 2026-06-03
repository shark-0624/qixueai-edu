package com.tianji.aigc.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.service.ChatSessionService;
import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/*用户发起请求
      ↓
ChatController.chat(question, sessionId)
      ↓
ChatServiceImpl.chat(question, sessionId)
      ↓
chatClient.prompt() ... .stream().chatResponse()
      ↓
[Advisor 链拦截]
      ↓
MessageChatMemoryAdvisor
      ↓
    ┌─ 调用 ChatMemory.get(conversationId)
    │       ↓
    │   ChatMemoryRepository.findByConversationId()  ← 这时调用“读取”
    │       ↓
    │   从 Redis 取出历史消息，附加到本次请求里
    │       ↓
    └─ AI 模型返回回答
           ↓
    ┌─ 调用 ChatMemory.add(conversationId, newMessages)
    │       ↓
    │   ChatMemoryRepository.saveAll()               ← 这时调用“保存”
    │       ↓
    └─ 将更新后的历史消息存入 Redis
*/

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tj.ai", name = "chat-type", havingValue = "ENHANCE")
public class ChatServiceImpl implements ChatService {

    private final ChatClient chatClient;

    private final SystemPromptConfig systemPromptConfig;

    // 存储大模型的生成状态，这里采用ConcurrentHashMap是确保线程安全
    // 目前的版本暂时用Map实现，如果考虑分布式环境的话，可以考虑用redis来实现
    //通过这个map来控制Flux流是否继续输出
//    private static final Map<String, Boolean> GENERATE_STATUS = new ConcurrentHashMap<>();

    private final ChatMemory chatMemory;

    private final StringRedisTemplate stringRedisTemplate;
    //通过一个容器,判断大模型是否继续生成,用于后续停止会话
    //容器实现,考虑到分布式,使用redis
    private static final String GENERATE_STATUS_KEY = "generate_status";

    // 输出结束的标记
    private static final ChatEventVO STOP_EVENT = ChatEventVO.builder().eventType(ChatEventTypeEnum.STOP.getValue()).build();

    private final VectorStore vectorStore;

    private final ChatSessionService chatSessionService;


    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        // 获取对话id
        var conversationId = ChatService.getConversationId(sessionId);
        // 大模型输出内容的缓存器，用于在输出中断后的数据存储
        var outputBuilder = new StringBuilder();
        //判断是否继续让模型生成的标识
        BoundHashOperations<String, Object, Object> hashOps = stringRedisTemplate.boundHashOps(GENERATE_STATUS_KEY);

        String requestId = IdUtil.fastSimpleUUID();

        var userId = UserContext.getUser();

        // 创建RAG增强
        var qaAdvisor = QuestionAnswerAdvisor.builder(this.vectorStore)
                .searchRequest(SearchRequest.builder().similarityThreshold(0.6d).topK(6).build())
                .build();

        // 异步更新会话信息(异步更新为了增加用户体验感)
        chatSessionService.update(sessionId, question, userId);

        return this.chatClient.prompt()
                .system(promptSystem -> promptSystem
                        .text(this.systemPromptConfig.getChatSystemMessage().get()) // 设置系统提示语
                        .param("now", DateUtil.now()) // 设置当前时间的参数
                )
                .advisors(advisor -> advisor
                            // 设置RAG增强
                            .advisors(qaAdvisor)
                            .param(ChatMemory.CONVERSATION_ID, conversationId))
                .toolContext(Map.of(Constant.REQUEST_ID, requestId, Constant.USER_ID, userId)) //通过工具上下文传递参数
                .user(question)
                .stream()
                .chatResponse()
                .doFirst(() -> { //输出开始，标记正在输出
                    hashOps.put(sessionId,"true"); // 放入redis里面使用的是字符串
                })
                .doOnComplete(() -> { //输出结束，清除标记
                    hashOps.delete(sessionId);
                })
                .doOnError(throwable -> hashOps.delete(sessionId)) // 错误时清除标记
                .doOnCancel(() -> {
                    // 当输出被取消时，保存输出的内容到历史记录中
                    this.saveStopHistoryRecord(conversationId, outputBuilder.toString());
                })
                // 输出过程中，判断是否正在输出，如果正在输出，则继续输出，否则结束输出
                .takeWhile(s ->hashOps.get(sessionId) != null)
                .map(chatResponse -> {
                    // 获取大模型的输出的内容
                    String text = chatResponse.getResult().getOutput().getText();
                    // 对于响应结果进行处理，如果是最后一条数据，就把此次消息id放到内存中
                    // 主要用于存储消息数据到 redis中，可以根据消息di获取的请求id，再通过请求id就可以获取到参数列表了
                    // 从而解决，在历史聊天记录中没有外参数的问题
                    var finishReason = chatResponse.getResult().getMetadata().getFinishReason();
                    if (StrUtil.equals(Constant.STOP, finishReason)) {
                        var messageId = chatResponse.getMetadata().getId();
                        ToolResultHolder.put(messageId, Constant.REQUEST_ID, requestId);
                    }
                    //将大模型的输出添加到缓存器中
                    outputBuilder.append(text);
                    // 封装响应对象
                    return ChatEventVO.builder()
                            .eventData(text)
                            .eventType(ChatEventTypeEnum.DATA.getValue())
                            .build();
                })
                .concatWith(Flux.defer(() -> {
                    // 通过请求id获取到参数列表，如果不为空，就将其追加到返回结果中
                    Map<String, Object> map = ToolResultHolder.get(requestId);
                    if (CollUtil.isNotEmpty(map)) {
                        ToolResultHolder.remove(requestId); // 清除参数列表

                        // 响应给前端的参数数据
                        var chatEventVO = ChatEventVO.builder()
                                .eventData(map)
                                .eventType(ChatEventTypeEnum.PARAM.getValue())
                                .build();
                        return Flux.just(chatEventVO, STOP_EVENT);
                    }
                    return Flux.just(STOP_EVENT);
                }));
    }

    /**
     * 保存停止输出的记录
     *
     * @param conversationId 会话id
     * @param content        大模型输出的内容
     */
    private void saveStopHistoryRecord(String conversationId, String content) {
        this.chatMemory.add(conversationId, new AssistantMessage(content));
    }

    @Override
    public void stop(String sessionId) {
        //判断是否继续让模型生成的标识
        BoundHashOperations<String, Object, Object> hashOps = stringRedisTemplate.boundHashOps(GENERATE_STATUS_KEY);
        // 移除标记
        hashOps.delete(sessionId);
    }

    @Override
    public String chatText(String question) {
        return null;

    }
}
