package com.example.josef.finalboss;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.support.v4.util.CircularArray;
import android.support.v4.util.CircularIntArray;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Array;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

import edu.emory.mathcs.jtransforms.fft.FloatFFT_1D;

import static java.lang.Math.max;
import static java.lang.Math.round;

public class TranscribeActivity_layout extends SurfaceView implements Runnable {
    //Recorder variables
    int winSize = 2048;
    int overlap = 1024;
    AudioRecord recorder;
    FloatFFT_1D fft;
    float[][] spectrogram = new float[15][512];
    float[] recorder_data = new float[winSize];
    RecordTask recordTask;
    boolean performTask = false;

    //Demo task variables
    int recordTime = 12;
    String demoMessage = "Click to start.";
    int steps = (int)Math.floor((recordTime*16000-winSize)/(winSize-overlap));
    float[][] demo_spectrogram = new float[steps][512];
    float[][] temp_demo_spectrogram = new float[185][512];

    //PIANO NN variables
    int piano_input_h ;
    int piano_input_w;
    int piano_conv1_h;
    int piano_conv1_w;
    int piano_conv1_padding = 1;
    int piano_conv2_h;
    int piano_conv2_w;
    int piano_conv2_padding = 1;
    int piano_fc_h;
    int piano_fc_w;
    float[][] piano_mu;
    float[][] piano_ss;
    float[][] piano_conv1_filter;
    float piano_conv1_bias;
    float[][] piano_conv2_filter;
    float piano_conv2_bias;
    float[][] piano_fc_weights;
    float[] piano_fc_bias;
    boolean piano_NN_busy = false;
    float[] piano_maxes = new float[40];
    double[] piano_thresholds = {9,5,4,9,5,10,12,1,10,11,11,8,2,2,3,2,4,2,7,1,12,9,1,2,2,0.010307,2,2,2,5,13,10,1,15,8,6,3,3,1,1};
    final float piano_multiplier = 1;

    //VOCAL NN variables
    int vocal_input_h;
    int vocal_input_w;
    int vocal_conv1_h;
    int vocal_conv1_w;
    int vocal_conv1_padding = 1;
    int vocal_conv2_h;
    int vocal_conv2_w;
    int vocal_conv2_padding = 1;
    int vocal_fc_h;
    int vocal_fc_w;
    float[][] vocal_mu;
    float[][] vocal_ss;
    float[][] vocal_conv1_filter;
    float vocal_conv1_bias;
    float[][] vocal_conv2_filter;
    float vocal_conv2_bias;
    float[][] vocal_fc_weights;
    float[] vocal_fc_bias = new float[40];
    boolean vocal_NN_busy = false;
    float[] vocal_maxes = new float[40];
    double[] vocal_thresholds = {9,5,4,9,5,10,12,1,10,11,11,8,2,2,3,2,4,2,7,1,12,9,1,2,2,0.010307,2,2,2,5,13,10,1,15,8,6,3,3,1,1};
    final float vocal_multiplier = 3;


    //TASK variables
    float time = 0;


