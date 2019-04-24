package com.veri.help.vhsensor.imageprocess;

public class ActualFaces {

    int face_id;
    String filename;
    int subject_id;
    float face_x;
    float face_y;
    float face_width;
    float face_height;
    float det_score;
    ActualBbox actualBbox;

    class ActualBbox {
        float face_x;
        float face_y;
        float face_width;
        float face_height;
        float g_area;

        ActualBbox(float x, float y, float w, float h){
            this.face_x = x;
            this.face_y = y;
            this.face_width = w;
            this.face_height = h;
            this.g_area = w * h;
        }
    }

    ActualFaces(int face_id, String filename, int subject_id, float face_x, float face_y, float face_width, float face_height, float det_score){
        this.face_id = face_id;
        this.filename = filename;
        this.subject_id = subject_id;
        this.face_x = face_x;
        this.face_y = face_y;
        this.face_width = face_width;
        this.face_height = face_height;
        this.det_score = det_score;
        actualBbox = new ActualBbox(face_x, face_y, face_width, face_height);
    }

}