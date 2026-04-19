package com.ycloud.chatapp.service;

import android.content.Context;
import com.ycloud.chatapp.model.Message;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * 消息存储管理器 - 负责群聊消息的持久化
 */
public class MessageStorage {
    
    private static final String HISTORY_PREFIX = "chat_history_group_";
    private Context context;

    public MessageStorage(Context context) {
        this.context = context;
    }

    /**
     * 获取群组历史文件路径
     */
    private String getHistoryFilePath(String groupId) {
        String safeName = groupId.replaceAll("[^a-zA-Z0-9]", "_");
        return HISTORY_PREFIX + safeName + ".json";
    }

    /**
     * 加载群组消息历史
     */
    public List<Message> loadHistory(String groupId) {
        List<Message> messages = new ArrayList<>();
        try {
            File file = new File(context.getFilesDir(), getHistoryFilePath(groupId));
            if (file.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(file));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
                
                JSONArray arr = new JSONArray(sb.toString());
                for (int i = 0; i < arr.length(); i++) {
                    messages.add(Message.fromJSON(arr.getJSONObject(i)));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return messages;
    }

    /**
     * 保存群组消息历史
     */
    public boolean saveHistory(String groupId, List<Message> messages) {
        try {
            JSONArray arr = new JSONArray();
            for (Message msg : messages) {
                arr.put(msg.toJSON());
            }
            
            File file = new File(context.getFilesDir(), getHistoryFilePath(groupId));
            FileWriter writer = new FileWriter(file);
            writer.write(arr.toString(2));
            writer.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 添加消息到历史
     */
    public boolean addMessage(String groupId, Message message) {
        List<Message> messages = loadHistory(groupId);
        messages.add(message);
        return saveHistory(groupId, messages);
    }

    /**
     * 添加多条消息到历史
     */
    public boolean addMessages(String groupId, List<Message> newMessages) {
        List<Message> messages = loadHistory(groupId);
        messages.addAll(newMessages);
        return saveHistory(groupId, messages);
    }

    /**
     * 清空群组历史
     */
    public boolean clearHistory(String groupId) {
        return saveHistory(groupId, new ArrayList<>());
    }

    /**
     * 获取文件大小
     */
    public long getHistorySize(String groupId) {
        File file = new File(context.getFilesDir(), getHistoryFilePath(groupId));
        return file.exists() ? file.length() : 0;
    }

    /**
     * 删除历史文件
     */
    public boolean deleteHistory(String groupId) {
        File file = new File(context.getFilesDir(), getHistoryFilePath(groupId));
        return !file.exists() || file.delete();
    }
}