package com.example.nabeo.locationhistory;

import android.Manifest;
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.FileObserver;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static com.example.nabeo.locationhistory.MySQLiteOpenHelper.CREATE_BSSID_TABLE;
import static com.example.nabeo.locationhistory.MySQLiteOpenHelper.CREATE_ROOM_TABLE;
import static com.example.nabeo.locationhistory.MySQLiteOpenHelper.DROP_BSSID_TABLE;
import static com.example.nabeo.locationhistory.MySQLiteOpenHelper.DROP_ROOM_TABLE;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    public static Context context;
    public final static String ROOT_DIR = Environment.getExternalStorageDirectory().toString();
    private SQLiteDatabase db;
    public static TextView mainTv;
    private Button startButton;
    private Button stopButton;
    private TextView accX, accY, accZ, pressureText, textView_activity, textView_steps, textView_distance, s1, s2, s3, s4, s5 = null;
    private MySQLiteOpenHelper helper;
    private SensorManager sensorManager;
    private FileObserver outputObserver;
    private File file;
    private float[] magnetic = new float[3];
    private float[] gravity = new float[3];
    private float[] acc = new float[3];
    private float[] linear_acc = new float[3];
    private float[] globalAccValues = new float[3];
    private float[] globalLinearAccValues = new float[3];
    private float pressure;
    private float[] gyro = new float[3];
    private float[] globalGyroValues = new float[3];
    private float[] Values = new float[13];
    private List<Double> Xw_list = new ArrayList<Double>();
    private List<Double> Yw_list = new ArrayList<Double>();
    private List<Double> Zw_list = new ArrayList<Double>();
    private List<Double> Pressure_list  = new ArrayList<Double>();
    private List<String> activities_array = new ArrayList<String>();
    private List<String> tmp_array = new ArrayList<String>();
    private Timer startTimer;
    private Timer collectRawDataTimer;
    private Timer predictTimer;
    private Scaller scaller;
    private int storeValuesCount = 0;
    private int preparationTime = 2000; //カウントダウン後の準備時間(ミリ秒)
    private int collectRawDataInterval = 20; //20msの間隔
    private int predictInterval = 1000; //???msの間隔
    private int decisionCount = 5;
    private int windowSize = 250; //5秒分のデータ
    private double[] param = new double[5];
    private boolean startTimerFlag = false;
    private boolean nowCollectingTimerFlag = false;
    private boolean nowPredictingTimerFlag = false;
    private boolean fileObserverFlag = false;
    public static boolean predictLocationFlag = false;
    private int predictCount = 0;
    private int stop_label1_count = 0;
    private int walk_label2_count = 0;
    private int upstairs_label3_count = 0;
    private int downstairs_label4_count = 0;
    private int upELV_label5_count = 0;
    private int downELV_label6_count = 0;
    private int run_label7_count = 0;
    private int stop_count = 0;
    private String currentState, previousState = null;
    DecimalFormat df = new DecimalFormat("0.00000");
    DecimalFormat paramf = new DecimalFormat("0.00000000");

    private int currentVolume;

    private double distance = 0;
    private double distance_previous = 0;
    private double speed = 0;
    private long steps = 0;
    private double stride;

    private boolean firstFlag = true;
    private boolean firstLineFlag = true;
    private long startTime;
    private long elapsedTime;
    private long tmp_time = 0;
    //    private boolean isPredictEnd = true;
    private boolean isParam = false;
    private int estimateCounter = 0;
    private static final int MY_PERMISSIONS_REQUEST_ALL = 0;

    private boolean lock = false;
    private boolean arriveFlag = false;
    public static boolean writeFlag = false;
    public static long startPredictTime;

    private String Calendar = ROOT_DIR +"/Calender";
    private String LocationHistoryPath = ROOT_DIR + "/LocationHistory";
    private String BSSID_DB = Calendar + "/bssid_db.txt";
    private String ROOM_DB = Calendar + "/room_db.txt";
    private String dataPredictPath = LocationHistoryPath + "/" + "NowInfo.txt";
    private String outputPath = LocationHistoryPath + "/" + "output.txt";
    private String resultsPath = LocationHistoryPath + "/" + "results.csv";
    final private String modelPath = LocationHistoryPath + "/7state_model_windowSize_5s.scale.txt.model";
    final private String scale_path = LocationHistoryPath + "/scale_data_windowSize5s.txt";

    public native void jniSvmPredict(String option);

    static{
        System.loadLibrary("jnilibsvm");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = getApplicationContext();
        helper = new MySQLiteOpenHelper(this);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            // 必要な権限が許可されているかチェック
            if (!(checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)) {
                requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, MY_PERMISSIONS_REQUEST_ALL);
            }
        }

        mainTv = (TextView)findViewById(R.id.mainTv);
        accX = (TextView) findViewById(R.id.accX);
        accY = (TextView) findViewById(R.id.accY);
        accZ = (TextView) findViewById(R.id.accZ);
        pressureText = (TextView) findViewById(R.id.pressure);
        textView_activity = (TextView) findViewById(R.id.activity);
        textView_steps = (TextView) findViewById(R.id.steps);
        textView_distance = (TextView) findViewById(R.id.distance);
        s1 = (TextView) findViewById(R.id.s1);
        s2 = (TextView) findViewById(R.id.s2);
        s3 = (TextView) findViewById(R.id.s3);
        s4 = (TextView) findViewById(R.id.s4);
        s5 = (TextView) findViewById(R.id.s5);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        startButton = (Button)findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //buildDatabase();
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                movementState();
//                Intent intent = new Intent(MainActivity.this, PredictService.class);
//                PendingIntent pendingIntent = PendingIntent.getService(MainActivity.this, 0, intent, 0);
//                AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
//                am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 20*1000, pendingIntent);
            }
        });

        stopButton = (Button)findViewById(R.id.stopButton);
        stopButton.setEnabled(false);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                stopSensor();
                if (startTimerFlag) {
                    startTimer.cancel();
                    startTimerFlag = false;
                }
                if (nowCollectingTimerFlag) {
                    collectRawDataTimer.cancel();
                    nowCollectingTimerFlag = false;
                }
                if (nowPredictingTimerFlag) {
                    predictTimer.cancel();
                    nowPredictingTimerFlag = false;
                }
                outputObserver.stopWatching();
                Intent intent = new Intent(MainActivity.this, PredictService.class);
                PendingIntent pendingIntent = PendingIntent.getService(MainActivity.this, 0, intent, 0);
                AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
                am.cancel(pendingIntent);
            }
        });
    }

    public void movementState(){
        try {
            scaller = new Scaller().loadRange(new File(scale_path));
        } catch (IOException e) {
            e.printStackTrace();
        }

        createTemplateFile();

        startTimer = new Timer();
        startTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                startTimerFlag = true;
                //**** センサデータの収集開始 *****//
                collectRawDataTimer = new Timer();
                collectRawDataTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        storeValues(Values); //センサ値を記録
                        nowCollectingTimerFlag = true;
                        if (storeValuesCount > windowSize + 1) {
                            isParam = true;
                        }
                    }
                }, collectRawDataInterval, collectRawDataInterval); //一定間隔でデータを記録，特徴量生成
                //**** 移動状態推定の開始 *****//
                predictTimer = new Timer();
                predictTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        nowPredictingTimerFlag = true;
                        predictCount++;
                        if (isParam) {
                            if ((storeValuesCount > windowSize + 1) && !predictLocationFlag) {
                                createParam(); //特徴量生成 時間窓5秒
                                jniSvmPredict(dataPredictPath + " " + modelPath + " " + outputPath);
                            }
                            isParam = false;
                            if (!fileObserverFlag) {
                                outputObserver.startWatching();
                                fileObserverFlag = true;
                            }
                            if (firstFlag) {
//                                startTime = System.currentTimeMillis();
                                startTime = new Timestamp(System.currentTimeMillis()).getTime();
                                firstFlag = false;
                            }
//                            if(predictCount == 5){
//                                Intent intent = new Intent(MainActivity.this, PredictService.class);
//                                intent.putExtra("currentState", currentState);
//                                startService(intent);
//                            }
                        }
                    }
                }, predictInterval, predictInterval); //一定間隔で一定間隔で推定
            }
        }, preparationTime); //準備時間

        //推定した結果を読み込み，多数決後のものを時系列で記録する
        file = new File(resultsPath);

        outputObserver = new FileObserver(outputPath) {
            @Override
            public void onEvent(int event, String path) {
                if (event == FileObserver.CLOSE_WRITE) {
                    try {
                        BufferedReader bufferedReader = new BufferedReader(new FileReader(outputPath));
                        String str = bufferedReader.readLine();
                        bufferedReader.close();
//                        Log.d("str",String.valueOf(str));
                        if (str != null) {
                            int label = Integer.parseInt(str);
                            switch (label) {
                                case 1:
                                    activities_array.add("Stop");
                                    tmp_array.add("Stop");
                                    break;
                                case 2:
                                    activities_array.add("Walking");
                                    tmp_array.add("Walking");
                                    break;
                                case 3:
                                    activities_array.add("Upstairs");
                                    tmp_array.add("Upstairs");
                                    break;
                                case 4:
                                    activities_array.add("Downstairs");
                                    tmp_array.add("Downstairs");
                                    break;
                                case 5:
                                    activities_array.add("Up-Elevator");
                                    tmp_array.add("Up-Elevator");
                                    break;
                                case 6:
                                    activities_array.add("Down-Elevator");
                                    tmp_array.add("Down-Elevator");
                                    break;
                                case 7:
                                    activities_array.add("Running");
                                    tmp_array.add("Running");
                                    break;
                                default:
                                    break;
                            }

                            if (activities_array.size() == decisionCount) {
                                //どの状態が一番多いか判定
                                for (int i = 0; i < activities_array.size(); i++) {
                                    if (activities_array.get(i).equals("Stop")) {
                                        stop_label1_count++;
                                    } else if (activities_array.get(i).equals("Walking")) {
                                        walk_label2_count++;
                                    } else if (activities_array.get(i).equals("Upstairs")) {
                                        upstairs_label3_count++;
                                    } else if (activities_array.get(i).equals("Downstairs")) {
                                        downstairs_label4_count++;
                                    } else if (activities_array.get(i).equals("Up-Elevator")) {
                                        upELV_label5_count++;
                                    } else if (activities_array.get(i).equals("Down-Elevator")) {
                                        downELV_label6_count++;
                                    } else if (activities_array.get(i).equals("Running")) {
                                        run_label7_count++;
                                    }
                                }
                                int max = stop_label1_count;
                                currentState = "Stop";
                                if (max < walk_label2_count) {
                                    max = walk_label2_count;
                                    currentState = "Walking";
                                }
                                if (max < upstairs_label3_count) {
                                    max = upstairs_label3_count;
                                    currentState = "Upstairs";
                                }
                                if (max < downstairs_label4_count) {
                                    max = downstairs_label4_count;
                                    currentState = "Downstairs";
                                }
                                if (max < upELV_label5_count) {
                                    max = upELV_label5_count;
                                    currentState = "Up-Elevator";
                                }
                                if (max < downELV_label6_count) {
                                    max = downELV_label6_count;
                                    currentState = "Down-Elevator";
                                }
                                if (max < run_label7_count) {
                                    max = run_label7_count;
                                    currentState = "Running";
                                }

                                Log.d("Current Info.", "現在の移動状態：" + currentState + " カウント"+ "(" + currentState + "): " + max);

                                if(currentState.equals("Stop") && !lock){
                                    stop_count++;
                                    if(stop_count >= 3){
                                        Intent intent = new Intent(MainActivity.this, PredictService.class);
                                        intent.putExtra("arriveFlag", arriveFlag);
                                        startService(intent);
                                        stop_count = 0;
                                        lock = true;
                                    }
                                }else if(!currentState.equals("Stop")){
                                    lock = false;
                                    arriveFlag = true;
                                }

                                if(writeFlag){
                                    PutStringToFile.writeLocationHistry(currentState, PutStringToFile.MOVE_STATE, false);
                                }

                                elapsedTime = new Timestamp(System.currentTimeMillis()).getTime() - startTime;
//                                Log.d("elaspedTime",String.valueOf(elapsedTime));

                                double distance_difference = getDistance(steps)- distance_previous;
//                                Log.d("distance_difference", String.valueOf(distance_difference));
                                speed = getSpeed(distance_difference, (elapsedTime - tmp_time));
//                                Log.d("spped", String.valueOf(speed));
                                distance_previous = getDistance(steps);

                                BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8"));
                                if (firstLineFlag) {
                                    bufferedWriter.write("推定回数, " + "タイムスタンプ, " + "経過時間 (s), " + "状態, " + "多数決カウント, " + "候補1, " + "候補2, " + "候補3, " + "候補4, " + "候補5, " + "消費カロリー (kcal), " + "総消費カロリー (kcal), " + "歩数, " + "移動距離");
                                    bufferedWriter.write("\n");
                                    firstLineFlag = false;
                                }
                                bufferedWriter.write((estimateCounter + 1) + ", ");
                                bufferedWriter.write(new Timestamp(System.currentTimeMillis()).toString() + ", ");
                                int second = (int)(elapsedTime/1000);
                                int comma = (int)(elapsedTime%1000);

                                bufferedWriter.write(second + "." + comma + ", ");
                                bufferedWriter.write(currentState + ", ");
                                bufferedWriter.write(max + ", ");
                                bufferedWriter.write(activities_array.get(0) + ", " + activities_array.get(1) + ", " + activities_array.get(2) + ", " + activities_array.get(3) + ", " + activities_array.get(4) + ", ");
                                bufferedWriter.write(df.format(100) + ", ");
                                bufferedWriter.write(df.format(100) + ", ");
                                bufferedWriter.write(String.valueOf(steps) + ", ");
                                bufferedWriter.write(df.format((distance)));
                                bufferedWriter.write("\n");
                                bufferedWriter.close();
                                estimateCounter++;
                                replaceArray();
                                counterReset();
                                //endMonitor(move_state); //推定回数が規定に達したら測定終了
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();

        List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ALL);

        for (Sensor sensor : sensors) {
            if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
            }
            if (sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
            }
            if (sensor.getType() == Sensor.TYPE_GRAVITY) {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
            }
            if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
            }
            if (sensor.getType() == Sensor.TYPE_PRESSURE) {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
            }
            if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
            }
            if (sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                acc = event.values.clone();
                globalAccValues = convertGlobalValues(acc);
                Values[0] = globalAccValues[0];
                Values[1] = globalAccValues[1];
                Values[2] = globalAccValues[2];
                break;

            case Sensor.TYPE_LINEAR_ACCELERATION:
                linear_acc = event.values.clone();
                globalLinearAccValues = convertGlobalValues(linear_acc);
                Values[3] = globalLinearAccValues[0];
                Values[4] = globalLinearAccValues[1];
                Values[5] = globalLinearAccValues[2];
                accX.setText(String.valueOf(globalLinearAccValues[0]));
                accY.setText(String.valueOf(globalLinearAccValues[1]));
                accZ.setText(String.valueOf(globalLinearAccValues[2]));
                break;

            case Sensor.TYPE_GRAVITY:
                gravity = event.values.clone();
                break;

            case Sensor.TYPE_MAGNETIC_FIELD:
                magnetic = event.values.clone();
                break;

            case Sensor.TYPE_PRESSURE:
                pressure = event.values[0];
                Values[6] = pressure;
                pressureText.setText(String.valueOf(pressure));

                if (tmp_array.size() < 6) {
                    if (tmp_array.size() >= 1) {
                        s1.setText(tmp_array.get(0));
                    }
                    if (tmp_array.size() >= 2) {
                        s2.setText(tmp_array.get(1));
                    }
                    if (tmp_array.size() >= 3) {
                        s3.setText(tmp_array.get(2));
                    }
                    if (tmp_array.size() >= 4) {
                        s4.setText(tmp_array.get(3));
                    }
                    if (tmp_array.size() >= 5) {
                        s5.setText(tmp_array.get(4));
                    }
                }

                if (tmp_array.size() >= 6) {
                    s1.setText(tmp_array.get(tmp_array.size() - 5));
                    s2.setText(tmp_array.get(tmp_array.size() - 4));
                    s3.setText(tmp_array.get(tmp_array.size() - 3));
                    s4.setText(tmp_array.get(tmp_array.size() - 2));
                    s5.setText(tmp_array.get(tmp_array.size() - 1));
                }

                if (currentState == null) {
                    textView_activity.setText("");
                } else {
                    textView_activity.setText(String.valueOf(currentState));
                }
                break;

            case Sensor.TYPE_GYROSCOPE:
                gyro = event.values.clone();
                globalGyroValues = convertGlobalValues(gyro);
                Values[7] = gyro[0];
                Values[8] = gyro[1];
                Values[9] = gyro[2];
                Values[10] = globalGyroValues[0];
                Values[11] = globalGyroValues[1];
                Values[12] = globalGyroValues[2];
                break;

            case Sensor.TYPE_STEP_DETECTOR:
                if (isParam) {
                    steps++;
//                    textView_steps.setText("歩数：" + String.valueOf(steps) + " 歩");
//                    textView_distance.setText("移動距離：" + df.format(getDistance(steps)) + " m");

//                    Log.d("steps:", String.valueOf(steps));
//                    Log.d("distance:", String.valueOf(distance));
                }
                break;
        }
    }

    //センサ値を世界座標系に変換
    private float[] convertGlobalValues(float[] deviceValues) {
        float[] globalValues = new float[4];
        if (acc != null && gravity != null && magnetic != null) {
            float[] inR = new float[16];
            float[] outR = new float[16];
            SensorManager.getRotationMatrix(inR, null, gravity, magnetic);
            SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_X, SensorManager.AXIS_Y, outR);
            float[] temp = new float[4];
            float[] inv = new float[16];
            temp[0] = deviceValues[0];
            temp[1] = deviceValues[1];
            temp[2] = deviceValues[2];
            temp[3] = 0;
            android.opengl.Matrix.invertM(inv, 0, outR, 0);
            android.opengl.Matrix.multiplyMV(globalValues, 0, inv, 0, temp, 0);
        }
        return globalValues;
    }

    //CSVファイルのテンプレート（一行目の項目名）を作成
    private void createTemplateFile() {
        File file = new File(LocationHistoryPath + "/" + "test.csv");
        String items = "TimeStamp," +
                "Acc_X," + "Acc_Y," + "Acc_Z," +
                "Acc_X_Glo," + "Acc_Y_Glo," + "Acc_Z_Glo," +
                "Pressure," +
                "Gyro_X," + "Gyro_Y," + "Gyro_Z," +
                "Gyro_X_Glo," + "Gyro+Y_Glo," + "Gyro_Z_Glo";
        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8"));
            bufferedWriter.write(items);
            bufferedWriter.write("\n");
            bufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //センサ値をを格納 (CSVファイルの2行目以降)
    private void storeValues(float[] values) {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String file = LocationHistoryPath + "/" + "test.csv";
        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8"));
            bufferedWriter.write(timestamp.toString() + ",");
            for (int i = 0; i < values.length; i++) {
                if (i == values.length - 1) {
                    bufferedWriter.write(String.valueOf(values[i]));
                } else {
                    bufferedWriter.write(String.valueOf(values[i]) + ",");
                }
            }
            bufferedWriter.write("\n");
            bufferedWriter.close();

            Xw_list.add(storeValuesCount, Double.valueOf(Values[3]));
            Yw_list.add(storeValuesCount, Double.valueOf(Values[4]));
            Zw_list.add(storeValuesCount, Double.valueOf(Values[5]));
            Pressure_list.add(storeValuesCount, Double.valueOf(Values[6]));

            storeValuesCount++;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //特徴量を記録
    private void createParam() {
        File file = new File(dataPredictPath);
        param = getParam();
        if (!Double.isNaN(param[0]) && !Double.isNaN(param[1]) && !Double.isNaN(param[2]) && !Double.isNaN(param[3]) && !Double.isNaN(param[4])) {
            String str = "0 1:" + paramf.format(param[0]) + " 2:" + paramf.format(param[1]) + " 3:" + paramf.format(param[2]) + " 4:" + paramf.format(param[3]) + " 5:" + paramf.format(param[4]);
            Log.d("param_str", str);
            String str_scaled = scaller.calcScaleFromLine(str);
            Log.d("param_str_scaled", str_scaled);
            try {
                BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
                bufferedWriter.write(str_scaled);
                bufferedWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //特徴量を生成
    private double[] getParam() {
//        double[] param = new double[5];
        param[0] = getAve_AccXY();
        param[1] = getAve_AccZ();
        param[2] = getVar_AccXY();
        param[3] = getVar_AccZ();
        param[4] = getDiff_Pressure();
        return param;
    }

    //XY軸合成加速度の平均
    private double getAve_AccXY() {
        double sum = 0;
        for (int i = 0; i < windowSize; i++) {
//            sum = sum + Math.sqrt((Xw[(storeValuesCount - windowSize + i)] * Xw[(storeValuesCount - windowSize + i)]) + (Yw[(storeValuesCount - windowSize + i)]) * Yw[(storeValuesCount - windowSize + i)]);
            sum = sum + Math.sqrt((Xw_list.get(storeValuesCount - windowSize + i) * Xw_list.get(storeValuesCount - windowSize + i)) + (Yw_list.get(storeValuesCount - windowSize + i) * Yw_list.get(storeValuesCount - windowSize + i)));
        }
        return sum / windowSize;
    }

    //Z軸軸加速度の平均
    private double getAve_AccZ() {
        double sum = 0;
        for (int i = 0; i < windowSize; i++) {
//            sum = sum + Zw[(storeValuesCount - windowSize + i)];
            sum = sum + Zw_list.get(storeValuesCount - windowSize + i);
        }
        return sum / windowSize;
    }

    //XY軸軸合成加速度の分散
    private double getVar_AccXY() {
        double ave = getAve_AccXY();
        double temp = 0;
        for (int i = 0; i < windowSize; i++) {
//            temp += (Math.sqrt((Xw[(storeValuesCount - windowSize + i)] * Xw[(storeValuesCount - windowSize + i)]) + (Yw[(storeValuesCount - windowSize + i)]) * Yw[(storeValuesCount - windowSize + i)]) - ave) * (Math.sqrt((Xw[(storeValuesCount - windowSize + i)] * Xw[(storeValuesCount - windowSize + i)]) + (Yw[(storeValuesCount - windowSize + i)]) * Yw[(storeValuesCount - windowSize + i)]) - ave);
            temp += (Math.sqrt((Xw_list.get(storeValuesCount - windowSize + i) * Xw_list.get(storeValuesCount - windowSize + i)) + (Yw_list.get(storeValuesCount - windowSize + i)) * Yw_list.get(storeValuesCount - windowSize + i)) - ave) * (Math.sqrt((Xw_list.get(storeValuesCount - windowSize + i) * Xw_list.get(storeValuesCount - windowSize + i)) + (Yw_list.get(storeValuesCount - windowSize + i)) * Yw_list.get(storeValuesCount - windowSize + i)) - ave);
        }
        return temp / windowSize;
    }

    //Z軸加速度の分散
    private double getVar_AccZ() {
        double ave = getAve_AccZ();
        double temp = 0;
        for (int i = 0; i < windowSize; i++) {
//            temp += ((Zw[storeValuesCount - windowSize + i] - ave) * (Zw[storeValuesCount - windowSize + i] - ave));
            temp += ((Zw_list.get(storeValuesCount - windowSize + i) - ave) * (Zw_list.get(storeValuesCount - windowSize + i) - ave));
        }
        return temp / windowSize;
    }

    private double getDiff_Pressure() {
//        return Pressure[storeValuesCount - windowSize - 1] - Pressure[storeValuesCount - 1];
        double diff;
        diff = Pressure_list.get(storeValuesCount - windowSize - 1) - Pressure_list.get(storeValuesCount - 1);
        return diff;
    }

    //distance in meters
    public double getDistance(long steps) {
        //stride = (0.38 * height) + 0.09;
        stride = 0.45 * 178;
        distance = (steps * stride)/100;
        return distance;
    }

    //movement speed in m/s
    public double getSpeed(double distance_difference, long elapsedTimeForSpeed){
        int second = (int)(elapsedTimeForSpeed/1000);
        float comma = (float)(elapsedTimeForSpeed%1000)/1000;
        speed = distance_difference/(second+comma); //速度 = 距離÷時間(秒)
//        Log.d("walk_speed", String.valueOf(speed));
        return speed;
    }

    public void replaceArray() {
        for (int i = 0; i < decisionCount - 1; i++) {
            activities_array.set(i, activities_array.get(i + 1));
        }
        activities_array.remove(decisionCount - 1);
    }

    public void counterReset() {
        stop_label1_count = 0;
        walk_label2_count = 0;
        upstairs_label3_count = 0;
        downstairs_label4_count = 0;
        upELV_label5_count = 0;
        downELV_label6_count = 0;
        run_label7_count = 0;
    }

    private void stopSensor() {
        super.onStop();
        sensorManager.unregisterListener(this);
    }

    //csvからデータベースへインポート
    public void buildDatabase(){
        db = helper.getWritableDatabase();
        db.execSQL(DROP_BSSID_TABLE);
        db.execSQL(CREATE_BSSID_TABLE);
        String fileName = BSSID_DB;
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(fileName), "UTF-8"));
            String line;
            int i = 0;
            while ((line = br.readLine()) != null) { //１行ごとに読み込む
                String [] strAry = line.split(",");
                db.execSQL("insert into room(name) values ('" + strAry[1] + "')");
                i++;
            }
            Toast.makeText(MainActivity.this, "全ﾃﾞｰﾀの登録が完了しました", Toast.LENGTH_LONG).show();
            br.close();
            Log.v("count", Integer.toString(i));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showDB(){
        StringBuilder sb = new StringBuilder();
        db = helper.getReadableDatabase();
        Cursor c = db.rawQuery("select * from room", null);
        if(c.getCount() != 0) {
            while (c.moveToNext()) {
                sb.append(c.getInt(0) + "," + c.getString(1) + "\n");
            }
            c.close();
            //tv.setText(sb.toString());
            Log.v("db", sb.toString());
        }
    }

    private class Scaller{
        // y' = lower + (upper - lower) * ((y - min) / (max - min))
        private double upper, lower;
        private Map<Integer, Map<String, Double>> features;

        Scaller() {
            upper = 0.0; lower = 0.0;
            features = new HashMap<>();
        }

        Scaller loadRange(File rangFile) throws IOException {
            Map<Integer, Map<String, Double>> map = new HashMap<>();
            BufferedReader br = new BufferedReader(new FileReader(rangFile));
            String line;
            while ((line = br.readLine()) != null) {
                String[] strings;
                if ((strings = line.split(" ")).length == 2) { //lower and upper
                    this.lower = Double.valueOf(strings[0]);
                    this.upper = Double.valueOf(strings[1]);
                }
                if ((strings = line.split(" ")).length == 3) { //class min max
                    Map<String, Double> min_max = new HashMap<>();
                    min_max.put("min", Double.valueOf(strings[1]));
                    min_max.put("max", Double.valueOf(strings[2]));
                    map.put(Integer.valueOf(strings[0]), min_max);
                }
            }
            br.close();
            this.features = map;
            return this;
        }

        //一件分
        String calcScaleFromLine(String line) {
            StringBuilder sb = new StringBuilder();
            String[] strings = line.split(" ");
            sb.append(strings[0]); // write class
            for (int i = 1; i < strings.length; i++) {
                String[] ss = strings[i].split(":");
                if (calcScale(ss[0], Double.valueOf(ss[1])) == -1.0) sb.append(" " + ss[0] + ":" + (int)calcScale(ss[0], Double.valueOf(ss[1])));
                else if (calcScale(ss[0], Double.valueOf(ss[1])) != 0.0) sb.append(" " + ss[0] + ":" + calcScale(ss[0], Double.valueOf(ss[1])));
            }
            return sb.toString();
        }

        //全体
        void calcScaleFromFile(File inputDataFile, File outputScaledFile) throws IOException {
            BufferedReader br = new BufferedReader(new FileReader(inputDataFile));
            BufferedWriter bw = new BufferedWriter(new FileWriter(outputScaledFile));
            String line;
            while ((line = br.readLine()) != null) {
                String[] strings = line.split(" ");
                bw.write(strings[0]); // write class
                for (int i = 1; i < strings.length; i++) {
                    String[] ss = strings[i].split(":");
                    if (calcScale(ss[0], Double.valueOf(ss[1])) == -1.0) bw.write(" " + ss[0] + ":" + (int)calcScale(ss[0], Double.valueOf(ss[1])));
                    else if (calcScale(ss[0], Double.valueOf(ss[1])) != 0.0) bw.write(" " + ss[0] + ":" + calcScale(ss[0], Double.valueOf(ss[1])));
                }
                bw.write(System.getProperty("line.separator"));
            }
            bw.flush();
            bw.close();
            br.close();
        }

        double calcScale(String classNum, int num) {
            return calcScale(classNum, (double)num);
        }

        double calcScale(String classNum, double num) {
            double d = lower + (upper - lower) * ((num - features.get(Integer.parseInt(classNum)).get("min")) / (features.get(Integer.parseInt(classNum)).get("max")- features.get(Integer.parseInt(classNum)).get("min")));
            BigDecimal value = new BigDecimal(d);
            MathContext mc = new MathContext(6, RoundingMode.HALF_UP);
            return value.round(mc).doubleValue();
        }
    }
}