/**
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 **/

package com.android.incallui;

import android.content.Context;
import android.app.UiModeManager;
import android.content.res.Configuration;

/* The purpose of this singleton class is to fetch system features when
   app starts. This should be done by Application or first Activity
   class. Then other classes use the data it has already initialised from Framework
 */
public class SystemFeatures {
    private static SystemFeatures _instance;
    private boolean mIsFeatureWatch = false;
    private boolean mIsScreenRound = false;

    private SystemFeatures() {
    }

    public static SystemFeatures getInstance() {
        if (_instance == null) {
            _instance = new SystemFeatures();
        }
        return _instance;
    }

    public void init(Context context) {
        if ( ((UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE)).
                getCurrentModeType() == Configuration.UI_MODE_TYPE_WATCH) {
                mIsFeatureWatch = true;
        }
        mIsScreenRound = context.getResources().getConfiguration().isScreenRound();
    }

    public boolean isFeatureWatchEnabled() {
        return mIsFeatureWatch;
    }

    public boolean isWatchScreenRound() {
        return mIsScreenRound;
    }
}
