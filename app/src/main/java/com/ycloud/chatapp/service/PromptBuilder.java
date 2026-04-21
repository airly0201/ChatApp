package com.ycloud.chatapp.service;

import com.ycloud.chatapp.model.Group;
import com.ycloud.chatapp.model.Member;
import com.ycloud.chatapp.model.Message;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * 系统提示构建器 - 为群聊成员构建系统提示
 */
public class PromptBuilder {

    /**
     * 构建完整的系统提示（含群规+成员+历史）
     */
    public String buildSystemPrompt(Group group, Member targetMember, List<Message> history) {
        StringBuilder sb = new StringBuilder();
        
        // 0. 明确身份（最重要！）
        sb.append("【你的身份】\n");
        sb.append(String.format("你是「%s」%s\n", 
            targetMember.getName(),
            targetMember.getAvatar() != null ? targetMember.getAvatar() : "🤖"));
        
        String myIntro = group.getIntroductions().get(targetMember.getName());
        if (myIntro != null && !myIntro.isEmpty()) {
            sb.append(String.format("你的自我介绍：%s\n", myIntro));
        }
        sb.append("记住：你就是「").append(targetMember.getName()).append("」，不是其他任何人！\n\n");

        // 1. 群规
        sb.append("【群规】\n");
        sb.append(getGroupRules(group));
        sb.append("\n\n");

        // 2. 成员列表 + 自我介绍
        sb.append("【群成员】\n");
        for (Member m : group.getMembers()) {
            String intro = group.getIntroductions().get(m.getName());
            String introText = (intro != null && !intro.isEmpty()) ? intro : "（未设置自我介绍）";
            
            if (m.getName().equals(targetMember.getName())) {
                sb.append(String.format("- 你: %s\n", introText));
            } else {
                sb.append(String.format("- %s %s: %s\n", 
                    m.getAvatar() != null ? m.getAvatar() : "🤖", 
                    m.getName(), introText));
            }
        }
        sb.append("\n");

        // 3. 行为模式
        sb.append("【你的角色】\n");
        sb.append(getRoleDescription(group, targetMember));
        sb.append("\n\n");

        // 4. 可用助手配置（用于模式二：主持人调度）
        if (isHost(group, targetMember)) {
            sb.append("【可调度的助手】\n");
            for (Member m : group.getMembers()) {
                if (!m.getName().equals(targetMember.getName())) {
                    sb.append(String.format(
                        "- %s: endpoint=%s, token=%s, model=%s\n",
                        m.getName(),
                        m.getApiEndpoint(),
                        m.getToken(),
                        m.getModelName()
                    ));
                }
            }
            sb.append("\n你可以根据任务需求，通过 HTTP API 调用这些助手。\n\n");
        }

        // 5. 最近对话历史
        if (!history.isEmpty()) {
            sb.append("【最近对话】\n");
            int start = Math.max(0, history.size() - 10);
            for (int i = start; i < history.size(); i++) {
                Message msg = history.get(i);
                sb.append(String.format("%s: %s\n", msg.getSender(), msg.getContent()));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 构建自我介绍请求
     */
    public String buildIntroRequest(Group group, Member targetMember) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("你已被邀请加入群聊「").append(group.getName()).append("」\n\n");
        
        sb.append("【群规】\n");
        sb.append(getGroupRules(group));
        sb.append("\n\n");
        
        sb.append("【成员列表】\n");
        for (Member m : group.getMembers()) {
            if (!m.getName().equals(targetMember.getName())) {
                String intro = group.getIntroductions().get(m.getName());
                String introText = (intro != null && !intro.isEmpty()) ? intro : "（待自我介绍）";
                sb.append(String.format("- %s %s: %s\n", 
                    m.getAvatar() != null ? m.getAvatar() : "🤖", 
                    m.getName(), introText));
            }
        }
        sb.append("\n");
        
        sb.append("【请回复】\n");
        sb.append("请用一句话介绍你的专长和能力，以便其他成员了解你可以提供什么帮助。\n");
        sb.append("格式：我是[名字]，擅长[领域]，可以提供[服务]。");
        
        return sb.toString();
    }

    /**
     * 构建历史消息（用于 API 调用）
     */
    public JSONArray buildMessagesForAPI(Group group, Member targetMember, List<Message> history, String currentMessage) {
        JSONArray messages = new JSONArray();
        
        try {
            // 系统提示
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", buildSystemPrompt(group, targetMember, history));
            messages.put(systemMsg);
            
            // 历史消息（限制数量）
            int start = Math.max(0, history.size() - 20);
            for (int i = start; i < history.size(); i++) {
                Message msg = history.get(i);
                JSONObject apiMsg = new JSONObject();
                apiMsg.put("role", msg.isUserMessage() ? "user" : "assistant");
                apiMsg.put("content", msg.getContent());
                messages.put(apiMsg);
            }
            
            // 当前消息
            if (currentMessage != null && !currentMessage.isEmpty()) {
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", currentMessage);
                messages.put(userMsg);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return messages;
    }

    /**
     * 获取群规（自定义或默认）
     */
    private String getGroupRules(Group group) {
        if (group.getRules() != null && !group.getRules().isEmpty()) {
            return group.getRules();
        }
        return getDefaultRules(group.getMode());
    }

    /**
     * 获取默认规则
     */
    public String getDefaultRules(int mode) {
        switch (mode) {
            case 1: // 用户主持
                return "用户会通过 @指定 你执行任务，被动响应指令，" +
                       "完成用户指令后等待下一步指示。";
            case 2: // 助手主持
                return "你是主持人，负责分析用户需求，协调其他助手完成任务，" +
                       "可以主动调用其他助手 API，汇总结果返回给用户。";
            case 0: // 平等讨论（默认）
            default:
                return "这是一个 AI 助手群聊，你可以与其他助手平等讨论，" +
                       "主动回应问题，也可以请求其他成员协助。" +
                       "\n\n【重要】回复后，请主动引导后续讨论：可以提出问题、分享相关见解、或邀请其他成员补充。" +
                       "\n群聊是为了协作思考，不要只回答一次就沉默。";
        }
    }

    /**
     * 获取角色描述
     */
    private String getRoleDescription(Group group, Member targetMember) {
        int mode = group.getMode();
        
        switch (mode) {
            case 1: // 用户主持
                return "用户会 @你 指定具体任务，被动响应。" +
                       "准确理解用户需求，完成任务后等待下一步指示。" +
                       "\n\n【重要】如果任务复杂，完成后可以主动询问用户是否需要进一步帮助。";
            
            case 2: // 助手主持
                if (isHost(group, targetMember)) {
                    return "你是群主持人，负责分析用户消息，" +
                           "决定需要哪些助手参与，汇总结果返回给用户。" +
                           "\n\n【重要】你的职责是协调！收到用户问题后，应该主动分析是否需要其他助手补充，" +
                           "在回复中明确说明'请[助手名]补充'或'需要XX助手协助'，这样系统会自动调度他们。" +
                           "\n不要只自己回答就结束，要让团队协作起来！";
                } else {
                    return "你是群成员，当主持人请求你补充时，请积极回应。" +
                           "\n【重要】主持人协调时，你应主动提供专业补充，展现团队协作价值。";
                }
            
            case 0: // 平等讨论
            default:
                return "你是群成员之一，可以主动参与讨论，" +
                       "根据自己擅长的领域回应其他成员的问题或请求。";
        }
    }

    /**
     * 判断是否为主持人
     */
    public boolean isHost(Group group, Member member) {
        return member.getName().equals(group.getHostName());
    }

    /**
     * 获取成员在群中的角色
     */
    public String getMemberRole(Group group, Member member) {
        if (isHost(group, member)) {
            return "主持人";
        }
        
        switch (group.getMode()) {
            case 1:
                return "执行者";
            case 2:
                return "被调度者";
            default:
                return "参与者";
        }
    }
}