package com.ycloud.chatapp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.ycloud.chatapp.model.Group;
import com.ycloud.chatapp.model.Member;
import com.ycloud.chatapp.model.Message;
import com.ycloud.chatapp.service.GroupManager;
import com.ycloud.chatapp.service.MemberDispatcher;
import com.ycloud.chatapp.service.MessageStorage;
import com.ycloud.chatapp.service.PromptBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 群聊界面 - 核心功能
 */
public class GroupChatActivity extends Activity {
    private String groupId;
    private Group group;
    private GroupManager groupManager;
    private MessageStorage messageStorage;
    private PromptBuilder promptBuilder;
    private MemberDispatcher dispatcher;
    private List<Message> messages = new ArrayList<>();
    
    private LinearLayout msgContainer;
    private EditText input;
    private ScrollView scrollView;
    private Button sendBtn;
    private TextView modeIndicator;
    
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isLoading = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        groupId = getIntent().getStringExtra("group_id");
        if (groupId == null) {
            finish();
            return;
        }
        
        SharedPreferences prefs = getSharedPreferences("chat_settings", MODE_PRIVATE);
        groupManager = new GroupManager(prefs);
        messageStorage = new MessageStorage(this);
        promptBuilder = new PromptBuilder();
        dispatcher = new MemberDispatcher();
        
        group = groupManager.getGroup(groupId);
        if (group == null) {
            finish();
            return;
        }
        
        // 加载历史消息
        messages = messageStorage.loadHistory(groupId);
        
        // 检查是否需要自我介绍
        checkAndRequestIntroductions();
        
        // 构建界面
        buildUI();
        
