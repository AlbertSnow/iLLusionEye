package com.albert.snow.illusioneye

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.opengl.GLSurfaceView
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.annotation.NonNull
import com.albert.snow.illusioneye.opengl.Texture2dProgram
import com.albert.snow.illusioneye.util.CameraUtils
import com.albert.snow.illusioneye.util.PermissionHelper
import com.albert.snow.illusioneye.widget.AspectFrameLayout
import com.albert.snow.illusioneye.widget.preview.FetchFrameRender
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference

const val FILTER_NONE = 0
val FILTER_BLACK_WHITE = 1
val FILTER_BLUR = 2
val FILTER_SHARPEN = 3
val FILTER_EDGE_DETECT = 4
val FILTER_EMBOSS = 5
class MainActivity : AppCompatActivity(), SurfaceTexture.OnFrameAvailableListener{

    private var mFrameImageView: ImageView? = null
    private val TAG = "MainActivity"
    private val VERBOSE = false

    // Camera filters; must match up with cameraFilterNames in strings.xml

    private var mGLView: GLSurfaceView? = null
    private var mRenderer: FetchFrameRender? = null
    private var mCamera: Camera? = null
    private var mCameraHandler: CameraHandler? = null
    private var mRecordingEnabled: Boolean = false      // controls button state

    private var mCameraPreviewWidth: Int = 0
    private var mCameraPreviewHeight:Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val outputFile = File(filesDir, "camera-test.mp4")
        val fileText = findViewById(R.id.cameraOutputFile_text) as TextView
        fileText.setText(outputFile.toString())

        val spinner = findViewById(R.id.cameraFilter_spinner) as Spinner
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.cameraFilterNames, android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // Apply the adapter to the spinner.
        spinner.adapter = adapter
//        spinner.setOnItemSelectedListener(this)

        // Define a handler that receives camera-control messages from other threads.  All calls
        // to Camera must be made on the same thread.  Note we create this before the renderer
        // thread, so we know the fully-constructed object will be visible.
        mCameraHandler = CameraHandler(this)

//        mRecordingEnabled = sVideoEncoder.isRecording()

        // Configure the GLSurfaceView.  This will start the Renderer thread, with an
        // appropriate EGL context.
        mGLView = findViewById(R.id.cameraPreview_surfaceView) as GLSurfaceView
        mGLView!!.setEGLContextClientVersion(2)     // select GLES 2.0
        mRenderer = FetchFrameRender(mCameraHandler, glCallback)
        mGLView!!.setRenderer(mRenderer)
        mGLView!!.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        initView();

        Log.d(TAG, "onCreate complete: $this")
    }

    private fun initView() {
        mFrameImageView  = findViewById<ImageView>(R.id.main_frame_image)
    }

    private val glCallback = Texture2dProgram.GLCallback {
        mFrameImageView?.post {
            mFrameImageView?.setImageBitmap(it)
        }
    }


    override fun onResume() {
        Log.d(TAG, "onResume -- acquiring camera")
        super.onResume()
        updateControls()

        if (PermissionHelper.hasCameraPermission(this)) {
            if (mCamera == null) {
                openCamera(1280, 720)      // updates mCameraPreviewWidth/Height
            }

        } else {
            PermissionHelper.requestCameraPermission(this, false)
        }

        mGLView!!.onResume()
        mGLView!!.queueEvent {
            mRenderer!!.setCameraPreviewSize(
                mCameraPreviewWidth,
                mCameraPreviewHeight
            )
        }
        Log.d(TAG, "onResume complete: $this")
    }

    override fun onPause() {
        Log.d(TAG, "onPause -- releasing camera")
        super.onPause()
        releaseCamera()
        mGLView!!.queueEvent {
            // Tell the renderer that it's about to be paused so it can clean up.
            mRenderer!!.notifyPausing()
        }
        mGLView!!.onPause()
        Log.d(TAG, "onPause complete")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        mCameraHandler!!.invalidateHandler()     // paranoia
    }

    override fun onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array<String>, @NonNull grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!PermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(
                this,
                "Camera permission is needed to run this application", Toast.LENGTH_LONG
            ).show()
            PermissionHelper.launchPermissionSettings(this)
            finish()
        } else {
            openCamera(1280, 720)      // updates mCameraPreviewWidth/Height

        }
    }
