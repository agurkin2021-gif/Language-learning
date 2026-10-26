package ru.spanish.learning;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Locale;

/** Russian Android TTS for assistant replies. Alternates with ASR, not Realtime audio. */
final class AssistantVoiceOutput {
    interface Listener { void done(boolean success); }
    private final Handler ui=new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ready,initialized,closed;
    private int generation;
    private String pendingText;
    private Listener pending;

    AssistantVoiceOutput(Context context) {
        tts=new TextToSpeech(context.getApplicationContext(), status -> ui.post(() -> {
            if(closed)return;
            if(status==TextToSpeech.SUCCESS){
                int lang=tts.setLanguage(Locale.forLanguageTag("ru-RU"));
                ready=lang!=TextToSpeech.LANG_MISSING_DATA
                        && lang!=TextToSpeech.LANG_NOT_SUPPORTED;
                tts.setSpeechRate(0.95f);
                tts.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                    @Override public void onStart(String id){}
                    @Override public void onDone(String id){ui.post(()->finished(id,true));}
                    @Override public void onError(String id){ui.post(()->finished(id,false));}
                });
            }
            initialized=true;
            if(pending!=null){
                String queued=pendingText;
                Listener callback=pending;
                pending=null; pendingText=null;
                if(ready) speak(queued,callback);
                else callback.done(false);
            }
        }));
    }

    void speak(String text,Listener callback){
        if(closed){callback.done(false);return;}
        stop();
        if(text==null||text.trim().isEmpty()){callback.done(true);return;}
        if(!initialized){pending=callback;pendingText=text;return;}
        if(!ready){callback.done(false);return;}
        String token="asr-reply:"+(++generation);
        pending=callback;
        if(tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,token)==TextToSpeech.ERROR)
            finished(token,false);
    }

    private void finished(String token,boolean success){
        if(closed||!("asr-reply:"+generation).equals(token))return;
        Listener callback=pending;pending=null;pendingText=null;
        if(callback!=null)callback.done(success);
    }
    void stop(){
        generation++;pending=null;pendingText=null;
        if(tts!=null)tts.stop();
    }
    void close(){if(closed)return;stop();closed=true;if(tts!=null)tts.shutdown();}
}