        // 显示历史消息
        displayMessages();
    }
    
    private void checkAndRequestIntroductions() {
        // 检查是否有成员未自我介绍
        Member needIntro = groupManager.getMemberNeedingIntro(groupId);
        if (needIntro != null) {
            // 请求自我介绍
            final Member finalNeedIntro = needIntro;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Message introMsg = dispatcher.requestIntroduction(finalNeedIntro, group, promptBuilder);
                    
                    // 保存自我介绍
                    groupManager.updateIntroduction(groupId, finalNeedIntro.getName(), introMsg.getContent());
                    
                    // 添加到历史
                    messages.add(introMsg);
                    messageStorage.addMessage(groupId, introMsg);
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            displaySingleMessage(introMsg);
                            scrollToBottom();
                        }
                    });
                }
            }).start();
        }
    }
    
    private void buildUI() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#FAFAFA"));
        
        // 顶部栏
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setBackgroundColor(Color.parseColor("#2196F3"));
        topBar.setPadding(24, 48, 24, 16);
        
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        
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
        
        LinearLayout titleLayout = new LinearLayout(this);
        titleLayout.setOrientation(LinearLayout.VERTICAL);
        titleLayout.setLayoutParams(new LinearLayout.LayoutParams(0, 
            LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        
        TextView title = new TextView(this);
        title.setText("👥 " + group.getName());
        title.setTextSize(18);
        title.setTextColor(Color.WHITE);
        titleLayout.addView(title);
        
        modeIndicator = new TextView(this);
        modeIndicator.setText(group.getModeName() + " · " + group.getMembers().size() + "人");
        modeIndicator.setTextSize(12);
        modeIndicator.setTextColor(Color.parseColor("#DDFFFFFF"));
        titleLayout.addView(modeIndicator);
        
        topRow.addView(backBtn);
        topRow.addView(titleLayout);
        topBar.addView(topRow);
        
        // 显示成员头像行
        LinearLayout memberRow = new LinearLayout(this);
        memberRow.setPadding(0, 8, 0, 0);
        for (Member m : group.getMembers()) {
            TextView avatar = new TextView(this);
            avatar.setText(m.getAvatar());
            avatar.setTextSize(20);
            memberRow.addView(avatar);
        }
        topBar.addView(memberRow);
        
        layout.addView(topBar);
        
        // 消息区
        scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        
        msgContainer = new LinearLayout(this);
        msgContainer.setOrientation(LinearLayout.VERTICAL);
        msgContainer.setPadding(16, 16, 16, 16);
        scrollView.addView(msgContainer);
        
        layout.addView(scrollView);
        
        // 输入区
        LinearLayout inputBar = new LinearLayout(this);
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        inputBar.setBackgroundColor(Color.WHITE);
        inputBar.setPadding(16, 12, 16, 12);
        
        input = new EditText(this);
        input.setHint("发送消息...");
        input.setTextSize(16);
        input.setLayoutParams(new LinearLayout.LayoutParams(0, 
            LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        input.setPadding(16, 12, 16, 12);
        input.setBackgroundResource(android.R.drawable/edit_text);
        
        sendBtn = new Button(this);
        sendBtn.setText("发送");
        sendBtn.setTextSize(14);
        sendBtn.setPadding(24, 12, 24, 12);
        sendBtn.setBackgroundColor(Color.parseColor("#2196F3"));
        sendBtn.setTextColor(Color.WHITE);
        
        inputBar.addView(input);
        inputBar.addView(sendBtn);
        layout.addView(inputBar);
        
        setContentView(layout);
        
        // 发送按钮事件
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });
    }
    
    private void sendMessage() {
        if (isLoading) return;
        
        String content = input.getText().toString().trim();
        if (content.isEmpty()) return;
        
        input.setText("");
        isLoading = true;
        sendBtn.setEnabled(false);
        
        // 添加用户消息
        Message userMsg = new Message("你", content);
        messages.add(userMsg);
        messageStorage.addMessage(groupId, userMsg);
        displaySingleMessage(userMsg);
        scrollToBottom();
        
        // 调用助手
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    List<Message> responses;
                    
                    switch (group.getMode()) {
                        case 1: // 用户主持
                            // 简单实现：发给所有人（后续可加@选择）
                            responses = dispatcher.broadcast(group, content, messages, promptBuilder, null);
                            break;
                            
                        case 2: // 助手主持
                            Message hostResponse = dispatcher.sendToHost(group, content, messages, promptBuilder);
                            responses = new ArrayList<>();
                            responses.add(hostResponse);
                            break;
                            
                        case 0: // 平等讨论（默认）
                        default:
                            responses = dispatcher.broadcast(group, content, messages, promptBuilder, null);
                            break;
                    }
                    
                    // 添加响应到历史
                    for (Message response : responses) {
                        messages.add(response);
                    }
                    messageStorage.addMessages(groupId, responses);
                    
                    // 显示响应
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            for (Message response : responses) {
                                displaySingleMessage(response);
                            }
                            scrollToBottom();
                            isLoading = false;
                            sendBtn.setEnabled(true);
                        }
                    });
                    
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(GroupChatActivity.this, 
                                "调用失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            isLoading = false;
                            sendBtn.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }
    
    private void displayMessages() {
        msgContainer.removeAllViews();
        for (Message msg : messages) {
            displaySingleMessage(msg);
        }
        scrollToBottom();
    }
    
    private void displaySingleMessage(Message msg) {
        // 确定颜色
        int bgColor;
        int textColor;
        
        if (msg.isUserMessage()) {
            bgColor = Color.parseColor("#DCF8C6");  // 绿色（用户）
            textColor = Color.BLACK;
        } else if (msg.getSender().contains("你")) {
            bgColor = Color.parseColor("#E8E8E8");  // 灰色（系统）
            textColor = Color.DKGRAY;
        } else {
            bgColor = Color.WHITE;  // 白色（助手）
            textColor = Color.BLACK;
        }
        
        // 容器
        LinearLayout msgWrapper = new LinearLayout(this);
        msgWrapper.setOrientation(LinearLayout.VERTICAL);
        
        android.view.ViewGroup.MarginLayoutParams wrapperParams = 
            new android.view.ViewGroup.MarginLayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapperParams.bottomMargin = 8;
        msgWrapper.setLayoutParams(wrapperParams);
        
        // 发送者
        TextView senderView = new TextView(this);
        senderView.setText(msg.getSender());
        senderView.setTextSize(12);
        senderView.setTextColor(Color.parseColor("#666666"));
        msgWrapper.addView(senderView);
        
        // 消息内容
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundColor(bgColor);
        bubble.setPadding(16, 12, 16, 12);
        
        // 根据内容换行
        wrapTextContent(msg.getContent(), textColor, bubble);
        
        // 时间
        TextView timeView = new TextView(this);
        timeView.setText(msg.getTime());
        timeView.setTextSize(10);
        timeView.setTextColor(Color.GRAY);
        timeView.setGravity(android.view.Gravity.END);
        
        LinearLayout footerLayout = new LinearLayout(this);
        footerLayout.setGravity(android.view.Gravity.END);
        footerLayout.addView(timeView);
        bubble.addView(footerLayout);
        
        msgWrapper.addView(bubble);
        
        // 对齐方式
        if (msg.isUserMessage()) {
            msgWrapper.setGravity(android.view.Gravity.END);
        } else {
            msgWrapper.setGravity(android.view.Gravity.START);
        }
        
        msgContainer.addView(msgWrapper);
    }
    
    private void wrapTextContent(String content, int textColor, LinearLayout container) {
        // 简单处理：检测换行符，分段显示
        String[] lines = content.split("\n");
        
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                // 空白行，用空 TextView 模拟
                TextView spacer = new TextView(this);
                spacer.setText(" ");
                spacer.setTextSize(1);
                container.addView(spacer);
            } else {
                TextView textView = new TextView(this);
                textView.setText(line);
                textView.setTextSize(16);
                textView.setTextColor(textColor);
                
                // 代码块处理
                if (line.startsWith("```") || line.trim().startsWith("```")) {
                    textView.setTypeface(Typeface.MONOSPACE);
                    textView.setBackgroundColor(Color.parseColor("#F0F0F0"));
                }
                
                container.addView(textView);
            }
        }
    }
    
    private void scrollToBottom() {
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }
}