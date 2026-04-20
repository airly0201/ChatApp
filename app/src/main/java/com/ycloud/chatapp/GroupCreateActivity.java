package com.ycloud.chatapp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

import com.ycloud.chatapp.model.Group;
import com.ycloud.chatapp.model.Member;
import com.ycloud.chatapp.service.GroupManager;
import com.ycloud.chatapp.util.Logger;

/**
 * 创建群组界面
 */
public class GroupCreateActivity extends Activity {
    private SharedPreferences prefs;
    private GroupManager groupManager;
    private List<JSONObject> serverList = new ArrayList<>();
    private List<CheckBox> memberCheckBoxes = new ArrayList<>();
    
    private EditText groupNameInput;
    private EditText rulesInput;
    private Spinner modeSpinner;
    private Spinner hostSpinner;
    private LinearLayout membersContainer;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Logger.i("GroupCreateActivity", "onCreate 开始");
        
        try {
            prefs = getSharedPreferences("chat_settings", MODE_PRIVATE);
            groupManager = new GroupManager(prefs);
            
            loadServers();
            
            // 主布局
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setBackgroundColor(Color.parseColor("#FAFAFA"));
            
            // 顶部栏
            LinearLayout topBar = new LinearLayout(this);
            topBar.setOrientation(LinearLayout.HORIZONTAL);
            topBar.setBackgroundColor(Color.parseColor("#4CAF50"));
            topBar.setPadding(24, 48, 24, 24);
            
            Button backBtn = new Button(this);
            backBtn.setText("←");
            backBtn.setTextSize(18);
            backBtn.setBackgroundColor(Color.TRANSPARENT);
            backBtn.setTextColor(Color.WHITE);
            backBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
            
            TextView title = new TextView(this);
            title.setText("创建群聊");
            title.setTextSize(20);
            title.setTextColor(Color.WHITE);
            title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            
            topBar.addView(backBtn);
            topBar.addView(title);
            layout.addView(topBar);
            
            // 内容区
            ScrollView scrollView = new ScrollView(this);
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(24, 16, 24, 16);
            scrollView.addView(content);
            layout.addView(scrollView);
            
            // 群名称
            TextView nameLabel = new TextView(this);
            nameLabel.setText("群名称");
            nameLabel.setTextSize(14);
            nameLabel.setTextColor(Color.GRAY);
            content.addView(nameLabel);
            
            groupNameInput = new EditText(this);
            groupNameInput.setHint("例如：AI 技术讨论组");
            groupNameInput.setTextSize(16);
        groupNameInput.setPadding(16, 16, 16, 16);
        groupNameInput.setBackgroundColor(Color.WHITE);
        content.addView(groupNameInput);
        
        // 群聊模式
        TextView modeLabel = new TextView(this);
        modeLabel.setText("\n群聊模式");
        modeLabel.setTextSize(14);
        modeLabel.setTextColor(Color.GRAY);
        content.addView(modeLabel);
        
        modeSpinner = new Spinner(this);
        String[] modes = {"平等讨论", "用户主持 (@指定)", "助手主持 (智能调度)"};
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, modes);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(modeAdapter);
        content.addView(modeSpinner);
        
        // 模式说明
        TextView modeDesc = new TextView(this);
        modeDesc.setText("平等讨论：所有助手都可响应\n用户主持：用户 @指定助手，被指定者响应\n助手主持：主持人智能调度其他助手");
        modeDesc.setTextSize(12);
        modeDesc.setTextColor(Color.GRAY);
        content.addView(modeDesc);
        
        // 群规
        TextView rulesLabel = new TextView(this);
        rulesLabel.setText("\n群规（可选）");
        rulesLabel.setTextSize(14);
        rulesLabel.setTextColor(Color.GRAY);
        content.addView(rulesLabel);
        
        rulesInput = new EditText(this);
        rulesInput.setHint("为空则使用默认群规");
        rulesInput.setTextSize(14);
        rulesInput.setPadding(16, 16, 16, 16);
        rulesInput.setBackgroundColor(Color.WHITE);
        rulesInput.setMinLines(3);
        content.addView(rulesInput);
        
        // 选择成员
        TextView membersLabel = new TextView(this);
        membersLabel.setText("\n选择成员（至少2人）");
        membersLabel.setTextSize(14);
        membersLabel.setTextColor(Color.GRAY);
        content.addView(membersLabel);
        
