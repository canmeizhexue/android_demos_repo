package com.canmeizhexue.silence.wxredpacketdemo;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class RobMoneyService extends AccessibilityService {
    private static final String TAG = "RobMoneyService---zyp-";
    private long pretime;
    private boolean justInDetailPage = false;
    private boolean fromNotification=false;
    private static final long MAX_TIME=2000;
    private boolean justInTalkPage=false;
    private boolean handlingConentChanged=false;

    @Override
    protected void onServiceConnected() {
        Log.i(TAG, "onServiceConnected:");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
//        Log.i(TAG, "onAccessibilityEvent:");
        int eventType = event.getEventType();
        switch (eventType) {
            //第一步：监听通知栏消息
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                onNotificationEvent(event);
                break;
            //第二步：监听是否进入微信红包消息界面
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                onWindowStateChanged(event);
                break;

            case AccessibilityEvent.TYPE_VIEW_CLICKED:
//				Log.i(TAG, "AccessibilityEvent.按钮被点击了，主要是用来找到view的id-----:" );
                AccessibilityNodeInfo accessibilityNodeInfo = event.getSource();
                if (accessibilityNodeInfo != null) {
//					Log.i(TAG, "AccessibilityEvent.按钮被点击了，主要是用来找到view的id-----:"+accessibilityNodeInfo.toString() );
                }
                break;
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                Log.i(TAG, "TYPE_WINDOW_CONTENT_CHANGED-----:"+event.getClassName() );
                if(!"android.widget.ListView".equals(event.getClassName())){
                    return ;
                }
                if(handlingConentChanged){
                    return;
                }
                handlingConentChanged=true;
                CharSequence className = event.getClassName();
                //在聊天页面收到红包的时候，打印出来android.widget.TextView
                if(justInTalkPage){
/*                    AccessibilityNodeInfo source =event.getSource();
                    if(source!=null){
                        printViewTree(source,false);
                    }*/
                    AccessibilityNodeInfo source = event.getSource();
                    if(source!=null){
                        Log.i(TAG, "TYPE_WINDOW_CONTENT_CHANGED-----:"+source.getClassName() );
                        List<AccessibilityNodeInfo> infos = source.findAccessibilityNodeInfosByViewId("@id/a4v");
                        if(infos!=null && infos.size()>0){
                            recycle(infos.get(infos.size()-1));
                        }

                    }


                }
                handlingConentChanged=false;
                break;
        }
    }
    private void onWindowContentChanged(AccessibilityNodeInfo accessibilityNodeInfo){
        //聊天页面hy是listView的id
        Log.i(TAG, "onWindowContentChanged-----:" + accessibilityNodeInfo.getChildCount());
            if(justInDetailPage){
                justInDetailPage=false;
                return ;
            }
            if(accessibilityNodeInfo.getText()!=null && "领取红包".equals(accessibilityNodeInfo.getText())){
                recycle(accessibilityNodeInfo);
                return ;
            }
            AccessibilityNodeInfo parent = accessibilityNodeInfo.getParent();
            while (parent!=null&& parent.getChildCount()>1){
                for(int i=0;i<parent.getChildCount();i++){
                    AccessibilityNodeInfo child = parent.getChild(i);
                    if(child!=accessibilityNodeInfo && child.getChildCount()==0 ){
                        recycle(child);
                        return;
                    }
                }
                parent = parent.getParent();
            }
    }
    private void onWindowStateChanged(AccessibilityEvent event) {
        String className = event.getClassName().toString();
        Log.i(TAG, "AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED-----:" + className);
        if (className.equals("com.tencent.mm.ui.LauncherUI")) {
            justInTalkPage=true;
            if (justInDetailPage) {
                //刚刚关闭红包详情页，然后到这个页面，所以忽略这一次
                Log.i(TAG, "刚刚关闭红包详情页，然后到这个页面，所以忽略这一次");
//                justInDetailPage = false;
                AccessibilityServiceInfo accessibilityServiceInfo=getServiceInfo();
                if(accessibilityServiceInfo!=null){
                    accessibilityServiceInfo.eventTypes |=AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                    setServiceInfo(accessibilityServiceInfo);
                }
                return;
            }
            Log.i(TAG, "现在是聊天页面，开始抢红包，要打开红包对话框:");
            //开始抢红包,弹出拆开红包的对话框
            getPacket();
        } else if (className.equals("com.tencent.mm.plugin.luckymoney.ui.En_fba4b94f")) {
            justInTalkPage=false;
            AccessibilityServiceInfo accessibilityServiceInfo=getServiceInfo();
            if(accessibilityServiceInfo!=null){
                accessibilityServiceInfo.eventTypes &=~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                setServiceInfo(accessibilityServiceInfo);
            }
            long now = System.currentTimeMillis();

            //因为这个地方有混淆，，所以不对应了，，com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI
            //开始打开红包
            Log.i(TAG, "红包对话框打开--现在开始抢--openPacket--:" + (now - pretime));
            openPacket();
        } else if (className.equals("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI")) {
            justInTalkPage=false;
            AccessibilityServiceInfo accessibilityServiceInfo=getServiceInfo();
            if(accessibilityServiceInfo!=null){
                accessibilityServiceInfo.eventTypes &=~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                setServiceInfo(accessibilityServiceInfo);
            }
            justInDetailPage = true;
            fromNotification = false;
            //详情页面，现在我们要关闭它，
            long now = System.currentTimeMillis();
            MainActivity.paramEntity.spendTimeInMs = now - pretime;
            sendBroadcast(new Intent(MainActivity.ACTION));
            Log.i(TAG, "已经进入红包详情页，--共花费时间--:" + MainActivity.paramEntity.spendTimeInMs + "  ms");
//            Toast.makeText(this, "已经进入详情页，一共花费时间" + MainActivity.paramEntity.spendTimeInMs + "  ms", Toast.LENGTH_SHORT).show();
//            printParents("红包详情");

//            close();
//				closeRedPacketDetailActivity();
        }
    }

    private void onNotificationEvent(AccessibilityEvent event) {
        justInTalkPage=false;
        List<CharSequence> texts = event.getText();
        if (!texts.isEmpty()) {
            for (CharSequence text : texts) {
                String content = text.toString();
                Log.i(TAG, "监听到通知栏内容:" + content);
                if (content.contains("[微信红包]")) {
                    fromNotification = true;
                    pretime = System.currentTimeMillis();
                    //模拟打开通知栏消息
                    if (event.getParcelableData() != null
                            &&
                            event.getParcelableData() instanceof Notification) {
                        Notification notification = (Notification) event.getParcelableData();
                        PendingIntent pendingIntent = notification.contentIntent;
                        try {
                            pendingIntent.send();
                        } catch (CanceledException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    /**
     * 查找到
     */
    @SuppressLint("NewApi")
    private void openPacket() {
        final AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo != null) {
//            Log.i(TAG, "要打开红包了---能找到带特定字的view吗？---打印一下view树:");
//            printViewTree(nodeInfo, false);
            //抢红包
            final List<AccessibilityNodeInfo> list = nodeInfo
                    .findAccessibilityNodeInfosByText("開");
            Log.i(TAG, "要打开红包了----延时毫秒数-- ---" + MainActivity.paramEntity.delayTimeInMs);
            if (list != null && list.size() > 0) {
                Log.i(TAG, "要打开红包了------ 延迟一下再执行---");
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        for (AccessibilityNodeInfo n : list) {
                            n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        }
                    }
                }, MainActivity.paramEntity.delayTimeInMs);
            } else {
                //目前是乱点的

                long now = System.currentTimeMillis();
                long timeSpended = now-pretime;
                Log.i(TAG, "要打开红包了------ 没找到带開字的view，可能是张图片,那就乱点了--timeSpended---"+timeSpended);
                if(timeSpended>MAX_TIME){
                    printViewTree(nodeInfo, true);
                }else{
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            printViewTree(nodeInfo, true);
                        }
                    }, MainActivity.paramEntity.delayTimeInMs-timeSpended);
                }


            }

        }

    }

    @SuppressLint("NewApi")
    private void getPacket() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        recycle(rootNode);
    }

    /**
     * 打印一个节点的结构
     *
     * @param info
     */
    @SuppressLint("NewApi")
    public void recycle(AccessibilityNodeInfo info) {
//		Log.i(TAG, "recycle"+",info.getChildCount() == "+info.getChildCount() );
        if (info.getChildCount() == 0) {
            if (info.getText() != null) {
                if ("领取红包".equals(info.getText().toString())) {
//                    printParents("领取红包");
                    //这里有一个问题需要注意，就是需要找到一个可以点击的View
                	Log.i(TAG, "找到领取红包的view了 ---"+info.getClassName());
                    info.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    AccessibilityNodeInfo parent = info.getParent();
                    while (parent != null) {
//                		Log.i(TAG, "parent isClick:"+parent.isClickable());
                        if (parent.isClickable()) {
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            break;
                        }
                        parent = parent.getParent();
                    }

                }
            }

        } else {
            for (int i = 0; i < info.getChildCount(); i++) {
                if (info.getChild(i) != null) {
                    recycle(info.getChild(i));
                }
            }
        }
    }

    @Override
    public void onInterrupt() {
    }

    @SuppressLint("NewApi")
    private void printViewTree(AccessibilityNodeInfo info, boolean performClick) {
        if (info != null) {
            int childCount = info.getChildCount();
            Log.i(TAG, "classname isClick:" + info.getClassName() + "-----" + info.isClickable() + "-------" + info.getViewIdResourceName());
            if (info.getText() != null) {
                Log.i(TAG, "classname text:" + "-------" + info.getText().toString());
            }
            if (childCount > 0) {
                for (int i = 0; i < childCount; i++) {
                    printViewTree(info.getChild(i), performClick);
                }
            } else {
                if (performClick && info.isClickable()) {
                    info.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            }
        }
    }

    @SuppressLint("NewApi")
    private void closeRedPacketDetailActivity() {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        Log.i(TAG, " 打印红包详情页信息--------:");
        printViewTree(nodeInfo, false);

    }
    /**
     * 关闭红包详情界面,实现自动返回聊天窗口
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void close() {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo != null) {
            //bia bic bif bil bio bm7  bmc  h4 gj
            //为了演示,直接查看了关闭按钮的id
            List<AccessibilityNodeInfo> infos = nodeInfo.findAccessibilityNodeInfosByViewId("@id/text1");
            Log.i(TAG, "试图关闭详情页时，寻找指定id的view  :" + "---text1----" );
            nodeInfo.recycle();
            for (AccessibilityNodeInfo item : infos) {
                Log.i(TAG, "试图关闭详情页时，寻找指定id的view  :" + "---text1--找到了一个--" );
                if(item.getText()==null){
                    item.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }else{
                    Log.i(TAG, "试图关闭详情页时，classname text:" + "-------" + item.getText().toString());
                }

            }
        }
    }
    private void printParents(String text){
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if(nodeInfo!=null){
            Log.i(TAG, "printParents--root，classname text:" + "-------" + nodeInfo.getClassName()+"--------"+nodeInfo.getViewIdResourceName());
            List<AccessibilityNodeInfo> lists=nodeInfo.findAccessibilityNodeInfosByText(text);
            if(lists!=null&& lists.size()>0){
                nodeInfo = lists.get(0);
                while (nodeInfo!=null){
                    Log.i(TAG, "printParents，classname text:" + "-------" + nodeInfo.getClassName()+"--------"+nodeInfo.getViewIdResourceName());
                    nodeInfo=nodeInfo.getParent();
                }
            }
        }


    }


}
