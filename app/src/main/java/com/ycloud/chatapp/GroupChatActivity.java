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
import android.view.WindowManager;
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
    
    // @提及功能的状态追踪
    private boolean isSelectingMember = false;
    private int lastAtPosition = -1;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置软键盘模式：只调整内容区域，不顶起头部
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        
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
                    
                    // 记录自我介绍到日志
                    Logger.i("ChatLog", "[群聊:" + group.getName() + "] 【" + finalNeedIntro.getName() + " 自我介绍】" + introMsg.getContent());
                    
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
        
        // 添加菜单按钮（导出日志等）
        Button menuBtn = new Button(this);
        menuBtn.setText("⋮");
        menuBtn.setTextSize(20);
        menuBtn.setBackgroundColor(Color.TRANSPARENT);
        menuBtn.setTextColor(Color.WHITE);
        menuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenuDialog();
            }
        });
        topRow.addView(menuBtn);
        
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
        
        // 添加 @所有人 快捷按钮
        Button atAllBtn = new Button(this);
        atAllBtn.setText("@所有");
        atAllBtn.setTextSize(12);
        atAllBtn.setPadding(12, 8, 12, 8);
        atAllBtn.setBackgroundColor(Color.parseColor("#FF9800"));
        atAllBtn.setTextColor(Color.WHITE);
        atAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                input.setText("@所有人 ");
                input.setSelection(input.getText().length());
                input.requestFocus();
            }
        });
        inputBar.addView(atAllBtn);
        
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
        
        // 监听 @ 提及 - 优化版：每次输入@都弹出，选择后直到发送/删除前不再弹出
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();
                int cursorPos = input.getSelectionStart();
                if (cursorPos < 0) cursorPos = 0;
                
                // 检查是否删除了之前的@（即lastAtPosition所指的@不存在了）
                if (lastAtPosition >= 0) {
                    boolean atStillExists = false;
                    if (lastAtPosition < text.length() && text.charAt(lastAtPosition) == '@') {
                        atStillExists = true;
                    }
                    if (!atStillExists) {
                        // @被删除了，重置状态
                        lastAtPosition = -1;
                        isSelectingMember = false;
                    }
                }
                
                // 找到光标前的最后一个@（不在空格后的）
                int atPos = -1;
                for (int i = cursorPos - 1; i >= 0; i--) {
                    if (text.charAt(i) == '@') {
                        // 检查@前面是空格或开头
                        if (i == 0 || text.charAt(i - 1) == ' ') {
                            atPos = i;
                            break;
                        }
                    }
                }
                
                if (atPos >= 0) {
                    String afterAt = (atPos + 1 < text.length()) ? text.substring(atPos + 1) : "";
                    boolean isNewAt = (atPos != lastAtPosition);  // 检测是否是一个新的@
                    
                    // 如果正在选择成员中，且正在输入名字（@后面有非空格文字），不弹窗
                    if (isSelectingMember && lastAtPosition >= 0) {
                        String afterLastAt = (lastAtPosition + 1 < text.length()) ? text.substring(lastAtPosition + 1) : "";
                        if (!afterLastAt.isEmpty() && !afterLastAt.startsWith(" ")) {
                            return;  // 正在输入名字，不弹出
                        }
                    }
                    
                    // 显示列表条件：@后面是空格或空
                    if (afterAt.isEmpty() || afterAt.startsWith(" ")) {
                        // 如果是新@，或者之前没有在选择状态，就弹出
                        if (isNewAt || !isSelectingMember) {
                            showMemberPopup(input, atPos);
                            isSelectingMember = true;
                            lastAtPosition = atPos;
                        }
                    }
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
        updateDebugPanel("发送中...");
        
        input.setText("");
        isLoading = true;
        sendBtn.setEnabled(false);
        
        // 重置@提及状态，允许下次输入@时弹出助手列表
        isSelectingMember = false;
        lastAtPosition = -1;
        
        // 添加用户消息
        Message userMsg = new Message("你", content);
        messages.add(userMsg);
        messageStorage.addMessage(groupId, userMsg);
        
        // 记录聊天内容到日志
        Logger.i("ChatLog", "[群聊:" + group.getName() + "] 【用户】" + content);
        
        displaySingleMessage(userMsg);
        scrollToBottom();
        
        // 调用助手
        updateDebugPanel("等待响应...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                // 必须初始化为 final（effectively final）
                final List<Message> responses;
                String modeName = group.getModeName();
                Logger.i("GroupChatActivity", "群聊模式: " + modeName + ", 成员数: " + group.getMembers().size() + ", 历史消息数: " + messages.size());
                
                switch (group.getMode()) {
                    case 1: // 用户主持 - @指定某个助手
                        Logger.i("GroupChatActivity", "用户主持模式: 解析@指定的助手");
                        // 创建新的列表
                        List<Message> case1Responses = new ArrayList<>();
                        // 解析所有 @ 提及的助手
                        List<Member> targetMembers = parseAllMentionedMembers(content);
                        if (!targetMembers.isEmpty()) {
                            Logger.i("GroupChatActivity", "发送给指定助手: " + targetMembers.size() + "个");
                            // 对每个被@的助手发送消息
                            for (Member targetMember : targetMembers) {
                                String messageForMember = buildMessageForMember(targetMember, content);
                                Message singleResponse = dispatcher.sendToMember(targetMember, group, messageForMember, messages, promptBuilder);
                                case1Responses.add(singleResponse);
                            }
                        } else if (!content.contains("@")) {
                            // 没有@时，提示用户需要@指定
                            case1Responses.add(new Message("系统", "请使用 @ 符号指定要回覆的助手，例如: @助手名称 你的问题"));
                        } else {
                            case1Responses.add(new Message("系统", "未找到指定的助手，请检查名称是否正确"));
                        }
                        responses = case1Responses;
                        break;
                        
                    case 2: // 助手主持 - 协调模式
                        // 检查消息是否包含 @ 提及，有@则只让被@的助手响应（跳过主持人协调）
                        List<Member> mentionedForMode2 = parseAllMentionedMembers(content);
                        if (!mentionedForMode2.isEmpty()) {
                            Logger.i("GroupChatActivity", "助手主持模式: @指定助手 " + mentionedForMode2.size() + "个，直接响应");
                            List<Message> case2Responses = new ArrayList<>();
                            // 对每个被@的助手发送消息
                            for (Member mentionedMember : mentionedForMode2) {
                                String messageForMember = buildMessageForMember(mentionedMember, content);
                                Message singleResponse = dispatcher.sendToMember(mentionedMember, group, messageForMember, messages, promptBuilder);
                                case2Responses.add(singleResponse);
                            }
                            responses = case2Responses;
                        } else {
                            Logger.i("GroupChatActivity", "助手主持模式: 先主持人，后成员补充");
                            List<Message> case2Responses = dispatcher.coordinateGroup(group, content, messages, promptBuilder);
                            // 直接在方法返回时保证不为 null
                            if (case2Responses == null) {
                                case2Responses = new ArrayList<>();
                            }
                            responses = case2Responses;
                        }
                        break;
                        
                    case 0: // 平等讨论（默认）
                    default:
                        // 检查消息是否包含 @ 提及，有@则只让被@的助手响应，无@则广播
                        List<Member> mentionedMembers = parseAllMentionedMembers(content);
                        if (!mentionedMembers.isEmpty()) {
                            Logger.i("GroupChatActivity", "平等讨论模式: @指定助手 " + mentionedMembers.size() + "个");
                            List<Message> case0Responses = new ArrayList<>();
                            // 对每个被@的助手发送消息
                            for (Member mentionedMember : mentionedMembers) {
                                String messageForMember = buildMessageForMember(mentionedMember, content);
                                Message singleResponse = dispatcher.sendToMember(mentionedMember, group, messageForMember, messages, promptBuilder);
                                case0Responses.add(singleResponse);
                            }
                            responses = case0Responses;
                        } else {
                            Logger.i("GroupChatActivity", "广播消息到所有成员");
                            List<Message> case0Responses = dispatcher.broadcast(group, content, messages, promptBuilder, null);
                            // 直接在方法返回时保证不为 null
                            if (case0Responses == null) {
                                case0Responses = new ArrayList<>();
                            }
                            responses = case0Responses;
                        }
                        break;
                }
                
                Logger.i("GroupChatActivity", "收到响应数: " + responses.size());
                
                // 创建 final 副本供内部类使用
                final List<Message> finalResponses = responses;
                
                // 记录每个助手的响应到日志
                for (Message response : responses) {
                    Logger.i("ChatLog", "[群聊:" + group.getName() + "] 【" + response.getSender() + "】" + response.getContent());
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
                        for (Message response : finalResponses) {
                            displaySingleMessage(response);
                        }
                        scrollToBottom();
                        isLoading = false;
                        sendBtn.setEnabled(true);
                    }
                });
                
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
                
                // 启用文本选择功能（长按可选中复制）
                textView.setTextIsSelectable(true);
                textView.setFocusable(true);
                
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
    
    /**
     * 解析消息中 @ 提及的成员
     * 支持格式：@助手名 或 @‍💻 助手名（带emoji）
     */
    private Member parseMentionedMember(String content) {
        List<Member> allMembers = parseAllMentionedMembers(content);
        return allMembers.isEmpty() ? null : allMembers.get(0);
    }
    
    /**
     * 解析消息中所有 @ 提及的成员
     * 返回所有被@的助手列表（去重）
     * 支持 @所有人 或 @all 让所有助手响应
     */
    private List<Member> parseAllMentionedMembers(String content) {
        List<Member> mentionedMembers = new ArrayList<>();
        
        if (group == null || group.getMembers() == null) {
            return mentionedMembers;
        }
        
        // 检查是否@所有人
        String lowerContent = content.toLowerCase();
        if (lowerContent.contains("@所有人") || lowerContent.contains("@all") || lowerContent.contains("@ everyone")) {
            Logger.i("GroupChatActivity", "检测到@所有人，添加所有成员");
            mentionedMembers.addAll(group.getMembers());
            return mentionedMembers;
        }
        
        // 查找所有 @ 符号位置
        List<Integer> atPositions = new ArrayList<>();
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '@') {
                atPositions.add(i);
            }
        }
        
        if (atPositions.isEmpty()) {
            return mentionedMembers;
        }
        
        // 尝试解析每个@位置后的名称
        for (int atPos : atPositions) {
            String afterAt = content.substring(atPos + 1);
            
            // 去除可能的前导emoji或特殊字符（零宽连接符等）
            afterAt = afterAt.replaceAll("^[\\u200B-\\u200D\\uFEFF]", ""); // 去除零宽字符
            afterAt = afterAt.replaceAll("^[\\p{So}\\p{Sk}]+", ""); // 去除emoji modifier
            afterAt = afterAt.trim();
            
            if (afterAt.isEmpty()) {
                continue;
            }
            
            // 提取第一个单词（到空格为止）
            String[] parts = afterAt.split("\\s+");
            if (parts.length == 0) continue;
            
            String mentionedName = parts[0];
            
            // 去除可能的标点符号
            mentionedName = mentionedName.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5-]", "");
            
            if (mentionedName.isEmpty()) {
                continue;
            }
            
            // 查找匹配的成员
            for (Member member : group.getMembers()) {
                String memberName = member.getName();
                
                // 精确匹配
                boolean matched = memberName.equals(mentionedName);
                // 模糊匹配（被@的名称包含成员名，或成员名包含被@的名称）
                if (!matched && mentionedName.length() >= 2 && (memberName.contains(mentionedName) || mentionedName.contains(memberName))) {
                    matched = true;
                }
                
                if (matched && !mentionedMembers.contains(member)) {
                    mentionedMembers.add(member);
                    break; // 一个@只匹配一个成员
                }
            }
        }
        
        return mentionedMembers;
    }
    
    /**
     * 清理消息中的@引用，生成发送给助手的消息
     * 如果消息中有@该助手，加上"你被@了"的提示
     */
    private String buildMessageForMember(Member member, String originalMessage) {
        StringBuilder sb = new StringBuilder();
        
        // 检查是否@了这个助手
        boolean mentioned = false;
        String memberName = member.getName();
        
        // 检查是否@所有人，如果是则不显示@提示
        String lowerMessage = originalMessage.toLowerCase();
        boolean isMentionAll = lowerMessage.contains("@所有人") || lowerMessage.contains("@all") || lowerMessage.contains("@ everyone");
        
        // 简单的@检测
        if (!isMentionAll && originalMessage.contains("@" + memberName)) {
            mentioned = true;
        } else if (!isMentionAll) {
            // 也检查带emoji的情况
            for (int i = 0; i < originalMessage.length(); i++) {
                if (originalMessage.charAt(i) == '@') {
                    String afterAt = originalMessage.substring(i + 1);
                    afterAt = afterAt.replaceAll("^[\\u200B-\\u200D\\uFEFF]", "");
                    afterAt = afterAt.replaceAll("^[\\p{So}\\p{Sk}]+", "");
                    afterAt = afterAt.trim();
                    if (afterAt.startsWith(memberName) || afterAt.split("\\s+")[0].contains(memberName)) {
                        mentioned = true;
                        break;
                    }
                }
            }
        }
        
        if (mentioned) {
            sb.append("你在群聊中被@了！请针对以下问题回复：\n\n");
        }
        
        // 清理消息中的@引用部分（可选：也可以保留让助手自己判断）
        // 这里保留原消息但去掉@该助手的部分
        String cleanedMessage = originalMessage;
        cleanedMessage = cleanedMessage.replaceAll("@" + memberName, "").trim();
        // 也清理其他@（可选）
        
        sb.append(cleanedMessage);
        
        return sb.toString();
    }
    
    /**
     * 显示菜单对话框
     */
    private void showMenuDialog() {
        final String[] menuItems = {"导出日志", "清空聊天记录", "刷新界面"};
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("菜单");
        builder.setItems(menuItems, (dialog, which) -> {
            if (which == 0) {
                // 导出日志
                try {
                    String logPath = Logger.exportLogs(getExternalFilesDir(null).getAbsolutePath());
                    Toast.makeText(this, "📋 日志已导出到:\n" + logPath, Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "❌ 导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else if (which == 1) {
                // 清空聊天记录
                new android.app.AlertDialog.Builder(this)
                    .setTitle("确认清空")
                    .setMessage("确定要清空当前群聊的所有聊天记录吗？")
                    .setPositiveButton("确定", (d, w) -> {
                        messageStorage.clearHistory(groupId);
                        messages.clear();
                        msgContainer.removeAllViews();
                        Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            } else if (which == 2) {
                // 刷新界面
                msgContainer.removeAllViews();
                displayMessages();
                Toast.makeText(this, "已刷新", Toast.LENGTH_SHORT).show();
            }
        });
        builder.show();
    }
}