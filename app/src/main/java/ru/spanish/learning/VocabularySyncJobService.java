package ru.spanish.learning;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.atomic.AtomicBoolean;

/** Periodic, unmetered-by-ChatGPT GitHub backup of the phone's local vocabulary. */
public final class VocabularySyncJobService extends JobService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean stopped = new AtomicBoolean();

    @Override public boolean onStartJob(JobParameters params) {
        stopped.set(false);
        PrivateVocabularySync.runScheduled(this, () -> main.post(() -> {
            if (!stopped.get()) jobFinished(params, false);
        }));
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        stopped.set(true);
        // Android will retry when the platform permits.
        return true;
    }
}
