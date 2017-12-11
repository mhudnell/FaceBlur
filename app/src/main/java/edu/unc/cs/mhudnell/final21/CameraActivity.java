package edu.unc.cs.mhudnell.final21;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Camera;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class CameraActivity extends Activity {

    private static final String TAG = "FaceBlur::CameraAct";
    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    private int captureState = STATE_PREVIEW;
    private SQLiteDatabase db = null;
    private ImageButton captureButton;
    private ImageButton galleryButton;
    private TextureView tv;
    private Size previewSize;
    private Size imageSize;
    private ImageReader imageReader;
    private File imageFile;
    private String lastPath;
//    private String imageFileName;
    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new
            ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                backgroundHandler.post(new ImageSaver(reader.acquireLatestImage()));
                }
            };
    private  class ImageSaver implements Runnable {

        private final Image image;
        public ImageSaver(Image image) {
            this.image = image;
        }

        @Override
        public void run() {
            ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);

            FileOutputStream fileOutputStream = null;
            try {
                if(lastPath != null && lastPath.equals(imageFile.getAbsolutePath()))
                    return;
                Log.v(TAG, imageFile.getAbsolutePath());
                fileOutputStream = new FileOutputStream(imageFile.getAbsolutePath());
                fileOutputStream.write(bytes);
                lastPath = imageFile.getAbsolutePath();
            } catch(IOException e) {
                e.printStackTrace();
            } finally {
                image.close();
                if(fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
    }
    private String cameraId;
    private CaptureRequest.Builder captureRequestBuilder;
    private CameraCaptureSession previewCaptureSession;
    private CameraCaptureSession.CaptureCallback previewCaptureCallback = new
            CameraCaptureSession.CaptureCallback() {
                private void process(CaptureResult captureResult) {
                    switch (captureState) {
                        case STATE_PREVIEW:
                            // Do nothing
                            break;
                        case STATE_WAIT_LOCK:
                            captureState = STATE_PREVIEW;
                            Integer afState = captureResult.get(CaptureResult.CONTROL_AF_STATE);
                            if(afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                                Toast.makeText(getApplicationContext(), "Image Captured!", Toast.LENGTH_SHORT).show();
                                startStillCaptureRequest();
                            }
                            break;
                    }
                }
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);

                    process(result);
                }
            };
    private TextureView.SurfaceTextureListener surfaceTextureListener=new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCamera(width, height);
            connectCamera();
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };
    private CameraDevice cameraDevice;
    private CameraDevice.StateCallback cameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            startPreview();
