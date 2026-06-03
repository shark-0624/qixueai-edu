package com.tianji.aigc.enums;

import cn.hutool.core.util.EnumUtil;
import lombok.Getter;

/**
 * 智能体类型
 */
@Getter
public enum AgentTypeEnum {
    ROUTE("ROUTE", "路由智能体"),
    RECOMMEND("RECOMMEND", "课程推荐智能体"),
    CONSULT("CONSULT", "课程咨询智能体"),
    BUY("BUY", "课程购买智能体"),
    KNOWLEDGE("KNOWLEDGE", "知识讲解智能体");


    private final String agentName;
    private final String desc;

    AgentTypeEnum(String agentName, String desc) {
        this.agentName = agentName;
        this.desc = desc;
    }

    @Override
    public String toString() {
        return this.name();
    }


    /**
     * 通过智能体的名称查找枚举
     */
    public static AgentTypeEnum agentNameOf(String agentName) {
        /*
        * 为什么是 null？
Hutool 的 EnumUtil.getBy 方法会遍历 AgentTypeEnum 的所有枚举值，
 通过 AgentTypeEnum::getAgentName（即 agentName 字段）与传入的字符串做对比。
ROUTE 的 agentName 是 "ROUTE"，不匹配。
RECOMMEND 的 agentName 是 "RECOMMEND"，不匹配。
其他枚举值同理，都不会匹配 "你好,我是智能客服"。
当找不到任何匹配项时，EnumUtil.getBy 返回 null。*/
        return EnumUtil.getBy(AgentTypeEnum::getAgentName, agentName);
    }

}