package com.example.nabeo.locationhistory;

import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * Created by nabeo on 2017/10/12.
 */

public class PutStringToFile {
    public static int NOW_LOCATION = 0, MOVE_STATE = 1;
    public static File file;
    private static long[] time = new long[2];
    public final static String ROOT_DIR = Environment.getExternalStorageDirectory().toString();
    private static DateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm");
    private static DateFormat formatter2 = new SimpleDateFormat("HH:mm:ss");
    private static String LOCATION_RESULT = ROOT_DIR + "/LocationHistory/location_history.txt";
    private static String room = " ";
    private static String state = " ";

    PutStringToFile(){

    }

    public static void writeLocationHistry(String str, int mode, boolean endFlag){
        long currentTime = System.currentTimeMillis();
        String currentTimeFormatted = formatter.format(currentTime);
        try {
            file = new File(LOCATION_RESULT) ;
            FileWriter fileWriter = new FileWriter(file, true);
            switch (mode) {
                case 0:
                    if(endFlag){
                        time[1] = currentTime;
                        room = str;
                        fileWriter.write(currentTimeFormatted + " " + room + "     所要時間 " + formatter2.format(currentTime - MainActivity.startPredictTime) + "\n");
                        fileWriter.write("--------------------------------------------------------------\n");
                        fileWriter.close();

                    }else {
                        time[0] = currentTime;
                        room = str;
                        fileWriter.write(currentTimeFormatted + " " + room + "\n");
                        fileWriter.close();
                    }
                break;
                case 1:
                    state = str;
                    fileWriter.write("                    " + state + "\n");
                    fileWriter.close();
                break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