//
//    // spinner selected
//    override fun onItemSelected(parent: AdapterView<*>, view: View, pos: Int, id: Long) {
//        val spinner = parent as Spinner
//        val filterNum = spinner.selectedItemPosition
//
//        Log.d(TAG, "onItemSelected: $filterNum")
//        mGLView!!.queueEvent {
//            // notify the renderer that we want to change the encoder's state
//            mRenderer!!.changeFilterMode(filterNum)
//        }
//    }
//
//    override fun onNothingSelected(parent: AdapterView<*>) {}

    /**
     * Opens a camera, and attempts to establish preview mode at the specified width and height.
     *
     *
     * Sets mCameraPreviewWidth and mCameraPreviewHeight to the actual width/height of the preview.
     */
    private fun openCamera(desiredWidth: Int, desiredHeight: Int) {
        if (mCamera != null) {
            throw RuntimeException("camera already initialized")
        }

        val info = Camera.CameraInfo()

        // Try to find a front-facing camera (e.g. for videoconferencing).
        val numCameras = Camera.getNumberOfCameras()
        for (i in 0 until numCameras) {
            Camera.getCameraInfo(i, info)
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mCamera = Camera.open(i)
                break
            }
        }
        if (mCamera == null) {
            Log.d(TAG, "No front-facing camera found; opening default")
            mCamera = Camera.open()    // opens first back-facing camera
        }
        if (mCamera == null) {
            throw RuntimeException("Unable to open camera")
        }

        val parms = mCamera?.parameters
        if (parms == null) {
            return;
        }


        CameraUtils.choosePreviewSize(parms, desiredWidth, desiredHeight)

        // Give the camera a hint that we're recording video.  This can have a big
        // impact on frame rate.
        parms.setRecordingHint(true)

        // leave the frame rate set to default
        mCamera?.parameters = parms

        val fpsRange = IntArray(2)
        val mCameraPreviewSize = parms.previewSize
        parms.getPreviewFpsRange(fpsRange)
        var previewFacts = mCameraPreviewSize.width.toString() + "x" + mCameraPreviewSize.height
        if (fpsRange[0] == fpsRange[1]) {
            previewFacts += " @" + fpsRange[0] / 1000.0 + "fps"
        } else {
            previewFacts += " @[" + fpsRange[0] / 1000.0 +
                    " - " + fpsRange[1] / 1000.0 + "] fps"
        }
        val text = findViewById(R.id.cameraParams_text) as TextView
        text.text = previewFacts

        mCameraPreviewWidth = mCameraPreviewSize.width
        mCameraPreviewHeight = mCameraPreviewSize.height


        val layout = findViewById(R.id.cameraPreview_afl) as AspectFrameLayout

        val display = (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay

        if (display.rotation == Surface.ROTATION_0) {
            mCamera?.setDisplayOrientation(90)
            layout.setAspectRatio(mCameraPreviewHeight.toDouble() / mCameraPreviewWidth)
        } else if (display.rotation == Surface.ROTATION_270) {
            layout.setAspectRatio(mCameraPreviewHeight.toDouble() / mCameraPreviewWidth)
            mCamera?.setDisplayOrientation(180)
        } else {
            // Set the preview aspect ratio.
            layout.setAspectRatio(mCameraPreviewWidth.toDouble() / mCameraPreviewHeight)
        }
    }

    /**
     * Stops camera preview, and releases the camera to the system.
     */
    private fun releaseCamera() {
        if (mCamera != null) {
            mCamera?.stopPreview()
            mCamera?.release()
            mCamera = null
            Log.d(TAG, "releaseCamera -- done")
        }
    }

    /**
     * onClick handler for "record" button.
     */
    fun clickToggleRecording(unused: View) {
        mRecordingEnabled = !mRecordingEnabled
        mGLView!!.queueEvent {
            // notify the renderer that we want to change the encoder's state
//            mRenderer!!.changeRecordingState(mRecordingEnabled)
        }
        updateControls()
    }