    //GUI variables
    SurfaceHolder surfaceHolder;
    Canvas canvas;
    Boolean canDraw = false;
    Bitmap background;
    Bitmap record_button;
    Bitmap gclef;
    Bitmap dot;
    Bitmap dot_sharp;
    Bitmap high_dot;
    Bitmap high_sharp;
    float half_dot_height = (float)24.8;
    int piano_lowest = 414;
    int vocal_lowest = 914;
    int[] note_heights = {2,3,3,4,4,5,5,6,7,7,8,8,9,10,10,11,11,12,12,13,0,0,1,1,2,3,3,4,4,5,5,6,7,7,8,8,9,10,10,11};
    Paint text_paint = new Paint();
    int speed_divider = 2;
    int nr_elements = steps*speed_divider;
    float[][] pianoRoll = new float[nr_elements][40];
    float[][] vocalRoll = new float[nr_elements][40];
    int maxWidth = 1500;
    int increment = round((1500-200)/nr_elements);

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if(action == MotionEvent.ACTION_DOWN){
            float x = event.getX();
            float y = event.getY();
            if(x > 600 && x < 1225 && y > 450 && y < 600 ) {
                if (performTask == false) {
                    performTask = true;
                    RecordTask recordTask = new RecordTask();
                    recordTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null);
                    record_button = BitmapFactory.decodeResource(getResources(), R.drawable.record_button);
                    record_button = Bitmap.createScaledBitmap(record_button, 625, 150, true);
                } else {
                    performTask = false;
                    record_button = BitmapFactory.decodeResource(getResources(), R.drawable.back_button);
                    record_button = Bitmap.createScaledBitmap(record_button, 625, 150, true);
                    DemoTask demoTask = new DemoTask();
                    demoTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,null);
                }
            }
        }

        return super.onTouchEvent(event);
    }

    public TranscribeActivity_layout(Context context){
        super(context);
        surfaceHolder = getHolder();

        String piano_json_string;
        JSONObject piano_json_object;
        try {
            InputStream inputFile = context.getAssets().open("piano_NN.json");
            int size = inputFile.available();
            byte[] buffer = new byte[size];

            inputFile.read(buffer);

            piano_json_string = new String(buffer,"UTF-8");

        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        try{
            piano_json_object = new JSONObject(piano_json_string);


            piano_fc_h = piano_json_object.getJSONArray("piano_fc_weights").length();
            piano_fc_w = piano_json_object.getJSONArray("piano_fc_weights").getJSONArray(0).length();
            piano_input_h = piano_json_object.getJSONArray("piano_mu").length();
            piano_input_w = piano_json_object.getJSONArray("piano_mu").getJSONArray(0).length();
            piano_conv1_h = piano_json_object.getJSONArray("piano_conv1_filter").length();
            piano_conv1_w = piano_json_object.getJSONArray("piano_conv1_filter").getJSONArray(0).length();
            piano_conv2_h = piano_json_object.getJSONArray("piano_conv2_filter").length();
            piano_conv2_w = piano_json_object.getJSONArray("piano_conv2_filter").getJSONArray(0).length();

            piano_mu = new float[piano_input_h][piano_input_w];
            piano_ss = new float[piano_input_h][piano_input_w];
            piano_conv1_filter = new float[piano_conv1_h][piano_conv1_w];
            piano_conv2_filter = new float[piano_conv2_h][piano_conv2_w];
            piano_fc_weights = new float[piano_fc_h][piano_fc_w];
            piano_fc_bias = new float[piano_fc_w];

            piano_conv1_bias = (float) piano_json_object.getDouble("piano_conv1_bias");
            piano_conv2_bias = (float) piano_json_object.getDouble("piano_conv2_bias");

            for(int x = 0;x<piano_conv1_h;x++){
                for(int y = 0;y<piano_conv1_w;y++){
                    piano_conv1_filter[x][y] = (float)piano_json_object.getJSONArray("piano_conv1_filter").getJSONArray(x).getDouble(y);
                }
            }

            for(int x = 0;x<piano_conv2_h;x++){
                for(int y = 0;y<piano_conv2_w;y++){
                    piano_conv2_filter[x][y] = (float)piano_json_object.getJSONArray("piano_conv2_filter").getJSONArray(x).getDouble(y);
                }
            }

            Log.i("JSON",Integer.toString(piano_json_object.getJSONArray("piano_mu").length()));
            Log.i("JSON",Integer.toString(piano_json_object.getJSONArray("piano_mu").getJSONArray(0).length()));

            for(int x = 0;x<piano_fc_h;x++){
                for(int y = 0; y < piano_fc_w;y++){
                    piano_fc_weights[x][y] = (float)piano_json_object.getJSONArray("piano_fc_weights").getJSONArray(x).getDouble(y);
                }
            }



            for(int x = 0;x<40;x++){
                piano_fc_bias[x] = (float)piano_json_object.getJSONArray("piano_fc_bias").getDouble(x);
            }

            for(int x = 0;x<piano_input_h;x++){
                for(int y = 0; y < piano_input_w;y++){
                    piano_mu[x][y] = (float)piano_json_object.getJSONArray("piano_mu").getJSONArray(x).getDouble(y);
                }
            }

            for(int x = 0;x<piano_input_h;x++){
                for(int y = 0; y < piano_input_w;y++){
                    piano_ss[x][y] = (float)piano_json_object.getJSONArray("piano_ss").getJSONArray(x).getDouble(y);
                }
            }

            for(int x = 0;x<185;x++){
                for(int y = 0; y < 512;y++){
                    temp_demo_spectrogram[x][y] = (float)piano_json_object.getJSONArray("temp_input").getJSONArray(x).getDouble(y);
                }
            }


        } catch (Exception e){
            e.printStackTrace();
            return;
        }

        String vocal_json_string;
        JSONObject vocal_json_object;
        try {
            InputStream inputFile = context.getAssets().open("vocal_NN.json");
            int size = inputFile.available();
            byte[] buffer = new byte[size];

            inputFile.read(buffer);

            vocal_json_string = new String(buffer,"UTF-8");

        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        try{
            vocal_json_object = new JSONObject(vocal_json_string);

            vocal_fc_h = vocal_json_object.getJSONArray("vocal_fc_weights").length();
            vocal_fc_w = vocal_json_object.getJSONArray("vocal_fc_weights").getJSONArray(0).length();
            vocal_input_h = vocal_json_object.getJSONArray("vocal_mu").length();
            vocal_input_w = vocal_json_object.getJSONArray("vocal_mu").getJSONArray(0).length();
            vocal_conv1_h = vocal_json_object.getJSONArray("vocal_conv1_filter").length();
            vocal_conv1_w = vocal_json_object.getJSONArray("vocal_conv1_filter").getJSONArray(0).length();
            vocal_conv2_h = vocal_json_object.getJSONArray("vocal_conv2_filter").length();
            vocal_conv2_w = vocal_json_object.getJSONArray("vocal_conv2_filter").getJSONArray(0).length();

            vocal_mu = new float[vocal_input_h][vocal_input_w];
            vocal_ss = new float[vocal_input_h][vocal_input_w];
            vocal_conv1_filter = new float[vocal_conv1_h][vocal_conv1_w];
            vocal_conv2_filter = new float[vocal_conv2_h][vocal_conv2_w];
            vocal_fc_weights = new float[vocal_fc_h][vocal_fc_w];
            vocal_fc_bias = new float[vocal_fc_w];

            vocal_conv1_bias = (float) vocal_json_object.getDouble("vocal_conv1_bias");
            vocal_conv2_bias = (float) vocal_json_object.getDouble("vocal_conv2_bias");

            for(int x = 0;x<vocal_conv1_h;x++){
                for(int y = 0;y<vocal_conv1_w;y++){
                    vocal_conv1_filter[x][y] = (float)vocal_json_object.getJSONArray("vocal_conv1_filter").getJSONArray(x).getDouble(y);
                }
            }

            for(int x = 0;x<vocal_conv2_h;x++){
                for(int y = 0;y<vocal_conv2_w;y++){
                    vocal_conv2_filter[x][y] = (float)vocal_json_object.getJSONArray("vocal_conv2_filter").getJSONArray(x).getDouble(y);
                }
            }

            Log.i("JSON",Integer.toString(vocal_json_object.getJSONArray("vocal_mu").length()));
            Log.i("JSON",Integer.toString(vocal_json_object.getJSONArray("vocal_mu").getJSONArray(0).length()));

            for(int x = 0;x<vocal_fc_h;x++){
                for(int y = 0; y < vocal_fc_w;y++){
                    vocal_fc_weights[x][y] = (float)vocal_json_object.getJSONArray("vocal_fc_weights").getJSONArray(x).getDouble(y);
                }
            }



            for(int x = 0;x<40;x++){
                vocal_fc_bias[x] = (float)vocal_json_object.getJSONArray("vocal_fc_bias").getDouble(x);
            }

            for(int x = 0;x<vocal_input_h;x++){
                for(int y = 0; y < vocal_input_w;y++){
                    vocal_mu[x][y] = (float)vocal_json_object.getJSONArray("vocal_mu").getJSONArray(x).getDouble(y);
                }
            }

            for(int x = 0;x<vocal_input_h;x++){
                for(int y = 0; y < vocal_input_w;y++){
                    vocal_ss[x][y] = (float)vocal_json_object.getJSONArray("vocal_ss").getJSONArray(x).getDouble(y);
                }
            }

        } catch (Exception e){
            e.printStackTrace();
            return;
        }


        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,16000, AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_FLOAT,winSize*4);
        recorder.startRecording();

        background = BitmapFactory.decodeResource(getResources(),R.drawable.background2);
        background = Bitmap.createScaledBitmap(background,1920,1080,true);

        record_button = BitmapFactory.decodeResource(getResources(),R.drawable.record_button);
        record_button = Bitmap.createScaledBitmap(record_button,625,150,true);

        text_paint.setColor(Color.BLACK);
        text_paint.setTextSize(60);

        gclef = BitmapFactory.decodeResource(getResources(),R.drawable.gbar);
        gclef = Bitmap.createScaledBitmap(gclef,2500,400,false);


        dot = BitmapFactory.decodeResource(getResources(),R.drawable.dot);
        dot = Bitmap.createScaledBitmap(dot,115,68,false);

        dot_sharp = BitmapFactory.decodeResource(getResources(),R.drawable.dot_sharp);
        dot_sharp = Bitmap.createScaledBitmap(dot_sharp,115,68,false);

        high_dot = BitmapFactory.decodeResource(getResources(),R.drawable.high_dot);
        high_dot = Bitmap.createScaledBitmap(high_dot,115,68,false);

        high_sharp = BitmapFactory.decodeResource(getResources(),R.drawable.high_sharp);
        high_sharp = Bitmap.createScaledBitmap(high_sharp,115,68,false);
    }

    @Override
    public void run() {
        while(canDraw){
            if(!surfaceHolder.getSurface().isValid()){
                continue;
            }

            canvas = surfaceHolder.lockCanvas();

            //Draw background
            canvas.drawBitmap(background,0,0,null);
            //Draw piano clef
            canvas.drawBitmap(gclef,100,100,null);
            //Draw vocal clef
            canvas.drawBitmap(gclef,100,600,null);
            //Draw button
            canvas.drawBitmap(record_button,600,450,null);
            //Draw text
            canvas.drawText(demoMessage,1225,550,text_paint);
            canvas.rotate(270, 80, 350);
            canvas.drawText("Piano:",80,350,text_paint);
            canvas.rotate(90, 80, 350);
            canvas.rotate(270, 80, 850);
            canvas.drawText("Vocal:",80,850,text_paint);
            canvas.rotate(90, 80, 850);

            //draw dots
            for(int x = 0;x<nr_elements;x++){
                for(int y = 0;y<40;y++){
                    if(pianoRoll[x][y] > 5){
                        drawPianoNote(maxWidth-(x*increment),y);
                    }
                }
            }
            for(int x = 0;x<nr_elements;x++){
                for(int y = 0;y<40;y++){
                    if(vocalRoll[x][y] > 5){
                        drawVocalNote(maxWidth-(x*increment),y);
                    }
                }
            }
            surfaceHolder.unlockCanvasAndPost(canvas);
            if(performTask == true) {
                rotateArray(pianoRoll);
                rotateArray(vocalRoll);
            }
        }
    }

    private void drawPianoNote(int left,int note){
        if(note < 20){
            if(note == 2 || note ==  4 || note ==  6 || note ==  9  || note == 11 || note ==  14 || note ==  16 || note == 18){
                canvas.drawBitmap(dot_sharp,left,round(piano_lowest - note_heights[note] * half_dot_height),null);
            }
            else{
                canvas.drawBitmap(dot,left,round(piano_lowest - note_heights[note] * half_dot_height),null);
            }
        }
        else{
            if(note == 21 || note ==  23 || note ==  26 || note ==  28  || note == 30 || note ==  33 || note ==  35 || note == 38){
                canvas.drawBitmap(high_sharp,left,round(piano_lowest - note_heights[note] * half_dot_height),null);
            }
            else{
                canvas.drawBitmap(high_dot,left,round(piano_lowest - note_heights[note] * half_dot_height),null);
            }
        }
    }

    private void drawVocalNote(int left,int note){
        if(note < 20){
            if(note == 2 || note ==  4 || note ==  6 || note ==  9  || note == 11 || note ==  14 || note ==  16 || note == 18){
                canvas.drawBitmap(dot_sharp,left,round(vocal_lowest - note_heights[note] * half_dot_height),null);
            }
            else{
                canvas.drawBitmap(dot,left,round(vocal_lowest - note_heights[note] * half_dot_height),null);
            }
        }
        else{
            if(note == 21 || note ==  23 || note ==  26 || note ==  28  || note == 30 || note ==  33 || note ==  35 || note == 38){
                canvas.drawBitmap(high_sharp,left,round(vocal_lowest - note_heights[note] * half_dot_height),null);
            }
            else{
                canvas.drawBitmap(high_dot,left,round(vocal_lowest - note_heights[note] * half_dot_height),null);
            }
        }
    }

    public void resume(){
        canDraw = true;
        Thread thread = new Thread(this);
        thread.start();

        RecordTask recordTask = new RecordTask();
        recordTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,null);
    }

    private class DemoTask extends AsyncTask<Void,String,Void> {
        float[] recorder_buffer = new float[recordTime*16000];


        @Override
        protected Void doInBackground(Void... values) {
            publishProgress("Recording...");
            recorder.read(recorder_buffer, 0, recordTime*16000, AudioRecord.READ_BLOCKING);
            publishProgress("Converting...");


            float[] fft_buffer = new float[winSize];

            for(int step = 0; step < steps;step++) {
                int start = (winSize - overlap) * step;
                for (int x = 0; x < winSize; x++) {
                    fft_buffer[x] = recorder_buffer[start + x];
                }
                fft.realForward(fft_buffer);
                for (int x = 0; x < 512; x++) {
                    float value = (float) Math.sqrt(Math.pow(fft_buffer[2 * x], 2) + Math.pow(fft_buffer[2 * x + 1], 2));
                    demo_spectrogram[step][x] = piano_multiplier * value;
                }
            }

            for(int x = 0; x < steps; x++){
                for(int y = 0; y < 512; y++){
                    demo_spectrogram[x][y] = temp_demo_spectrogram[x][y];
                }
            }
            FullPianoTask fullPianoTask = new FullPianoTask();
            fullPianoTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null);
            FullVocalTask fullVocalTask = new FullVocalTask();
            fullVocalTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,null);

            publishProgress("Processing...");

            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            demoMessage = values[0];
        }

        @Override
        protected void onPostExecute(Void s) {
            super.onPostExecute(s);
            return;
        }
    }

    private class FullVocalTask extends AsyncTask<Void,Integer,Void>{

        float[][] bufferLayer = new float[vocal_input_h][vocal_input_w];
        float[][] conv1_layer = new float[vocal_input_h-vocal_conv1_h+2*vocal_conv1_padding+1][vocal_input_w-vocal_conv1_w+2*vocal_conv1_padding+1];
        float[][] conv2_layer = new float[conv1_layer.length-vocal_conv2_h+2*vocal_conv2_padding+1][conv1_layer[0].length-vocal_conv2_w+2*vocal_conv2_padding+1];
        float[][] flat_layer = new float[1][vocal_fc_h];
        float[][] fc_layer = new float[1][40];
        float[][] input_layer = new float[vocal_input_h][vocal_input_w];
        @Override
        protected Void doInBackground(Void...values){
            for(int step = 0; step < steps-vocal_input_h;step++) {
                for (int x = 0; x < vocal_input_h; x++) {
                    bufferLayer[x] = demo_spectrogram[x+step];
                }
                input_layer = subtractMatrix(bufferLayer, vocal_mu);
                input_layer = divideMatrix(input_layer, vocal_ss);
                conv1_layer = convolution(input_layer, vocal_conv1_filter, 1);
                reLU(conv1_layer, conv1_layer.length, conv1_layer[0].length, vocal_conv1_bias);
                conv2_layer = convolution(conv1_layer, vocal_conv2_filter, 1);
                reLU(conv2_layer, conv2_layer.length, conv2_layer[0].length, vocal_conv2_bias);
                flat_layer = flattenMatrix(conv2_layer, conv2_layer.length, conv2_layer[0].length);
                fc_layer = multiplyMatrices(flat_layer, vocal_fc_weights, 1, vocal_fc_h, 40);
                for (int x = 0; x < 40; x++) {
                    fc_layer[0][x] += vocal_fc_bias[x];
                }
                publishProgress(step);
            }
            demoMessage = "Vocal done.";
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer...values){
            super.onProgressUpdate(values);
            int step = values[0];
            vocalRoll[nr_elements-(speed_divider*step)-1] = fc_layer[0];
            vocalRoll[nr_elements-(speed_divider*step)-2] = new float[40];
        }
    }

    private class FullPianoTask extends AsyncTask<Void,Integer,Void>{

        float[][] bufferLayer = new float[piano_input_h][piano_input_w];
        float[][] conv1_layer = new float[piano_input_h-piano_conv1_h+2*piano_conv1_padding+1][piano_input_w-piano_conv1_w+2*piano_conv1_padding+1];
        float[][] conv2_layer = new float[conv1_layer.length-piano_conv2_h+2*piano_conv2_padding+1][conv1_layer[0].length-piano_conv2_w+2*piano_conv2_padding+1];
        float[][] flat_layer = new float[1][piano_fc_h];
        float[][] fc_layer = new float[1][40];
        float[][] input_layer = new float[piano_input_h][piano_input_w];
        @Override
        protected Void doInBackground(Void...values){
            for(int step = 0; step < steps-piano_input_h;step++) {
                for (int x = 0; x < piano_input_h; x++) {
                    bufferLayer[x] = demo_spectrogram[x+step];
                }
                input_layer = subtractMatrix(bufferLayer, piano_mu);
                input_layer = divideMatrix(input_layer, piano_ss);
                conv1_layer = convolution(input_layer, piano_conv1_filter, 1);
                reLU(conv1_layer, conv1_layer.length, conv1_layer[0].length, piano_conv1_bias);
                conv2_layer = convolution(conv1_layer, piano_conv2_filter, 1);
                reLU(conv2_layer, conv2_layer.length, conv2_layer[0].length, piano_conv2_bias);
                flat_layer = flattenMatrix(conv2_layer, conv2_layer.length, conv2_layer[0].length);
                fc_layer = multiplyMatrices(flat_layer, piano_fc_weights, 1, piano_fc_h, 40);
                for (int x = 0; x < 40; x++) {
                    fc_layer[0][x] += piano_fc_bias[x];
                }
                publishProgress(step);
            }
            demoMessage = "Piano done.";
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer...values){
            super.onProgressUpdate(values);
            int step = values[0];
            pianoRoll[nr_elements-(speed_divider*step)-1] = fc_layer[0];
            pianoRoll[nr_elements-(speed_divider*step)-2] = new float[40];
        }
    }


    private class RecordTask extends AsyncTask<Void,Void,Void> {
        float[] recorder_buffer = new float[winSize/2];


        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            int sampleRate = recorder.getSampleRate();
            if(sampleRate != 16000){
                Log.e("AUDIO RECORDER","WRONG SAMPLE RATE CHOSEN");
            }
            else{
                Log.i("AUDIO RECORDER","STARTING EXECUTION");
                performTask = true;
                fft = new FloatFFT_1D(winSize);
            }
        }

        @Override
        protected Void doInBackground(Void... strings) {
            while(performTask == true) {


                recorder.read(recorder_data, winSize/2, winSize/2, AudioRecord.READ_BLOCKING);

                for(int x=0;x<winSize/2;x++){
                    recorder_buffer[x] = recorder_data[x+winSize/2];
                }

                fft.realForward(recorder_data);
                rotateArray(spectrogram);
                //Log.i("RECORDER TIME",Float.toString(SystemClock.elapsedRealtime()-time));
                for(int x=0;x<512;x++){
                    float value = (float)Math.sqrt(Math.pow(recorder_data[2*x],2)+Math.pow(recorder_data[2*x+1],2));
                    spectrogram[0][x] = piano_multiplier*value;
                }
                time = SystemClock.elapsedRealtime();

                for(int x=0;x<winSize/2;x++){
                    recorder_data[x] = recorder_buffer[x];
                }


                publishProgress();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
            if(piano_NN_busy == false){
                //Log.i("RECORDER","PIANO SYSTEM NOT BUSY");
                piano_NN_busy = true;
                PianoNetworkTask pianoNetworkTask = new PianoNetworkTask();
                pianoNetworkTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null);
            }
            else {
                //Log.i("RECORDER", "PIANO SYSTEM BUSY");
            }
            if(vocal_NN_busy == false){
                Log.i("RECORDER","VOCAL SYSTEM NOT BUSY");
                vocal_NN_busy = true;
                VocalNetworkTask vocalNetworkTask = new VocalNetworkTask();
                vocalNetworkTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,null);
            }
            else{
                Log.i("RECORDER","VOCAL SYSTEM BUSY");
            }
        }

        @Override
        protected void onPostExecute(Void s) {
            super.onPostExecute(s);
            Log.i("RECORDER","STOPPED");
            return;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            performTask = false;
            Log.i("RECORDER","CANCEL");
        }
    }

    private class PianoNetworkTask extends AsyncTask<Void,Void,Void>{

        float[][] bufferLayer = new float[piano_input_h][piano_input_w];
        float[][] conv1_layer = new float[piano_input_h-piano_conv1_h+2*piano_conv1_padding+1][piano_input_w-piano_conv1_w+2*piano_conv1_padding+1];
        float[][] conv2_layer = new float[conv1_layer.length-piano_conv2_h+2*piano_conv2_padding+1][conv1_layer[0].length-piano_conv2_w+2*piano_conv2_padding+1];
        float[][] flat_layer = new float[1][piano_fc_h];
        float[][] fc_layer = new float[1][40];
        float[][] input_layer = new float[piano_input_h][piano_input_w];
        @Override
        protected Void doInBackground(Void...values){
            for (int x = 0; x < piano_input_h; x++) {
                bufferLayer[x] = spectrogram[x];
            }
            //printMatrix(bufferLayer,"BEFORE SUBTRACT");
            input_layer = subtractMatrix(bufferLayer,piano_mu);
            //printMatrix(bufferLayer,"AFTER SUBTRACT");
            input_layer = divideMatrix(input_layer,piano_ss);
            //printMatrix(bufferLayer,"AFTER DIVIDE");
            conv1_layer = convolution(input_layer, piano_conv1_filter, 1);
            reLU(conv1_layer,conv1_layer.length,conv1_layer[0].length,piano_conv1_bias);
            //printMatrix(conv1_layer,"CONV1");
            conv2_layer = convolution(conv1_layer,piano_conv2_filter,1);
            reLU(conv2_layer,conv2_layer.length,conv2_layer[0].length,piano_conv2_bias);
            //printMatrix(conv2_layer,"CONV2");
            flat_layer = flattenMatrix(conv2_layer,conv2_layer.length,conv2_layer[0].length);
            fc_layer = multiplyMatrices(flat_layer,piano_fc_weights,1,piano_fc_h,40);
            for(int x=0;x<40;x++){
                fc_layer[0][x] += piano_fc_bias[x];
            }
            //printMatrix(fc_layer,"FC");
            publishProgress();
            return null;
        }

        @Override
        protected void onProgressUpdate(Void...values){
            super.onProgressUpdate(values);
            piano_NN_busy = false;
            pianoRoll[0] = fc_layer[0];
        }
    }

    private class VocalNetworkTask extends AsyncTask<Void,Void,Void>{
        float[][] bufferLayer = new float[vocal_input_h][vocal_input_w];
        float[][] conv1_layer = new float[vocal_input_h-vocal_conv1_h+2*vocal_conv1_padding+1][vocal_input_w-vocal_conv1_w+2*vocal_conv1_padding+1];
        float[][] conv2_layer = new float[conv1_layer.length-vocal_conv2_h+2*vocal_conv2_padding+1][conv1_layer[0].length-vocal_conv2_w+2*vocal_conv2_padding+1];
        float[][] flat_layer = new float[1][vocal_fc_h];
        float[][] fc_layer = new float[1][40];
        float[][] input_layer = new float[vocal_input_h][vocal_input_w];
        @Override
        protected Void doInBackground(Void...values){
            for (int x = 0; x < vocal_input_h; x++) {
                bufferLayer[x] = spectrogram[x];
            }
            //printMatrix(bufferLayer,"BEFORE SUBTRACT");
            input_layer = subtractMatrix(bufferLayer,vocal_mu);
            //printMatrix(bufferLayer,"AFTER SUBTRACT");
            input_layer = divideMatrix(input_layer,vocal_ss);
            //printMatrix(bufferLayer,"AFTER DIVIDE");
            conv1_layer = convolution(input_layer, vocal_conv1_filter, 1);
            reLU(conv1_layer,conv1_layer.length,conv1_layer[0].length,vocal_conv1_bias);
            //printMatrix(conv1_layer,"CONV1");
            conv2_layer = convolution(conv1_layer,vocal_conv2_filter,1);
            reLU(conv2_layer,conv2_layer.length,conv2_layer[0].length,vocal_conv2_bias);
            //printMatrix(conv2_layer,"CONV2");
            flat_layer = flattenMatrix(conv2_layer,conv2_layer.length,conv2_layer[0].length);
            fc_layer = multiplyMatrices(flat_layer,vocal_fc_weights,1,vocal_fc_h,40);
            for(int x=0;x<40;x++){
                fc_layer[0][x] += vocal_fc_bias[x];
            }
            //printMatrix(fc_layer,"FC");
            publishProgress();
            return null;
        }

        @Override
        protected void onProgressUpdate(Void...values){
            super.onProgressUpdate(values);
            vocal_NN_busy = false;
            vocalRoll[0] = fc_layer[0];
        }
    }

    private void rotateArray(int[][] arr){
        for(int x=arr.length-1;x>0;x--){
            arr[x] = arr[x-1];
        }
        arr[0] = new int[arr[0].length];
    }

    private void rotateArray(float[][] arr){
        for(int x=arr.length-1;x>0;x--){
            arr[x] = arr[x-1];
        }
        arr[0] = new float[arr[0].length];
    }

    private void printMatrix(int[][] input,String name){
        System.out.print(name);
        System.out.println();
        int rows = input.length;
        int cols = input[0].length;
        for(int i = 0; i<rows; i++){
            for(int j = 0; j<cols; j++){
                System.out.print(input[i][j]);
                System.out.print(",");
            }
            System.out.println();
        }
    }

    private void printMatrix(float[][] input,String name){
        System.out.print(name);
        System.out.println();
        int filter_rows = input.length;
        int filter_cols = input[0].length;
        for(int i = 0; i<filter_rows; i++){
            for(int j = 0; j<filter_cols-1; j++){
                System.out.print(String.format(Locale.US,"%.3f",input[i][j]));
                System.out.print(",");
            }
            System.out.print(String.format(Locale.US,"%.3f",input[i][filter_cols-1]));
            System.out.print(";");
            System.out.println();
        }
    }

    private float[][] convolution(float[][] layer,float[][] filter,int padding){
        int layer_h = layer.length;
        int layer_w = layer[0].length;
        int filter_h = filter.length;
        int filter_w = filter[0].length;
        float[][] tempLayer = new float[layer_h+2*padding][layer_w+2*padding];
        for(int x=padding;x<layer_h+padding;x++){
            for(int y=padding;y<layer_w+padding;y++){
                tempLayer[x][y] = layer[x-padding][y-padding];
            }
        }
        int result_h = layer_h - filter_h + 2*padding + 1;
        int result_w = layer_w - filter_w + 2*padding + 1;
        float[][] result = new float[result_h][result_w];
        for(int result_x = 0; result_x < result_h;result_x++){
            for(int result_y = 0;result_y < result_w;result_y++){
                for(int filter_x=0;filter_x<filter_h;filter_x++){
                    for(int filter_y=0;filter_y<filter_w;filter_y++){
                        result[result_x][result_y] = result[result_x][result_y] + filter[filter_x][filter_y]*tempLayer[result_x+filter_x][result_y+filter_y];
                    }
                }
            }
        }

        return result;
    }

    public static float[][] multiplyMatrices(float[][] firstMatrix, float[][] secondMatrix, int r1, int c1, int c2) {
        float[][] product = new float[r1][c2];
        for(int i = 0; i < r1; i++) {
            for (int j = 0; j < c2; j++) {
                for (int k = 0; k < c1; k++) {
                    product[i][j] += firstMatrix[i][k] * secondMatrix[k][j];
                }
            }
        }

        return product;
    }

    private static void reLU(float[][] input_matrix,int rows,int cols,float bias){
        for(int x = 0;x<rows;x++){
            for(int y=0;y<cols;y++){
                input_matrix[x][y] = max(input_matrix[x][y] + bias,0);
            }
        }
    }

    private static float[][] subtractMatrix(float[][] subtractee,float[][] subtracter){
        float[][] result_matrix = new float[subtractee.length][subtractee[0].length];
        for(int x = 0;x<subtractee.length;x++){
            for(int y = 0;y<subtractee[0].length;y++){
                result_matrix[x][y] = subtractee[x][y] - subtracter[x][y];
            }
        }
        return result_matrix;
    }

    private static float[][] divideMatrix(float[][] dividee,float[][] divider){
        float[][] result_matrix = new float[dividee.length][dividee[0].length];
        for(int x = 0;x<dividee.length;x++){
            for(int y = 0;y<dividee[0].length;y++){
                result_matrix[x][y] = dividee[x][y] / divider[x][y];
            }
        }
        return result_matrix;
    }

    private static float[][] flattenMatrix(float[][] matrix,int rows,int cols){
        float[][] returnMatrix = new float[1][rows*cols];
        for(int x = 0;x<rows;x++){
            for(int y = 0;y<cols;y++){
                returnMatrix[0][x+rows*y] = matrix[x][y];
            }
        }

        return returnMatrix;
    }
}
