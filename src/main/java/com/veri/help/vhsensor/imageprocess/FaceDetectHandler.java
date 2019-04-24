package com.veri.help.vhsensor.imageprocess;


import android.content.res.AssetManager;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import com.veri.help.vhsensor.detector.FaceInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

public class FaceDetectHandler extends SQLiteOpenHelper {

    //information of database
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "DetectFaceDB.db";
    public static final String TABLE_NAME = "DETECTFACE";
    public static final String FACEID = "FACEID";
    public static final String FILENAME = "FILENAME";
    public static final String PROCESSING_TIME = "PROCESSING_TIME";
    public static final String FACE_X = "FACE_X";
    public static final String FACE_Y = "FACE_Y";
    public static final String FACE_WIDTH = "FACE_WIDTH";
    public static final String FACE_HEIGHT = "FACE_HEIGHT";
    public static final String DET_AREA = "DET_AREA";
    public static final String SCORE = "SCORE";

    private static final String TAG = "FaceDetectHandler";

    //initialize the database
    public FaceDetectHandler(Context context, String name,
                     SQLiteDatabase.CursorFactory factory, int version) {
        super(context, DATABASE_NAME, factory, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" +
                FACEID + " INTEGER PRIMARY KEY," +
                FILENAME + " TEXT," +
                FACE_X + " FLOAT," +
                FACE_Y + " FLOAT," +
                FACE_WIDTH + " FLOAT," +
                FACE_HEIGHT + " FLOAT," +
                DET_AREA + " FLOAT," +
                SCORE + " FLOAT )";
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
        db.close();
    }

/*    public String loadHandler(String mCSVfile, InputStream inStream) {
        String result = "";
        String query = "Select*FROM " + TABLE_NAME;
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(query, null);
        while (cursor.moveToNext()) {
            int result_0 = cursor.getInt(0);
            String result_1 = cursor.getString(1);
            result += String.valueOf(result_0) + " " + result_1 +
                    System.getProperty("line.separator");
        }
        cursor.close();
        db.close();
        return result;
    }*/

    public void addHandler(int res_id, String filename, List<FaceInfo> faces_detected, float width_scale, float height_scale) {
        ContentValues values = new ContentValues();
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);  //need to remove later, saras
        onCreate(db);  //need to remove later, saras
        for (int j=0; j < faces_detected.size(); j++ ) {
            float x = (faces_detected.get(j).bbox.xMin * width_scale);
            float y = (faces_detected.get(j).bbox.yMin * height_scale);
            float w = (faces_detected.get(j).bbox.xMax - faces_detected.get(j).bbox.xMin) * width_scale;
            float h = (faces_detected.get(j).bbox.yMax - faces_detected.get(j).bbox.yMin) * height_scale;
            float area = w * h;
            float score = faces_detected.get(j).bbox.score;

            String INSERT_TABLE = "INSERT INTO " + TABLE_NAME + " VALUES (" +
                    String.valueOf(res_id) + "" + String.valueOf(j) + "," +
                    "'" + filename + "'," +
                    x + "," +
                    y + "," +
                    w + "," +
                    h + "," +
                    area + "," +
                    score + ")";
            db.execSQL(INSERT_TABLE);
        }
        Log.d(TAG, "Inserted into table" + TABLE_NAME);
        db.close();
    }

    public void findHandler(String filename) {
        String query = "Select * FROM " + TABLE_NAME + " WHERE " + FILENAME + " = " + "'" + filename + "'";
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(query, null);
        if (cursor.moveToFirst()) {
            cursor.moveToFirst();
            Log.d(TAG, "From db, col1" + (cursor.getString(0)));
            Log.d(TAG, "From db, col2" + (cursor.getString(1)));
            Log.d(TAG, "From db, col3" + (cursor.getString(2)));
            cursor.close();
        }
        db.close();
    }

 /*   public boolean deleteHandler(int ID) {
        booleanresult = false;
        Stringquery = "Select*FROM " + TABLE_NAME + "WHERE" + COLUMN_ID + "= '" + String.valueOf(ID) + "'";
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(query, null);
        Student student = new Student();
        if (cursor.moveToFirst()) {
            student.setID(Integer.parseInt(cursor.getString(0)));
            db.delete(TABLE_NAME, COLUMN_ID + "=?",
                    newString[] {
                String.valueOf(student.getID())
            });
            cursor.close();
            result = true;
        }
        db.close();
        return result;
    }

    public boolean updateHandler(int ID, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues args = new ContentValues();
        args.put(COLUMN_ID, ID);
        args.put(COLUMN_NAME, name);
        return db.update(TABLE_NAME, args, COLUMN_ID + "=" + ID, null) > 0;
    }*/
}
