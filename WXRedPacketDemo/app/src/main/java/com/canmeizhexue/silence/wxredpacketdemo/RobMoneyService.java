package com.canmeizhexue.silence.wxredpacketdemo;

import android.accessibilityservice.AccessibilityService;
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
import android.widget.Toast;

import java.util.List;

public class RobMoneyService extends AccessibilityService {
	private static final String TAG="RobMoneyService---zyp-";
	private long pretime;

	@Override
	protected void onServiceConnected() {
		Log.i(TAG, "onServiceConnected:");
	}

	@Override
	public void onAccessibilityEvent(AccessibilityEvent event) {
		Log.i(TAG, "onAccessibilityEvent:");
		int eventType = event.getEventType();
		switch (eventType) {
		//第一步：监听通知栏消息
		case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
			List<CharSequence> texts = event.getText();
			if (!texts.isEmpty()) {
				for (CharSequence text : texts) {
					String content = text.toString();
					Log.i(TAG, "text:"+content);
					if (content.contains("[微信红包]")) {
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
			break;
		//第二步：监听是否进入微信红包消息界面
		case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
			String className = event.getClassName().toString();
			Log.i(TAG, "AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED-----:"+className);
			if (className.equals("com.tencent.mm.ui.LauncherUI")) {
				Log.i(TAG, "开始抢红包:");
				//开始抢红包,弹出拆开红包的对话框
				getPacket();
			} else if (className.equals("com.tencent.mm.plugin.luckymoney.ui.En_fba4b94f")) {
				long now = System.currentTimeMillis();

				//因为这个地方有混淆，，所以不对应了，，com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI
				//开始打开红包
				Log.i(TAG, "开始打开红包----openPacket--:"+(now-pretime));
				openPacket();
			}else if(className.equals("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI")){
				//详情页面，现在我们要关闭它，
				long now = System.currentTimeMillis();
				MainActivity.paramEntity.spendTimeInMs = now-pretime;
				sendBroadcast(new Intent(MainActivity.ACTION));
				Log.i(TAG, "进入红包详情页--共花费时间--:"+MainActivity.paramEntity.spendTimeInMs +"  ms");
				Toast.makeText(this, "进入详情页一共花费时间"+MainActivity.paramEntity.spendTimeInMs +"  ms", Toast.LENGTH_SHORT).show();
//				closeRedPacketDetailActivity();
			}
			break;
			case AccessibilityEvent.TYPE_VIEW_CLICKED:
//				Log.i(TAG, "AccessibilityEvent.按钮被点击了，主要是用来找到view的id-----:" );
				AccessibilityNodeInfo accessibilityNodeInfo=event.getSource();
				if(accessibilityNodeInfo!=null){
//					Log.i(TAG, "AccessibilityEvent.按钮被点击了，主要是用来找到view的id-----:"+accessibilityNodeInfo.toString() );
				}
				break;
		}
	}

	/**
	 * 查找到
	 */
	@SuppressLint("NewApi")
	private void openPacket() {
		final AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
		if (nodeInfo != null) {
			Log.i(TAG, "要打开红包了---能找到带特定字的view吗？---打印一下view树:");
			printViewTree(nodeInfo,false);
			//抢红包
			final List<AccessibilityNodeInfo> list = nodeInfo
					.findAccessibilityNodeInfosByText("開");
			Log.i(TAG, "要打开红包了----延时毫秒数-- ---"+MainActivity.paramEntity.delayTimeInMs);
			if(list!=null && list.size()>0 ){
				Log.i(TAG, "要打开红包了------ 延迟一下再执行---");
				new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
					@Override
					public void run() {
						for (AccessibilityNodeInfo n : list) {
							n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
						}
					}
				},MainActivity.paramEntity.delayTimeInMs);
			}else{
				//目前是乱点的
				Log.i(TAG, "要打开红包了------ 没找到带開字的view，可能是张图片,那就乱点了---");
				new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
					@Override
					public void run() {
						printViewTree(nodeInfo,true);
					}
				},MainActivity.paramEntity.delayTimeInMs);

			}

		}

	}

	@SuppressLint("NewApi")
	private void getPacket() {
		AccessibilityNodeInfo rootNode = getRootInActiveWindow();
		recycle(rootNode);
	}
	
	/**
	 * 找到聊天页面的领取红包按钮，然后点击，之后会弹出红包对话框
	 * @param info
	 */
	@SuppressLint("NewApi")
	public void recycle(AccessibilityNodeInfo info) {
//		Log.i(TAG, "recycle"+",info.getChildCount() == "+info.getChildCount() );
        if (info.getChildCount() == 0) {
        	if(info.getText() != null){
        		if("领取红包".equals(info.getText().toString())){
        			//这里有一个问题需要注意，就是需要找到一个可以点击的View
//                	Log.i(TAG, "Click"+",isClick:"+info.isClickable());
                	info.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                	AccessibilityNodeInfo parent = info.getParent();
                	while(parent != null){
//                		Log.i(TAG, "parent isClick:"+parent.isClickable());
                		if(parent.isClickable()){
                			parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                			break;
                		}
                		parent = parent.getParent();
                	}
                	
            	}
        	}
        	
        } else {  
            for (int i = 0; i < info.getChildCount(); i++) {  
                if(info.getChild(i)!=null){  
                    recycle(info.getChild(i));  
                }  
            }  
        }  
    }  

	@Override
	public void onInterrupt() {
	}
	@SuppressLint("NewApi")
	private void printViewTree(AccessibilityNodeInfo info,boolean performClick){
		if(info!=null ){
			int childCount = info.getChildCount();
			Log.i(TAG, "classname isClick:" +info.getClassName()+"-----"+info.isClickable()+"-------"+info.getViewIdResourceName());
			if(info.getText()!=null){
				Log.i(TAG, "classname text:" + "-------"+info.getText().toString() );
			}
			if(childCount>0){
				for(int i=0;i<childCount;i++){
					printViewTree(info.getChild(i),performClick);
				}
			}else{
				if(performClick && info.isClickable()){
					info.performAction(AccessibilityNodeInfo.ACTION_CLICK);
				}
			}
		}
	}
	@SuppressLint("NewApi")
	private void closeRedPacketDetailActivity(){
		AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
		Log.i(TAG, " 打印红包详情页信息--------:");
		printViewTree(nodeInfo,false);

	}
	/**
	 * 关闭红包详情界面,实现自动返回聊天窗口
	 */
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	private void close() {
		AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
		if (nodeInfo != null) {
			//TODO 为了演示,直接查看了关闭按钮的id
			List<AccessibilityNodeInfo> infos = nodeInfo.findAccessibilityNodeInfosByViewId("@id/ez");
			nodeInfo.recycle();
			for (AccessibilityNodeInfo item : infos) {
				item.performAction(AccessibilityNodeInfo.ACTION_CLICK);
			}
		}
	}
	
}
