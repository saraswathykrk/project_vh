package com.veri.help.vhsensor.imageprocess;


import android.content.res.AssetManager;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.veri.help.vhsensor.detector.FaceInfo;
import com.veri.help.vhsensor.imageprocess.ActualFaces;

public class DBHandlerUCCS extends SQLiteOpenHelper {

    //information of database
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "DetectFaceDB.db";
    public static final String TABLE_NAME = "ActualFaceUCCS";
    public static final String FACEID = "FACE_ID";
    public static final String FILENAME = "FILE_NAME";
    //    public static final String SUBJECTID = "SUBJECT_ID";
    public static final String FACE_X = "FACE_X";
    public static final String FACE_Y = "FACE_Y";
    public static final String FACE_WIDTH = "FACE_WIDTH";
    public static final String FACE_HEIGHT = "FACE_HEIGHT";
    public static final String DETECTION_SCORE = "DETECTION_SCORE";
    public static final String IMAGE_TABLE = "ImageFiles";
    public static final String PROCESSED = "PROCESS_FLAG";
    public int face_id_seq = 0;

    private static final String TAG = "FaceDetectHandler";

    //initialize the database
    public DBHandlerUCCS(Context context, String name,
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
                DETECTION_SCORE + " FLOAT )";
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public String loadHandler(InputStream inStream) {

        BufferedReader buffer = new BufferedReader(new InputStreamReader(inStream));
        String line = "";
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);  //need to remove later, saras
        onCreate(db);  //need to remove later, saras
        db.beginTransaction();
        Log.d(TAG, "In load handler");
        try {
            while ((line = buffer.readLine()) != null) {
                String[] colums = line.split(",");
                if (colums[0].trim().equals("# FILE")){
                    Log.d(TAG, "skipping the header row");
                    continue;
                }
                else if (colums[0].trim().equals("007794e4648808dba73a67d1f04e1d9b.jpg")){
                    Log.d(TAG, "print first row");
                    Log.d(TAG,colums[0].trim());
                    Log.d(TAG,colums[1].trim());
                    Log.d(TAG,colums[2].trim());
                    Log.d(TAG,colums[3].trim());
                    Log.d(TAG,colums[4].trim());
                    Log.d(TAG,colums[5].trim());
//                    continue;
                }
                if (colums.length != 6) {
                    Log.d("CSVParser", "Skipping Bad CSV Row");
                    continue;
                }

                /*ContentValues cv = new ContentValues();
                cv.put(FACEID, colums[0].trim());
                cv.put(FILENAME, colums[1].trim());
                cv.put(SUBJECTID, colums[2].trim());
                cv.put(FACE_X, colums[3].trim());
                cv.put(FACE_Y, colums[4].trim());
                cv.put(FACE_WIDTH, colums[5].trim());
                cv.put(FACE_HEIGHT, colums[6].trim());

                db.insert(TABLE_NAME, null, cv);*/
                String INSERT_TABLE = "INSERT INTO " + TABLE_NAME +
                        "(" +
                        FACEID + "," +
                        FILENAME + "," +
                        FACE_X + "," +
                        FACE_Y + "," +
                        FACE_WIDTH + "," +
                        FACE_HEIGHT + "," +
                        DETECTION_SCORE + ")" +
                        " VALUES (" +
                        (face_id_seq++) + "," +
                        "'" + colums[0].trim() + "'," +
                        colums[1].trim() + "," +
                        colums[2].trim() + "," +
                        colums[3].trim() + "," +
                        colums[4].trim() + "," +
                        colums[5].trim() + ")";
                db.execSQL(INSERT_TABLE);
            }
            Log.d(TAG," In load handler - inserted rows");
            db.setTransactionSuccessful();
            db.endTransaction();
            return "Data Loaded";

        } catch (IOException e) {
            e.printStackTrace();
            return "Data Not Loaded";
        }

       /* String result = "";
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
        return result;*/
    }

