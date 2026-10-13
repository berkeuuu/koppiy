package com.ekrankopya.app;

import android.content.Context;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.widget.Toast;

/** Orta tuşa basılı tutulunca açılır: kendini hemen gizler ve ekran seçim modunu başlatır. */
public class AssistSession extends VoiceInteractionSession {

    public AssistSession(Context context) {
        super(context);
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        hide();
        int r = CopyService.trigger();
        if (r == 1) {
            Toast.makeText(getContext(), R.string.toast_service_off, Toast.LENGTH_LONG).show();
        } else if (r == 2) {
            Toast.makeText(getContext(), R.string.toast_switch_off, Toast.LENGTH_LONG).show();
        }
    }
}
