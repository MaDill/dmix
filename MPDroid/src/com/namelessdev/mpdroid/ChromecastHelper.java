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

import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;

import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.view.View;

/**
 * Helper for keeping the chromecast code out of the main activity
 *
 * @author Arnaud BARISAIN-MONROSE
 */
public class ChromecastHelper extends MediaRouter.Callback {

    public static final String APP_ID = "5D4E0A01";

    private MainMenuActivity mMainMenuActivity;

    private MediaRouter mMediaRouter;

    private MediaRouteSelector mMediaRouteSelector;

    private MediaRouter.Callback mMediaRouterCallback;

    private CastDevice mSelectedDevice;

    private int mRouteCount = 0;

    public ChromecastHelper(MainMenuActivity activity) {
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

        // TODO : Add casting support here
    }

    @Override
    public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo info) {
        mSelectedDevice = null;
    }
}
