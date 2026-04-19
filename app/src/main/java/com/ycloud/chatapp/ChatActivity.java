package com.ycloud.chatapp;
import android.view.ViewTreeObserver;
import android.graphics.Rect;
import android.graphics.Rect;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import java.io.File;
import android.net.Uri;
import android.provider.MediaStore;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.ImageButton;
import java.io.ByteArrayOutputStream;
import android.media.MediaRecorder;
import android.media.MediaPlayer;
import java.io.IOException;
import android.media.MediaRecorder;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import android.net.Uri;
import android.provider.MediaStore;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.ImageButton;
import java.io.ByteArrayOutputStream;
import android.media.MediaRecorder;
import android.media.MediaPlayer;
import java.io.IOException;
import android.media.MediaRecorder;
import android.Manifest;
import android.content.pm.PackageManager;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatActivity extends Activity {
    private LinearLayout chatContainer;
    private EditText inputText;
    private ScrollView scrollView;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    private String serverName;
    private String serverUrl;
    private String authToken;
    private String avatar;
    private String serverType = "openclaw";
    private String avatarImage;
    private List<JSONObject> conversationHistory = new ArrayList<>();
    private SharedPreferences prefs;
    private int connectTimeout = 30000;
    private int readTimeout = 120000;
    private boolean isSending = false;
    private static final int PICK_IMAGE_REQUEST = 200;
    private String pendingImagePath;
    private MediaRecorder mediaRecorder;
    private String audioFilePath;
    private boolean isRecording = false;
    private ImageButton micBtn;
    
    private int chatTextSize = 14;
    private int titleTextSize = 16;
    private int inputTextSize = 14;
    
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    
    private String getHistoryFileName() {
        String safeName = serverName.replaceAll("[^a-zA-Z0-9]", "_");
        return "chat_history_" + safeName + ".json";
    }
    
    private String getPendingFileName() {
        String safeName = serverName.replaceAll("[^a-zA-Z0-9]", "_");
        return "pending_" + safeName + ".json";
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        serverName = getIntent().getStringExtra("server_name");
        serverUrl = getIntent().getStringExtra("server_url");
        authToken = getIntent().getStringExtra("auth_token");
        avatar = getIntent().getStringExtra("avatar");
        avatarImage = getIntent().getStringExtra("avatar_image");
        if (avatar == null) avatar = "🤖";
        
        if (serverName == null) {
            finish();
            return;
        }
        
        prefs = getSharedPreferences("chat_settings", MODE_PRIVATE);
        loadTimeoutFromServer();
        loadFontSize();
        initUI();
        loadHistory();
        displayMessages();
        checkPendingMessage();
    }
    
    private void loadTimeoutFromServer() {
        try {
            String json = prefs.getString("saved_servers", "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                if (arr.getJSONObject(i).optString("name", "").equals(serverName)) {
                    connectTimeout = arr.getJSONObject(i).optInt("connect_timeout", 30) * 1000;
                    readTimeout = arr.getJSONObject(i).optInt("read_timeout", 120) * 1000;
                    serverType = arr.getJSONObject(i).optString("server_type", "openclaw");
                    break;
                }
            }
        } catch (Exception e) {}
    }
    
    // 根据服务器类型获取模型名称
    private String getModelName() {
        if ("hermes".equals(serverType)) {
            return "hermes-agent";
        } else if ("custom".equals(serverType)) {
            // 自定义类型使用默认模型名（可以后续扩展为可配置）
            return "gpt-3.5-turbo";
        } else {
            // OpenClaw
            return "openclaw:main";
        }
    }
    
    private void loadFontSize() {
        try {
            chatTextSize = Integer.parseInt(prefs.getString("font_size_chat", "14"));
            inputTextSize = Integer.parseInt(prefs.getString("font_size_input", "14"));
            titleTextSize = chatTextSize + 2;
        } catch (Exception e) {}
    }
    
    private void checkPendingMessage() {
        try {
            File pendingFile = new File(getFilesDir(), getPendingFileName());
            if (pendingFile.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(pendingFile));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                
                String pendingContent = sb.toString();
                if (!pendingContent.isEmpty()) {
                    final String content = pendingContent;
                    addMessage("你", content, timeFormat.format(new Date()));
                    sendMessage(content);
                }
                pendingFile.delete();
            }
        } catch (Exception e) {}
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (isSending) {
            savePendingMessage("");
        }
    }
    
    private void savePendingMessage(String content) {
        try {
            File pendingFile = new File(getFilesDir(), getPendingFileName());
            FileWriter fw = new FileWriter(pendingFile);
            fw.write(content);
            fw.close();
        } catch (Exception e) {}
    }
    
    private void clearPendingMessage() {
        try {
            File pendingFile = new File(getFilesDir(), getPendingFileName());
            if (pendingFile.exists()) {
                pendingFile.delete();
            }
        } catch (Exception e) {}
    }
    
    private void initUI() {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.rgb(250, 250, 250));
        
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(Color.rgb(100, 149, 237));
        topBar.setPadding(24, 56, 24, 24);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        
        ImageButton backBtn = new ImageButton(this);
        backBtn.setImageResource(android.R.drawable.ic_menu_revert);
        backBtn.setBackgroundColor(Color.TRANSPARENT);
        backBtn.setColorFilter(Color.WHITE);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        
        // 头像
        ImageView avatarView = new ImageView(this);
        int avSize = 56;
        LinearLayout.LayoutParams avParams = new LinearLayout.LayoutParams(avSize, avSize);
        avParams.gravity = Gravity.CENTER_VERTICAL;
        avatarView.setLayoutParams(avParams);
        avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        
        // 加载头像
        loadServerAvatar(serverName, avatarView);
        
        TextView title = new TextView(this);
        title.setText(" " + serverName);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, titleTextSize + 2);
        title.setTextColor(Color.WHITE);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        
        ImageButton settingsBtn = new ImageButton(this);
        settingsBtn.setImageResource(android.R.drawable.ic_menu_preferences);
        settingsBtn.setBackgroundColor(Color.TRANSPARENT);
        settingsBtn.setColorFilter(Color.WHITE);
        settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.content.Intent intent = new android.content.Intent(ChatActivity.this, SettingsActivity.class);
                intent.putExtra("edit_server", serverName);
                startActivity(intent);
            }
        });
        
        topBar.addView(backBtn);
        topBar.addView(avatarView);
        topBar.addView(title);
        topBar.addView(settingsBtn);
        mainLayout.addView(topBar);
        
        scrollView = new ScrollView(this);
        chatContainer = new LinearLayout(this);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        chatContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        chatContainer.setPadding(8, 16, 8, 16);
        scrollView.addView(chatContainer);
        mainLayout.addView(scrollView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        
        LinearLayout inputBar = new LinearLayout(this);
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        inputBar.setBackgroundColor(Color.WHITE);
        inputBar.setPadding(16, 12, 16, 12);
        inputBar.setGravity(Gravity.CENTER_VERTICAL);
        
        inputText = new EditText(this);
        inputText.setHint("输入消息...");
        inputText.setHintTextColor(Color.rgb(136, 136, 136));
        inputText.setTextSize(TypedValue.COMPLEX_UNIT_SP, inputTextSize);
        inputText.setTextColor(Color.rgb(26, 26, 26));
        inputText.setPadding(24, 20, 24, 20);
        // 灰色圆角边框
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setCornerRadius(20);
        inputBg.setColor(Color.WHITE);
        inputBg.setStroke(2, Color.rgb(200, 200, 200));
        inputText.setBackground(inputBg);
        inputText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        
        // 设置可聚焦
        inputText.setFocusable(true);
        inputText.setFocusableInTouchMode(true);
        
        // 点击输入框时直接弹出键盘
        inputText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                inputText.requestFocus();
                // 强制显示键盘
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.showSoftInput(inputText, InputMethodManager.SHOW_FORCED);
            }
        });
        
        // 页面加载完成后自动聚焦输入框（短暂延迟确保界面就绪）
        inputText.postDelayed(new Runnable() {
            @Override
            public void run() {
                inputText.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.showSoftInput(inputText, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 300);
        
        
        // 表情按钮
        TextView emojiBtn = new TextView(this);
        emojiBtn.setText("😊");
        emojiBtn.setTextSize(24);
        emojiBtn.setBackgroundColor(Color.TRANSPARENT);
        emojiBtn.setPadding(8, 6, 8, 6);
        
        // 表情面板
        final LinearLayout emojiContainer = new LinearLayout(this);
        emojiContainer.setOrientation(LinearLayout.VERTICAL);
        emojiContainer.setVisibility(View.GONE);
        
        ScrollView emojiScroll = new ScrollView(this);
        LinearLayout emojiGrid = new LinearLayout(this);
        emojiGrid.setOrientation(LinearLayout.VERTICAL);
        
        String[] rows = {
            "😀 😃 😄 😁 😅 😂 👍 ❤️",
            "😍 🥰 😘 😙 😚 😋 😛 😜", 
            "🤔 🤨 😐 😑 😶 😏",
            "😌 😔 😪 😴 🤒 🤕",
            "👍 👎 👌 ✌️ 🤞 🤘",
            "❤️ 🧡 💛 💚 💙 💜"
        };
        
        for (final String row : rows) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            String[] items = row.split(" ");
            for (final String item : items) {
                TextView btn = new TextView(this);
                btn.setText(item);
                btn.setTextSize(22);
                btn.setPadding(12, 8, 12, 8);
                btn.setBackgroundColor(Color.TRANSPARENT);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        inputText.setText(inputText.getText().toString() + item + " ");
                        inputText.setSelection(inputText.getText().length());
                    }
                });
                rowLayout.addView(btn);
            }
            emojiGrid.addView(rowLayout);
        }
        
        emojiScroll.addView(emojiGrid);
        emojiScroll.setBackgroundColor(Color.rgb(250, 250, 250));
        emojiContainer.addView(emojiScroll);
        
        emojiBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (emojiContainer.getVisibility() == View.VISIBLE) {
                    emojiContainer.setVisibility(View.GONE);
                } else {
                    emojiContainer.setVisibility(View.VISIBLE);
                }
            }
        });
        
        // 输入框添加到inputBar
        inputBar.addView(inputText);
        
        // 发送按钮 - 使用图标
        ImageButton sendBtn = new ImageButton(this);
        sendBtn.setImageResource(android.R.drawable.ic_menu_send);
        sendBtn.setBackgroundColor(Color.TRANSPARENT);
        sendBtn.setColorFilter(Color.WHITE);
        int sendSize = 78;
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(sendSize, sendSize);
        sendParams.gravity = Gravity.CENTER_VERTICAL;
        sendParams.leftMargin = 12;
        sendBtn.setLayoutParams(sendParams);
        GradientDrawable sendBg = new GradientDrawable();
        sendBg.setCornerRadius(sendSize/2);
        sendBg.setColor(Color.rgb(76, 175, 80)); // 绿色
        sendBtn.setBackground(sendBg);
        
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String msg = inputText.getText().toString().trim();
                if (!msg.isEmpty() && !isSending) {
                    inputText.setText("");
                    
                    addMessage("你", msg, timeFormat.format(new Date()));
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            scrollView.fullScroll(View.FOCUS_DOWN);
                        }
                    }, 300);
                    sendMessage(msg);
                }
            }
        });
        
        inputBar.addView(sendBtn);
        mainLayout.addView(inputBar);
        
        // 功能按钮栏 - 放在输入框下方
        LinearLayout funcBar = new LinearLayout(this);
        funcBar.setOrientation(LinearLayout.HORIZONTAL);
        funcBar.setBackgroundColor(Color.rgb(245, 245, 245));
        funcBar.setPadding(16, 8, 16, 8);
        funcBar.setGravity(Gravity.CENTER_VERTICAL);
        
        // 表情按钮
        funcBar.addView(emojiBtn);
        
        // 图片按钮
        ImageButton imgBtn = new ImageButton(this);
        imgBtn.setImageResource(android.R.drawable.ic_menu_gallery);
        imgBtn.setBackgroundColor(Color.TRANSPARENT);
        imgBtn.setColorFilter(Color.rgb(100, 149, 237));
        imgBtn.setPadding(8, 4, 8, 4);
        imgBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, PICK_IMAGE_REQUEST);
            }
        });
        funcBar.addView(imgBtn);
        
        // 麦克风按钮
        micBtn = new ImageButton(this);
        micBtn.setImageResource(android.R.drawable.ic_btn_speak_now);
        micBtn.setBackgroundColor(Color.TRANSPARENT);
        micBtn.setColorFilter(Color.rgb(100, 149, 237));
        micBtn.setPadding(8, 4, 8, 4);
        micBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRecording) {
                    stopRecording();
                } else {
                    startRecording();
                }
            }
        });
        funcBar.addView(micBtn);
        
        // 添加占位View让按钮靠左
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1));
        funcBar.addView(spacer);
        
        mainLayout.addView(funcBar);
        
        mainLayout.addView(emojiContainer);
        
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        
        // 监听键盘状态，弹出后聚焦输入框
        final View contentView = findViewById(android.R.id.content);
        contentView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            private int previousHeight = 0;
            @Override
            public void onGlobalLayout() {
                Rect r = new Rect();
                contentView.getWindowVisibleDisplayFrame(r);
                int screenHeight = contentView.getRootView().getHeight();
                int keypadHeight = screenHeight - r.bottom;
                
                if (keypadHeight > screenHeight * 0.15) {
                    // 键盘弹出，聚焦输入框
                    if (previousHeight <= screenHeight * 0.15) {
                        inputText.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                inputText.requestFocus();
                                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                                imm.showSoftInput(inputText, InputMethodManager.SHOW_IMPLICIT);
                            }
                        }, 100);
                    }
                }
                previousHeight = keypadHeight;
            }
        });
        
        setContentView(mainLayout);
        
        // 键盘监听 - 检测键盘显示后滚动
        final ViewTreeObserver.OnGlobalLayoutListener layoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            private boolean wasKeyboardOpen = false;
            @Override
            public void onGlobalLayout() {
                Rect r = new Rect();
                mainLayout.getWindowVisibleDisplayFrame(r);
                int screenHeight = mainLayout.getRootView().getHeight();
                int keyboardHeight = screenHeight - r.bottom;
                boolean isKeyboardOpen = keyboardHeight > screenHeight * 0.15;
                if (isKeyboardOpen && !wasKeyboardOpen) {
                    // 键盘刚打开时滚动
                    wasKeyboardOpen = true;
                    scrollView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            scrollView.fullScroll(View.FOCUS_DOWN);
                        }
                    }, 300);
                } else if (!isKeyboardOpen) {
                    wasKeyboardOpen = false;
                }
            }
        };
        mainLayout.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        
    }
        
    private void displayMessages() {
        chatContainer.removeAllViews();
        
        if (conversationHistory.isEmpty()) {
            addMessage("助手", "你好！我是云头转向 AI\n现在开始对话吧！👋", timeFormat.format(new Date()));
        } else {
            for (JSONObject msg : conversationHistory) {
                try {
                    String role = msg.getString("role");
                    String content = msg.getString("content");
                    String time = msg.optString("time", timeFormat.format(new Date()));
                    addMessage(role.equals("user") ? "你" : "助手", content, time);
                } catch (Exception e) {}
            }
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadTimeoutFromServer();
        loadFontSize();
    }
    
    private void loadHistory() {
        conversationHistory.clear();
        try {
            File file = new File(getFilesDir(), getHistoryFileName());
            if (file.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(file));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONArray arr = new JSONArray(sb.toString());
                for (int i = 0; i < arr.length(); i++) {
                    conversationHistory.add(arr.getJSONObject(i));
                }
            }
        } catch (Exception e) {}
    }
    
    private void saveHistory() {
        try {
            JSONArray arr = new JSONArray();
            for (JSONObject msg : conversationHistory) {
                arr.put(msg);
            }
            File file = new File(getFilesDir(), getHistoryFileName());
            FileWriter fw = new FileWriter(file);
            fw.write(arr.toString());
            fw.close();
        } catch (Exception e) {}
    }
    

    private void addMessage(String sender, String content, String time) {
        // 调试：确保content不为空
        if (content == null) {
            content = "[null]";
        }
        
        LinearLayout msgContainer = new LinearLayout(this);
        msgContainer.setOrientation(LinearLayout.VERTICAL);
        msgContainer.setGravity(Gravity.CENTER);  // 全部居中
        
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        containerParams.bottomMargin = 16;
        msgContainer.setLayoutParams(containerParams);
        
        TextView metaView = new TextView(this);
        metaView.setText(sender + "  " + time);
        metaView.setTextSize(TypedValue.COMPLEX_UNIT_SP, chatTextSize - 3);
        metaView.setTextColor(Color.rgb(102, 119, 129));
        metaView.setGravity(Gravity.CENTER);
        metaView.setPadding(0, 8, 0, 8);
        msgContainer.addView(metaView);
        
        TextView bubble = new TextView(this);
        bubble.setTextIsSelectable(true);
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, chatTextSize);
        bubble.setPadding(20, 16, 20, 16);
        bubble.setGravity(Gravity.START);
        
        // 圆角灰色边框气泡
        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setCornerRadius(16f);
        bubbleBg.setStroke(2, Color.rgb(180, 180, 180));
        if (sender.equals("你")) {
            bubbleBg.setColor(Color.rgb(220, 220, 220));
        } else {
            bubbleBg.setColor(Color.WHITE);
        }
        bubble.setBackground(bubbleBg);
        bubble.setTextColor(Color.rgb(26, 26, 26));
        
        // 无论用户还是助手，都直接设置文本
        String displayText = content;
        // 如果是图片消息，显示图片
        if (content.startsWith("data:image") || content.contains("base64")) {
            try {
                String base64Data = content;
                if (content.contains(",")) {
                    base64Data = content.split(",")[1];
                }
                byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                bubble.setCompoundDrawablesWithIntrinsicBounds(null, null, null, new android.graphics.drawable.BitmapDrawable(getResources(), bmp));
                bubble.setText("");
                bubble.setHeight(200);
            } catch (Exception e) {
                bubble.setText(displayText);
            }
        } else {
            bubble.setText(displayText);
        }
        
        // 助手消息需要代码高亮
        if (!sender.equals("你") && content != null && content.length() > 0) {
            bubble.setText(formatContent(content));
        }
        
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
            (int) (getResources().getDisplayMetrics().widthPixels * 0.96),
            LinearLayout.LayoutParams.WRAP_CONTENT);
        bubble.setLayoutParams(bubbleParams);
        
        msgContainer.addView(bubble);
        chatContainer.addView(msgContainer);
        
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        }, 300);
    }
    
    private CharSequence formatContent(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        SpannableStringBuilder ssb = new SpannableStringBuilder(content);
        
        Pattern inlinePattern = Pattern.compile("`([^`]+)`");
        Matcher inlineMatcher = inlinePattern.matcher(content);
        while (inlineMatcher.find()) {
            int offset = 4; // "助手: "的长度
            ssb.setSpan(new android.text.style.TypefaceSpan("monospace"),
                inlineMatcher.start() + offset, inlineMatcher.end() + offset, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(Color.rgb(233, 30, 99)),
                inlineMatcher.start() + offset, inlineMatcher.end() + offset, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        return ssb;
    }
    
    private void sendMessage(final String content) {
        isSending = true;
        savePendingMessage(content);
        
        final TextView loading = new TextView(this);
        loading.setText("正在输入...");
        loading.setTextSize(TypedValue.COMPLEX_UNIT_SP, chatTextSize - 2);
        loading.setTextColor(Color.GRAY);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(20, 12, 20, 12);
        chatContainer.addView(loading);
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    JSONArray messages = new JSONArray();
                    
                    int start = Math.max(0, conversationHistory.size() - 20);
                    for (int i = start; i < conversationHistory.size(); i++) {
                        messages.put(conversationHistory.get(i));
                    }
                    
                    JSONObject userMsg = new JSONObject();
                    userMsg.put("role", "user");
                    userMsg.put("content", content);
                    messages.put(userMsg);
                    
                    URL url = new URL(serverUrl + "/v1/chat/completions");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setRequestProperty("Authorization", "Bearer " + authToken);
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(connectTimeout);
                    conn.setReadTimeout(readTimeout);
                    
                    JSONObject json = new JSONObject();
                    json.put("model", getModelName());
                    json.put("messages", messages);
                    
                    OutputStream os = conn.getOutputStream();
                    os.write(json.toString().getBytes("UTF-8"));
                    os.close();
                    
                    final int responseCode = conn.getResponseCode();
                    final String currentTime = timeFormat.format(new Date());
                    
                    if (responseCode == 200) {
                        BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();
                        
                        final JSONObject result = new JSONObject(sb.toString());
                        String reply = result.getJSONArray("choices")
                            .getJSONObject(0).getJSONObject("message")
                            .getString("content");
                        
                        conversationHistory.add(new JSONObject("{\"role\":\"user\",\"content\":\"" + escapeJson(content) + "\",\"time\":\"" + currentTime + "\"}"));
                        conversationHistory.add(new JSONObject("{\"role\":\"assistant\",\"content\":\"" + escapeJson(reply) + "\",\"time\":\"" + currentTime + "\"}"));
                        saveHistory();
                        clearPendingMessage();
                        
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                isSending = false;
                                chatContainer.removeView(loading);
                                addMessage("助手", reply, currentTime);
                            }
                        });
                    } else {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                isSending = false;
                                clearPendingMessage();
                                chatContainer.removeView(loading);
                                addMessage("错误", "响应码: " + responseCode, currentTime);
                            }
                        });
                    }
                    
                } catch (final Exception e) {
                    final String currentTime = timeFormat.format(new Date());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            isSending = false;
                            clearPendingMessage();
                            chatContainer.removeView(loading);
                            addMessage("错误", e.getMessage(), currentTime);
                        }
                    });
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }
    
    private void loadServerAvatar(String serverName, ImageView avatarView) {
        // 首先尝试使用传递过来的avatarImage路径
        if (avatarImage != null && !avatarImage.isEmpty()) {
            File f = new File(getFilesDir(), avatarImage);
            if (f.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (bmp != null) {
                    avatarView.setImageBitmap(bmp);
                    return;
                }
            }
        }
        
        // 从保存的服务器列表中查找
        try {
            String json = prefs.getString("saved_servers", "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject s = arr.getJSONObject(i);
                if (s.optString("name", "").equals(serverName)) {
                    String avatarImg = s.optString("avatar_image", "");
                    if (avatarImg != null && !avatarImg.isEmpty()) {
                        File f = new File(getFilesDir(), avatarImg);
                        if (f.exists()) {
                            Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                            if (bmp != null) {
                                avatarView.setImageBitmap(bmp);
                                return;
                            }
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {}
        // 默认显示emoji头像
        avatarView.setImageResource(android.R.drawable.ic_menu_myplaces);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            try {
                Bitmap bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                // 压缩图片
                Bitmap scaled = Bitmap.createScaledBitmap(bmp, 400, 400 * bmp.getHeight() / bmp.getWidth(), true);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.JPEG, 70, bos);
                byte[] bytes = bos.toByteArray();
                String base64 = Base64.encodeToString(bytes, Base64.DEFAULT);
                pendingImagePath = "data:image/jpeg;base64," + base64;
                // 发送图片消息
                addMessage("你", "[图片]", timeFormat.format(new Date()));
                sendImageMessage(pendingImagePath);
            } catch (Exception e) {
                addMessage("错误", "选择图片失败: " + e.getMessage(), timeFormat.format(new Date()));
            }
        }
    }
    
    private void sendImageMessage(final String imageData) {
        isSending = true;
        final TextView loading = new TextView(this);
        loading.setText("发送中...");
        loading.setTextSize(TypedValue.COMPLEX_UNIT_SP, chatTextSize - 2);
        loading.setTextColor(Color.GRAY);
        chatContainer.addView(loading);
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    JSONArray messages = new JSONArray();
                    int start = Math.max(0, conversationHistory.size() - 20);
                    for (int i = start; i < conversationHistory.size(); i++) {
                        messages.put(conversationHistory.get(i));
                    }
                    
                    JSONObject userMsg = new JSONObject();
                    userMsg.put("role", "user");
                    userMsg.put("content", "[用户发送了一张图片]");
                    messages.put(userMsg);
                    
                    URL url = new URL(serverUrl + "/v1/chat/completions");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setRequestProperty("Authorization", "Bearer " + authToken);
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(connectTimeout);
                    conn.setReadTimeout(readTimeout);
                    
                    JSONObject json = new JSONObject();
                    json.put("model", getModelName());
                    json.put("messages", messages);
                    
                    OutputStream os = conn.getOutputStream();
                    os.write(json.toString().getBytes("UTF-8"));
                    os.close();
                    
                    final int responseCode = conn.getResponseCode();
                    final String currentTime = timeFormat.format(new Date());
                    
                    if (responseCode == 200) {
                        BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();
                        
                        final JSONObject result = new JSONObject(sb.toString());
                        String reply = result.getJSONArray("choices")
                            .getJSONObject(0).getJSONObject("message")
                            .getString("content");
                        
                        conversationHistory.add(new JSONObject("{\"role\":\"user\",\"content\":\"[图片]\",\"time\":\"" + currentTime + "\"}"));
                        conversationHistory.add(new JSONObject("{\"role\":\"assistant\",\"content\":\"" + escapeJson(reply) + "\",\"time\":\"" + currentTime + "\"}"));
                        saveHistory();
                        
                        final String finalReply = reply;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                isSending = false;
                                chatContainer.removeView(loading);
                                addMessage("助手", finalReply, currentTime);
                            }
                        });
                    } else {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                isSending = false;
                                chatContainer.removeView(loading);
                                addMessage("错误", "响应码: " + responseCode, currentTime);
                            }
                        });
                    }
                } catch (final Exception e) {
                    final String currentTime = timeFormat.format(new Date());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            isSending = false;
                            chatContainer.removeView(loading);
                            addMessage("错误", e.getMessage(), currentTime);
                        }
                    });
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }
    
    private void startRecording() {
        audioFilePath = getFilesDir().getAbsolutePath() + "/voice_" + System.currentTimeMillis() + ".mp4";
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setOutputFile(audioFilePath);
        
        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            micBtn.setColorFilter(Color.RED);
            addMessage("系统", "正在录音...点击停止", timeFormat.format(new Date()));
        } catch (IOException e) {
            addMessage("错误", "录音失败: " + e.getMessage(), timeFormat.format(new Date()));
        }
    }
    
    private void stopRecording() {
        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
            micBtn.setColorFilter(Color.rgb(100, 149, 237));
            
            // 发送语音
            addMessage("你", "[语音]", timeFormat.format(new Date()));
            sendVoiceMessage(audioFilePath);
        } catch (Exception e) {
            addMessage("错误", "停止录音失败: " + e.getMessage(), timeFormat.format(new Date()));
        }
    }
    
    private void sendVoiceMessage(final String filePath) {
        isSending = true;
        final TextView loading = new TextView(this);
        loading.setText("发送中...");
        loading.setTextSize(TypedValue.COMPLEX_UNIT_SP, chatTextSize - 2);
        loading.setTextColor(Color.GRAY);
        chatContainer.addView(loading);
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    JSONArray messages = new JSONArray();
                    int start = Math.max(0, conversationHistory.size() - 20);
                    for (int i = start; i < conversationHistory.size(); i++) {
                        messages.put(conversationHistory.get(i));
                    }
                    
                    JSONObject userMsg = new JSONObject();
                    userMsg.put("role", "user");
                    userMsg.put("content", "[用户发送了一段语音]");
                    messages.put(userMsg);
                    
                    URL url = new URL(serverUrl + "/v1/chat/completions");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setRequestProperty("Authorization", "Bearer " + authToken);
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(connectTimeout);
                    conn.setReadTimeout(readTimeout);
                    
                    JSONObject json = new JSONObject();
                    json.put("model", getModelName());
                    json.put("messages", messages);
                    
                    OutputStream os = conn.getOutputStream();
                    os.write(json.toString().getBytes("UTF-8"));
                    os.close();
                    
                    final int responseCode = conn.getResponseCode();
                    final String currentTime = timeFormat.format(new Date());
                    
                    if (responseCode == 200) {
                        BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();
                        
                        final JSONObject result = new JSONObject(sb.toString());
                        String reply = result.getJSONArray("choices")
                            .getJSONObject(0).getJSONObject("message")
                            .getString("content");
                        
                        conversationHistory.add(new JSONObject("{\"role\":\"user\",\"content\":\"[语音]\",\"time\":\"" + currentTime + "\"}"));
                        conversationHistory.add(new JSONObject("{\"role\":\"assistant\",\"content\":\"" + escapeJson(reply) + "\",\"time\":\"" + currentTime + "\"}"));
                        saveHistory();
                        
                        final String finalReply = reply;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                isSending = false;
                                chatContainer.removeView(loading);
                                addMessage("助手", finalReply, currentTime);
                            }
                        });
                    } else {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                isSending = false;
                                chatContainer.removeView(loading);
                                addMessage("错误", "响应码: " + responseCode, currentTime);
                            }
                        });
                    }
                } catch (final Exception e) {
                    final String currentTime = timeFormat.format(new Date());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            isSending = false;
                            chatContainer.removeView(loading);
                            addMessage("错误", e.getMessage(), currentTime);
                        }
                    });
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }
    
    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
