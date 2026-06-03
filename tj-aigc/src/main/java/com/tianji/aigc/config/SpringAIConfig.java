package com.tianji.aigc.config;

import com.tianji.aigc.advisor.RecordOptimizationAdvisor;
import com.tianji.aigc.memory.MyChatMemoryRepository;
import com.tianji.aigc.memory.RedisChatMemoryRepository;
import com.tianji.aigc.tools.CourseTools;
import com.tianji.aigc.tools.OrderTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
* 这个类把日志记录器、记忆增强器、课程工具、订单工具全部装进 ChatClient，并注册为全局 Bean。
这样任何智能体（包括 RouteAgent）都能直接注入这个 ChatClient，共享这些基础能力。*/
@Configuration
public class SpringAIConfig {

    @Value("${tj.ai.memory.max:100}")
    private Integer maxMessages;

    /**
     * 配置 ChatClient
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder,
                                 Advisor loggerAdvisor, // 日志记录器
                                 Advisor messageChatMemoryAdvisor,
                                 Advisor recordOptimizationAdvisor,
                                 CourseTools courseTools, // 课程工具
                                 OrderTools orderTools // 预下单工具
    ) {
        return chatClientBuilder
                .defaultAdvisors(loggerAdvisor, messageChatMemoryAdvisor,recordOptimizationAdvisor) //添加 Advisor 功能增强
//               .defaultTools(courseTools, orderTools) //添加默认工具
                .build();
    }


    @Bean
    public ChatClient quickChatClient(ChatClient.Builder builder) {
        // 不使用任何 Advisor，直接返回 Builder 构建的客户端
        // 注意：这里不调用 .defaultAdvisors()，避免记忆等开销
        return builder.build();
    }

//    @Bean
//    public ChatClient openAiChatClient(@Qualifier("openAiChatModel") ChatModel openAiChatModel,
//                                       Advisor loggerAdvisor  // 日志记录器
//    ) {
//        return ChatClient.builder(openAiChatModel)
//                .defaultAdvisors(loggerAdvisor)
//                .build();
//    }
    /**
     * 日志记录器
     */
    @Bean
    public Advisor loggerAdvisor() {
        return new SimpleLoggerAdvisor();
    }

    @Bean
    public ChatMemoryRepository redisChatMemoryRepository() {
        return new RedisChatMemoryRepository();
    }

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        // 基于 chatMemoryRepository 对象构建 chatMemory 对象
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(this.maxMessages) // 最多保存 100 条对话, 如果超出的话，会自动删除最旧的对话
                .build();
    }

    /**
     * 基于Redis的会话记忆，聊天记忆整合到message列表中实现多轮对话
     */
    @Bean
    public Advisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        // 创建基于 chatMemory 的 Advisor 对象
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }


    @Bean
    public Advisor recordOptimizationAdvisor(MyChatMemoryRepository myChatMemoryRepository){
        return new RecordOptimizationAdvisor(myChatMemoryRepository);
    }

    @Bean
    public TtsClient ttsClient(@Value("${spring.ai.dashscope.api-key}") String apiKey) {
        return new TtsClient(apiKey);
    }
}
