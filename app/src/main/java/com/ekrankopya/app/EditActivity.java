package com.ekrankopya.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Seçilen metni kopyalamadan önce düzenlemek için küçük pencere. */
public class EditActivity extends Activity {

    static final String EXTRA_TEXT = "text";

    private EditText edit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String text = getIntent().getStringExtra(EXTRA_TEXT);
        if (text == null) {
            text = "";
        }
        setTitle(R.string.edit_title);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Util.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(R.string.edit_title);
        title.setTextSize(18);
        title.setPadding(0, 0, 0, Util.dp(this, 8));
        root.addView(title);

        edit = new EditText(this);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setMinLines(4);
        edit.setMaxLines(12);
        edit.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        edit.setText(text);
        edit.setSelection(text.length());
        root.addView(edit, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.END);

        Button cancel = new Button(this);
        cancel.setText(R.string.btn_cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        row.addView(cancel);

        Button copy = new Button(this);
        copy.setText(R.string.btn_copy);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Koppiy", edit.getText().toString()));
                Util.buzz(EditActivity.this);
                Toast.makeText(EditActivity.this, R.string.toast_copied, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        row.addView(copy);
        root.addView(row);

        setContentView(root);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
}
