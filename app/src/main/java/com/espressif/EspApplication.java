package com.espressif;

import android.app.Application;
import android.util.Log;

import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.user_module.AppHelper;

import java.util.HashMap;

public class EspApplication extends Application {

    private static final String TAG = EspApplication.class.getSimpleName();

    public HashMap<String, EspNode> nodeMap;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ESP Application is created");
        AppHelper.init(this);
        ESPProvisionManager.getInstance(this);
        nodeMap = new HashMap<>();
    }
}
