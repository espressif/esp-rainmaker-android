package com.espressif.rainmaker.wxapi;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.rainmaker.BuildConfig;
import com.espressif.rainmaker.databinding.ActivityWeChatProgressBinding;
import com.tencent.mm.opensdk.constants.ConstantsAPI;
import com.tencent.mm.opensdk.modelbase.BaseReq;
import com.tencent.mm.opensdk.modelbase.BaseResp;
import com.tencent.mm.opensdk.modelmsg.SendAuth;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;

public class WXEntryActivity extends Activity implements IWXAPIEventHandler {

    private final String TAG = getClass().getSimpleName();

    private IWXAPI api;
    private ActivityWeChatProgressBinding binding;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWeChatProgressBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);
        displayProgress();

        api = WXAPIFactory.createWXAPI(this, BuildConfig.CHINA_WE_CHAT_APP_ID);
        api.handleIntent(getIntent(), this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        api.handleIntent(intent, this);
    }

    @Override
    public void onReq(BaseReq baseReq) {
        Log.d(TAG, "onReq: " + baseReq.transaction);
    }

    @Override
    public void onResp(BaseResp baseResp) {
        Log.d(TAG, "onResp: " + baseResp);
        if (baseResp.getType() == ConstantsAPI.COMMAND_SENDAUTH) {
            SendAuth.Resp authResp = (SendAuth.Resp) baseResp;
            String code = authResp.code;
            Log.d(TAG, "WeChat login response code : " + code);

            // Send this code to your server to get an access token and then use it to get user info
            ApiManager apiManager = ApiManager.getInstance(getApplicationContext());
            apiManager.getOAuthTokenForWechat(code, new ApiResponseListener() {
                @Override
                public void onSuccess(@Nullable Bundle data) {
                    finish();
                    EspApplication.loggedInUsingWeChat = true;
                }

                @Override
                public void onResponseFailure(@NonNull Exception exception) {
                    exception.printStackTrace();
                    finish();
                }

                @Override
                public void onNetworkFailure(@NonNull Exception exception) {
                    exception.printStackTrace();
                    finish();
                }
            });
        } else {
            Log.e(TAG, "Another response type : " + baseResp.getType());
        }
    }

    private void displayProgress() {

        RotateAnimation rotate = new RotateAnimation(0, 180, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(3000);
        rotate.setRepeatCount(Animation.INFINITE);
        rotate.setInterpolator(new LinearInterpolator());
        binding.ivClaiming.startAnimation(rotate);
    }
}
