package com.canmeizhexue.silence.mdstyledemo;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private RecyclerView mRecyclerView;
    private List<String> mDatas;
    private HomeAdapter mAdapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initData();
        mRecyclerView = (RecyclerView) findViewById(R.id.id_recyclerview);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
        mRecyclerView.setAdapter(mAdapter = new HomeAdapter());

    }
    protected void initData()
    {
        mDatas = new ArrayList<String>();
        mDatas.add("CoordinatorLayout--FloatingActionButton");
    }
    class HomeAdapter extends RecyclerView.Adapter<HomeAdapter.MyViewHolder> implements View.OnClickListener
    {

        @Override
        public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
        {
            MyViewHolder holder = new MyViewHolder(LayoutInflater.from(
                    MainActivity.this).inflate(R.layout.item_main_activity, parent,
                                               false));
            return holder;
        }

        @Override
        public void onBindViewHolder(MyViewHolder holder, int position)
        {
            holder.tv.setText(mDatas.get(position));
            holder.tv.setOnClickListener(this);
            holder.tv.setTag(R.id.id_num,position);
        }

        @Override
        public int getItemCount()
        {
            return mDatas.size();
        }

        @Override
        public void onClick(View v) {
            int position = (int) v.getTag(R.id.id_num);
            Log.d("silence","------position---"+position+"------"+mDatas.get(position));
            Intent intent=null ;
            switch (position){
                case 0:
                    intent = new Intent(MainActivity.this,CoordinatorLayoutFAB.class);

                    break;
            }
            if(intent!=null){
                startActivity(intent);
            }

        }

        class MyViewHolder extends RecyclerView.ViewHolder
        {

            TextView tv;

            public MyViewHolder(View view)
            {
                super(view);
                tv = (TextView) view.findViewById(R.id.id_num);
            }
        }
    }


}
