package ru.spanish.learning;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.Looper;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** The system can stop this job; cancellation is checked before any install commit. */
public final class UpdateJobService extends JobService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Integer, AtomicBoolean> jobs = new HashMap<>();

    @Override public boolean onStartJob(JobParameters params) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean previous = jobs.put(params.getJobId(), cancelled);
        if (previous != null) previous.set(true);
        AppUpdater.runScheduled(this, cancelled, () -> main.post(() -> {
            if (jobs.get(params.getJobId()) != cancelled) return;
            jobs.remove(params.getJobId());
            jobFinished(params, false);
        }));
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        AtomicBoolean cancelled = jobs.remove(params.getJobId());
        if (cancelled != null) cancelled.set(true);
        return true;
    }

    @Override public void onDestroy() {
        for (AtomicBoolean cancelled : jobs.values()) cancelled.set(true);
        jobs.clear();
        super.onDestroy();
    }
}
