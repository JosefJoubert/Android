package com.example.josef.finalboss;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;

public class TranscribeActivity extends Activity {
    TranscribeActivity_layout layout;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        layout = new TranscribeActivity_layout(this);
        setContentView(layout);
    }

    @Override
    protected void onResume() {
        super.onResume();
        layout.resume();
    }

}
