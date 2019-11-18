/*
 * Copyright 2017 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.mediaplayersample;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.net.Uri;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import android.util.Log;

/**
 * Exposes the functionality of the {@link MediaPlayer} and implements the {@link PlayerAdapter}
 * so that {@link MainActivity} can control music playback.
 */
public final class MediaPlayerHolder implements PlayerAdapter {
    public static final String TAG = "MediaPlayerHolder";
    public static final int PLAYBACK_POSITION_REFRESH_INTERVAL_MS = 1000;

    private final Context mContext;
    private MediaPlayer mMediaPlayer;
    private int mResourceId;
    private PlaybackInfoListener mPlaybackInfoListener;
    private ScheduledExecutorService mExecutor;
    private Runnable mSeekbarPositionUpdateTask;
    private int loopStart;
    private int loopEnd;
    private boolean looping;

    public MediaPlayerHolder(Context context) {
        mContext = context.getApplicationContext();
    }

    /**
     * Once the {@link MediaPlayer} is released, it can't be used again, and another one has to be
     * created. In the onStop() method of the {@link MainActivity} the {@link MediaPlayer} is
     * released. Then in the onStart() of the {@link MainActivity} a new {@link MediaPlayer}
     * object has to be created. That's why this method is private, and called by load(int) and
     * not the constructor.
     */
    private void initializeMediaPlayer() {
        if (mMediaPlayer == null) {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setLooping(true);
//            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
//                @Override
//                public void onCompletion(MediaPlayer mediaPlayer) {
//                    stopUpdatingCallbackWithPosition(true);
//                    logToUI("MediaPlayer playback completed");
//                    if (mPlaybackInfoListener != null) {
//                        mPlaybackInfoListener.onStateChanged(PlaybackInfoListener.State.COMPLETED);
//                        mPlaybackInfoListener.onPlaybackCompleted();
//                    }
//                }
//            });
            logToUI("mMediaPlayer = new MediaPlayer()");
        }
    }

    public void setPlaybackInfoListener(PlaybackInfoListener listener) {
        mPlaybackInfoListener = listener;
    }

    // Implements PlaybackControl.
    public void loadMedia(Uri uri) {

        initializeMediaPlayer();

        try {
            logToUI("load() {1. setDataSource}");
            mMediaPlayer.setDataSource(mContext, uri);
        } catch (Exception e) {
            logToUI(e.toString());
        }

        try {
            logToUI("load() {2. prepare}");
            mMediaPlayer.prepare();
        } catch (Exception e) {
            logToUI(e.toString());
        }

        initializeProgressCallback();
        logToUI("initializeProgressCallback()");
    }

    @Override
    public void release() {
        if (mMediaPlayer != null) {
            logToUI("release() and mMediaPlayer = null");
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    @Override
    public boolean isPlaying() {
        if (mMediaPlayer != null) {
            return mMediaPlayer.isPlaying();
        }
        return false;
    }

    @Override
    public void play() {
        if (mMediaPlayer != null && !mMediaPlayer.isPlaying()) {
           mMediaPlayer.start();
            if (mPlaybackInfoListener != null) {
                mPlaybackInfoListener.onStateChanged(PlaybackInfoListener.State.PLAYING);
            }
            startUpdatingCallbackWithPosition();
        }
    }

    @Override
    public void pause() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
            if (mPlaybackInfoListener != null) {
                mPlaybackInfoListener.onStateChanged(PlaybackInfoListener.State.PAUSED);
            }
            logToUI("playbackPause()");
        }
    }

