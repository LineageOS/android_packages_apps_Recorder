/*
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.provider.MediaStore
import android.telephony.TelephonyManager
import android.text.format.DateUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.lineageos.recorder.service.SoundRecorderService
import org.lineageos.recorder.status.UiStatus
import org.lineageos.recorder.task.DeleteRecordingTask
import org.lineageos.recorder.task.TaskExecutor
import org.lineageos.recorder.ui.WaveFormView
import org.lineageos.recorder.utils.LocationHelper
import org.lineageos.recorder.utils.OnBoardingHelper
import org.lineageos.recorder.utils.PermissionManager
import org.lineageos.recorder.utils.PreferencesManager
import org.lineageos.recorder.utils.Utils
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.reflect.safeCast

class RecorderActivity : AppCompatActivity(R.layout.activity_main) {
    // Views
    private val elapsedTimeText by lazy { findViewById<TextView>(R.id.main_elapsed_time) }
    private val mainView by lazy { findViewById<ConstraintLayout>(R.id.main_root) }
    private val pauseResume by lazy { findViewById<ImageView>(R.id.sound_pause_resume) }
    private val recordingText by lazy { findViewById<TextView>(R.id.main_title) }
    private val recordingVisualizer by lazy { findViewById<WaveFormView>(R.id.main_recording_visualizer) }
    private val settings by lazy { findViewById<ImageView>(R.id.sound_settings) }
    private val soundFab by lazy { findViewById<FloatingActionButton>(R.id.sound_fab) }
    private val soundList by lazy { findViewById<ImageView>(R.id.sound_list_icon) }

    private val locationHelper by lazy { LocationHelper(this) }
    private val permissionManager: PermissionManager by lazy { PermissionManager(this) }
    private val preferencesManager: PreferencesManager by lazy { PreferencesManager(this) }
    private val taskExecutor: TaskExecutor by lazy { TaskExecutor() }

    private var returnAudio = false
    private var hasRecordedAudio = false

    private var uiStatus = UiStatus.READY

    private val telephonyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (TelephonyManager.ACTION_PHONE_STATE_CHANGED == intent.action) {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                if (TelephonyManager.EXTRA_STATE_OFFHOOK == state) {
                    togglePause()
                }
            }
        }
    }

    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                SoundRecorderService.MSG_UI_STATUS -> setStatus(msg.obj as UiStatus)
                SoundRecorderService.MSG_SOUND_AMPLITUDE -> setVisualizerAmplitude(msg.obj as Int)
                SoundRecorderService.MSG_TIME_ELAPSED -> setElapsedTime(msg.obj as Long)
                else -> super.handleMessage(msg)
            }
        }
    })

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val messengerService = Messenger(service)
            this@RecorderActivity.service = messengerService
            try {
                val msg = Message.obtain(
                    null,
                    SoundRecorderService.MSG_REGISTER_CLIENT
                )
                msg.replyTo = messenger
                messengerService.send(msg)
            } catch (e: RemoteException) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }
    private var service: Messenger? = null
    private var isServiceBound = false

    public override fun onCreate(savedInstance: Bundle?) {
        super.onCreate(savedInstance)

        soundFab.setOnClickListener { toggleSoundRecorder() }
        pauseResume.setOnClickListener { togglePause() }
        soundList.setOnClickListener { openList() }
        settings.setOnClickListener { openSettings() }

        Utils.setFullScreen(window, mainView)
        Utils.setVerticalInsets(mainView)

        lifecycle.addObserver(taskExecutor)

        if (MediaStore.Audio.Media.RECORD_SOUND_ACTION == intent.action) {
            returnAudio = true
            soundList.isVisible = false
            settings.isVisible = false
        }

        doBindService()

        OnBoardingHelper.onBoardList(this, soundList)
        OnBoardingHelper.onBoardSettings(this, settings)
    }

    public override fun onDestroy() {
        doUnbindService()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()

        ContextCompat.registerReceiver(
            this,
            telephonyReceiver,
            IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        unregisterReceiver(telephonyReceiver)

        super.onStop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)

        if (requestCode == PermissionManager.REQUEST_CODE) {
            if (permissionManager.hasEssentialPermissions()) {
                toggleAfterPermissionRequest()
            } else {
                permissionManager.onEssentialPermissionsDenied()
            }
        }
    }

    private fun setStatus(status: UiStatus) {
        applyUiStatus(status)

        if (returnAudio && hasRecordedAudio) {
            Utils.cancelShareNotification(this)
            promptUser()
        }
    }

    private fun setVisualizerAmplitude(amplitude: Int) {
        recordingVisualizer.post { recordingVisualizer.setAmplitude(amplitude) }
    }

    private fun setElapsedTime(seconds: Long) {
        elapsedTimeText.post {
            elapsedTimeText.text = DateUtils.formatElapsedTime(seconds)
        }
    }

    private fun toggleAfterPermissionRequest() {
        Handler(Looper.getMainLooper()).postDelayed({ toggleSoundRecorder() }, 500)
    }

    private fun toggleSoundRecorder() {
        if (permissionManager.requestEssentialPermissions()) {
            return
        }

        if (uiStatus == UiStatus.READY) {
            // Start
            startService(
                Intent(this, SoundRecorderService::class.java)
                    .setAction(SoundRecorderService.ACTION_START)
                    .putExtra(SoundRecorderService.EXTRA_FILE_NAME, newRecordFileName)
            )
        } else {
            // Stop
            startService(
                Intent(this, SoundRecorderService::class.java)
                    .setAction(SoundRecorderService.ACTION_STOP)
            )
            hasRecordedAudio = true
        }
    }

    private fun togglePause() {
        when (uiStatus) {
            UiStatus.RECORDING -> startService(
                Intent(this, SoundRecorderService::class.java)
                    .setAction(SoundRecorderService.ACTION_PAUSE)
            )

            UiStatus.PAUSED -> startService(
                Intent(this, SoundRecorderService::class.java)
                    .setAction(SoundRecorderService.ACTION_RESUME)
            )

            UiStatus.READY -> {}
        }
    }

    private fun applyUiStatus(status: UiStatus) {
        uiStatus = status
        if (UiStatus.READY == status) {
            recordingText.text = getString(R.string.main_sound_action)
            soundFab.setImageResource(R.drawable.ic_mic)
            elapsedTimeText.isVisible = false
            recordingVisualizer.isVisible = false
            pauseResume.isVisible = false
        } else {
            soundFab.setImageResource(R.drawable.ic_stop)
            elapsedTimeText.isVisible = true
            recordingVisualizer.isVisible = true
            recordingVisualizer.setAmplitude(0)
            pauseResume.isVisible = true
            val prDrawable: Drawable?
            if (UiStatus.PAUSED == status) {
                recordingText.text = getString(R.string.sound_recording_title_paused)
                pauseResume.contentDescription = getString(R.string.resume)
                prDrawable = ContextCompat.getDrawable(this, R.drawable.avd_play_to_pause)
            } else {
                recordingText.text = getString(R.string.sound_recording_title_working)
                pauseResume.contentDescription = getString(R.string.pause)
                prDrawable = ContextCompat.getDrawable(this, R.drawable.avd_pause_to_play)
            }
            pauseResume.tooltipText = pauseResume.contentDescription
            pauseResume.setImageDrawable(prDrawable)
            AnimatedVectorDrawable::class.safeCast(prDrawable)?.start()
        }
    }

    private fun doBindService() {
        bindService(
            Intent(this, SoundRecorderService::class.java),
            serviceConnection, BIND_AUTO_CREATE
        )
        isServiceBound = true
    }

    private fun doUnbindService() {
        service?.let {
            try {
                val msg: Message = Message.obtain(
                    null,
                    SoundRecorderService.MSG_UNREGISTER_CLIENT
                )
                msg.replyTo = messenger
                it.send(msg)
            } catch (e: RemoteException) {
                // There is nothing special we need to do if the service
                // has crashed.
            }
        }

        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    private fun openSettings() {
        val intent = Intent(this, DialogActivity::class.java)
            .putExtra(
                DialogActivity.EXTRA_IS_RECORDING,
                uiStatus != UiStatus.READY
            )
        startActivity(intent)
    }

    private fun openList() {
        startActivity(Intent(this, ListActivity::class.java))
    }

    private fun confirmLastResult() {
        val resultIntent = Intent().setData(preferencesManager.lastItemUri)
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun discardLastResult() {
        preferencesManager.lastItemUri?.let {
            taskExecutor.runTask(DeleteRecordingTask(contentResolver, it)) {
                Utils.cancelShareNotification(this)
                preferencesManager.lastItemUri = null
            }
        }
        cancelResult(true)
    }

    private fun cancelResult(quit: Boolean) {
        setResult(RESULT_CANCELED, Intent())
        hasRecordedAudio = false
        if (quit) {
            finish()
        }
    }

    private fun promptUser() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_result_title)
            .setMessage(R.string.confirm_result_message)
            .setPositiveButton(R.string.confirm) { _: DialogInterface?, _: Int ->
                confirmLastResult()
            }
            .setNegativeButton(R.string.discard) { _: DialogInterface?, _: Int ->
                discardLastResult()
            }
            .setNeutralButton(R.string.record_again) { _: DialogInterface?, _: Int ->
                cancelResult(false)
            }
            .setCancelable(false)
            .show()
    }

    private val newRecordFileName: String
        get() {
            val tag = locationHelper.currentLocationName ?: FILE_NAME_FALLBACK
            val formatter = DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                .appendLiteral(' ')
                .append(DateTimeFormatter.ISO_LOCAL_TIME)
                .toFormatter(Locale.getDefault())
            val now = LocalDateTime.now()
            return String.format(
                FILE_NAME_BASE, tag,
                formatter.format(now.truncatedTo(ChronoUnit.SECONDS))
            ) + ".%1\$s"
        }

    companion object {
        private const val FILE_NAME_BASE = "%1\$s (%2\$s)"
        private const val FILE_NAME_FALLBACK = "Sound record"
    }
}
