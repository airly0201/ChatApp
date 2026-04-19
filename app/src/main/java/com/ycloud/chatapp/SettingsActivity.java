package com.ycloud.chatapp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;
import android.provider.MediaStore;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.widget.ImageView;
import java.io.File;
import java.io.FileOutputStream;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.io.InputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import android.app.AlertDialog;

public class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private EditText serverInput, tokenInput, nameInput, connTimeout, readTimeout;
    private EditText fontChat, fontTitle, fontInput;
    private LinearLayout serverListContainer, avatarContainer;
    private Spinner serverTypeSpinner;
    private List<JSONObject> serverList = new ArrayList<>();
    private String[] avatars = {"🤖", "👨‍💻", "🎯", "🚀", "⭐", "🔥", "💡", "🎨", "🦊", "🐱"};
    private String currentAvatar = "🤖";
    private ImageView avatarPreview;
    private String selectedAvatarPath;
    private String editingServerName = null;
    
    // 头像图片路径（相对于应用私有目录）
    private static final String AVATAR_DIR = "avatars";
    private String currentAvatarImagePath = null;
    
    private static final int PICK_IMAGE_REQUEST = 100;
    private static final int REQUEST_EXPORT = 101;
    private static final int REQUEST_IMPORT = 102;
    private String exportConfigData;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        prefs = getSharedPreferences("chat_settings", MODE_PRIVATE);
        
        // 初始化头像图片目录
        File avatarDir = new File(getFilesDir(), AVATAR_DIR);
        if (!avatarDir.exists()) {
            avatarDir.mkdirs();
        }
        
        loadServerList();
        
        Intent intent = getIntent();
        if (intent != null) {
            editingServerName = intent.getStringExtra("edit_server");
            if (editingServerName != null) {
                for (JSONObject s : serverList) {
                    if (s.optString("name", "").equals(editingServerName)) {
                        try {
                            currentAvatar = s.optString("avatar", "🤖");
                            // 检查是否有自定义头像图片
                            String avatarImg = s.optString("avatar_image", "");
                            if (!avatarImg.isEmpty()) {
                                currentAvatarImagePath = avatarImg;
                            }
                        } catch (Exception e) {}
                        break;
                    }
                }
            }
        }
        
        if (currentAvatar == null) currentAvatar = "🤖";
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#FAFAFA"));
        
        // 顶部栏
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(Color.parseColor("#2196F3"));
        topBar.setPadding(24, 48, 24, 24);
        
        ImageButton backBtn = new ImageButton(this);
        backBtn.setImageResource(android.R.drawable.ic_menu_revert);
        backBtn.setBackgroundColor(Color.TRANSPARENT);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        
        TextView title = new TextView(this);
        title.setText(editingServerName != null ? "⚙️ 编辑服务器" : "⚙️ 设置");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        
        topBar.addView(backBtn);
        topBar.addView(title);
        layout.addView(topBar);
        
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(40, 20, 40, 40);
        scrollView.addView(content);
        layout.addView(scrollView);
        
        // 头像选择区域
        TextView avatarLabel = new TextView(this);
        avatarLabel.setText("选择头像");
        avatarLabel.setTextSize(14);
        avatarLabel.setTextColor(Color.GRAY);
        content.addView(avatarLabel);
        
        // 头像预览
        avatarPreview = new ImageView(this);
        avatarPreview.setLayoutParams(new LinearLayout.LayoutParams(120, 120));
        avatarPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarPreview.setBackgroundColor(Color.parseColor("#E0E0E0"));
        
        // 如果有自定义头像图片，显示它
        if (currentAvatarImagePath != null && !currentAvatarImagePath.isEmpty()) {
            File avatarFile = new File(getFilesDir(), currentAvatarImagePath);
            if (avatarFile.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(avatarFile.getAbsolutePath());
                if (bmp != null) {
                    avatarPreview.setImageBitmap(bmp);
                }
            }
        } else {
            // 显示emoji头像
            TextView emojiView = new TextView(this);
            emojiView.setText(currentAvatar);
            emojiView.setTextSize(48);
            avatarPreview.setPadding(20, 20, 20, 20);
            avatarPreview.setScaleType(ImageView.ScaleType.CENTER);
            avatarPreview.setBackgroundColor(Color.parseColor("#F5F5F5"));
        }
        
        content.addView(avatarPreview);
        
        // 从相册选择按钮
        Button pickImageBtn = new Button(this);
        pickImageBtn.setText("📷 从相册选择图片");
        pickImageBtn.setTextSize(14);
        pickImageBtn.setPadding(20, 16, 20, 16);
        pickImageBtn.setBackgroundColor(Color.parseColor("#4CAF50"));
        pickImageBtn.setTextColor(Color.WHITE);
        pickImageBtn.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        android.view.ViewGroup.MarginLayoutParams pickParams = (android.view.ViewGroup.MarginLayoutParams) pickImageBtn.getLayoutParams();
        if (pickParams == null) {
            pickParams = new android.view.ViewGroup.MarginLayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        pickParams.topMargin = 12;
        pickImageBtn.setLayoutParams(pickParams);
        
        pickImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openImagePicker();
            }
        });
        content.addView(pickImageBtn);
        
        // 清除自定义头像按钮
        if (currentAvatarImagePath != null && !currentAvatarImagePath.isEmpty()) {
            Button clearAvatarBtn = new Button(this);
            clearAvatarBtn.setText("❌ 清除自定义头像");
            clearAvatarBtn.setTextSize(14);
            clearAvatarBtn.setPadding(20, 16, 20, 16);
            clearAvatarBtn.setBackgroundColor(Color.parseColor("#F44336"));
            clearAvatarBtn.setTextColor(Color.WHITE);
            clearAvatarBtn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            android.view.ViewGroup.MarginLayoutParams clearParams = (android.view.ViewGroup.MarginLayoutParams) clearAvatarBtn.getLayoutParams();
        if (clearParams == null) {
            clearParams = new android.view.ViewGroup.MarginLayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        clearParams.topMargin = 8;
        clearAvatarBtn.setLayoutParams(clearParams);
            
            clearAvatarBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clearCustomAvatar();
                }
            });
            content.addView(clearAvatarBtn);
        }
        
        // 分隔线
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(Color.parseColor("#E0E0E0"));
        android.view.ViewGroup.MarginLayoutParams dividerParams = (android.view.ViewGroup.MarginLayoutParams) divider.getLayoutParams();
        if (dividerParams == null) {
            dividerParams = new android.view.ViewGroup.MarginLayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1);
        }
        dividerParams.topMargin = 20;
        divider.setLayoutParams(dividerParams);
        content.addView(divider);
        
        // Emoji头像选择
        TextView emojiLabel = new TextView(this);
        emojiLabel.setText("\n或选择Emoji头像");
        emojiLabel.setTextSize(14);
        emojiLabel.setTextColor(Color.GRAY);
        content.addView(emojiLabel);
        
        avatarContainer = new LinearLayout(this);
        avatarContainer.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(avatarContainer);
        
        displayAvatars();
        
        // 服务器名称
        TextView nameLabel = new TextView(this);
        nameLabel.setText("\n服务器名称");
        nameLabel.setTextSize(14);
        nameLabel.setTextColor(Color.GRAY);
        content.addView(nameLabel);
        
        nameInput = new EditText(this);
        nameInput.setTextSize(16);
        nameInput.setPadding(20, 20, 20, 20);
        nameInput.setBackgroundColor(Color.WHITE);
        content.addView(nameInput);
        
        // 服务器地址
        TextView serverLabel = new TextView(this);
        serverLabel.setText("\n服务器地址");
        serverLabel.setTextSize(14);
        serverLabel.setTextColor(Color.GRAY);
        content.addView(serverLabel);
        
        serverInput = new EditText(this);
        serverInput.setTextSize(16);
        serverInput.setPadding(20, 20, 20, 20);
        serverInput.setBackgroundColor(Color.WHITE);
        content.addView(serverInput);
        
        // Token
        TextView tokenLabel = new TextView(this);
        tokenLabel.setText("\n认证Token");
        tokenLabel.setTextSize(14);
        tokenLabel.setTextColor(Color.GRAY);
        content.addView(tokenLabel);
        
        tokenInput = new EditText(this);
        tokenInput.setTextSize(16);
        tokenInput.setPadding(20, 20, 20, 20);
        tokenInput.setBackgroundColor(Color.WHITE);
        content.addView(tokenInput);
        
        // 服务器类型选择
        TextView typeLabel = new TextView(this);
        typeLabel.setText("\n服务器类型");
        typeLabel.setTextSize(14);
        typeLabel.setTextColor(Color.GRAY);
        content.addView(typeLabel);
        
        serverTypeSpinner = new Spinner(this);
        String[] serverTypes = {"OpenClaw", "Hermes", "自定义"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, serverTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        serverTypeSpinner.setAdapter(adapter);
        serverTypeSpinner.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        android.view.ViewGroup.MarginLayoutParams spinnerParams = (android.view.ViewGroup.MarginLayoutParams) serverTypeSpinner.getLayoutParams();
        if (spinnerParams == null) {
            spinnerParams = new android.view.ViewGroup.MarginLayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        spinnerParams.topMargin = 8;
        spinnerParams.bottomMargin = 8;
        serverTypeSpinner.setLayoutParams(spinnerParams);
        content.addView(serverTypeSpinner);
        
        // 服务器类型说明
        TextView typeDesc = new TextView(this);
        typeDesc.setText("OpenClaw: 使用 openclaw:main 模型\nHermes: 使用 hermes-agent 模型\n自定义: 需手动填写模型名称");
        typeDesc.setTextSize(12);
        typeDesc.setTextColor(Color.GRAY);
        content.addView(typeDesc);
        
        // 超时设置（每个服务器独立）
        TextView timeoutTitle = new TextView(this);
        timeoutTitle.setText("\n⏱️ 超时设置");
        timeoutTitle.setTextSize(16);
        timeoutTitle.setTextColor(Color.parseColor("#1A1A1A"));
        content.addView(timeoutTitle);
        
        TextView timeoutDesc = new TextView(this);
        timeoutDesc.setText("连接超时：建立连接最长时间\n读取超时：等待服务器响应时间");
        timeoutDesc.setTextSize(12);
        timeoutDesc.setTextColor(Color.GRAY);
        content.addView(timeoutDesc);
        
        // 连接超时
        TextView connLabel = new TextView(this);
        connLabel.setText("\n连接超时（秒）");
        connLabel.setTextSize(14);
        connLabel.setTextColor(Color.GRAY);
        content.addView(connLabel);
        
        connTimeout = new EditText(this);
        connTimeout.setHint("默认30秒");
        connTimeout.setTextSize(16);
        connTimeout.setPadding(20, 20, 20, 20);
        connTimeout.setBackgroundColor(Color.WHITE);
        content.addView(connTimeout);
        
        // 读取超时
        TextView readLabel = new TextView(this);
        readLabel.setText("\n读取超时（秒）");
        readLabel.setTextSize(14);
        readLabel.setTextColor(Color.GRAY);
        content.addView(readLabel);
        
        readTimeout = new EditText(this);
        readTimeout.setHint("默认120秒");
        readTimeout.setTextSize(16);
        readTimeout.setPadding(20, 20, 20, 20);
        readTimeout.setBackgroundColor(Color.WHITE);
        content.addView(readTimeout);
        
        // 字体大小设置
        TextView fontTitle2 = new TextView(this);
        fontTitle2.setText("\n🔤 字体大小");
        fontTitle2.setTextSize(16);
        fontTitle2.setTextColor(Color.parseColor("#1A1A1A"));
        content.addView(fontTitle2);
        
        TextView fontDesc = new TextView(this);
        fontDesc.setText("设置聊天界面的字体大小（建议10-24）");
        fontDesc.setTextSize(12);
        fontDesc.setTextColor(Color.GRAY);
        content.addView(fontDesc);
        
        // 聊天字体
        TextView chatLabel = new TextView(this);
        chatLabel.setText("\n聊天消息字体大小");
        chatLabel.setTextSize(14);
        chatLabel.setTextColor(Color.GRAY);
        content.addView(chatLabel);
        
        fontChat = new EditText(this);
        fontChat.setHint("默认14");
        fontChat.setTextSize(16);
        fontChat.setPadding(20, 20, 20, 20);
        fontChat.setBackgroundColor(Color.WHITE);
        content.addView(fontChat);
        
        // 输入框字体
        TextView inputLabel = new TextView(this);
        inputLabel.setText("\n输入框字体大小");
        inputLabel.setTextSize(14);
        inputLabel.setTextColor(Color.GRAY);
        content.addView(inputLabel);
        
        fontInput = new EditText(this);
        fontInput.setHint("默认14");
        fontInput.setTextSize(16);
        fontInput.setPadding(20, 20, 20, 20);
        fontInput.setBackgroundColor(Color.WHITE);
        content.addView(fontInput);
        
        // 加载保存的设置
        fontChat.setText(prefs.getString("font_size_chat", "14"));
        fontInput.setText(prefs.getString("font_size_input", "14"));
        
        // 填充编辑数据
        if (editingServerName != null) {
            for (JSONObject s : serverList) {
                if (s.optString("name", "").equals(editingServerName)) {
                    try {
                        nameInput.setText(s.getString("name"));
                        serverInput.setText(s.getString("server"));
                        tokenInput.setText(s.getString("token"));
                        connTimeout.setText(s.optString("connect_timeout", "30"));
                        readTimeout.setText(s.optString("read_timeout", "120"));
                        
                        // 加载服务器类型
                        String serverType = s.optString("server_type", "openclaw");
                        if (serverType.equals("hermes")) {
                            serverTypeSpinner.setSelection(1);
                        } else if (serverType.equals("custom")) {
                            serverTypeSpinner.setSelection(2);
                        } else {
                            serverTypeSpinner.setSelection(0); // 默认 OpenClaw
                        }
                    } catch (Exception e) {}
                    break;
                }
            }
        }
        
        // 保存按钮
        Button saveBtn = new Button(this);
        saveBtn.setText("💾 保存");
        saveBtn.setTextSize(16);
        saveBtn.setPadding(0, 24, 0, 24);
        saveBtn.setBackgroundColor(Color.parseColor("#2196F3"));
        saveBtn.setTextColor(Color.WHITE);
        android.view.ViewGroup.MarginLayoutParams saveParams = new ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveParams.topMargin = 30;
        saveBtn.setLayoutParams(saveParams);
        
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCurrentServer();
            }
        });
        content.addView(saveBtn);
        
        // 导出/导入配置按钮
        if (editingServerName == null) {
            LinearLayout exportImportLayout = new LinearLayout(this);
            exportImportLayout.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = 20;
            exportImportLayout.setLayoutParams(lp);
            
            // 导出按钮
            Button exportBtn = new Button(this);
            exportBtn.setText("📤 导出配置");
            exportBtn.setTextSize(14);
            exportBtn.setPadding(20, 16, 20, 16);
            exportBtn.setBackgroundColor(Color.parseColor("#4CAF50"));
            exportBtn.setTextColor(Color.WHITE);
            exportBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            exportBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    exportConfig();
                }
            });
            
            // 导入按钮
            Button importBtn = new Button(this);
            importBtn.setText("📥 导入配置");
            importBtn.setTextSize(14);
            importBtn.setPadding(20, 16, 20, 16);
            importBtn.setBackgroundColor(Color.parseColor("#FF9800"));
            importBtn.setTextColor(Color.WHITE);
            importBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            importBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    importConfig();
                }
            });
            
            exportImportLayout.addView(exportBtn);
            exportImportLayout.addView(importBtn);
            content.addView(exportImportLayout);
        }
        
        // 已保存的服务器列表
        if (editingServerName == null) {
            TextView listTitle = new TextView(this);
            listTitle.setText("\n📋 已保存的服务器\n(点击编辑，长按删除)");
            listTitle.setTextSize(16);
            listTitle.setTextColor(Color.parseColor("#1A1A1A"));
            content.addView(listTitle);
            
            serverListContainer = new LinearLayout(this);
            serverListContainer.setOrientation(LinearLayout.VERTICAL);
            content.addView(serverListContainer);
            
            displayServerList();
        }
        
        setContentView(layout);
    }
    
    // 打开图片选择器
    private void openImagePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开图片选择器", Toast.LENGTH_SHORT).show();
        }
    }
    
    // 处理选中的图片
    private void handleSelectedImage(Uri imageUri) {
        try {
            // 复制图片到应用私有目录
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            if (inputStream == null) {
                Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 解码图片
            Bitmap original = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            
            if (original == null) {
                Toast.makeText(this, "图片解码失败", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 缩放到合适大小（头像120x120）
            int size = 240; // 2倍清晰度
            float scale = Math.min((float) size / original.getWidth(), 
                                   (float) size / original.getHeight());
            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);
            Bitmap scaled = Bitmap.createBitmap(original, 0, 0, 
                original.getWidth(), original.getHeight(), matrix, true);
            
            // 保存到私有目录
            String fileName = "avatar_" + System.currentTimeMillis() + ".png";
            File avatarDir = new File(getFilesDir(), AVATAR_DIR);
            File avatarFile = new File(avatarDir, fileName);
            
            FileOutputStream fos = new FileOutputStream(avatarFile);
            scaled.compress(Bitmap.CompressFormat.PNG, 90, fos);
            fos.close();
            
            // 设置路径
            currentAvatarImagePath = AVATAR_DIR + "/" + fileName;
            
            // 更新预览
            avatarPreview.setImageBitmap(scaled);
            
            Toast.makeText(this, "头像已更新", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Toast.makeText(this, "处理图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    // 清除自定义头像
    private void clearCustomAvatar() {
        // 删除图片文件
        if (currentAvatarImagePath != null && !currentAvatarImagePath.isEmpty()) {
            File avatarFile = new File(getFilesDir(), currentAvatarImagePath);
            if (avatarFile.exists()) {
                avatarFile.delete();
            }
            currentAvatarImagePath = null;
            
            // 恢复显示emoji
            avatarPreview.setImageDrawable(null);
            TextView emojiView = new TextView(this);
            emojiView.setText(currentAvatar);
            emojiView.setTextSize(48);
            avatarPreview.setPadding(20, 20, 20, 20);
            avatarPreview.setBackgroundColor(Color.parseColor("#F5F5F5"));
            
            Toast.makeText(this, "已清除自定义头像", Toast.LENGTH_SHORT).show();
            
            // 刷新界面重新显示
            recreate();
        }
    }
    
    private void saveCurrentServer() {
        String name = nameInput.getText().toString().trim();
        String server = serverInput.getText().toString().trim();
        String token = tokenInput.getText().toString().trim();
        String ctVal = connTimeout.getText().toString().trim();
        String rtVal = readTimeout.getText().toString().trim();
        String fcVal = fontChat.getText().toString().trim();
        String fiVal = fontInput.getText().toString().trim();
        
        // 获取服务器类型
        int typeIndex = serverTypeSpinner.getSelectedItemPosition();
        String[] serverTypeValues = {"openclaw", "hermes", "custom"};
        String serverType = serverTypeValues[typeIndex];
        
        if (name.isEmpty() || server.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "请填写完整", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            int ct = ctVal.isEmpty() ? 30 : Integer.parseInt(ctVal);
            int rt = rtVal.isEmpty() ? 120 : Integer.parseInt(rtVal);
            int fc = fcVal.isEmpty() ? 14 : Integer.parseInt(fcVal);
            int fi = fiVal.isEmpty() ? 14 : Integer.parseInt(fiVal);
            
            // 字体大小限制在10-24
            if (fc < 10) fc = 10;
            if (fc > 24) fc = 24;
            if (fi < 10) fi = 10;
            if (fi > 24) fi = 24;
            
            boolean found = false;
            for (int i = 0; i < serverList.size(); i++) {
                if (serverList.get(i).getString("name").equals(name)) {
                    JSONObject obj = new JSONObject();
                    obj.put("name", name);
                    obj.put("server", server);
                    obj.put("token", token);
                    obj.put("avatar", currentAvatar);
                    obj.put("avatar_image", currentAvatarImagePath != null ? currentAvatarImagePath : "");
                    obj.put("connect_timeout", String.valueOf(ct));
                    obj.put("read_timeout", String.valueOf(rt));
                    obj.put("server_type", serverType);
                    serverList.set(i, obj);
                    found = true;
                    break;
                }
            }
            if (!found) {
                JSONObject obj = new JSONObject();
                obj.put("name", name);
                obj.put("server", server);
                obj.put("token", token);
                obj.put("avatar", currentAvatar);
                obj.put("avatar_image", currentAvatarImagePath != null ? currentAvatarImagePath : "");
                obj.put("connect_timeout", String.valueOf(ct));
                obj.put("read_timeout", String.valueOf(rt));
                obj.put("server_type", serverType);
                serverList.add(0, obj);
            }
            saveServerList();
            
            // 保存服务器设置
            prefs.edit()
                .putString("server_url", server)
                .putString("auth_token", token)
                .putString("current_server_name", name)
                .putString("current_avatar", currentAvatar)
                .putString("current_avatar_image", currentAvatarImagePath != null ? currentAvatarImagePath : "")
                .putString("connect_timeout", String.valueOf(ct))
                .putString("read_timeout", String.valueOf(rt))
                .putString("server_type", serverType)
                .apply();
            
            // 保存字体设置（全局）
            prefs.edit()
                .putString("font_size_chat", String.valueOf(fc))
                .putString("font_size_input", String.valueOf(fi))
                .apply();
            
            Toast.makeText(this, "已保存: " + name, Toast.LENGTH_SHORT).show();
            
            if (editingServerName != null) {
                finish();
            } else {
                displayServerList();
            }
            
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void displayAvatars() {
        avatarContainer.removeAllViews();
        
        for (final String avatar : avatars) {
            Button avatarBtn = new Button(this);
            avatarBtn.setText(avatar);
            avatarBtn.setTextSize(28);
            avatarBtn.setPadding(16, 12, 16, 12);
            avatarBtn.setBackgroundColor(avatar.equals(currentAvatar) && currentAvatarImagePath == null ? 
                Color.parseColor("#BBDEFB") : Color.WHITE);
            
            avatarBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentAvatar = avatar;
                    currentAvatarImagePath = null; // 清除自定义头像
                    displayAvatars();
                    // 更新预览
                    avatarPreview.setImageDrawable(null);
                    avatarPreview.setPadding(20, 20, 20, 20);
                    avatarPreview.setBackgroundColor(Color.parseColor("#F5F5F5"));
                }
            });
            
            avatarContainer.addView(avatarBtn);
        }
    }
    
    private void loadServerList() {
        try {
            String json = prefs.getString("saved_servers", "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                serverList.add(arr.getJSONObject(i));
            }
        } catch (Exception e) {}
    }
    
    private void saveServerList() {
        try {
            JSONArray arr = new JSONArray();
            for (JSONObject obj : serverList) {
                arr.put(obj);
            }
            prefs.edit().putString("saved_servers", arr.toString()).apply();
        } catch (Exception e) {}
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // 图片选择处理
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                handleSelectedImage(imageUri);
            }
        }
        
        // 导出配置到文件
        if (requestCode == REQUEST_EXPORT && resultCode == RESULT_OK && data != null) {
            try {
                Uri uri = data.getData();
                java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                if (os != null) {
                    os.write(exportConfigData.getBytes());
                    os.close();
                    Toast.makeText(this, "✅ 配置已导出!", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "❌ 导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
        
        // 从文件导入配置
        if (requestCode == REQUEST_IMPORT && resultCode == RESULT_OK && data != null) {
            try {
                Uri uri = data.getData();
                java.io.InputStream is = getContentResolver().openInputStream(uri);
                if (is != null) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    is.close();
                    
                    JSONObject config = new JSONObject(sb.toString());
                    
                    // 确认导入
                    new AlertDialog.Builder(this)
                        .setTitle("📥 确认导入")
                        .setMessage("将导入以下配置:\n\n• 服务器列表\n• 当前服务器\n• 超时设置\n• 服务器类型\n\n⚠️ 这将直接覆盖现有服务器配置，是否继续？")
                        .setPositiveButton("覆盖导入", (dialog, which) -> {
                            try {
                                applyImportedConfig(config);
                                Toast.makeText(this, "✅ 配置导入成功！\n请重启应用以生效", Toast.LENGTH_LONG).show();
                            } catch (Exception e) {
                                Toast.makeText(this, "❌ 导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "❌ 读取失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void displayServerList() {
        if (serverListContainer == null) return;
        
        serverListContainer.removeAllViews();
        
        if (serverList.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无保存的服务器");
            empty.setTextColor(Color.GRAY);
            empty.setPadding(0, 20, 0, 20);
            serverListContainer.addView(empty);
            return;
        }
        
        String currentName = prefs.getString("current_server_name", "");
        
        for (int i = 0; i < serverList.size(); i++) {
            final JSONObject server = serverList.get(i);
            try {
                final String name = server.getString("name");
                String serverUrl = server.getString("server");
                String avatar = server.optString("avatar", "🤖");
                final String avatarImage = server.optString("avatar_image", "");
                final boolean isCurrent = name.equals(currentName);
                
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.HORIZONTAL);
                item.setBackgroundColor(Color.WHITE);
                item.setPadding(20, 16, 20, 16);
                ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.topMargin = 12;
                item.setLayoutParams(params);
                
                // 头像显示
                if (!avatarImage.isEmpty()) {
                    // 显示自定义头像图片
                    ImageView avatarImgView = new ImageView(this);
                    avatarImgView.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
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
                    avatarView.setTextSize(32);
                    avatarView.setPadding(0, 0, 16, 0);
                    item.addView(avatarView);
                }
                
                TextView info = new TextView(this);
                String ct = server.optString("connect_timeout", "30");
                String rt = server.optString("read_timeout", "120");
                info.setText(name + "\n超时:" + ct + "s/" + rt + "s");
                info.setTextSize(14);
                info.setTextColor(Color.parseColor("#1A1A1A"));
                info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                
                if (isCurrent) {
                    info.setText(info.getText() + " ✅");
                }
                
                item.addView(info);
                
                item.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(SettingsActivity.this, SettingsActivity.class);
                        intent.putExtra("edit_server", name);
                        startActivity(intent);
                    }
                });
                
                item.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        serverList.remove(server);
                        saveServerList();
                        displayServerList();
                        Toast.makeText(SettingsActivity.this, "已删除: " + name, Toast.LENGTH_SHORT).show();
                        return true;
                    }
                });
                
                serverListContainer.addView(item);
                
            } catch (Exception e) {}
        }
    }
    
    // 导出配置到文件（只导出服务器基础参数）
    private void exportConfig() {
        try {
            JSONObject config = new JSONObject();
            
            // 只导出服务器相关的基础参数
            String[] serverKeys = {
                "saved_servers",
                "current_server_name", 
                "server_url",
                "auth_token",
                "connect_timeout",
                "read_timeout",
                "server_type"
            };
            
            for (String key : serverKeys) {
                String value = prefs.getString(key, null);
                if (value != null) {
                    config.put(key, value);
                }
            }
            
            // 添加版本标记
            config.put("_export_version", "ChatApp v3.4");
            config.put("_export_time", System.currentTimeMillis());
            
            // 使用 SAF 创建文件
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(android.content.Intent.EXTRA_TITLE, "chatapp_servers.json");
            exportConfigData = config.toString(2);
            startActivityForResult(intent, REQUEST_EXPORT);
            
        } catch (Exception e) {
            Toast.makeText(this, "❌ 导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    // 导入配置
    private void importConfig() {
        try {
            // 使用 SAF 打开文件
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            startActivityForResult(intent, REQUEST_IMPORT);
            
        } catch (Exception e) {
            Toast.makeText(this, "❌ 打开文件选择器失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    // 应用导入的配置
    private void applyImportedConfig(JSONObject config) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            
            // 排除元数据字段
            String[] metaFields = {"_export_version", "_export_time"};
            
            @SuppressWarnings("unchecked")
            Iterator<String> keys = config.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                
                // 跳过元数据
                boolean isMeta = false;
                for (String meta : metaFields) {
                    if (meta.equals(key)) {
                        isMeta = true;
                        break;
                    }
                }
                if (isMeta) continue;
                
                Object value = config.get(key);
                if (value instanceof String) {
                    editor.putString(key, (String) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                } else if (value instanceof Long) {
                    editor.putLong(key, (Long) value);
                } else if (value instanceof Float) {
                    editor.putFloat(key, ((Number) value).floatValue());
                }
            }
            
            editor.apply();
            
            Toast.makeText(this, "✅ 配置导入成功！\n请重启应用以生效", Toast.LENGTH_LONG).show();
            
        } catch (Exception e) {
            Toast.makeText(this, "❌ 应用配置失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}