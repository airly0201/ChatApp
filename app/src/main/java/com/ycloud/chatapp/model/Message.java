package com.ycloud.chatapp.model;

import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 消息数据模型
 */
public class Message {
    private String sender;      // 发送者名称
    private String content;     // 消息内容
    private String time;        // 时间戳
    private String avatar;      // 发送者头像

    private static final SimpleDateFormat TIME_FORMAT = 
        new SimpleDateFormat("HH:mm", Locale.getDefault());

    public Message() {}

    public Message(String sender, String content) {
        this.sender = sender;
        this.content = content;
        this.time = TIME_FORMAT.format(new Date());
    }

    public Message(String sender, String content, String time) {
        this.sender = sender;
        this.content = content;
        this.time = time;
    }

    public Message(String sender, String content, String time, String avatar) {
        this.sender = sender;
        this.content = content;
        this.time = time;
        this.avatar = avatar;
    }

    // Getters and Setters
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    // 判断是否用户消息
    public boolean isUserMessage() {
        return "你".equals(sender) || "用户".equals(sender);
    }

    // JSON 序列化
    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("sender", sender);
            obj.put("content", content);
            obj.put("time", time);
            if (avatar != null) {
                obj.put("avatar", avatar);
            }
        } catch (org.json.JSONException e) {
            e.printStackTrace();
        }
        return obj;
    }

    // JSON 反序列化
    public static Message fromJSON(JSONObject obj) {
        Message msg = new Message();
        msg.sender = obj.optString("sender", "");
        msg.content = obj.optString("content", "");
        msg.time = obj.optString("time", TIME_FORMAT.format(new Date()));
        msg.avatar = obj.optString("avatar", "");
        return msg;
    }

    // 转换为 API 消息格式
    public JSONObject toAPIMessage() {
        JSONObject obj = new JSONObject();
        try {
            String role = isUserMessage() ? "user" : "assistant";
            obj.put("role", role);
            obj.put("content", content);
        } catch (org.json.JSONException e) {
            e.printStackTrace();
        }
        return obj;
    }

    // 从 API 消息创建
    public static Message fromAPIMessage(JSONObject apiMsg, String displayName) {
        Message msg = new Message();
        msg.sender = displayName;
        msg.content = apiMsg.optString("content", "");
        msg.time = TIME_FORMAT.format(new Date());
        return msg;
    }
}