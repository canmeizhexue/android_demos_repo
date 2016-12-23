package com.canmeizhexue.andfixdemo;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
//http://www.wjdiankong.cn/android热修复框架andfix原理解析及使用/
//https://github.com/canmeizhexue/AndFix
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView textView = (TextView) findViewById(R.id.helloworld);
        textView.setText(getString());

    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("silence","----我还加日志了");
    }

    private String getString(){
        return OldClass.getString();
    }
}
