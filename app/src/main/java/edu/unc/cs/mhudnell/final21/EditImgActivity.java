package edu.unc.cs.mhudnell.final21;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.opencv.android.LoaderCallbackInterface;
import org.opencv.core.Core;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;


public class EditImgActivity extends AppCompatActivity {

    private static final String TAG = "FaceBlur::EditImgAct";
    private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
    private static final Scalar FACE_RECT_COLOR_SELECTED = new Scalar(255, 0, 0, 255);

    private String id;
    private SQLiteDatabase db;
    private ImageView iv;
    private Button btn_blur;
    private Button btn_save;
    private Button btn_revert;
    private int ivWidth = 1080;
    private int ivHeight = 1113;
    private Bitmap mBitmap;

    private File imageFile;
    private File mCascadeFile;
    private CascadeClassifier mJavaDetector;
    private Mat mGray;
    private Mat mRgba;
    private Mat mRgbaRect;
    private Rect[] facesArray;
    private Rect mSelectedFace;

    private float mRelativeFaceSize = 0.2f;
    private int mAbsoluteFaceSize = 0;

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            Log.i(TAG, "onManagerConnected");
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");

                    try {
                        // load cascade file from application resources
                        InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                        mJavaDetector.load( mCascadeFile.getAbsolutePath() );
                        if (mJavaDetector.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mJavaDetector = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());

                        detectFacesInImage(false, null);

                        cascadeDir.delete();

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }

//                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    Log.i(TAG, "OpenCV not loaded successfully");
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

//    static {
//        if(!OpenCVLoader.initDebug()){
//            Log.v(TAG, "OpenCV not loaded");
//        } else {
//            Log.v(TAG, "OpenCV loaded");
//        }
//    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_img);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Edit Image");

//        db = openOrCreateDatabase("faceBlurDB", Context.MODE_PRIVATE, null);
        db = openOrCreateDatabase("faceBlurDB", Context.MODE_PRIVATE, null);
        id = Integer.toString(getIntent().getExtras().getInt("id"));

        iv = (ImageView) findViewById(R.id.iv);
        btn_blur = (Button) findViewById(R.id.btn_blur);
        btn_save = (Button) findViewById(R.id.btn_save);
        btn_revert = (Button) findViewById(R.id.btn_revert);
        btn_blur.setEnabled(false);
        btn_save.setEnabled(false);
        iv.post(new Runnable() {
            @Override
            public void run() {
                ivWidth = iv.getWidth();
                ivHeight = iv.getHeight();
                Log.v(TAG,"ivWidth: "+ivWidth+", ivHeight: "+ivHeight);
            }
        });

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
//        iv.setRotation(90);

//        loadImage();
//        iv.setImageResource(R.drawable.placeholder);
    }

    @Override
    public void onResume()
    {
        super.onResume();
    }

    @Override
    protected void onPause() {
        db.close();
        super.onPause();
    }

    private void detectFacesInImage(boolean useOriginal, Bitmap bm){

        mGray = new Mat();
        mRgba = new Mat();
        if(bm == null)
            getBitmap(useOriginal);
        Bitmap bmp32 = mBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Utils.bitmapToMat(bmp32, mRgba);
//        mRgbaRect = mRgba.clone();
        Log.v(TAG, "mRgba size: "+mRgba.size().toString()+" channels: "+Integer.toString(mRgba.channels()));
        Imgproc.cvtColor(mRgba, mGray, Imgproc.COLOR_RGB2GRAY);
        Log.v(TAG, "mGray size: "+mGray.size().toString()+" channels: "+Integer.toString(mGray.channels()));

        if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
        }

        MatOfRect faces = new MatOfRect();

        if (mJavaDetector != null)
            mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                    new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());

        facesArray = faces.toArray();
//        for (int i = 0; i < facesArray.length; i++) {
//            Imgproc.rectangle(mRgbaRect, facesArray[i].tl(), facesArray[i].br(), FACE_RECT_COLOR, 3);
//            Log.v(TAG, "face"+Integer.toString(i)+":: x:"+facesArray[i].x+", y: "+facesArray[i].y);
//        }

