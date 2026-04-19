package com.ycloud.chatapp.service;

import android.content.SharedPreferences;
import com.ycloud.chatapp.model.Group;
import com.ycloud.chatapp.model.Member;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 群组管理器 - 负责群组的 CRUD 操作
 */
public class GroupManager {
    private static final String KEY_SAVED_GROUPS = "saved_groups";
    private SharedPreferences prefs;

    public GroupManager(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    /**
     * 创建群组
     */
    public Group createGroup(String name, List<Member> members, int mode, String rules) {
        Group group = new Group(name);
        group.setMode(mode);
        group.setRules(rules);
        
        if (mode == 2 && !members.isEmpty()) {
            // 模式二：默认第一个成员为主持人
            group.setHostName(members.get(0).getName());
        }
        
        group.setMembers(members);
        
        // 保存到存储
        List<Group> groups = getAllGroups();
        groups.add(0, group);
        saveGroups(groups);
        
        return group;
    }

    /**
     * 删除群组
     */
    public boolean deleteGroup(String groupId) {
        List<Group> groups = getAllGroups();
        boolean removed = groups.removeIf(g -> g.getId().equals(groupId));
        if (removed) {
            saveGroups(groups);
        }
        return removed;
    }

    /**
     * 获取群组
     */
    public Group getGroup(String groupId) {
        List<Group> groups = getAllGroups();
        for (Group g : groups) {
            if (g.getId().equals(groupId)) {
                return g;
            }
        }
        return null;
    }

    /**
     * 获取所有群组
     */
    public List<Group> getAllGroups() {
        List<Group> groups = new ArrayList<>();
        try {
            String json = prefs.getString(KEY_SAVED_GROUPS, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                groups.add(Group.fromJSON(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return groups;
    }

    /**
     * 更新群组
     */
    public boolean updateGroup(Group group) {
        List<Group> groups = getAllGroups();
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).getId().equals(group.getId())) {
                groups.set(i, group);
                saveGroups(groups);
                return true;
            }
        }
        return false;
    }

    /**
     * 添加成员到群组
     */
    public boolean addMember(String groupId, Member member) {
        Group group = getGroup(groupId);
        if (group != null) {
            // 检查是否已存在
            for (Member m : group.getMembers()) {
                if (m.getName().equals(member.getName())) {
                    return false;
                }
            }
            group.getMembers().add(member);
            return updateGroup(group);
        }
        return false;
    }

    /**
     * 移除群成员
     */
    public boolean removeMember(String groupId, String memberName) {
        Group group = getGroup(groupId);
        if (group != null) {
            boolean removed = group.getMembers().removeIf(m -> m.getName().equals(memberName));
            if (removed) {
                // 如果是主持人被移除，重置主持人
                if (memberName.equals(group.getHostName())) {
                    if (!group.getMembers().isEmpty()) {
                        group.setHostName(group.getMembers().get(0).getName());
                    } else {
                        group.setHostName("");
                    }
                }
                return updateGroup(group);
            }
        }
        return false;
    }

    /**
     * 设置主持人
     */
    public boolean setHost(String groupId, String memberName) {
        Group group = getGroup(groupId);
        if (group != null) {
            group.setHostName(memberName);
            return updateGroup(group);
        }
        return false;
    }

    /**
     * 更新自我介绍
     */
    public boolean updateIntroduction(String groupId, String memberName, String intro) {
        Group group = getGroup(groupId);
        if (group != null) {
            group.getIntroductions().put(memberName, intro);
            return updateGroup(group);
        }
        return false;
    }

    /**
     * 检查成员自我介绍是否完整
     */
    public boolean isIntroductionComplete(String groupId) {
        Group group = getGroup(groupId);
        if (group == null) return false;
        
        for (Member m : group.getMembers()) {
            String intro = group.getIntroductions().get(m.getName());
            if (intro == null || intro.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取需要自我介绍的成员
     */
    public Member getMemberNeedingIntro(String groupId) {
        Group group = getGroup(groupId);
        if (group == null) return null;
        
        for (Member m : group.getMembers()) {
            String intro = group.getIntroductions().get(m.getName());
            if (intro == null || intro.isEmpty()) {
                return m;
            }
        }
        return null;
    }

    /**
     * 保存群组列表
     */
    private void saveGroups(List<Group> groups) {
        try {
            JSONArray arr = new JSONArray();
            for (Group g : groups) {
                arr.put(g.toJSON());
            }
            prefs.edit().putString(KEY_SAVED_GROUPS, arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}