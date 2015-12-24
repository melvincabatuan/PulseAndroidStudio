package pt.chambino.p.pulse;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.MyCameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.videoio.Videoio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import pt.chambino.p.pulse.Pulse.Face;
import pt.chambino.p.pulse.dialog.BpmDialog;
import pt.chambino.p.pulse.dialog.ConfigDialog;
import pt.chambino.p.pulse.view.BpmView;
import pt.chambino.p.pulse.view.PulseView;

public class App extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "Pulse::App";

    private MyCameraBridgeViewBase camera;
    private BpmView bpmView;
    private PulseView pulseView;
    private Pulse pulse;

    private Paint faceBoxPaint;
    private Paint faceBoxTextPaint;

    private ConfigDialog configDialog;

    private Mat mRgba;

    private SurfaceHolder mHolder;

    private BaseLoaderCallback loaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {

                case LoaderCallbackInterface.SUCCESS:
                    loaderCallbackSuccess();
                    break;

                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    private void loaderCallbackSuccess() {
        System.loadLibrary("pulse");

        pulse = new Pulse();
        pulse.setFaceDetection(initFaceDetection);
        pulse.setMagnification(initMagnification);
        pulse.setMagnificationFactor(initMagnificationFactor);

        File dir = getDir("cascade", Context.MODE_PRIVATE);
        File file = createFileFromResource(dir, R.raw.lbpcascade_frontalface, "xml");
        pulse.load(file.getAbsolutePath());
        dir.delete();

        pulseView.setGridSize(pulse.getMaxSignalSize());

        camera.enableView();
    }

    private File createFileFromResource(File dir, int id, String extension) {
        String name = getResources().getResourceEntryName(id) + "." + extension;
        InputStream is = getResources().openRawResource(id);
        File file = new File(dir, name);

        try {
            FileOutputStream os = new FileOutputStream(file);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();
        } catch (IOException ex) {
            Log.e(TAG, "Failed to create file: " + file.getPath(), ex);
        }

        return file;
    }

    public App() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.app);

        camera = (MyCameraBridgeViewBase) findViewById(R.id.camera);
        // camera.setCvCameraViewListener(this);
        camera.SetCaptureFormat(Videoio.CV_CAP_ANDROID);
        camera.setMaxFrameSize(600, 600);
        mHolder = camera.getHolder();

        bpmView = (BpmView) findViewById(R.id.bpm);
        bpmView.setBackgroundColor(Color.DKGRAY);
        bpmView.setTextColor(Color.LTGRAY);

        pulseView = (PulseView) findViewById(R.id.pulse);

        faceBoxPaint = initFaceBoxPaint();
        faceBoxTextPaint = initFaceBoxTextPaint();
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    private static final String CAMERA_ID = "camera-id";
    private static final String FPS_METER = "fps-meter";
    private static final String FACE_DETECTION = "face-detection";
    private static final String MAGNIFICATION = "magnification";
    private static final String MAGNIFICATION_FACTOR = "magnification-factor";

    private boolean initFaceDetection = true;
    private boolean initMagnification = true;
    private int initMagnificationFactor = 100;

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        camera.setCameraId(savedInstanceState.getInt(CAMERA_ID));
        camera.setFpsMeter(savedInstanceState.getBoolean(FPS_METER));

        initFaceDetection = savedInstanceState.getBoolean(FACE_DETECTION, initFaceDetection);
        initMagnification = savedInstanceState.getBoolean(MAGNIFICATION, initMagnification);
        initMagnificationFactor = savedInstanceState.getInt(MAGNIFICATION_FACTOR, initMagnificationFactor);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(CAMERA_ID, camera.getCameraId());
        outState.putBoolean(FPS_METER, camera.isFpsMeterEnabled());

        // if OpenCV Manager is not installed, pulse hasn't loaded
        if (pulse != null) {
            outState.putBoolean(FACE_DETECTION, pulse.hasFaceDetection());
            outState.putBoolean(MAGNIFICATION, pulse.hasMagnification());
            outState.putInt(MAGNIFICATION_FACTOR, pulse.getMagnificationFactor());
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, loaderCallback);

        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onPause() {
        if (camera != null) {
            camera.disableView();
        }
        bpmView.setNoBpm();
        pulseView.setNoPulse();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.app, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.record:
                onRecord(item);
                return true;
            case R.id.switch_camera:
                camera.switchCamera();
                return true;
            case R.id.config:
                if (configDialog == null) configDialog = new ConfigDialog();
                configDialog.show(getFragmentManager(), null);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean recording = false;
    private List<Double> recordedBpms;
    private BpmDialog bpmDialog;
    private double recordedBpmAverage;

    private void onRecord(MenuItem item) {
        recording = !recording;
        if (recording) {
            item.setIcon(android.R.drawable.ic_media_pause);

            if (recordedBpms == null) recordedBpms = new LinkedList<Double>();
            else recordedBpms.clear();
        } else {
            item.setIcon(android.R.drawable.ic_media_play);

            recordedBpmAverage = 0;
            for (double bpm : recordedBpms) recordedBpmAverage += bpm;
            recordedBpmAverage /= recordedBpms.size();

            if (bpmDialog == null) bpmDialog = new BpmDialog();
            bpmDialog.show(getFragmentManager(), null);
        }
    }

    public double getRecordedBpmAverage() {
        return recordedBpmAverage;
    }

    public Pulse getPulse() {
        return pulse;
    }

    public MyCameraBridgeViewBase getCamera() {
        return camera;
    }

    private Rect noFaceRect;

    private Rect initNoFaceRect(int width, int height) {
        double r = pulse.getRelativeMinFaceSize();
        int x = (int) (width * (1. - r) / 2.);
        int y = (int) (height * (1. - r) / 2.);
        int w = (int) (width * r);
        int h = (int) (height * r);
        return new Rect(x, y, w, h);
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.d(TAG, "onCameraViewStarted(" + width + ", " + height + ")");
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        pulse.start(width, height);
        noFaceRect = initNoFaceRect(width, height);
    }

    @Override
    public void onCameraViewStopped() {
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        mRgba = inputFrame.rgba();

        pulse.onFrame(mRgba);

        Bitmap bmp = null;
        bmp = Bitmap.createBitmap(mRgba.cols(), mRgba.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mRgba, bmp);

        if (bmp != null) {
            Canvas canvas = mHolder.lockCanvas();

            if (canvas != null) {

                Face face = getCurrentFace(pulse.getFaces()); // TODO support multiple faces
                if (face != null) {
                    onFace(canvas, face);
                } else {
                    // draw no face box
                    canvas.drawPath(createFaceBoxPath(noFaceRect), faceBoxPaint);
                    canvas.drawText("Face here",
                            canvas.getWidth() / 2f,
                            canvas.getHeight() / 2f,
                            faceBoxTextPaint);

                    // no faces
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            bpmView.setNoBpm();
                            pulseView.setNoPulse();
                        }
                    });
                }
            }
        }
        bmp.recycle();

        return mRgba;
    }


    private int currentFaceId = 0;

    private Face getCurrentFace(Face[] faces) {
        Face face = null;

        if (currentFaceId > 0) {
            face = findFace(faces, currentFaceId);
        }

        if (face == null && faces.length > 0) {
            face = faces[0];
        }

        if (face == null) {
            currentFaceId = 0;
        } else {
            currentFaceId = face.getId();
        }

        return face;
    }

    private Face findFace(Face[] faces, int id) {
        for (Face face : faces) {
            if (face.getId() == id) {
                return face;
            }
        }
        return null;
    }

    private void onFace(Canvas canvas, Face face) {
        // grab face box
        Rect box = face.getBox();

        // draw face box
        canvas.drawPath(createFaceBoxPath(box), faceBoxPaint);

        if (pulse.hasFaceDetection() && !face.existsPulse()) {
            // draw hint text
            canvas.drawText("Hold still",
                    box.x + box.width / 2f,
                    box.y + box.height / 2f - 20,
                    faceBoxTextPaint);
            canvas.drawText("in a",
                    box.x + box.width / 2f,
                    box.y + box.height / 2f,
                    faceBoxTextPaint);
            canvas.drawText("bright place",
                    box.x + box.width / 2f,
                    box.y + box.height / 2f + 20,
                    faceBoxTextPaint);
        }

        // update views
        if (face.existsPulse()) {
            final double bpm = face.getBpm();
            final double[] signal = face.getPulse();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    bpmView.setBpm(bpm);
                    pulseView.setPulse(signal);
                }
            });
            if (recording) {
                recordedBpms.add(bpm);
            }
        } else {
            // no pulse
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    bpmView.setNoBpm();
                    pulseView.setNoPulse();
                }
            });
        }
    }

    private Paint initFaceBoxPaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setShadowLayer(2, 0, 0, Color.BLACK);
        return p;
    }

    private Paint initFaceBoxTextPaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setShadowLayer(2, 0, 0, Color.DKGRAY);
        p.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/ds_digital/DS-DIGIB.TTF"));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(20f);
        return p;
    }

    private Path createFaceBoxPath(Rect box) {
        float size = box.width * 0.25f;
        Path path = new Path();
        // top left
        path.moveTo(box.x, box.y + size);
        path.lineTo(box.x, box.y);
        path.lineTo(box.x + size, box.y);
        // top right
        path.moveTo(box.x + box.width, box.y + size);
        path.lineTo(box.x + box.width, box.y);
        path.lineTo(box.x + box.width - size, box.y);
        // bottom left
        path.moveTo(box.x, box.y + box.height - size);
        path.lineTo(box.x, box.y + box.height);
        path.lineTo(box.x + size, box.y + box.height);
        // bottom right
        path.moveTo(box.x + box.width, box.y + box.height - size);
        path.lineTo(box.x + box.width, box.y + box.height);
        path.lineTo(box.x + box.width - size, box.y + box.height);
        return path;
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "App Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://pt.chambino.p.pulse/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "App Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://pt.chambino.p.pulse/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }
}