//    /**
//     * onClick handler for "rebind" checkbox.
//     */
//    public void clickRebindCheckbox(View unused) {
//        CheckBox cb = (CheckBox) findViewById(R.id.rebindHack_checkbox);
//        TextureRender.sWorkAroundContextProblem = cb.isChecked();
//    }

    /**
     * Updates the on-screen controls to reflect the current state of the app.
     */
    private fun updateControls() {
        val toggleRelease = findViewById(R.id.toggleRecording_button) as Button
        val id = if (mRecordingEnabled)
            R.string.toggleRecordingOff
        else
            R.string.toggleRecordingOn
        toggleRelease.setText(id)

        //CheckBox cb = (CheckBox) findViewById(R.id.rebindHack_checkbox);
        //cb.setChecked(TextureRender.sWorkAroundContextProblem);
    }

    /**
     * Connects the SurfaceTexture to the Camera preview output, and starts the preview.
     */
    private fun handleSetSurfaceTexture(st: SurfaceTexture) {
        st.setOnFrameAvailableListener(this)
        try {
            mCamera?.setPreviewTexture(st)
        } catch (ioe: IOException) {
            throw RuntimeException(ioe)
        }

        mCamera?.startPreview()
    }

    override fun onFrameAvailable(st: SurfaceTexture) {
        // The SurfaceTexture uses this to signal the availability of a new frame.  The
        // thread that "owns" the external texture associated with the SurfaceTexture (which,
        // by virtue of the context being shared, *should* be either one) needs to call
        // updateTexImage() to latch the buffer.
        //
        // Once the buffer is latched, the GLSurfaceView thread can signal the encoder thread.
        // This feels backward -- we want recording to be prioritized over rendering -- but
        // since recording is only enabled some of the time it's easier to do it this way.
        //
        // Since GLSurfaceView doesn't establish a Looper, this will *probably* execute on
        // the main UI thread.  Fortunately, requestRender() can be called from any thread,
        // so it doesn't really matter.
        if (VERBOSE) Log.d(TAG, "ST onFrameAvailable")
        mGLView!!.requestRender()
    }

    /**
     * Handles camera operation requests from other threads.  Necessary because the Camera
     * must only be accessed from one thread.
     *
     *
     * The object is created on the UI thread, and all handlers run there.  Messages are
     * sent from other threads, using sendMessage().
     */
    public class CameraHandler(activity: MainActivity) : Handler() {
        val TAG = "CameraHandler"

        // Weak reference to the Activity; only access this from the UI thread.
        private val mWeakActivity: WeakReference<MainActivity>

        init {
            mWeakActivity = WeakReference<MainActivity>(activity)
        }

        /**
         * Drop the reference to the activity.  Useful as a paranoid measure to ensure that
         * attempts to access a stale Activity through a handler are caught.
         */
        fun invalidateHandler() {
            mWeakActivity.clear()
        }

        override// runs on UI thread
        fun handleMessage(inputMessage: Message) {
            val what = inputMessage.what
            Log.d(TAG, "CameraHandler [$this]: what=$what")

            val activity = mWeakActivity.get()
            if (activity == null) {
                Log.w(TAG, "CameraHandler.handleMessage: activity is null")
                return
            }

            when (what) {
                MSG_SET_SURFACE_TEXTURE -> activity.handleSetSurfaceTexture(inputMessage.obj as SurfaceTexture)
                else -> throw RuntimeException("unknown msg $what")
            }
        }

        companion object {
            val MSG_SET_SURFACE_TEXTURE = 0
        }
    }

    fun doCapture(view: View) {
        mRenderer?.captureFrame()
    }

}