//        Utils.matToBitmap(mRgbaRect, bmp);
//        iv.setImageBitmap(bmp);
        drawImage(null);

        int[] viewCoords = new int[2];
        iv.getLocationOnScreen(viewCoords);
        Log.v(TAG, "viewCoords[0]: "+viewCoords[0]+", viewCoords[1]: "+viewCoords[1]);

        final int[] bmCoords = getBitmapPosition(mBitmap);
        Log.v(TAG, "bmCoords[0]: "+bmCoords[0]+", bmCoords[1]: "+bmCoords[1]);

        iv.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                double bmX = (event.getX() - bmCoords[0])*(16.0/9.0);
                double bmY = (event.getY() - bmCoords[1])*(16.0/9.0);
                Log.v(TAG, "onTouch:: getX: "+event.getX()+" getY: "+event.getY());
                Log.v(TAG, "onTouch:: bmX: "+bmX+" bmY: "+bmY);
                for(Rect face : facesArray){
                    Point p = new Point(bmX, bmY);
                    if(face.contains(p)) {
                        drawImage(p);
                        Log.v(TAG, "face contains point");
                    }
                }
                return false;
            }
        });

        return;
    }

    private void drawImage(Point p){
        mRgbaRect = mRgba.clone();

        for (int i = 0; i < facesArray.length; i++) {
            Scalar rectColor;
            if(p != null && facesArray[i].contains(p)){
                rectColor = FACE_RECT_COLOR_SELECTED;
                mSelectedFace = facesArray[i];
                btn_blur.setEnabled(true);
            } else {
                rectColor = FACE_RECT_COLOR;
            }
            Imgproc.rectangle(mRgbaRect, facesArray[i].tl(), facesArray[i].br(), rectColor, 3);
//            Log.v(TAG, "face"+Integer.toString(i)+":: x:"+facesArray[i].x+", y: "+facesArray[i].y);
        }

        Utils.matToBitmap(mRgbaRect, mBitmap);
        iv.setImageBitmap(mBitmap);
    }

    private void loadImage() {
        Cursor c = db.rawQuery("SELECT * FROM Photos WHERE pid="+id, null);
        c.moveToFirst();
        String path = c.getString(1);
        Log.v(TAG, path);

        Uri uri = Uri.fromFile(new File(path));
        this.getContentResolver().notifyChange(uri, null);
        ContentResolver cr = this.getContentResolver();
        try {
            Bitmap bm = android.provider.MediaStore.Images.Media.getBitmap(cr, uri);
            iv.setImageBitmap(bm);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void getBitmap(boolean useOriginal) {
        Cursor c = db.rawQuery("SELECT * FROM Photos WHERE pid="+id, null);
        c.moveToFirst();
        String path;
        if(!useOriginal && c.getString(2) != null){
            path = c.getString(2);
            btn_revert.setEnabled(true);
        } else {
            path = c.getString(1);
            btn_revert.setEnabled(false);
        }
        Log.v(TAG, path);

        Uri uri = Uri.fromFile(new File(path));
        this.getContentResolver().notifyChange(uri, null);
        ContentResolver cr = this.getContentResolver();
//        Bitmap bm = null;
        try {
            mBitmap = android.provider.MediaStore.Images.Media.getBitmap(cr, uri);
//            iv.setImageBitmap(bm);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return;
    }

    private int[] getBitmapPosition(Bitmap bm) {
        // These holds the ratios for the ImageView and the bitmap
        double bitmapRatio  = ((double)bm.getWidth())/bm.getHeight();
//        Log.v(TAG, "iv.getWidth(): "+iv.getWidth());
//        Log.v(TAG, "iv.getHeight(): "+iv.getHeight());
//        Log.v(TAG, "getbmpos ivWidth: "+ivWidth);
//        Log.v(TAG, "getbmpos ivHeight: "+ivHeight);
//        double imageViewRatio  = ((double)iv.getWidth())/iv.getHeight();
        double imageViewRatio  = ((double)ivWidth)/ivHeight;
        int drawLeft = 0;
        int drawTop = 0;
        double drawHeight = 0;
        double drawWidth = 0;
        int pos[] = new int[2];

        if(bitmapRatio > imageViewRatio){
            drawLeft = 0;
//            drawHeight = (imageViewRatio/bitmapRatio) * iv.getHeight();
//            drawTop = (int)((iv.getHeight() - drawHeight)/2);
            drawHeight = (imageViewRatio/bitmapRatio) * ivHeight;
            drawTop = (int)((ivHeight - drawHeight)/2);
        } else {
            drawTop = 0;
//            drawWidth = (bitmapRatio/imageViewRatio) * iv.getWidth();
//            drawLeft = (int)((iv.getWidth() - drawWidth)/2);
            drawWidth = (bitmapRatio/imageViewRatio) * ivWidth;
            drawLeft = (int)((ivWidth - drawWidth)/2);
        }

        pos[0] = drawLeft;
        pos[1] = drawTop;

        return pos;
    }

    public void blurFace(View v){
        Mat mask = mRgba.submat(mSelectedFace);
        Imgproc.GaussianBlur(mask, mask, new Size(55, 55), 55); // or any other processing

        drawImage(null);
//        detectFacesInImage(false, mBitmap);
        btn_save.setEnabled(true);
        btn_revert.setEnabled(true);
    }

    public void revertToOriginal(View v){
        detectFacesInImage(true, null);
        Toast.makeText(getApplicationContext(), "Reverted to original", Toast.LENGTH_SHORT).show();
        btn_save.setEnabled(true);
        btn_revert.setEnabled(false);
        btn_revert.setEnabled(false);
    }

    public void saveChanges(View v){
        try {
            File file = createImageFile();
            saveToDB(file);
            printDB();
            Toast.makeText(getApplicationContext(), "Image saved", Toast.LENGTH_SHORT).show();
            btn_save.setEnabled(false);
            btn_blur.setEnabled(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deletePhoto(View v){
        String[] strArr = {id};
        db.delete("Photos", "pid=?", strArr);
        this.finish();
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
//        saveToDB(imageFile);
        return imageFile;
    }

    private void saveToDB(File file) {
        // update entry in Photos table
        ContentValues cv = new ContentValues();
        cv.put("alt_path", file.getAbsolutePath());

        String[] strArr = {id};
        db.update("Photos", cv, "pid=?", strArr);

        Utils.matToBitmap(mRgba, mBitmap);
        try{
            FileOutputStream fOut = new FileOutputStream(file);
            mBitmap.compress(Bitmap.CompressFormat.PNG, 85, fOut);
            fOut.flush();
            fOut.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
