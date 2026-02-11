/*
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.dialer.watch;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.android.dialer.R;
import com.android.dialer.dialpadview.DialpadFragment;
import com.android.dialer.dialpadview.DialpadFragment.DialpadListener;
import com.android.dialer.dialpadview.DialpadFragment.LastOutgoingCallCallback;

/**
 * WatchDialpadActivity hosts the dialpad interface on Watch OS.
 * It loads the DialpadFragment and handles interactions such as
 * query changes, call placement, and UI visibility.
 */
public class WatchDialpadActivity extends Activity
        implements DialpadFragment.OnDialpadQueryChangedListener,
        DialpadFragment.HostInterface,
        DialpadListener {

    private static final String TAG = WatchDialpadActivity.class.getSimpleName();

    private DialpadFragment mDialpadFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_dialpad);

        if (savedInstanceState == null) {
            mDialpadFragment = new DialpadFragment();
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.replace(R.id.dialpad_fragment_container,
                    mDialpadFragment, "DIALPAD_FRAGMENT");
            transaction.commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mDialpadFragment == null) {
            mDialpadFragment =
                    (DialpadFragment) getFragmentManager().findFragmentByTag("DIALPAD_FRAGMENT");
        }

        if (mDialpadFragment != null) {
            View fragmentView = mDialpadFragment.getView();
            if (fragmentView != null) {
                View callButton = fragmentView.findViewById(R.id.dialpad_floating_action_button);
                if (callButton != null) {
                    callButton.setVisibility(View.VISIBLE);
                } else {
                    Log.e(TAG, "Call button is null, unable to set visibility");
                }
            } else {
                Log.e(TAG, "Dialpad fragment view is null");
            }
        } else {
            Log.e(TAG, "Dialpad fragment is null");
        }
    }

    @Override
    public void onDialpadQueryChanged(String query) {
    }

    @Override
    public boolean onDialpadSpacerTouchWithEmptyQuery() {
        return false;
    }

    @Override
    public boolean shouldShowDialpadChooser() {
        return false;
    }

    @Override
    public void getLastOutgoingCall(LastOutgoingCallCallback callback) {
    }

    @Override
    public void onDialpadShown() {
        if (mDialpadFragment != null && mDialpadFragment.getView() != null) {
            View callButton =
                    mDialpadFragment.getView().findViewById(R.id.dialpad_floating_action_button);
            if (callButton != null) {
                callButton.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onCallPlacedFromDialpad() {
        if (mDialpadFragment != null) {
            mDialpadFragment.clearDialpad();
        }
    }
}
