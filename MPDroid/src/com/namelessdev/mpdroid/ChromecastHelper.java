/*
 * Copyright (C) 2010-2014 The MPDroid Project
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

package com.namelessdev.mpdroid;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import org.a0z.mpd.MPDStatus;
import org.a0z.mpd.Music;
import org.a0z.mpd.exception.MPDServerException;

import android.os.Bundle;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.util.Log;
import android.view.View;

import java.io.IOException;

/**
 * Helper for keeping the chromecast code out of the main activity
 *
 * @author Arnaud BARISAIN-MONROSE
 */
public class ChromecastHelper extends MediaRouter.Callback
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static final String TAG = "ChromecastHelper";

    public static final String APP_ID = "5D4E0A01";

    private MPDApplication app;

    private MainMenuActivity mMainMenuActivity;

    private MediaRouter mMediaRouter;

    private MediaRouteSelector mMediaRouteSelector;

    private MediaRouter.Callback mMediaRouterCallback;

    private CastDevice mSelectedDevice;

    private int mRouteCount = 0;

    private GoogleApiClient mApiClient;

    private boolean mWaitingForReconnect = false;

    private boolean mApplicationStarted = false;

    private String mSessionId;

    private Music mCurrentMusic;

    private RemoteMediaPlayer mRemoteMediaPlayer;

    public ChromecastHelper(MainMenuActivity activity) {
        app = (MPDApplication) activity.getApplication();
        mMainMenuActivity = activity;

        mMediaRouter = MediaRouter.getInstance(activity.getApplicationContext());
        mMediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(APP_ID))
                .build();

        // Configure the MediaRouteButton
        mMainMenuActivity.getMediaRouteButton().setRouteSelector(mMediaRouteSelector);
    }

    public void onResume() {
        mMediaRouter.addCallback(mMediaRouteSelector, this,
                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
    }

    public void onPause() {
        mMediaRouter.removeCallback(this);
    }

    /**
     * A simple method to return a status with error logging.
     *
     * @return An MPDStatus object.
     */
    private MPDStatus getStatus() {
        MPDStatus mpdStatus = null;
        try {
            mpdStatus = app.oMPDAsyncHelper.oMPD.getStatus();
        } catch (MPDServerException e) {
            Log.d(TAG, "Couldn't get the status to updatePlayingInfo()", e);
        }

        if (mpdStatus == null) {
            Log.d(TAG, "mpdStatus was null, could not updatePlayingInfo().");
        }

        return mpdStatus;
    }

    private void fillMetadataWithStatus(MediaMetadata mediaMetadata, MPDStatus status, boolean fetchCoverArt) {
        final int songPos = status.getSongPos();
        if (songPos >= 0) {
            mCurrentMusic = app.oMPDAsyncHelper.oMPD.getPlaylist().getByIndex(songPos);
        }
        if (mCurrentMusic != null) {
            mediaMetadata.putString(MediaMetadata.KEY_ALBUM_ARTIST, mCurrentMusic.getAlbumArtist());
            mediaMetadata.putString(MediaMetadata.KEY_ARTIST, mCurrentMusic.getArtist());
            mediaMetadata.putString(MediaMetadata.KEY_ALBUM_TITLE, mCurrentMusic.getAlbum());
            mediaMetadata.putString(MediaMetadata.KEY_TITLE, mCurrentMusic.getTitle());
            if (fetchCoverArt) {
                //TODO : Implement cover art support
            }
        }
    }

    public void startCasting()
    {
        Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
                .builder(mSelectedDevice, new CastListener());

        mApiClient = new GoogleApiClient.Builder(mMainMenuActivity)
                .addApi(Cast.API, apiOptionsBuilder.build())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mApiClient.connect();
    }


    private void loadMPDStream() {
        mRemoteMediaPlayer = new RemoteMediaPlayer();
        MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);
        fillMetadataWithStatus(mediaMetadata, getStatus(), false);
        MediaInfo mediaInfo = new MediaInfo.Builder(
                StreamingService.getStreamSource(app))
                .setContentType("audio/mpeg")
                .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                .setMetadata(mediaMetadata)
                .build();
        try {
            mRemoteMediaPlayer.load(mApiClient, mediaInfo, true)
                    .setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                        @Override
                        public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                            if (result.getStatus().isSuccess()) {
                                Log.d(TAG, "Media loaded successfully");
                            }
                        }
                    });
        } catch (IllegalStateException e) {
            Log.e(TAG, "Problem occurred with media during loading", e);
        } catch (Exception e) {
            Log.e(TAG, "Problem opening media during loading", e);
        }
    }

    private void teardown() {
        Log.d(TAG, "Tearing down ChromecastHelper");
        if (mApiClient != null) {
            if (mApplicationStarted) {
                if (mApiClient.isConnected()) {
                    Cast.CastApi.stopApplication(mApiClient, mSessionId);
                    if (mRemoteMediaPlayer != null) {
                        mRemoteMediaPlayer.stop(mApiClient);
                    }
                    mApiClient.disconnect();
                }
                mApplicationStarted = false;
            }
            mApiClient = null;
        }
        mSelectedDevice = null;
        mWaitingForReconnect = false;
        mSessionId = null;
    }

    /**
     * MediaRouter callbacks
     */
    @Override
    public void onRouteAdded(MediaRouter router, MediaRouter.RouteInfo route) {
        if (++mRouteCount >= 1) {
            // Show the button when a device is discovered.
            mMainMenuActivity.getMediaRouteButton().setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRouteRemoved(MediaRouter router, MediaRouter.RouteInfo route) {
        if (--mRouteCount == 0) {
            // Hide the button if there are no devices discovered.
            mMainMenuActivity.getMediaRouteButton().setVisibility(View.GONE);
        }
    }

    @Override
    public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo info) {
        // Handle route selection.
        mSelectedDevice = CastDevice.getFromBundle(info.getExtras());

        startCasting();
    }

    @Override
    public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo info) {
        mSelectedDevice = null;
    }

    /**
     * Chromecast connection callbacks
     */

    @Override
    public void onConnected(Bundle bundle) {
        if (mWaitingForReconnect) {
            mWaitingForReconnect = false;
            //reconnectChannels();
        } else {
            try {
                Cast.CastApi.launchApplication(mApiClient, APP_ID, false)
                        .setResultCallback(
                                new ResultCallback<Cast.ApplicationConnectionResult>() {
                                    @Override
                                    public void onResult(Cast.ApplicationConnectionResult result) {
                                        Status status = result.getStatus();
                                        if (status.isSuccess()) {
                                            ApplicationMetadata applicationMetadata =
                                                    result.getApplicationMetadata();
                                            mSessionId = result.getSessionId();
                                            String applicationStatus = result
                                                    .getApplicationStatus();
                                            mApplicationStarted = true;
                                            loadMPDStream();
                                        } else {
                                            teardown();
                                        }
                                    }
                                }
                        );

            } catch (Exception e) {
                Log.e(TAG, "Failed to launch chromecast application", e);
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        mWaitingForReconnect = true;
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }
}

class CastListener extends Cast.Listener {

}