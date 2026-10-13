package com.ekrankopya.app;

import android.content.Intent;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

/**
 * Sistem, asistan uygulamalarından bir ses tanıma servisi bekler. Koppiy ses kullanmaz,
 * bu yüzden bu servis sadece boş bir yer tutucudur.
 */
public class StubRecognitionService extends RecognitionService {

    @Override
    protected void onStartListening(Intent recognizerIntent, Callback listener) {
        try {
            listener.error(SpeechRecognizer.ERROR_CLIENT);
        } catch (RemoteException ignored) {
        }
    }

    @Override
    protected void onCancel(Callback listener) {
    }

    @Override
    protected void onStopListening(Callback listener) {
    }
}
