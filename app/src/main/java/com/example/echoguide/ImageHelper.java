package com.example.echoguide;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class ImageHelper {

    public static Bitmap getBitmapFromBytes(byte[] bytes) {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
}
