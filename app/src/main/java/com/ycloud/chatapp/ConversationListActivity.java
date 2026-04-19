package com.ycloud.chatapp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

import com.ycloud.chatapp.model.Group;
import com.ycloud.chatapp.service.GroupManager;
import com.ycloud.chatapp.service.MessageStorage;

public class ConversationListActivity extends Activity {
    private LinearLayout listContainer;
    private LinearLayout tabContainer;
    private SharedPreferences prefs;
    private List<JSONObject> serverList = new ArrayList<>();
    private List<Group> groupList = new ArrayList<>();
    private GroupManager groupManager;
    private MessageStorage messageStorage;
    
    private int currentTab = 0;  // 0=单聊, 1=群聊
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        prefs = getSharedPreferences("chat_settings", MODE_PRIVATE);
        groupManager = new GroupManager(prefs);
        messageStorage = new MessageStorage(this);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#FAFAFA"));
        
        // 顶部栏
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(Color.parseColor("#2196F3"));
        topBar.setPadding(24, 48, 24, 24);
        
        TextView title = new TextView(this);
        title.setText("💬 对话");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        
        ImageButton addBtn = new ImageButton(this);
        addBtn.setImageResource(android.R.drawable.ic_input_add);
        addBtn.setBackgroundColor(Color.TRANSPARENT);
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentTab == 0) {
                    startActivity(new Intent(ConversationListActivity.this, SettingsActivity.class));
                } else {
                    startActivity(new Intent(ConversationListActivity.this, GroupCreateActivity.class));
                }
            }
        });
        
        topBar.addView(title);
        topBar.addView(addBtn);
        layout.addView(topBar);
        
        // Tab 切换
        tabContainer = new LinearLayout(this);
        tabContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabContainer.setPadding(16, 16, 16, 8);
        
        // 单聊 Tab
        Button tabSingle = new Button(this);
        tabSingle.setText("👤 单聊");
        tabSingle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        tabSingle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentTab = 0;
                updateTabStyle();
                loadServers();
                displayList();
            }
        });
        
        // 群聊 Tab
        Button tabGroup = new Button(this);
        tabGroup.setText("👥 群聊");
        tabGroup.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        tabGroup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentTab = 1;
                updateTabStyle();
                loadGroups();
                displayList();
            }
        });
        
        tabContainer.addView(tabSingle);
        tabContainer.addView(tabGroup);
        layout.addView(tabContainer);
        
        // 列表
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(16, 16, 16, 16);
        layout.addView(listContainer);
        
        setContentView(layout);
        
        updateTabStyle();
        loadServers();
        loadGroups();
        displayList();
    }
    
    private void updateTabStyle() {
        if (tabContainer.getChildCount() >= 2) {
            Button tabSingle = (Button) tabContainer.getChildAt(0);
            Button tabGroup = (Button) tabContainer.getChildAt(1);
            
            if (currentTab == 0) {
                tabSingle.setBackgroundColor(Color.parseColor("#2196F3"));
                tabSingle.setTextColor(Color.WHITE);
                tabGroup.setBackgroundColor(Color.parseColor("#E0E0E0"));
                tabGroup.setTextColor(Color.BLACK);
            } else {
                tabSingle.setBackgroundColor(Color.parseColor("#E0E0E0"));
                tabSingle.setTextColor(Color.BLACK);
                tabGroup.setBackgroundColor(Color.parseColor("#2196F3"));
                tabGroup.setTextColor(Color.WHITE);
            }
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadServers();
        loadGroups();
        displayList();
    }
    
    private void loadGroups() {
        groupList = groupManager.getAllGroups();
    }
    
    private void loadServers() {
        serverList.clear();
        try {
            String json = prefs.getString("saved_servers", "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                serverList.add(arr.getJSONObject(i));
            }
            
            String current = prefs.getString("current_server_name", "");
            String currentUrl = prefs.getString("server_url", "");
            String currentToken = prefs.getString("auth_token", "");
            String currentAvatar = prefs.getString("current_avatar", "🤖");
            String currentAvatarImage = prefs.getString("current_avatar_image", "");
            
            if (!current.isEmpty() && !currentUrl.isEmpty()) {
                boolean found = false;
                for (JSONObject s : serverList) {
                    if (s.optString("name", "").equals(current)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    JSONObject obj = new JSONObject();
                    obj.put("name", current);
                    obj.put("server", currentUrl);
                    obj.put("token", currentToken);
                    obj.put("avatar", currentAvatar);
                    obj.put("avatar_image", currentAvatarImage);
                    obj.put("server_type", prefs.getString("server_type", "openclaw"));
                    serverList.add(0, obj);
                }
            }
        } catch (Exception e) {}
    }
    
    private void displayList() {
        listContainer.removeAllViews();
        
        if (currentTab == 0) {
            displayServerList();
        } else {
            displayGroupList();
        }
    }
    
    private void displayServerList() {
        if (serverList.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无单聊\n\n点击右上角 + 添加服务器");
            empty.setTextSize(16);
            empty.setTextColor(Color.GRAY);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, 100, 0, 100);
            listContainer.addView(empty);
            return;
        }
        
        for (final JSONObject server : serverList) {
            try {
                final String name = server.getString("name");
                final String serverUrl = server.getString("server");
                final String token = server.getString("token");
                final String avatar = server.optString("avatar", "🤖");
                final String avatarImage = server.optString("avatar_image", "");
                
                String safeName = name.replaceAll("[^a-zA-Z0-9]", "_");
                File historyFile = new File(getFilesDir(), "chat_history_" + safeName + ".json");
                String lastMsg = "暂无消息";
                if (historyFile.exists()) {
                    try {
                        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(historyFile));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();
                        JSONArray arr = new JSONArray(sb.toString());
                        if (arr.length() > 0) {
                            JSONObject last = arr.getJSONObject(arr.length() - 1);
                            lastMsg = last.getString("content");
                            if (lastMsg.length() > 30) lastMsg = lastMsg.substring(0, 30) + "...";
                        }
                    } catch (Exception e) {}
                }
                
                addListItem(true, avatar, avatarImage, name, lastMsg, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        prefs.edit()
                            .putString("server_url", serverUrl)
                            .putString("auth_token", token)
                            .putString("current_server_name", name)
                            .putString("current_avatar", avatar)
                            .putString("current_avatar_image", avatarImage)
                            .apply();
                        
                        Intent intent = new Intent(ConversationListActivity.this, ChatActivity.class);
                        intent.putExtra("server_name", name);
                        intent.putExtra("server_url", serverUrl);
                        intent.putExtra("auth_token", token);
                        intent.putExtra("avatar", avatar);
                        intent.putExtra("avatar_image", avatarImage);
                        startActivity(intent);
                    }
                }, null);
            } catch (Exception e) {}
        }
    }
    
    private void displayGroupList() {
        if (groupList.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无群聊\n\n点击右上角 + 创建群聊");
            empty.setTextSize(16);
            empty.setTextColor(Color.GRAY);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, 100, 0, 100);
            listContainer.addView(empty);
            return;
        }
        
        for (final Group group : groupList) {
            int memberCount = group.getMembers().size();
            String memberInfo = memberCount + "人 · " + group.getModeName();
            
            // 获取最后一条消息
            String lastMsg = "暂无消息";
            List<com.ycloud.chatapp.model.Message> history = messageStorage.loadHistory(group.getId());
            if (!history.isEmpty()) {
                com.ycloud.chatapp.model.Message last = history.get(history.size() - 1);
                lastMsg = last.getContent();
                if (lastMsg.length() > 30) lastMsg = lastMsg.substring(0, 30) + "...";
            }
            
            addListItem(false, "👥", "", group.getName(), lastMsg, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(ConversationListActivity.this, GroupChatActivity.class);
                    intent.putExtra("group_id", group.getId());
                    startActivity(intent);
                }
            }, new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    // 长按删除群组
                    groupManager.deleteGroup(group.getId());
                    messageStorage.deleteHistory(group.getId());
                    loadGroups();
                    displayList();
                    return true;
                }
            });
        }
    }
    
    private void addListItem(boolean isSingle, String avatar, String avatarImage, 
                            String title, String subtitle, 
                            View.OnClickListener clickListener,
                            View.OnLongClickListener longClickListener) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setBackgroundColor(Color.WHITE);
        item.setPadding(20, 20, 20, 20);
        android.view.ViewGroup.MarginLayoutParams params = 
            new android.view.ViewGroup.MarginLayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 12;
        item.setLayoutParams(params);
        
        // 头像
        if (!avatarImage.isEmpty()) {
            ImageView avatarImgView = new ImageView(this);
            avatarImgView.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
            avatarImgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            File avatarFile = new File(getFilesDir(), avatarImage);
            if (avatarFile.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(avatarFile.getAbsolutePath());
                avatarImgView.setImageBitmap(bmp);
            }
            item.addView(avatarImgView);
        } else {
            TextView avatarView = new TextView(this);
            avatarView.setText(avatar);
            avatarView.setTextSize(36);
            avatarView.setPadding(0, 0, 16, 0);
            item.addView(avatarView);
        }
        
        // 文字
        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setLayoutParams(new LinearLayout.LayoutParams(0, 
            LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(Color.parseColor("#1A1A1A"));
        textLayout.addView(titleView);
        
        TextView msgView = new TextView(this);
        msgView.setText(subtitle);
        msgView.setTextSize(14);
        msgView.setTextColor(Color.GRAY);
        textLayout.addView(msgView);
        
        item.addView(textLayout);
        
        item.setOnClickListener(clickListener);
        
        if (longClickListener != null) {
            item.setOnLongClickListener(longClickListener);
        }
        
        listContainer.addView(item);
    }
}