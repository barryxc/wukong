package com.barry.hooktest;


import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

//代码更新后，需要修改versionCode，或者卸载重装，否则不会生效
public class HookModule implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedBridge.log("HookModule handleLoadPackage:" + lpparam.packageName);

        if (lpparam.packageName.equals("xxxxx")) {

            //todo: 填写hook的类名,方法名,参数类型
            XposedHelpers.findAndHookMethod("xxx.xxxx", lpparam.classLoader, "xxxx", null, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("HookModule beforeHookedMethod:" + param.args[0]);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object result = param.getResult();
                    //hook 方法返回值
                    if (result instanceof String) {
                        param.setResult("hooked:");
                    }
                    XposedBridge.log("HookModule afterHookedMethod:" + result);
                }
            });
        }

    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        XposedBridge.log("HookModule initZygote");
    }
}
