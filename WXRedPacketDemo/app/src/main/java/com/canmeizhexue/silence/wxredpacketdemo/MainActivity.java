package com.canmeizhexue.silence.wxredpacketdemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    EditText editText;
    public static ParamEntity paramEntity = new ParamEntity();
    TextView spendTime;
    private MyBroadCast myBroadCast = new MyBroadCast();
    public static final String ACTION="com.canmeizhexue.silence.wxredpacketdemo.updateSpendTime";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        editText = (EditText) findViewById(R.id.delay_time);
        findViewById(R.id.save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                paramEntity.delayTimeInMs =Long.parseLong( editText.getText().toString());
                Toast.makeText(MainActivity.this,"保存成功",Toast.LENGTH_LONG).show();
            }
        });
        spendTime = (TextView) findViewById(R.id.spendTime);
        spendTime.setText("上次耗时："+paramEntity.spendTimeInMs+" ms");
        IntentFilter intentFilter = new IntentFilter(ACTION);
        registerReceiver(myBroadCast,intentFilter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(myBroadCast);
    }

    private class MyBroadCast extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            if(spendTime!=null){
                spendTime.setText("上次耗时："+paramEntity.spendTimeInMs+" ms");
            }
        }
    }
}
