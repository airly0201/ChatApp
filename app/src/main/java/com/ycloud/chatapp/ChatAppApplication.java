package com.ycloud.chatapp;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.ycloud.chatapp.util.Logger;

/**
 * 应用入口，全局异常捕获
 */
public class ChatAppApplication extends Application {
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // 初始化日志系统
        Logger.setContext(this);
        Logger.init(this);
        
        // 设置全局异常捕获
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                Logger.e("AppCrash", "未捕获的异常!", throwable);
                
                // 打印堆栈跟踪
                throwable.printStackTrace();
                
                // 可以在这里添加崩溃报告上传逻辑
                
                // 退出应用
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
            }
        });
        
        Logger.i("ChatAppApplication", "应用初始化完成");
        
        // 打印版本信息
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            Logger.i("ChatAppApplication", "Package: " + pInfo.packageName + 
                    ", Version: " + pInfo.versionName + 
                    " (" + pInfo.versionCode + ")");
        } catch (PackageManager.NameNotFoundException e) {
            // 忽略
        }
    }
}