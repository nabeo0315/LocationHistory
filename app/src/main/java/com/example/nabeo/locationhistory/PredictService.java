package com.example.nabeo.locationhistory;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import umich.cse.yctung.androidlibsvm.LibSVM;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.example.nabeo.locationhistory.MainActivity.writeFlag;

/**
 * Created by nabeo on 2017/09/27.
 */

public class PredictService extends IntentService {
    private MySQLiteOpenHelper hlpr;
    private SQLiteDatabase db;
    private Context context;
    private WifiManager manager;
    public final static String ROOT_DIR = Environment.getExternalStorageDirectory().toString();
    private boolean flag;
    private boolean finish;
    private boolean endFlag;
    private int count;
    private static File file;
    private StringBuilder sb;
    private DisplayToast displayToast;
    private Handler handler;
    private static DateFormat formatter;
    private LibSVM svm;
    private String SVM_DIR = ROOT_DIR +"/Calender";
    private String SVM_PREDICT_DATA = SVM_DIR + "/predict_data.txt";
    private String SVM_PREDICT_SCALED = SVM_DIR + "/predict_scaled.txt";
    private String SVM_SCALE = SVM_DIR + "/scale.txt";
    private String SVM_MODEL = SVM_DIR + "/model.model";
    private String SVM_OUTPUT = SVM_DIR + "/output.txt";
    private static String LOCATION_RESULT = ROOT_DIR + "/LocationHistory/location_history.txt";

    public PredictService(){
        super("PredictService");
        MainActivity.predictLocationFlag = true;
        this.context = MainActivity.context;
        this.hlpr = new MySQLiteOpenHelper(context);
        this.db = hlpr.getWritableDatabase();
        this.flag = false;
        this.finish = false;
        this.handler = new Handler();
        this.formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm");
        //this.svm = new LibSVM();
        System.loadLibrary("jnilibsvm");
    }

    public native void jniSvmPredict(String option);

    @Override
    protected void onHandleIntent(Intent intent) {
        count = 0;
        sb = new StringBuilder();
        manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        endFlag = intent.getBooleanExtra("arriveFlag", false);
        db.execSQL(hlpr.DROP_PREDICT_TABLE);
        db.execSQL(hlpr.CREATE_PREDICT_TABLE);

        if (new File(SVM_DIR).exists()) new File(SVM_DIR).mkdir();
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                //sb.append(count+1);
                for (ScanResult result : manager.getScanResults()) {
                    Cursor c = db.rawQuery("select id from bssid where mac = '" + result.BSSID + "'", null);
                    if (c.getCount() == 0) continue;
                    c.moveToNext();
                    db.execSQL("insert into predict(bssid_id, rssi, count) values (" + c.getInt(c.getColumnIndex("id")) + "," + result.level + "," + (count + 1) + ")");
                    //sb.append(" " + c.getInt(c.getColumnIndex("id")) + ":" + result.level);
                    //Log.v("data", c.getInt(c.getColumnIndex("id")) + "," + result.level + "," + (count + 1) );
                    c.close();

                    Log.v("predictService", "success");
                }
                //sb.append("\n");
                flag = true;
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        context.registerReceiver(broadcastReceiver, intentFilter);
        manager.startScan();
        while (!finish) {
            if (flag) {
                count++;
                if (count < 3) {//繰り返しの回数を指定
                    //publishProgress(count);
                    flag = false;
                    manager.startScan();
                } else {
                    finish = true;
                }
            }
            try {
                Thread.sleep(500);//繰り返しの多用を防止
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        context.unregisterReceiver(broadcastReceiver);
        for (int i = 1; i < 2; i++) {
            Cursor c = db.rawQuery("select * from predict where count =" + i + " order by bssid_id asc", null);
            sb.append(0);
            while (c.moveToNext()) {
                sb.append(" " + c.getInt(c.getColumnIndex("bssid_id")) + ":" + c.getInt(c.getColumnIndex("rssi")));
            }
            sb.append("\n");
        }

        try {
            // FileOutputStream out = this.context.openFileOutput("train_data.csv", Context.MODE_PRIVATE);
            //PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "UTF-8"));
            //writer.append(sb.toString());
            //writer.close();
            file = new File(SVM_PREDICT_DATA);
            if (file.exists()) {
                file.delete();
            }
            BufferedWriter bw = new BufferedWriter(new FileWriter(file));
            bw.write(sb.toString());
            bw.flush();
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Scale();

        try {
            jniSvmPredict(SVM_PREDICT_SCALED + " " + SVM_MODEL + " " + SVM_OUTPUT);
            //svm.predict(SVM_PREDICT_SCALED + " " + SVM_MODEL + " " + SVM_OUTPUT);
        } catch (Exception e) {
            e.printStackTrace();
        }


        PutStringToFile.writeLocationHistry(setResult(), PutStringToFile.NOW_LOCATION, endFlag);
        if(!MainActivity.writeFlag){
            MainActivity.startPredictTime = System.currentTimeMillis();
            MainActivity.writeFlag = true;
        }else{
            MainActivity.writeFlag = false;
        }
        MainActivity.predictLocationFlag = false;
    }

    private void Scale() {
        try {
            String[] args = {"-l", "-1", "-u", "0", "-r", SVM_SCALE, SVM_PREDICT_DATA};
            Scaler scaler = new Scaler();
            scaler.setOut_path(SVM_PREDICT_SCALED);
            scaler.run(args);

            //Scaller scaller = new Scaller().loadRange(new File(SVM_SCALE));
            //scaller.calcScaleFromFile(new File(SVM_PREDICT_BEFORE_SCALE), new File(SVM_PREDICT_DATA));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String setResult(){
        StringBuilder sb = new StringBuilder();
        LinkedHashMap<String, Integer> map = new LinkedHashMap<String, Integer>();
        Map.Entry<String, Integer> maxEntry = null;
        String room_name = " ";

        try{
            File file = new File(SVM_OUTPUT);
            FileReader filereader = new FileReader(file);
            StreamTokenizer st = new StreamTokenizer(filereader);

            while(st.nextToken()!= StreamTokenizer.TT_EOF){
                Log.v("st", String.valueOf(st.nval));
                Cursor c = db.rawQuery("select name from room where id='" + st.nval + "'", null);
                c.moveToNext();
                room_name = c.getString(c.getColumnIndex("name"));
                //sb.append(c.getString(c.getColumnIndex("name")) + "\n");
                if(map.containsKey(room_name)){
                    map.put(room_name, map.get(room_name) + 1);
                }else{
                    map.put(room_name, 1);
                }
            }

            for(Map.Entry<String, Integer> entry : map.entrySet()){
                if(maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0){
                    maxEntry = entry;
                }
            }

            sb.append(maxEntry.getKey());
            displayToast = new DisplayToast(context, sb.toString());
            handler.post(displayToast);
            Log.v("stringBuilder", sb.toString());
            room_name = sb.toString();
        }catch(FileNotFoundException e){
            System.out.println(e);
        }catch(IOException e){
            System.out.println(e);
        }

        AsyncUpdater asyncUpdater = new AsyncUpdater(context);
        asyncUpdater.execute(room_name);
        return room_name;
    }

//    public static void putResultToFile(String str){
//        long currentTime = System.currentTimeMillis();
//        String currentTimeFormatted = formatter.format(currentTime);
//        try {
//            file = new File(LOCATION_RESULT);
//            FileWriter fileWriter = new FileWriter(file, true);
//            fileWriter.write(currentTimeFormatted + " " + str + "\n");
//            fileWriter.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
}
