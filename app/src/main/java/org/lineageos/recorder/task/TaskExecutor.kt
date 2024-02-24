/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.task

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.util.Optional
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Consumer

class TaskExecutor : LifecycleEventObserver {
    private val executor = Executors.newFixedThreadPool(2)
    private val handler = Handler(Looper.getMainLooper())
    private val execFutures = mutableListOf<Future<*>>()

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            terminate(source)
        }
    }

    @Synchronized
    fun <T> runTask(
        @WorkerThread callable: Callable<T>,
        @MainThread consumer: Consumer<T>
    ) {
        val future = executor.submit(callable)
        execFutures.add(future)
        try {
            val result = future[1, TimeUnit.MINUTES]
            // It's completed, remove to free memory
            execFutures.remove(future)
            // Post result
            handler.post { consumer.accept(result) }
        } catch (e: InterruptedException) {
            Log.w(TAG, e)
        } catch (e: ExecutionException) {
            throw RuntimeException(
                "An error occurred while executing task",
                e.cause
            )
        } catch (e: TimeoutException) {
            throw RuntimeException(
                "An error occurred while executing task",
                e.cause
            )
        }
    }

    @Synchronized
    fun <T> runTask(
        @WorkerThread callable: Callable<Optional<T>>,
        @MainThread ifPresent: Consumer<T>,
        @MainThread ifNotPresent: Runnable
    ) {
        runTask(callable) { opt: Optional<T> ->
            if (opt.isPresent) {
                ifPresent.accept(opt.get())
            } else {
                ifNotPresent.run()
            }
        }
    }

    @Synchronized
    fun runTask(
        @WorkerThread task: Runnable,
        @MainThread callback: Runnable
    ) {
        val future = executor.submit(task)
        execFutures.add(future)
        try {
            future[1, TimeUnit.MINUTES]
            // It's completed, remove to free memory
            execFutures.remove(future)
            // Post result
            handler.post(callback)
        } catch (e: InterruptedException) {
            Log.w(TAG, e)
        } catch (e: ExecutionException) {
            throw RuntimeException(
                "An error occurred while executing task",
                e.cause
            )
        } catch (e: TimeoutException) {
            throw RuntimeException(
                "An error occurred while executing task",
                e.cause
            )
        }
    }

    fun terminate(owner: LifecycleOwner?) {
        // Unsubscribe
        owner?.lifecycle?.removeObserver(this)

        // Terminate all pending jobs
        executor.shutdown()
        if (hasUnfinishedTasks()) {
            try {
                if (!executor.awaitTermination(250, TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow()
                    executor.awaitTermination(100, TimeUnit.MILLISECONDS)
                }
            } catch (e: InterruptedException) {
                Log.e(TAG, "Interrupted", e)
                // (Re-)Cancel if current thread also interrupted
                executor.shutdownNow()
                // Preserve interrupt status
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun hasUnfinishedTasks(): Boolean {
        for (future in execFutures) {
            if (!future.isDone) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val TAG = "TaskExecutor"
    }
}