        membersContainer = new LinearLayout(this);
        membersContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(membersContainer);
        
        displayServerList();
        
        // 主持人选择（模式二）
        TextView hostLabel = new TextView(this);
        hostLabel.setText("\n选择主持人（助手主持模式）");
        hostLabel.setTextSize(14);
        hostLabel.setTextColor(Color.GRAY);
        content.addView(hostLabel);
        
        hostSpinner = new Spinner(this);
        content.addView(hostSpinner);
        
        // 创建按钮
        Button createBtn = new Button(this);
        createBtn.setText("✅ 创建群聊");
        createBtn.setTextSize(16);
        createBtn.setPadding(0, 24, 0, 24);
        createBtn.setBackgroundColor(Color.parseColor("#4CAF50"));
        createBtn.setTextColor(Color.WHITE);
        
        android.view.ViewGroup.MarginLayoutParams btnParams = new android.view.ViewGroup.MarginLayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = 24;
        createBtn.setLayoutParams(btnParams);
        
        createBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createGroup();
            }
        });
        content.addView(createBtn);
        
        setContentView(layout);
        
        Logger.i("GroupCreateActivity", "onCreate 完成");
        
        } catch (Exception e) {
            Logger.e("GroupCreateActivity", "onCreate 异常", e);
            Toast.makeText(this, "初始化失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private void loadServers() {
        serverList.clear();
        try {
            String json = prefs.getString("saved_servers", "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                serverList.add(arr.getJSONObject(i));
            }
            
            // 加上当前选中的
            String current = prefs.getString("current_server_name", "");
            String currentUrl = prefs.getString("server_url", "");
            String currentToken = prefs.getString("auth_token", "");
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
                    obj.put("avatar", prefs.getString("current_avatar", "🤖"));
                    obj.put("avatar_image", prefs.getString("current_avatar_image", ""));
                    obj.put("server_type", prefs.getString("server_type", "openclaw"));
                    serverList.add(0, obj);
                }
            }
        } catch (Exception e) {}
    }
    
    private void displayServerList() {
        membersContainer.removeAllViews();
        memberCheckBoxes.clear();
        
        List<String> hostNames = new ArrayList<>();
        
        for (final JSONObject server : serverList) {
            try {
                final String name = server.getString("name");
                final String avatar = server.optString("avatar", "🤖");
                
                hostNames.add(avatar + " " + name);
                
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.HORIZONTAL);
                item.setBackgroundColor(Color.WHITE);
                item.setPadding(16, 12, 16, 12);
                
                CheckBox checkBox = new CheckBox(this);
                checkBox.setText(avatar + " " + name);
                checkBox.setTextSize(16);
                checkBox.setTag(server);
                
                item.addView(checkBox);
                membersContainer.addView(item);
                memberCheckBoxes.add(checkBox);
                
            } catch (Exception e) {}
        }
        
        // 更新主持人选择
        ArrayAdapter<String> hostAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, hostNames);
        hostAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        hostSpinner.setAdapter(hostAdapter);
    }
    
    private void createGroup() {
        String name = groupNameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入群名称", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 收集选中的成员
        List<Member> members = new ArrayList<>();
        for (CheckBox cb : memberCheckBoxes) {
            if (cb.isChecked()) {
                JSONObject server = (JSONObject) cb.getTag();
                members.add(Member.fromServerConfig(server));
            }
        }
        
        if (members.size() < 2) {
            Toast.makeText(this, "请至少选择2个成员", Toast.LENGTH_SHORT).show();
            return;
        }
        
        int mode = modeSpinner.getSelectedItemPosition();
        String rules = rulesInput.getText().toString().trim();
        
        // 创建群组
        Group group = groupManager.createGroup(name, members, mode, rules);
        
        // 模式二：设置主持人
        if (mode == 2 && !members.isEmpty()) {
            int hostIndex = hostSpinner.getSelectedItemPosition();
            if (hostIndex >= 0 && hostIndex < members.size()) {
                groupManager.setHost(group.getId(), members.get(hostIndex).getName());
            }
        }
        
        Toast.makeText(this, "群聊创建成功！", Toast.LENGTH_SHORT).show();
        finish();
    }
}