package com.example.nabeo.locationhistory;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Created by jkm15 on 2017/09/27.
 */

public class AsyncUpdater extends AsyncTask<String, Void, Integer> {

    private Context context;
    private ProgressDialog progressDialog;
    private static Integer HTTP_ERROR = 5795;

    AsyncUpdater(Context context) {
        this.context = context;
    }

    @Override
    protected Integer doInBackground(String[] params) {
        String location = "";
//        try {
//            InputStream is = context.getAssets().open("locations.txt");
//            BufferedReader br = new BufferedReader(new InputStreamReader(is));
//            Map<String, Integer> map = new HashMap<>();
//            String line = "";
//            while ((line = br.readLine()) != null) {
//                map.put(line.split(":")[0], Integer.valueOf(line.split(":")[1]));
//            }
//            if (map.containsKey(params[0])) {
//                location = map.get(params[0]);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        //String uuid = ((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId();
        String uuid = getUniqueID();
        location = params[0];

        Log.d("URL", "http://160.16.124.241:49816/location/database/update?uuid=" + uuid + "&location=" + location);

        HttpURLConnection conn = null;
        try {
            //Log.d("URL", "http://160.16.124.241:49816/location/database/update?uuid=" + uuid + "&location=" + location);
            conn = (HttpURLConnection) new URL("http://160.16.124.241:49816/location/database/update?uuid=" + uuid + "&location=" + location).openConnection();
            conn.connect();

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return HTTP_ERROR;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) conn.disconnect();
        }

        return 0;
    }

    private String getUniqueID(){
        String myAndroidDeviceId = "";
        TelephonyManager mTelephony = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (mTelephony.getDeviceId() != null){
            myAndroidDeviceId = mTelephony.getDeviceId();
        }else{
            myAndroidDeviceId = Settings.Secure.getString(context.getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        }
        return myAndroidDeviceId;
    }

    @Override
    protected void onPostExecute(Integer i) {
        //this.progressDialog.dismiss();
        if (equals(i, HTTP_ERROR)) {
            Toast.makeText(context, "サーバとの通信に失敗しました", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPreExecute() {
//        this.progressDialog = new ProgressDialog(context);
//        this.progressDialog.setMessage("送信中...");
//        this.progressDialog.setCancelable(false);
//        this.progressDialog.show();
        Toast.makeText(context, "送信中", Toast.LENGTH_SHORT).show();
    }

    public static boolean equals(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }
}
