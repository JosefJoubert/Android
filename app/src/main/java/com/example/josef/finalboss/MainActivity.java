package com.example.josef.finalboss;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import edu.emory.mathcs.jtransforms.fft.FloatFFT_1D;

public class MainActivity extends Activity {
    boolean performTask = false;
    int winSize = 2048;
    DataPoint[] graph_data = new DataPoint[winSize/2];
    LineGraphSeries<DataPoint> graph_series;
    AudioRecord recorder;
    FloatFFT_1D fft;
    float[] recorder_data = new float[winSize];
    RecordTask recordTask;
    Activity thisActivity = this;
    EditText freq_text;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RadioButton radioButton = findViewById(R.id.radioButton);
        radioButton.setClickable(true);
        radioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                moveActivity();
            }
        });

        freq_text = findViewById(R.id.freq_text);

        freq_text.setText("Frequency: 0 Hz");

        ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.RECORD_AUDIO},5);
        int permissionResponse = ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO);
        if(permissionResponse != 0){
            Log.e("PERMISSION REQUEST","PERMISSION REQUEST DENIED");
        }
        else{
            Log.i("PERMISSION REQUEST",Integer.toString(permissionResponse));
        }

        GraphView graph = findViewById(R.id.graph);



        for(int x = 0;x<winSize/2;x++){
            graph_data[x] = new DataPoint(7.8125*x,0);
        }

        graph_series = new LineGraphSeries<>(graph_data);

        graph.addSeries(graph_series);

        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,16000, AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_FLOAT,winSize*4);
        recorder.startRecording();

        graph.getViewport().setScalable(true);
        graph.getViewport().setScrollable(true);

        graph.getViewport().setYAxisBoundsManual(true);

        graph.getViewport().setMaxY(0);
        graph.getViewport().setMinY(-80);
    }

    @Override
    protected void onResume() {
        super.onResume();
        recordTask = new RecordTask();
        recordTask.execute();
    }


    private void moveActivity() {
        Log.i("MAIN THREAD","GO TO RECORDING");
        recorder.stop();
        recorder.release();
        recordTask.cancel(true);
        Intent intent = new Intent(this,TranscribeActivity.class);
        startActivity(intent);

    }

    private class RecordTask extends AsyncTask<Void,Void,Void>{
        float time = 0;

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

                recorder.read(recorder_data, 0, winSize, AudioRecord.READ_BLOCKING);

                fft.realForward(recorder_data);

                for (int x = 0; x < winSize / 2; x++) {
                    float value = (float)(20*Math.log10( Math.sqrt(Math.pow(recorder_data[2 * x], 2) + Math.pow(recorder_data[2 * x + 1], 2))/winSize));
                    graph_data[x] = new DataPoint(7.8125*x, value);
                }
                publishProgress();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
            graph_series.resetData(graph_data);
            int largest = 0;
            for(int x = 1;x < winSize / 2;x++){
                if(graph_data[x].getY() > -40){
                    if(graph_data[x].getY() > graph_data[largest].getY()){
                        largest = x;
                    }
                }
            }
            if(largest != 0){
                freq_text.setText("Frequency: " + Float.toString((float)graph_data[largest].getX()));
            }
            else{
                freq_text.setText("Frequency: 0 Hz");
            }
        }

        @Override
        protected void onPostExecute(Void s) {
            super.onPostExecute(s);
            return;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            performTask = false;
            Log.i("RECORDER","CANCEL");
        }
    }




}
