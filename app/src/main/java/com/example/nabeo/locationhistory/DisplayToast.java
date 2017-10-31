package com.example.nabeo.locationhistory;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

/**
 * Created by nabeo on 2017/08/25.
 */

public class DisplayToast implements Runnable {
    private Context context;
    String text;

    public DisplayToast(Context context, String text){
        this.context = context;
        this.text = text;
    }

    public void run(){
        Log.i("DisplayToast", "active");
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
        MainActivity.mainTv.setText(text);
    }
}
