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
        
        // 并行调用所有成员 API
        List<MemberCallTask> tasks = new ArrayList<>();
        for (Member member : members) {
            MemberCallTask task = new MemberCallTask(member, group, message, history, promptBuilder);
            tasks.add(task);
        }
        
        try {
            // 执行所有任务
            List<Message> results = executeParallel(tasks);
            
            // 按成员顺序添加响应
            for (int i = 0; i < members.size(); i++) {
                Member member = members.get(i);
                if (i < results.size() && results.get(i) != null) {
                    responses.add(results.get(i));
                }
                if (listener != null) {
                    listener.onProgress(i + 1, members.size());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
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
            
            return new Message(
                member.getAvatar() + " " + member.getName(),
                response
            );
        } catch (Exception e) {
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
            return new Message("系统", "未找到主持人");
        }
        
        return sendToMember(host, group, message, history, promptBuilder);
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