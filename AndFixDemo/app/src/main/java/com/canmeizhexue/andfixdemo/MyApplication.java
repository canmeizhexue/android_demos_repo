package com.canmeizhexue.andfixdemo;

import android.app.Application;
import android.os.Environment;
import android.util.Log;

import com.alipay.euler.andfix.patch.PatchManager;

import java.io.File;
import java.io.IOException;

/**
 * <p>MyApplication类 概述，提供XXX功能</p>
 *
 * @author silence
 * @version 1.0 (2016-12-21 16:26)
 */
public class MyApplication extends Application{
    private static final String APATCH_PATH = "/out.apatch";
    private static final String TAG = "MyApplication";
    /**
     * patch manager
     */
    private PatchManager mPatchManager;
    @Override
    public void onCreate() {
        super.onCreate();
        // initialize
        mPatchManager = new PatchManager(this);
        mPatchManager.init("1.0");
        Log.d(TAG, "inited.");

        // load patch
        mPatchManager.loadPatch();
        Log.d(TAG, "apatch loaded.");

        // add patch at runtime
        try {
            // .apatch file path

            String patchFileString = Environment.getExternalStorageDirectory()
                    .getAbsolutePath() + APATCH_PATH;
            File file = new File(patchFileString);
            if(file.exists()){
                Log.d(TAG, "apatch:" + patchFileString + " 文件存在");
            }else{
                Log.d(TAG, "apatch:" + patchFileString + "  文件不存在");
            }
            Log.d(TAG, "apatch:" + patchFileString + "  .");
            mPatchManager.addPatch(patchFileString);
            Log.d(TAG, "apatch:" + patchFileString + " added.");
        } catch (IOException e) {
            Log.e(TAG, "", e);
        }
        Log.d(TAG, "apatch loaded.");
    }
}
