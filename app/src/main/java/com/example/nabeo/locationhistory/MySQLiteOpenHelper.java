package com.example.nabeo.locationhistory;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by nabeo on 2017/09/27.
 */

public class MySQLiteOpenHelper extends SQLiteOpenHelper {
    static String DBNAME = "LocationHistory.db";
    static int DBVERSION = 1;
    final static String CREATE_PREDICT_TABLE = "create table if not exists predict(bssid_id integer, rssi integer, count integer)";
    final static String DROP_PREDICT_TABLE = "drop table if exists predict";
    final static String CREATE_ROOM_TABLE = "create table if not exists room(id integer primary key autoincrement, name text unique)";
    final static String DROP_ROOM_TABLE = "drop table if exists room";
    final static String CREATE_BSSID_TABLE = "create table if not exists bssid(id integer primary key autoincrement, mac text unique)";
    final static String DROP_BSSID_TABLE = "drop table if exists bssid";

    public MySQLiteOpenHelper(Context context){
        super(context, DBNAME, null, DBVERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqliteDatabase){

    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int a, int b){

    }
}
