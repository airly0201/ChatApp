package com.ycloud.chatapp.service;

import com.ycloud.chatapp.model.Group;
import com.ycloud.chatapp.model.Member;
import com.ycloud.chatapp.model.Message;
import com.ycloud.chatapp.util.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 消息分发器 - 负责调用各成员的 API
 */
public class MemberDispatcher {
    
    private static final int MAX_CONCURRENT = 3;
    private ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT);
    
    /**
     * 广播模式：消息发送给所有成员（平等讨论）
     */
    public List<Message> broadcast(Group group, String message, List<Message> history, 
                                   PromptBuilder promptBuilder, OnProgressListener listener) {
        Logger.i("MemberDispatcher", "broadcast: 成员数=" + group.getMembers().size() + ", 消息=" + message.substring(0, Math.min(50, message.length())));
        List<Message> responses = new ArrayList<>();
        List<Member> members = group.getMembers();
        
        if (members.isEmpty()) {
            Logger.w("MemberDispatcher", "成员列表为空！");
            return responses;
        }
        
        // 并行调用所有成员 API
        List<MemberCallTask> tasks = new ArrayList<>();
        for (Member member : members) {
            MemberCallTask task = new MemberCallTask(member, group, message, history, promptBuilder);
            tasks.add(task);
        }
        
        try {
            // 执行所有任务
            List<Message> results = executeParallel(tasks);
            Logger.i("MemberDispatcher", "executeParallel 完成，结果数: " + results.size());
            
            // 按成员顺序添加响应
            for (int i = 0; i < members.size(); i++) {
                Member member = members.get(i);
                Message result = (i < results.size()) ? results.get(i) : null;
                Logger.i("MemberDispatcher", "成员 " + member.getName() + " 响应: " + (result != null ? "有" : "null"));
                if (result != null) {
                    responses.add(result);
                    Logger.i("MemberDispatcher", "添加响应: " + result.getContent().substring(0, Math.min(30, result.getContent().length())));
                }
                if (listener != null) {
                    listener.onProgress(i + 1, members.size());
                }
            }
        } catch (Exception e) {
            Logger.e("MemberDispatcher", "broadcast 异常: " + e.getMessage());
            e.printStackTrace();
        }
        
        Logger.i("MemberDispatcher", "broadcast 完成，总响应数: " + responses.size());
        return responses;
    }

    /**
     * 单发模式：消息发送给单个成员（用户主持）
     */
    public Message sendToMember(Member member, Group group, String message, 
                                List<Message> history, PromptBuilder promptBuilder) {
        Logger.i("MemberDispatcher", "sendToMember: " + member.getName() + ", 消息=" + message.substring(0, Math.min(30, message.length())));
        try {
            JSONArray messages = promptBuilder.buildMessagesForAPI(group, member, history, message);
            String response = callAPI(member, messages);
            
            Logger.i("MemberDispatcher", "sendToMember 成功: " + member.getName());
            
            return new Message(
                member.getAvatar() + " " + member.getName(),
                response
            );
        } catch (Exception e) {
            Logger.e("MemberDispatcher", "sendToMember 失败: " + member.getName() + ", 错误=" + e.getMessage());
            return new Message(
                member.getAvatar() + " " + member.getName(),
                "调用失败: " + e.getMessage()
            );
        }
    }

    /**
     * 主持人模式：消息发送给主持人，由主持人协调（助手主持）
     */
    public Message sendToHost(Group group, String message, List<Message> history, 
                              PromptBuilder promptBuilder) {
        Logger.i("MemberDispatcher", "sendToHost: 主持人=" + group.getHostName());
        Member host = findMemberByName(group, group.getHostName());
        if (host == null) {
            Logger.e("MemberDispatcher", "未找到主持人: " + group.getHostName());
            return new Message("系统", "未找到主持人");
        }
        
        return sendToMember(host, group, message, history, promptBuilder);
    }
    
    /**
     * 协调模式：主持人先响应，然后可以调度其他助手补充
     * 实现真正的多助手协作
     */
    public List<Message> coordinateGroup(Group group, String message, List<Message> history,
                                          PromptBuilder promptBuilder) {
        Logger.i("MemberDispatcher", "coordinateGroup: 开始协调，成员数=" + group.getMembers().size());
        List<Message> responses = new ArrayList<>();
        
        if (group.getHostName() == null || group.getHostName().isEmpty()) {
            Logger.e("MemberDispatcher", "主持人名称为空！");
            return responses;
        }
        
        // 1. 先调用主持人
        Logger.i("MemberDispatcher", "调用主持人: " + group.getHostName());
        Message hostResponse = sendToHost(group, message, history, promptBuilder);
        responses.add(hostResponse);
        Logger.i("MemberDispatcher", "主持人响应: " + hostResponse.getContent().substring(0, Math.min(50, hostResponse.getContent().length())));
        
        // 2. 更新历史，加入主持人响应
        List<Message> updatedHistory = new ArrayList<>(history);
        updatedHistory.addAll(responses);
        
        // 3. 检查主持人响应中是否明确要求其他助手参与
        String hostContent = hostResponse.getContent().toLowerCase();
        boolean needsCoordination = containsCoordinationSignal(hostContent);
        
        Logger.i("MemberDispatcher", "需要协调: " + needsCoordination + ", 内容: " + hostContent.substring(0, Math.min(30, hostContent.length())));
        
        if (needsCoordination) {
            Logger.i("MemberDispatcher", "主持人请求其他助手参与");
            // 4. 调用其他助手补充
            List<Member> otherMembers = new ArrayList<>();
            for (Member m : group.getMembers()) {
                if (!m.getName().equals(group.getHostName())) {
                    otherMembers.add(m);
                }
            }
            
            Logger.i("MemberDispatcher", "其他助手数: " + otherMembers.size());
            // 并行调用其他助手
            for (Member member : otherMembers) {
                try {
                    Logger.i("MemberDispatcher", "调用助手: " + member.getName());
                    Message memberResponse = sendToMember(member, group, message, updatedHistory, promptBuilder);
                    responses.add(memberResponse);
                    Logger.i("MemberDispatcher", "助手 " + member.getName() + " 响应完成");
                } catch (Exception e) {
                    Logger.e("MemberDispatcher", "调用助手失败 " + member.getName() + ": " + e.getMessage());
                }
            }
        } else {
            // 主持人没有明确要求时，其他助手也可以主动补充
            Logger.i("MemberDispatcher", "其他助手主动补充");
            for (Member m : group.getMembers()) {
                if (!m.getName().equals(group.getHostName())) {
                    // 简短提示，让其他助手补充
                    String followUp = "主持人已回复: " + hostResponse.getContent().substring(0, Math.min(100, hostResponse.getContent().length()));
                    try {
                        Logger.i("MemberDispatcher", "调用助手补充: " + m.getName());
                        Message memberResponse = sendToMember(m, group, followUp, updatedHistory, promptBuilder);
                        responses.add(memberResponse);
                    } catch (Exception e) {
                        Logger.e("MemberDispatcher", "助手补充失败: " + e.getMessage());
                    }
                }
            }
        }
        
        Logger.i("MemberDispatcher", "coordinateGroup 完成，总响应数: " + responses.size());
        return responses;
    }
    
    /**
     * 检查响应中是否包含协调信号（明确要求其他助手）
     */
    private boolean containsCoordinationSignal(String content) {
        String[] signals = {"调用", "请求", "请", "需要", "@", "助手", "协调", "分工", "分工协作"};
        for (String signal : signals) {
            if (content.contains(signal)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取自我介绍
     */
    public Message requestIntroduction(Member member, Group group, PromptBuilder promptBuilder) {
        try {
            String introPrompt = promptBuilder.buildIntroRequest(group, member);
            
            JSONArray messages = new JSONArray();
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", introPrompt);
            messages.put(systemMsg);
            
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", "请介绍自己");
            messages.put(userMsg);
            
            String response = callAPI(member, messages);
            
            return new Message(
                member.getAvatar() + " " + member.getName(),
                response
            );
        } catch (Exception e) {
            return new Message(
                member.getAvatar() + " " + member.getName(),
                "自我介绍请求失败: " + e.getMessage()
            );
        }
    }

    /**
     * 调用成员 API
     */
    private String callAPI(Member member, JSONArray messages) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(member.getApiEndpoint());
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Authorization", "Bearer " + member.getToken());
            conn.setDoOutput(true);
            conn.setConnectTimeout(member.getConnectTimeout() * 1000);
            conn.setReadTimeout(member.getReadTimeout() * 1000);
            
            JSONObject json = new JSONObject();
            json.put("model", member.getModelName());
            json.put("messages", messages);
            
            OutputStream os = conn.getOutputStream();
            os.write(json.toString().getBytes("UTF-8"));
            os.close();
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
                
                JSONObject result = new JSONObject(sb.toString());
                return result.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
            } else {
                throw new Exception("HTTP " + responseCode);
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 并行执行多个任务
     */
    private List<Message> executeParallel(List<MemberCallTask> tasks) throws Exception {
        final List<Message> results = new ArrayList<>(tasks.size());
        for (int i = 0; i < tasks.size(); i++) {
            results.add(null);
        }
        
        final Exception[] errors = new Exception[1];
        errors[0] = null;
        
        List<Thread> threads = new ArrayList<>();
        final int taskCount = tasks.size();
        
        for (int i = 0; i < tasks.size(); i++) {
            final int index = i;
            final MemberCallTask task = tasks.get(i);
            
            Thread thread = new Thread(() -> {
                try {
                    Message response = task.execute();
                    synchronized (results) {
                        results.set(index, response);
                    }
                } catch (Exception e) {
                    synchronized (errors) {
                        if (errors[0] == null) {
                            errors[0] = e;
                        }
                    }
                    synchronized (results) {
                        results.set(index, new Message(
                            task.member.getAvatar() + " " + task.member.getName(),
                            "调用失败: " + e.getMessage()
                        ));
                    }
                }
            });
            threads.add(thread);
            thread.start();
            
            // 限制并发数
            if (i >= MAX_CONCURRENT - 1 || i == taskCount - 1) {
                for (Thread t : threads) {
                    t.join();
                }
                threads.clear();
            }
        }
        
        if (errors[0] != null) {
            throw errors[0];
        }
        
        return results;
    }

    /**
     * 按名称查找成员
     */
    private Member findMemberByName(Group group, String name) {
        for (Member m : group.getMembers()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }

    /**
     * 成员调用任务
     */
    private class MemberCallTask {
        Member member;
        Group group;
        String message;
        List<Message> history;
        PromptBuilder promptBuilder;

        MemberCallTask(Member member, Group group, String message, 
                       List<Message> history, PromptBuilder promptBuilder) {
            this.member = member;
            this.group = group;
            this.message = message;
            this.history = history;
            this.promptBuilder = promptBuilder;
        }

        Message execute() throws Exception {
            JSONArray messages = promptBuilder.buildMessagesForAPI(group, member, history, message);
            String response = callAPI(member, messages);
            
            return new Message(
                member.getAvatar() + " " + member.getName(),
                response
            );
        }
    }

    /**
     * 进度监听器
     */
    public interface OnProgressListener {
        void onProgress(int current, int total);
    }
}