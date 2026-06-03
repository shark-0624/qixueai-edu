package com.tianji.aigc.service;

import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.common.utils.UserContext;
import reactor.core.publisher.Flux;

public interface ChatService {


    //static 方法属于接口本身，可以直接调用，不需要实现类的对象。
    //而 chat 和 stop 是抽象实例方法，必须由实现类（如 ChatServiceImpl）提供具体的逻辑，并且通过对象来调用。
    static String getConversationId(String sessionId) {
        return UserContext.getUser()+"_"+sessionId;
    }

    /**
     * 聊天
     *
     * @param question  问题
     * @param sessionId 会话id
     * @return 回答内容
     */
    Flux<ChatEventVO> chat(String question, String sessionId);


    /**
     * 停止生成
     *
     * @param sessionId 会话id
     */
    void stop(String sessionId);

    /**
     * 文本对话
     *
     * @param question 问题
     * @return 回答
     */
    String chatText(String question);
}
