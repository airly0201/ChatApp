package com.ycloud.chatapp.model;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 群组数据模型
 */
public class Group {
    private String id;
    private String name;
    private long createdAt;
    private int mode;               // 0=平等讨论, 1=用户主持, 2=助手主持
    private String hostName;        // 主持人名称
    private String rules;           // 自定义群规
    private List<Member> members;   // 成员列表
    private Map<String, String> introductions;  // 成员自我介绍

    public Group() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.mode = 0;  // 默认平等讨论
        this.members = new ArrayList<>();
        this.introductions = new HashMap<>();
    }

    public Group(String name) {
        this();
        this.name = name;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public int getMode() { return mode; }
    public void setMode(int mode) { this.mode = mode; }

    public String getHostName() { return hostName; }
    public void setHostName(String hostName) { this.hostName = hostName; }

    public String getRules() { return rules; }
    public void setRules(String rules) { this.rules = rules; }

    public List<Member> getMembers() { return members; }
    public void setMembers(List<Member> members) { this.members = members; }

    public Map<String, String> getIntroductions() { return introductions; }
    public void setIntroductions(Map<String, String> introductions) { this.introductions = introductions; }

    // JSON 序列化
    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("name", name);
        obj.put("created_at", createdAt);
        obj.put("mode", mode);
        obj.put("host_name", hostName != null ? hostName : "");
        obj.put("rules", rules != null ? rules : "");
        
        JSONArray membersArr = new JSONArray();
        for (Member m : members) {
            membersArr.put(m.toJSON());
        }
        obj.put("members", membersArr);
        
        JSONObject introsObj = new JSONObject();
        for (Map.Entry<String, String> entry : introductions.entrySet()) {
            introsObj.put(entry.getKey(), entry.getValue());
        }
        obj.put("introductions", introsObj);
        
        return obj;
    }

    // JSON 反序列化
    public static Group fromJSON(JSONObject obj) {
        Group group = new Group();
        try {
            group.id = obj.getString("id");
            group.name = obj.getString("name");
            group.createdAt = obj.optLong("created_at", System.currentTimeMillis());
            group.mode = obj.optInt("mode", 0);
            group.hostName = obj.optString("host_name", "");
            group.rules = obj.optString("rules", "");
            
            group.members = new ArrayList<>();
            JSONArray membersArr = obj.optJSONArray("members");
            if (membersArr != null) {
                for (int i = 0; i < membersArr.length(); i++) {
                    group.members.add(Member.fromJSON(membersArr.getJSONObject(i)));
                }
            }
            
            group.introductions = new HashMap<>();
            JSONObject introsObj = obj.optJSONObject("introductions");
            if (introsObj != null) {
                java.util.Iterator<String> keys = introsObj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    group.introductions.put(key, introsObj.optString(key, ""));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return group;
    }

    // 模式名称
    public String getModeName() {
        switch (mode) {
            case 1: return "用户主持";
            case 2: return "助手主持";
            default: return "平等讨论";
        }
    }
}