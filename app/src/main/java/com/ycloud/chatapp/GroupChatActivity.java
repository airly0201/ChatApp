package com.ycloud.chatapp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;

import com.ycloud.chatapp.model.Group;
import com.ycloud.chatapp.model.Member;
import com.ycloud.chatapp.model.Message;
import com.ycloud.chatapp.service.GroupManager;
import com.ycloud.chatapp.service.MemberDispatcher;
import com.ycloud.chatapp.service.MessageStorage;
import com.ycloud.chatapp.service.PromptBuilder;
import com.ycloud.chatapp.util.Logger;

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
    private TextView debugPanel;
    
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
            Logger.e("GroupChatActivity", "未找到群组: " + groupId);
            finish();
            return;
        }
        
        Logger.i("GroupChatActivity", "进入群聊: " + group.getName() + ", 模式: " + group.getModeName() + ", 成员数: " + group.getMembers().size());
        
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
        Logger.i("GroupChatActivity", "checkAndRequestIntroductions: 需要自我介绍的成员=" + (needIntro != null ? needIntro.getName() : "无"));
        if (needIntro != null) {
            // 请求自我介绍
            final Member finalNeedIntro = needIntro;
            Logger.i("GroupChatActivity", "请求 " + needIntro.getName() + " 自我介绍");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Message introMsg = dispatcher.requestIntroduction(finalNeedIntro, group, promptBuilder);
                    Logger.i("GroupChatActivity", "收到自我介绍: " + introMsg.getContent().substring(0, Math.min(50, introMsg.getContent().length())));
                    
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
        
        sendBtn = new Button(this);
        sendBtn.setText("发送");
        sendBtn.setTextSize(14);
        sendBtn.setPadding(24, 12, 24, 12);
        sendBtn.setBackgroundColor(Color.parseColor("#2196F3"));
        sendBtn.setTextColor(Color.WHITE);
        
        inputBar.addView(input);
        inputBar.addView(sendBtn);
        layout.addView(inputBar);
        
        // 调试面板 - 显示运行状态
        TextView debugPanel = new TextView(this);
        debugPanel.setText("🔍 运行状态: 初始化中...");
        debugPanel.setTextSize(12);
        debugPanel.setTextColor(Color.DKGRAY);
        debugPanel.setBackgroundColor(Color.parseColor("#FFF3CD"));
        debugPanel.setPadding(16, 8, 16, 8);
        debugPanel.setId(View.generateViewId());
        layout.addView(debugPanel);
        
        setContentView(layout);
        
        // 保存调试面板引用
        this.debugPanel = debugPanel;
        updateDebugPanel("就绪");
        
        // 监听 @ 提及
        input.addTextChangedListener(new TextWatcher() {
            private int atPosition = -1;
            
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                String text = s.toString();
                int lastAt = text.lastIndexOf('@', start + count - 1);
                if (lastAt >= 0) {
                    // 检查@后面是否还有空格或已结束
                    String afterAt = text.substring(lastAt + 1);
                    if (!afterAt.contains(" ") && !afterAt.isEmpty()) {
                        atPosition = lastAt;
                    } else if (afterAt.isEmpty()) {
                        atPosition = lastAt;
                    }
                }
            }
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();
                int cursorPos = input.getSelectionStart();
                
                // 检查是否在@后面输入
                if (atPosition >= 0 && atPosition < text.length()) {
                    String afterAt = text.substring(atPosition + 1);
                    // 如果用户输入了空格或@结束，显示助手列表
                    if (afterAt.isEmpty() || afterAt.startsWith(" ")) {
                        showMemberPopup(input, atPosition);
                        atPosition = -1;
                    }
                }
                
                // 检查新输入的@
                int lastAt = text.lastIndexOf('@');
                if (lastAt >= 0 && (lastAt == text.length() - 1 || 
                    text.charAt(lastAt + 1) == ' ' || cursorPos <= lastAt)) {
                    atPosition = lastAt;
                } else {
                    atPosition = -1;
                }
            }
        });
        
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
        
        Logger.i("GroupChatActivity", "发送消息: " + content);
        
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
                    String modeName = group.getModeName();
                    Logger.i("GroupChatActivity", "群聊模式: " + modeName + ", 成员数: " + group.getMembers().size());
                    
                    switch (group.getMode()) {
                        case 1: // 用户主持
                            Logger.i("GroupChatActivity", "广播消息到所有成员");
                            responses = dispatcher.broadcast(group, content, messages, promptBuilder, null);
                            break;
                            
                        case 2: // 助手主持 - 协调模式
                            Logger.i("GroupChatActivity", "助手主持模式: 先主持人，后成员补充");
                            responses = dispatcher.coordinateGroup(group, content, messages, promptBuilder);
                            break;
                            
                        case 0: // 平等讨论（默认）
                        default:
                            Logger.i("GroupChatActivity", "广播消息到所有成员");
                            responses = dispatcher.broadcast(group, content, messages, promptBuilder, null);
                            break;
                    }
                    
                    Logger.i("GroupChatActivity", "收到响应数: " + responses.size());
                    
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
                    Logger.e("GroupChatActivity", "调用助手失败: " + e.getMessage());
                    e.printStackTrace();
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
    
    // 显示成员选择弹出菜单
    private void showMemberPopup(final EditText editText, final int atPosition) {
        if (group == null || group.getMembers() == null || group.getMembers().isEmpty()) {
            return;
        }
        
        PopupMenu popup = new PopupMenu(this, editText);
        for (Member member : group.getMembers()) {
            String item = member.getAvatar() + " " + member.getName();
            popup.getMenu().add(0, popup.getMenu().size(), popup.getMenu().size(), item);
        }
        
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                String selected = item.getTitle().toString();
                String memberName = selected.substring(2); // 去掉emoji
                
                String text = editText.getText().toString();
                int cursorPos = editText.getSelectionStart();
                
                // 找到@的位置并替换
                int start = text.lastIndexOf('@', cursorPos - 1);
                if (start >= 0) {
                    String before = text.substring(0, start);
                    String after = text.substring(cursorPos);
                    String newText = before + "@" + memberName + " " + after;
                    editText.setText(newText);
                    editText.setSelection(start + memberName.length() + 2);
                }
                return true;
            }
        });
        
        popup.show();
    }
    
    private void scrollToBottom() {
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }
    
    private void updateDebugPanel(final String status) {
        if (debugPanel == null) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                debugPanel.setText("🔍 运行状态: " + status);
            }
        });
    }
}