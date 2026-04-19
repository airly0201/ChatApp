package com.ycloud.chatapp.model;

import org.json.JSONObject;

/**
 * 成员数据模型
 */
public class Member {
    private String name;            // 显示名称
    private String server;          // API 地址
    private String token;           // 认证令牌
    private String avatar;          // Emoji 头像
    private String avatarImage;     // 自定义头像路径
    private String serverType;      // openclaw/hermes/custom
    private int connectTimeout;     // 连接超时（秒）
    private int readTimeout;        // 读取超时（秒）
    private String intro;           // 自我介绍

    public Member() {}

    public Member(String name, String server, String token, String serverType) {
        this.name = name;
        this.server = server;
        this.token = token;
        this.serverType = serverType;
        this.avatar = "🤖";
        this.connectTimeout = 30;
        this.readTimeout = 120;
    }

    // 从现有服务器配置创建成员
    public static Member fromServerConfig(JSONObject server) {
        Member member = new Member();
        member.name = server.optString("name", "");
        member.server = server.optString("server", "");
        member.token = server.optString("token", "");
        member.avatar = server.optString("avatar", "🤖");
        member.avatarImage = server.optString("avatar_image", "");
        member.serverType = server.optString("server_type", "openclaw");
        member.connectTimeout = server.optInt("connect_timeout", 30);
        member.readTimeout = server.optInt("read_timeout", 120);
        return member;
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getServer() { return server; }
    public void setServer(String server) { this.server = server; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getAvatarImage() { return avatarImage; }
    public void setAvatarImage(String avatarImage) { this.avatarImage = avatarImage; }

    public String getServerType() { return serverType; }
    public void setServerType(String serverType) { this.serverType = serverType; }

    public int getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }

    public int getReadTimeout() { return readTimeout; }
    public void setReadTimeout(int readTimeout) { this.readTimeout = readTimeout; }

    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }

    // 获取模型名称
    public String getModelName() {
        if ("hermes".equals(serverType)) {
            return "hermes-agent";
        } else if ("custom".equals(serverType)) {
            return "gpt-3.5-turbo";
        } else {
            return "openclaw:main";
        }
    }

    // 获取 API 完整路径
    public String getApiEndpoint() {
        String endpoint = server;
        if (!endpoint.endsWith("/v1/chat/completions")) {
            if (!endpoint.endsWith("/")) {
                endpoint += "/";
            }
            endpoint += "v1/chat/completions";
        }
        return endpoint;
    }

    // JSON 序列化
    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("name", name);
            obj.put("server", server);
            obj.put("token", token);
            obj.put("avatar", avatar);
            obj.put("avatar_image", avatarImage);
            obj.put("server_type", serverType);
            obj.put("connect_timeout", connectTimeout);
            obj.put("read_timeout", readTimeout);
            obj.put("intro", intro != null ? intro : "");
        } catch (org.json.JSONException e) {
            e.printStackTrace();
        }
        return obj;
    }

    // JSON 反序列化
    public static Member fromJSON(JSONObject obj) {
        Member member = new Member();
        member.name = obj.optString("name", "");
        member.server = obj.optString("server", "");
        member.token = obj.optString("token", "");
        member.avatar = obj.optString("avatar", "🤖");
        member.avatarImage = obj.optString("avatar_image", "");
        member.serverType = obj.optString("server_type", "openclaw");
        member.connectTimeout = obj.optInt("connect_timeout", 30);
        member.readTimeout = obj.optInt("read_timeout", 120);
        member.intro = obj.optString("intro", "");
        return member;
    }

    // 获取显示名称（带头像）
    public String getDisplayName() {
        String avatarStr = avatar != null && !avatar.isEmpty() ? avatar : "🤖";
        return avatarStr + " " + name;
    }

    // 获取头像（优先图片，后 emoji）
    public String getAvatarForDisplay() {
        if (avatarImage != null && !avatarImage.isEmpty()) {
            return avatarImage;
        }
        return avatar;
    }
}