//            Toast.makeText(getApplicationContext(), "Camera connected", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
        }
    };
    private HandlerThread backgroundHandlerThread;
    private Handler backgroundHandler;
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    private int totalRotation;
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private static class CompareSizeByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() / (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);   // removes status bar
        setContentView(R.layout.activity_camera);


        db = openOrCreateDatabase("faceBlurDB", Context.MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS Photos (pid INTEGER PRIMARY KEY AUTOINCREMENT, orig_path TEXT, alt_path TEXT)");
//        db.execSQL("CREATE TABLE IF NOT EXISTS Tags (pid INTEGER, tag TEXT)");

        tv = (TextureView)findViewById(R.id.textureView);
        tv.setSurfaceTextureListener(surfaceTextureListener);
        captureButton = (ImageButton)findViewById(R.id.captureButton);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lockFocus();
            }
        });

        galleryButton = (ImageButton)findViewById(R.id.galleryButton);
        galleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(v.getContext(), GalleryActivity.class);
                startActivity(intent);
            }
        });
    }
    @Override
    public void onResume(){
        super.onResume();
        Log.v(TAG, "RESUME");
        startBackgroundThread();

        if(tv.isAvailable()){
            setupCamera(tv.getWidth(), tv.getHeight());
            connectCamera();
        } else {
            tv.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    protected  void onPause() {
        Log.v(TAG, "PAUSE");
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        if (!powerManager.isScreenOn()){    // Addresses bug in which onPause is sometimes called immediately after onResume
            Log.v(TAG, "CLOSE CAM AND THREAD");
            closeCamera();

            stopBackgroundThread();
        }

        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CAMERA_PERMISSION_RESULT) {
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(), "Application denied camera service", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for(String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT){
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
                int sensorOrientation = cameraCharacteristics.get(cameraCharacteristics.SENSOR_ORIENTATION);

                Log.v(TAG, "deviceOrientation: " + Integer.toString(deviceOrientation));
                Log.v(TAG, "sensorOrientation: " + Integer.toString(sensorOrientation));
                totalRotation = sensorToDeviceRotation(cameraCharacteristics, deviceOrientation);
                Log.v(TAG, "totalRotation: " + Integer.toString(totalRotation));

                boolean swapRotation = totalRotation == 90 || totalRotation == 270;
                int rotatedWidth = (swapRotation ? height : width);
                int rotatedHeight = (swapRotation ? width : height);
//                int rotatedWidth = width;
//                int rotatedHeight = height;

//                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);
                previewSize = getPreferredPreviewSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);
                imageSize = getPreferredPreviewSize(map.getOutputSizes(ImageFormat.JPEG), rotatedWidth, rotatedHeight);
                imageReader = ImageReader.newInstance(imageSize.getWidth(), imageSize.getHeight(), ImageFormat.JPEG, 1);
                imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);
                this.cameraId = cameraId;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void connectCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){ // ask permission if > Marshmallow
                if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(cameraId, cameraDeviceStateCallback, backgroundHandler);
                } else {
                    if(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                        Toast.makeText(this, "FaceBlur requires access to camera", Toast.LENGTH_SHORT).show();
                    }
                    requestPermissions(new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION_RESULT);

                }
            } else {
                cameraManager.openCamera(cameraId, cameraDeviceStateCallback, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startPreview() {
        Log.v(TAG, "startPreview");
        SurfaceTexture st = tv.getSurfaceTexture();
        st.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface previewSurface = new Surface(st);

        try {
//            int sensorOrientation = cameraCharacteristics.get(cameraCharacteristics.SENSOR_ORIENTATION);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);

            cameraDevice.createCaptureSession(Arrays.asList(previewSurface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            Log.v(TAG, "onConfigured");
                            previewCaptureSession = session;
                            try {
                                previewCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(getApplicationContext(), "Unable to setup camera preview", Toast.LENGTH_SHORT);
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startStillCaptureRequest() {
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(imageReader.getSurface());
//            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, totalRotation);
//            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 180);

            CameraCaptureSession.CaptureCallback stillCaptureCallback = new
                    CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                            super.onCaptureStarted(session, request, timestamp, frameNumber);

                            try {
                                createImageFile();
                            } catch(IOException e){
                                e.printStackTrace();
                            }
                        }
                    };
                    previewCaptureSession.capture(captureRequestBuilder.build(), stillCaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Size getPreferredPreviewSize(Size[] mapSizes, int width, int height){
        List<Size> collectorSizes = new ArrayList<>();
        for(Size option : mapSizes) {
            if(width > height) {
                if(option.getWidth() >= width && option.getHeight() >= height) {
                    collectorSizes.add(option);
                }
            } else {
                if(option.getWidth() >= height && option.getHeight() >= width) {
                    collectorSizes.add(option);
                }
            }
        }
        if(collectorSizes.size() > 0) {
            return Collections.min(collectorSizes, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                }
            });
        }
        return mapSizes[0];
    }
//    private Size chooseOptimalSize(Size[] choices, int width, int height){
//        List<Size> bigEnough = new ArrayList<Size>();
//        for(Size option: choices){
//            // check for 1. aspect ratio matches textureview 2. check value from preview sensor is big enough for tv
//            if(option.getHeight() == option.getWidth() * height / width &&
//                    option.getWidth() >= width && option.getHeight() >= height){
//                bigEnough.add(option);
//            }
//        }
//        if (bigEnough.size() > 0) {
//            return Collections.min(bigEnough, new CompareSizeByArea());
//        } else {
//            return choices[0];
//        }
//    }

    private void closeCamera() {
        if(cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void startBackgroundThread(){
        backgroundHandlerThread = new HandlerThread("FaceBlur");
        backgroundHandlerThread.start();
        backgroundHandler = new Handler(backgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread(){
        backgroundHandlerThread.quitSafely();
        try {
            backgroundHandlerThread.join();
            backgroundHandlerThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation){
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);
        return (sensorOrientation + deviceOrientation + 360) % 360;
    }

    private void lockFocus() {
        captureState = STATE_WAIT_LOCK;
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        try {
            previewCaptureSession.capture(captureRequestBuilder.build(), previewCaptureCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        imageFile = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        saveToDB(imageFile);
        printDB();
        return imageFile;
    }

    private void saveToDB(File file) {
        // insert entry into Photos table
        ContentValues cv = new ContentValues();
        cv.put("orig_path", file.getAbsolutePath());
        long pid = db.insert("Photos", null, cv);
    }

    private void printDB(){
        Cursor c = db.rawQuery("SELECT * FROM Photos", null);
        c.moveToFirst();
        for(int i = 0; i < c.getCount(); i++){
            String str = "";
            for(int j = 0; j < c.getColumnCount(); j++){
                str = str + c.getString(j) + " ";
            }
            Log.v("FACEBLUR_DEBUG_Photos"+i, str);
            c.moveToNext();
        }
    }
}