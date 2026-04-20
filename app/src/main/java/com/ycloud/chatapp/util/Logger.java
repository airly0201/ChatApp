package com.ycloud.chatapp.util;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 应用日志工具类
 * 支持日志写入文件和日志导出
 */
public class Logger {
    private static final String TAG = "ChatApp";
    private static final String LOG_FILE = "chatapp.log";
    private static final int MAX_LOG_SIZE = 100 * 1024; // 100KB
    
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static SimpleDateFormat fileNameFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    
    private static List<String> logBuffer = new ArrayList<>();
    private static boolean initialized = false;
    
    /**
     * 初始化日志系统
     */
    public static void init(Context context) {
        if (initialized) return;
        
        // 添加启动日志
        log("I", "Logger", "=== ChatApp 启动 ===");
        log("I", "Logger", "版本: " + getAppVersion(context));
        
        initialized = true;
    }
    
    private static String getAppVersion(Context context) {
        try {
            return context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    /**
     * 记录日志
     */
    public static void log(String level, String tag, String message) {
        String timestamp = dateFormat.format(new Date());
        String logLine = String.format("%s %s/%s: %s", timestamp, level, tag, message);
        
        // 添加到缓冲
        logBuffer.add(logLine);
        
        // 控制台输出
        if ("E".equals(level)) {
            Log.e(TAG, logLine);
        } else if ("W".equals(level)) {
            Log.w(TAG, logLine);
        } else {
            Log.i(TAG, logLine);
        }
        
        // 写入文件
        writeToFile(logLine);
    }
    
    public static void i(String tag, String message) {
        log("I", tag, message);
    }
    
    public static void w(String tag, String message) {
        log("W", tag, message);
    }
    
    public static void e(String tag, String message) {
        log("E", tag, message);
    }
    
    public static void e(String tag, String message, Throwable throwable) {
        log("E", tag, message);
        // 记录堆栈
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        logBuffer.add(sw.toString());
        writeToFile("STACKTRACE: " + sw.toString());
    }
    
    /**
     * 写入日志文件
     */
    private static synchronized void writeToFile(String line) {
        try {
            File logDir = getLogDirectory();
            if (logDir == null) return;
            
            File logFile = new File(logDir, LOG_FILE);
            
            // 检查文件大小
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                // 备份旧日志
                File backup = new File(logDir, "chatapp_" + fileNameFormat.format(new Date()) + ".log.bak");
                logFile.renameTo(backup);
            }
            
            // 写入日志
            FileWriter fw = new FileWriter(logFile, true);
            fw.write(line + "\n");
            fw.flush();
            fw.close();
            
        } catch (Exception e) {
            Log.e(TAG, "写入日志失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取日志目录
     */
    private static File getLogDirectory() {
        try {
            // 优先使用应用私有目录
            Context ctx = getContext();
            if (ctx != null) {
                File dir = new File(ctx.getFilesDir(), "logs");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                return dir;
            }
            
            // 备用：使用外部存储
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "ChatApp/logs");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            return dir;
        } catch (Exception e) {
            return null;
        }
    }
    
    private static Context context;
    public static void setContext(Context ctx) {
        context = ctx.getApplicationContext();
    }
    
    private static Context getContext() {
        return context;
    }
    
    /**
     * 获取日志文件路径
     */
    public static String getLogFilePath() {
        File dir = getLogDirectory();
        if (dir != null) {
            File f = new File(dir, LOG_FILE);
            if (f.exists()) {
                return f.getAbsolutePath();
            }
        }
        return null;
    }
    
    /**
     * 读取日志内容
     */
    public static String readLogs() {
        StringBuilder sb = new StringBuilder();
        synchronized (logBuffer) {
            for (String line : logBuffer) {
                sb.append(line).append("\n");
            }
        }
        
        // 尝试读取文件
        String path = getLogFilePath();
        if (path != null) {
            try {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(path));
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
            } catch (Exception e) {
                // 忽略
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 清除日志
     */
    public static void clearLogs() {
        logBuffer.clear();
        
        String path = getLogFilePath();
        if (path != null) {
            try {
                new java.io.FileWriter(path, false).close();
            } catch (Exception e) {
                // 忽略
            }
        }
        
        i("Logger", "日志已清除");
    }
    
    /**
     * 导出日志到指定路径
     */
    public static String exportLogs(String exportPath) {
        String content = readLogs();
        try {
            java.io.FileWriter fw = new java.io.FileWriter(exportPath);
            fw.write(content);
            fw.close();
            return exportPath;
        } catch (Exception e) {
            e("Logger", "导出日志失败: " + e.getMessage());
            return null;
        }
    }
}