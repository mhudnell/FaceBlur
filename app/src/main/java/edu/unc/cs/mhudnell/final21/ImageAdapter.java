package edu.unc.cs.mhudnell.final21;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import java.util.ArrayList;

/**
 * Created by maxhu on 11/28/2017.
 */

public class ImageAdapter extends BaseAdapter {

    private Context context;
    private ArrayList<Bitmap> bitmapList;

    public ImageAdapter(Context context, ArrayList<Bitmap> bitmapList){
        this.context = context;
        this.bitmapList = bitmapList;
    }

    @Override
    public int getCount() {
        return this.bitmapList.size();
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ImageView imageView;
        if (convertView == null) {
            imageView = new ImageView(this.context);
            imageView.setLayoutParams(new GridView.LayoutParams(200, 200));
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            imageView = (ImageView) convertView;
        }

        imageView.setImageBitmap(this.bitmapList.get(position));
        return imageView;
    }
}
