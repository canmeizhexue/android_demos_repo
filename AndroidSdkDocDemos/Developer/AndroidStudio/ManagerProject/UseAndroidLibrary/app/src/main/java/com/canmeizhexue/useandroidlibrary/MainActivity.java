package com.canmeizhexue.useandroidlibrary;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        startActivity(new Intent(this, ApplicationSecondLibrary.class));
        TextView textView = (TextView) findViewById(R.id.text);
        textView.setText(R.string.common_string);
    }
}
