/*
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.dialer.watch;

import android.content.Intent;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.android.dialer.R;
import com.android.dialer.app.settings.DialerSettingsActivity;

/**
 * WatchDialerActivity is the main entry point for the Watch OS version of the Dialer app.
 * It displays a list of dialer options (Dialpad, Contacts, Recents, Settings)
 * Clicking an option launches the corresponding activity or intent.
 */
public class WatchDialerActivity extends AppCompatActivity {

    private static final String TAG = WatchDialerActivity.class.getSimpleName();

    /**
     * Called when the activity is first created.
     * Sets the layout and initializes click listeners for each dialer option.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Activity started");
        setContentView(R.layout.activity_watch_dialer);
        setupCustomButtons();
    }

    /**
     * Sets up click listeners for each custom dialer option.
     * Each item is a LinearLayout styled to look like a button.
     */
    private void setupCustomButtons() {
        // Dialpad option
        findViewById(R.id.dialpadItem).setOnClickListener(v -> {
            startActivity(new Intent(this, WatchDialpadActivity.class));
        });

        // Contacts option
        findViewById(R.id.contactsItem).setOnClickListener(v -> {
            // Launch the default Contacts app
            Intent contactsIntent = new Intent(Intent.ACTION_VIEW);
            contactsIntent.setData(ContactsContract.Contacts.CONTENT_URI);
            startActivity(contactsIntent);
        });

        // Recents option
        findViewById(R.id.recentsItem).setOnClickListener(v -> {
            startActivity(new Intent(this, WatchRecentsActivity.class));
        });

        // Settings option
        findViewById(R.id.settingsItem).setOnClickListener(v -> {
            startActivity(new Intent(this, DialerSettingsActivity.class));
        });
    }
}