    @Override
    public void setLoop(int loopMode) {
        //TODO: A/B Loop creation logic.
        /**
         * When loop button is clicked, calls this based on current stage of loop creation.
         *
         * Stages:
         *  - 0: No music uploaded. No changes made to loop.
         *  - 1: Set start of loop
         *  - 2: Set end of loop. If start point is after end point, still creates loop properly.
         *  - 3: Clear loop start and end.
         *
         * Reference website implementation at function setLoop() in audio.js:
         * https://github.com/afxdance/music/blob/master/audio.js
         */

        //Hint: You will need to implement additional logic outside of this function.
        //Hint: Try looking at this.startUpdatingCallbackWithPosition(), which runs a task every millisecond.
        loopMode = loopMode % 4;    // Keep loopMode within the 4 possible inputs
        if (loopMode == -1) {
            return;
        } else if (loopMode == 0) {
            loopStart = mMediaPlayer.getCurrentPosition();
            Log.d(TAG, "Set loop start: " + loopStart);
        } else if (loopMode == 1) {
            loopEnd = mMediaPlayer.getCurrentPosition();
            Log.d(TAG, "Set loop end: " + loopEnd);
            if (loopStart > loopEnd) {  // Flip start/end if loop is inverted
                int temp = loopStart;
                loopStart = loopEnd;
                loopEnd = temp;
            }

            looping = true;
        } else {
            looping = false;

        }
    }

    @Override
    public void skipForward() {
        //TODO: Skips position forwards 5 seconds.
        //Hint: use this.seekTo(position) and MediaPlayer.getCurrentPosition()...
    }

    @Override
    public void skipBackward() {
        //TODO: Skips position backwards 5 seconds.
    }

    @Override
    public void increaseSpeed() {
        //TODO: Increases playback speed by 5%
        //Hint: use MediaPlayer.setPlaybackParams()...
    }

    @Override
    public void decreaseSpeed() {
        //TODO: Decreases playback speed by 5%
    }

    @Override
    public void seekTo(int position) {
        if (mMediaPlayer != null) {
            logToUI(String.format("seekTo() %d ms", position));
            mMediaPlayer.seekTo(position);
        }
    }

    /**
     * Syncs the mMediaPlayer position with mPlaybackProgressCallback via recurring task.
     */
    private void startUpdatingCallbackWithPosition() {
        if (mExecutor == null) {
            mExecutor = Executors.newSingleThreadScheduledExecutor();
        }
        if (mSeekbarPositionUpdateTask == null) {
            mSeekbarPositionUpdateTask = new Runnable() {
                @Override
                public void run() {
                    updateProgressCallbackTask();
                    Log.d(TAG, "____");
                    // Looping
                    if (looping) {
                        int curr = mMediaPlayer.getCurrentPosition();
                        Log.d(TAG, "LOOPING");
                        if (curr > loopEnd) {
                            Log.d(TAG, "Looping back from " + loopEnd + " to " + loopStart);
                            mMediaPlayer.seekTo(loopStart);
                        }
                    }
                }
            };
        }
        mExecutor.scheduleAtFixedRate(
                mSeekbarPositionUpdateTask,
                0,
                PLAYBACK_POSITION_REFRESH_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    // Reports media playback position to mPlaybackProgressCallback.
    private void stopUpdatingCallbackWithPosition(boolean resetUIPlaybackPosition) {
        if (mExecutor != null) {
            mExecutor.shutdownNow();
            mExecutor = null;
            mSeekbarPositionUpdateTask = null;
            if (resetUIPlaybackPosition && mPlaybackInfoListener != null) {
                mPlaybackInfoListener.onPositionChanged(0);
            }
        }
    }

    private void updateProgressCallbackTask() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            int currentPosition = mMediaPlayer.getCurrentPosition();
            if (mPlaybackInfoListener != null) {
                mPlaybackInfoListener.onPositionChanged(currentPosition);
            }
        }
    }

    @Override
    public void initializeProgressCallback() {
        final int duration = mMediaPlayer.getDuration();
        if (mPlaybackInfoListener != null) {
            mPlaybackInfoListener.onDurationChanged(duration);
            mPlaybackInfoListener.onPositionChanged(0);
            logToUI(String.format("firing setPlaybackDuration(%d sec)",
                                  TimeUnit.MILLISECONDS.toSeconds(duration)));
            logToUI("firing setPlaybackPosition(0)");
        }
    }

    private void logToUI(String message) {
        if (mPlaybackInfoListener != null) {
            mPlaybackInfoListener.onLogUpdated(message);
        }
    }

}
