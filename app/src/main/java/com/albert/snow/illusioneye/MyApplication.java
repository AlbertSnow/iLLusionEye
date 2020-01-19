package com.albert.snow.illusioneye;

import android.app.Application;
import android.content.Context;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;

import com.albert.snow.illusioneye.util.ScriptC_flip;

public class MyApplication extends Application {

    public static MyApplication instance;

    private ScriptC_flip flipScript;
    private RenderScript renderScript;


    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        instance = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initRenderScript();
    }

    public ScriptC_flip getFlipScript() {
        return flipScript;
    }

    public RenderScript getRenderScript() {
        return renderScript;
    }

    private void initRenderScript() {
        renderScript = RenderScript.create(this);
        flipScript = new ScriptC_flip(renderScript);
    }

    private void releaseRenderScript() {
        renderScript.destroy();
        flipScript.destroy();
    }

}
