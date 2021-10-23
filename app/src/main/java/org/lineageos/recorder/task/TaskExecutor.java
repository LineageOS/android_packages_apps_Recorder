/*
 * Copyright (C) 2021 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.recorder.task;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.OnLifecycleEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public final class TaskExecutor implements LifecycleObserver {
    private static final String TAG = "TaskExecutor";

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Future<?>> execFutures = new ArrayList<>(4);

    public synchronized <T> void runTask(@NonNull @WorkerThread Callable<T> callable,
                                         @NonNull @MainThread Consumer<T> consumer) {
        final Future<T> future = executor.submit(callable);
        execFutures.add(future);
        try {
            final T result = future.get(1, TimeUnit.MINUTES);
            // It's completed, remove to free memory
            execFutures.remove(future);
            // Post result
            handler.post(() -> consumer.accept(result));
        } catch (InterruptedException e) {
            Log.w(TAG, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException("An error occurred while executing task",
                    e.getCause());
        }
    }

    public synchronized <T> void runTask(@NonNull @WorkerThread Callable<Optional<T>> callable,
                                         @NonNull @MainThread Consumer<T> ifPresent,
                                         @NonNull @MainThread Runnable ifNotPresent) {
        runTask(callable, opt -> {
            if (opt.isPresent()) {
                ifPresent.accept(opt.get());
            } else {
                ifNotPresent.run();
            }
        });
    }

    public synchronized void runTask(@NonNull @WorkerThread Runnable task,
                                     @NonNull @MainThread Runnable callback) {
        final Future<?> future = executor.submit(task);
        execFutures.add(future);
        try {
            future.get(1, TimeUnit.MINUTES);
            // It's completed, remove to free memory
            execFutures.remove(future);
            // Post result
            handler.post(callback);
        } catch (InterruptedException e) {
            Log.w(TAG, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException("An error occurred while executing task",
                    e.getCause());
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    public void terminate(@Nullable LifecycleOwner owner) {
        // Unsubscribe
        if (owner != null) {
            owner.getLifecycle().removeObserver(this);
        }

        // Terminate all pending jobs
        executor.shutdown();
        if (hasUnfinishedTasks()) {
            try {
                if (!executor.awaitTermination(250, TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow();
                    //noinspection ResultOfMethodCallIgnored
                    executor.awaitTermination(100, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted", e);
                // (Re-)Cancel if current thread also interrupted
                executor.shutdownNow();
                // Preserve interrupt status
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean hasUnfinishedTasks() {
        for (final Future<?> future : execFutures) {
            if (!future.isDone()) {
                return true;
            }
        }
        return false;
    }
}
