package com.albert.snow.illusioneye.widget.preview;

import android.opengl.GLSurfaceView;
import android.os.Handler;

import com.albert.snow.illusioneye.MainActivity;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class FetchFrameRender implements GLSurfaceView.Renderer {

    MainActivity.CameraHandler mCameraHandler;

    public FetchFrameRender(MainActivity.CameraHandler cameraHandler) {
        mCameraHandler = cameraHandler;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {

    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {

    }

    @Override
    public void onDrawFrame(GL10 gl) {

    }

    public void setCameraPreviewSize(int mCameraPreviewWidth, int mCameraPreviewHeight) {

    }

    public void notifyPausing() {


    }
}