    public String addHandler() {
        String query = "Select DISTINCT " + FILENAME + " FROM " + TABLE_NAME;
        Log.d(TAG, "in addHandler - query:" + query);
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DROP TABLE IF EXISTS " + IMAGE_TABLE);  //need to remove later, saras
        String CREATE_TABLE = "CREATE TABLE " + IMAGE_TABLE + "(" +
                FILENAME + " TEXT," +
                PROCESSED + " VARCHAR2(1) )";
        db.execSQL(CREATE_TABLE);
        db.beginTransaction();
        Cursor cursor = db.rawQuery(query, null);
        ArrayList<ActualFaces> faces_actual = new ArrayList<ActualFaces>();

        if (cursor != null ) {
            if  (cursor.moveToFirst()) {
                do {
                    String INSERT_TABLE = "INSERT INTO " + IMAGE_TABLE +
                            "(" +
                            FILENAME + "," +
                            PROCESSED + ")" +
                            " VALUES (" +
                            "'" + cursor.getString(0) + "'," +
                            "'X'" + ")";
                    db.execSQL(INSERT_TABLE);
                }while (cursor.moveToNext());
            }
            cursor.close();
        }
        db.setTransactionSuccessful();
        db.endTransaction();
        return "Image Table created";
    }

    public ArrayList<ActualFaces> findHandler(String filename) {
        String query = "Select * FROM " + TABLE_NAME + " WHERE " + FILENAME + " = " + "'" + filename + "'";
        Log.d(TAG, "in find handler - query:" + query);
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(query, null);
        ArrayList<ActualFaces> faces_actual = new ArrayList<ActualFaces>();

        if (cursor != null ) {
            if  (cursor.moveToFirst()) {
                do {
                    Log.d(TAG, "From val db, col1" + (cursor.getString(0)));
                    Log.d(TAG, "From val db, col2" + (cursor.getString(1)));
                    Log.d(TAG, "From val db, col3" + (cursor.getString(2)));

                    faces_actual.add(new ActualFaces(Integer.parseInt(cursor.getString(0)),
                            cursor.getString(1),
                            0,
                            Float.parseFloat(cursor.getString(2)),
                            Float.parseFloat(cursor.getString(3)),
                            Float.parseFloat(cursor.getString(4)),
                            Float.parseFloat(cursor.getString(5)),
                            Float.parseFloat(cursor.getString(6))));
                }while (cursor.moveToNext());
            }
            cursor.close();
        } else {
            faces_actual = null;
        }
        db.close();
        return faces_actual;
    }

    public String findImgHandler() {
        String query = "Select * FROM " + IMAGE_TABLE + " WHERE " + PROCESSED + " = " + "'X'";
        Log.d(TAG, "in find IMG handler - query:" + query);
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(query, null);
        String unprocessed_file;

        if  (cursor.moveToFirst()) {
            Log.d(TAG, "From img handler db, col1" + (cursor.getString(0)));

            unprocessed_file = cursor.getString(0);
            cursor.close();
        }
        else {
            unprocessed_file = "not found";
        }
        db.close();
        return unprocessed_file;
    }

    public Boolean UpdateImgHandler(String filename, String Flag) {
        String query = "UPDATE " + IMAGE_TABLE + " SET " + PROCESSED + " = '" + Flag + "' WHERE " + FILENAME + " = " + "'" + filename + "'";
        Log.d(TAG, "in update IMG handler - query:" + query);
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL(query);
        db.close();
        return true;
    }


   /* public Student findHandler(Stringstudentname) {
         Stringquery = "Select * FROM " + TABLE_NAME + "WHERE" + COLUMN_NAME + " = " + "'" + studentname + "'";
         SQLiteDatabase db = this.getWritableDatabase();
         Cursor cursor = db.rawQuery(query, null);
         Student student = new Student();
         if (cursor.moveToFirst()) {
          cursor.moveToFirst();
          student.setID(Integer.parseInt(cursor.getString(0)));
          student.setStudentName(cursor.getString(1));
          cursor.close();
         } else {
          student = null;
         }
         db.close();
         return student;
        }


   public boolean deleteHandler(int ID) {
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
