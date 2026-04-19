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
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

public class ConversationListActivity extends Activity {
    private LinearLayout listContainer;
    private SharedPreferences prefs;
    private List<JSONObject> serverList = new ArrayList<>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        prefs = getSharedPreferences("chat_settings", MODE_PRIVATE);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#FAFAFA"));
        
        // 顶部栏
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(Color.parseColor("#2196F3"));
        topBar.setPadding(24, 48, 24, 24);
        
        TextView title = new TextView(this);
        title.setText("💬 对话列表");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        
        ImageButton addBtn = new ImageButton(this);
        addBtn.setImageResource(android.R.drawable.ic_input_add);
        addBtn.setBackgroundColor(Color.TRANSPARENT);
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ConversationListActivity.this, SettingsActivity.class));
            }
        });
        
        topBar.addView(title);
        topBar.addView(addBtn);
        layout.addView(topBar);
        
        // 列表
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(16, 16, 16, 16);
        layout.addView(listContainer);
        
        setContentView(layout);
        
        loadServers();
        displayList();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadServers();
        displayList();
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
                    serverList.add(0, obj);
                }
            }
        } catch (Exception e) {}
    }
    
    private void displayList() {
        listContainer.removeAllViews();
        
        if (serverList.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无对话\n\n点击右上角 + 添加服务器");
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
                
                // 检查历史消息
                String safeName = name.replaceAll("[^a-zA-Z0-9]", "_");
                File historyFile = new File(getFilesDir(), "chat_history_" + safeName + ".json");
                String lastMsg = "";
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
                
                // 头像显示
                if (!avatarImage.isEmpty()) {
                    // 显示自定义头像图片
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
                    // 显示emoji头像
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
                
                TextView nameView = new TextView(this);
                nameView.setText(name);
                nameView.setTextSize(16);
                nameView.setTextColor(Color.parseColor("#1A1A1A"));
                textLayout.addView(nameView);
                
                TextView msgView = new TextView(this);
                msgView.setText(lastMsg.isEmpty() ? "暂无消息" : lastMsg);
                msgView.setTextSize(14);
                msgView.setTextColor(Color.GRAY);
                textLayout.addView(msgView);
                
                item.addView(textLayout);
                
                item.setOnClickListener(new View.OnClickListener() {
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
                });
                
                listContainer.addView(item);
                
            } catch (Exception e) {}
        }
    }
}