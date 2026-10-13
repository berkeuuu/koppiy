package com.ekrankopya.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Asistan hareketi bu ekranı açarsa seçim modunu başlatıp hemen kapanır. */
public class AssistActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int r = CopyService.trigger();
        if (r == 1) {
            Toast.makeText(this, R.string.toast_service_off, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, MainActivity.class));
        } else if (r == 2) {
            Toast.makeText(this, R.string.toast_switch_off, Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
