package com.espressif;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.espressif.rainmaker.BuildConfig;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;

public class AppRegister extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final IWXAPI api = WXAPIFactory.createWXAPI(context, null, false);

        // Register the app to WeChat
        api.registerApp(BuildConfig.CHINA_WE_CHAT_APP_ID);
    }
}
