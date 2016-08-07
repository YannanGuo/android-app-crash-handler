package com.guoxiaoxing.crash;

import android.content.Intent;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Author: guoxiaoxing
 * Date: 16/8/7 下午4:28
 * Function: app crash默认页面
 * <p>
 * For more information, you can visit https://github.com/guoxiaoxing or contact me by
 * guoxiaoxingv@163.com
 */
public class AppCrashActivity extends AppCompatActivity {

    private TextView mTvCrashDetail;
    private Button mBtnRestartApp;

    @Override
    public void onCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        setContentView(R.layout.activity_app_crash);

        mTvCrashDetail = (TextView) findViewById(R.id.crash_tv_crash_detail);
        mBtnRestartApp = (Button) findViewById(R.id.crash_btn_restart_app);

        mBtnRestartApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Class restartActivityClass = AppCrashHandler.getRestartActivityClassFromIntent(getIntent());
                if (restartActivityClass != null) {
                    Intent intent = new Intent(AppCrashActivity.this, restartActivityClass);
                    finish();
                    startActivity(intent);
                }
            }
        });
        mTvCrashDetail.setText(AppCrashHandler.getAllErrorDetailsFromIntent(AppCrashActivity.this, getIntent()));
    }
}