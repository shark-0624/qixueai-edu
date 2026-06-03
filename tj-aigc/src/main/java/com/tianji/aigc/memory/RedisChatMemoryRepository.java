package com.tianji.aigc.memory;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

/**
 * 基于Redis实现的ChatMemoryRepository
 */
public class RedisChatMemoryRepository implements ChatMemoryRepository ,MyChatMemoryRepository{

    // 默认redis中key的前缀
    public static final String DEFAULT_PREFIX = "CHAT:";

    private final String prefix;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public RedisChatMemoryRepository() {
        this.prefix = DEFAULT_PREFIX;
    }

    public RedisChatMemoryRepository(String prefix) {
        this.prefix = prefix;
    }

    /**
     * 查询所有对话ID
     * 对话ID = userID + 会话ID
     * key = prefix + 对话ID
     * @return 会话ID列表
     */
    @Override
    public List<String> findConversationIds() {
        // 准备要匹配的模式，比如 "CHAT:*"
        var pattern = this.prefix + "*";
        // 创建一个 SCAN 选项对象，指定匹配模式和每次扫描的批次大小
        var scanOptions = org.springframework.data.redis.core.ScanOptions.scanOptions()
                .match(pattern)
                .count(100) // 每批次返回数量，可调整
                .build();

        // 使用 StringRedisTemplate 执行 SCAN 操作，得到一个可迭代的游标对象
        var cursor = this.stringRedisTemplate.scan(scanOptions);

        // 遍历游标，提取 Key，去掉前缀，最后转换成 List
        return cursor.stream()
                .map(key -> StrUtil.replace(key, this.prefix, ""))
                .toList();
    }


    @Override
    public List<Message> findByConversationId(String conversationId) {
        String key = getKey(conversationId);
        BoundListOperations<String, String> listOps = this.stringRedisTemplate.boundListOps(key);
        // 从Redis列表中获取所有的数据
        var messages = listOps.range(0, -1);
        // 将Redis返回的字符串列表转换为Message对象列表
        return CollStreamUtil.toList(messages, MessageUtil::toMessage);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        Assert.notEmpty(messages, "消息列表不能为空");
        var redisKey = this.getKey(conversationId);
        var listOps = this.stringRedisTemplate.boundListOps(redisKey);
        // 保存数据时，会传入全部的消息数据，包括之前的数据，所以需要先删除之前的数据，再添加新的数据
        this.deleteByConversationId(conversationId);
        // 将消息序列化并添加到Redis列表的右侧
        messages.forEach(message -> listOps.rightPush(MessageUtil.toJson(message)));
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        var redisKey = this.getKey(conversationId);
        this.stringRedisTemplate.delete(redisKey);
    }

    private String getKey(String conversationId) {
        return prefix + conversationId;
    }

    /**
     * 根据对话ID优化对话记录，删除最后的2条消息，因为这条消息是从路由智能体存储的，请求由后续的智能体处理
     * 为了确保历史消息的完整性，所以需要将中间转发的消息清理掉
     *
     * @param conversationId 对话的唯一标识符
     */
    //为什么只删除最近的两条数据?
    //拿购买智能体举例
    //路由智能体从nacos读取配置,然后获取配置的规则为BUG,然后再将BUG设置在promptSystem -> promptSystem.text(this.systemMessage())
    //一进一出,一个对话 两个BUG
    public void optimization(String conversationId) {
        var redisKey = this.getKey(conversationId);
        var listOps = this.stringRedisTemplate.boundListOps(redisKey);
        // 从Redis列表右侧弹出2个元素
        listOps.rightPop(2);
    }
}
