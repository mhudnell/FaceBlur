package edu.unc.cs.mhudnell.final21;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class GalleryActivity extends AppCompatActivity {

    private static final String TAG = "FaceBlur::GalleryAct";
    private GridView imageGrid;
    private ArrayList<Bitmap> bitmapList;
    private ArrayList<Integer> pidList;
    private SQLiteDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        getSupportActionBar().setTitle("FaceBlur Gallery");
//        db = openOrCreateDatabase("faceBlurDB", Context.MODE_PRIVATE, null);

        this.imageGrid = (GridView) findViewById(R.id.gallery_grid);
//        this.bitmapList = new ArrayList<Bitmap>();
//        this.pidList = new ArrayList<Integer>();

//        populateGallery();

//        this.imageGrid.setAdapter(new ImageAdapter(this, this.bitmapList));
//        this.imageGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//            public void onItemClick(AdapterView<?> parent, View v,
//                                    int position, long id) {
////                Toast.makeText(GalleryActivity.this, "" + position, Toast.LENGTH_SHORT).show();
//                Intent intent = new Intent(v.getContext(), EditImgActivity.class);
////                EditText editText = (EditText) findViewById(R.id.editText);
////                String message = editText.getText().toString();
//                intent.putExtra("id", pidList.get(position));
//                startActivity(intent);
//            }
//        });
    }
    @Override
    protected void onResume() {
        super.onResume();

        this.bitmapList = new ArrayList<Bitmap>();
        this.pidList = new ArrayList<Integer>();
        db = openOrCreateDatabase("faceBlurDB", Context.MODE_PRIVATE, null);
        populateGallery();
        this.imageGrid.setAdapter(new ImageAdapter(this, this.bitmapList));
        this.imageGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v,
                                    int position, long id) {
//                Toast.makeText(GalleryActivity.this, "" + position, Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(v.getContext(), EditImgActivity.class);
//                EditText editText = (EditText) findViewById(R.id.editText);
//                String message = editText.getText().toString();
                intent.putExtra("id", pidList.get(position));
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onPause() {
        db.close();
        super.onPause();
    }

    private void populateGallery() {
        Cursor c = db.rawQuery("SELECT * FROM Photos", null);
        c.moveToFirst();
        for(int i = 0; i < c.getCount(); i++){
            int pid = c.getInt(0);
            String path = c.getString(1);
            Uri uri = Uri.fromFile(new File(path));
            this.getContentResolver().notifyChange(uri, null);
            ContentResolver cr = this.getContentResolver();
            try {
                Bitmap bm = android.provider.MediaStore.Images.Media.getBitmap(cr, uri);

                this.pidList.add(pid);
                this.bitmapList.add(bm);
            } catch (IOException e) {
                e.printStackTrace();
            }
            c.moveToNext();
        }
    }

}
