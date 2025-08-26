/*
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.dialer.watch;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.support.v7.app.AppCompatActivity;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import android.net.Uri;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.wear.widget.WearableRecyclerView;

import com.android.dialer.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * WatchRecentsActivity displays recent call logs grouped by time (Today, Yesterday, Older)
 * in a WearableRecyclerView. It observes changes to the call log content provider
 * and updates the UI in real-time.
 */
public class WatchRecentsActivity extends AppCompatActivity {

    private static final String TAG = WatchRecentsActivity.class.getSimpleName();
    private static final String INTENT_EXTRA_NAME = "name";
    private static final String INTENT_EXTRA_NUMBER = "number";
    private static final String INTENT_EXTRA_DATE = "date";
    private static final String INTENT_EXTRA_DURATION = "duration";
    private static final String INTENT_EXTRA_TYPE = "type";

    private WearableRecyclerView mRecyclerView;
    private WatchCallLogAdapter mAdapter;
    private final List<Object> mGroupedCallLogItems = new ArrayList<>();
    private ContentObserver mCallLogObserver;
    private TextView mEmptyView;
    private Button mMakeCallButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Activity started");
        setContentView(R.layout.activity_watch_recents);

        mRecyclerView = findViewById(R.id.recyclerView);
        mEmptyView = findViewById(R.id.empty_view);
        mMakeCallButton = findViewById(R.id.make_call_button);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setEdgeItemsCenteringEnabled(false);

        mMakeCallButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, WatchDialpadActivity.class);
            startActivity(intent);
        });

        fetchCallLogs();
        mAdapter = new WatchCallLogAdapter(mGroupedCallLogItems, this::onCallLogItemClick);
        mRecyclerView.setAdapter(mAdapter);

        // Register observer for real-time updates
        mCallLogObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                Log.d(TAG, "Call log content changed, refreshing...");
                refreshCallLogs();
            }
        };
        getContentResolver().registerContentObserver(CallLog.Calls.CONTENT_URI,
                true, mCallLogObserver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Refreshing call logs");
        refreshCallLogs();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCallLogObserver != null) {
            Log.d(TAG, "onDestroy: Unregistering ContentObserver");
            getContentResolver().unregisterContentObserver(mCallLogObserver);
        }
    }

    /**
     * Fetches and groups call logs by time (Today, Yesterday, Older).
     */
    private void fetchCallLogs() {
        Log.d(TAG, "Fetching call logs...");
        mGroupedCallLogItems.clear();
        ContentResolver resolver = getContentResolver();

        try (Cursor cursor = resolver.query(
                CallLog.Calls.CONTENT_URI,
                new String[]{CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
                        CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION},
                null, null, CallLog.Calls.DATE + " DESC")) {
            if (cursor != null && cursor.moveToFirst()) {
                long currentTime = System.currentTimeMillis();
                long oneDayMillis = 24 * 60 * 60 * 1000;
                boolean todayAdded = false;
                boolean yesterdayAdded = false;
                boolean olderAdded = false;

                // Collect all unique numbers for batch lookup
                Map<String, String> contactNameCache = new HashMap<>();
                List<String> numbersToLookup = new ArrayList<>();

                do {
                    String number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));
                    if (!contactNameCache.containsKey(number)) {
                        numbersToLookup.add(number);
                    }
                } while (cursor.moveToNext());

                cursor.moveToFirst();

                // Perform a single batch query for all numbers
                for (String number : numbersToLookup) {
                    if (number == null || number.isEmpty()) {
                        Log.d(TAG, "Skipping empty or invalid number");
                        continue;
                    }

                    try {
                        String normalizedNumber = PhoneNumberUtils.normalizeNumber(number);
                        Uri lookupUri = Uri.withAppendedPath(
                                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                                Uri.encode(normalizedNumber));

                        // Use try-with-resources for contactCursor
                        try (Cursor contactCursor = resolver.query(
                                lookupUri,
                                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},
                                null, null, null)) {
                            if (contactCursor != null && contactCursor.moveToFirst()) {
                                String updatedName = contactCursor.getString(
                                        contactCursor.getColumnIndex(
                                                ContactsContract.PhoneLookup.DISPLAY_NAME));
                                contactNameCache.put(number, updatedName);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error performing contact lookup for number: " + number, e);
                    }
                }

                // Process call logs and group them by time
                do {
                    String number =
                            cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));
                    String cachedName =
                            cursor.getString(cursor.getColumnIndex(CallLog.Calls.CACHED_NAME));
                    int type =
                            cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE));
                    long date =
                            cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE));
                    long duration =
                            cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DURATION));

                    // Use the updated name from the contact lookup if available
                    String updatedName = contactNameCache.getOrDefault(number, cachedName);
                    String name = (updatedName != null && !updatedName.isEmpty()) ? updatedName :
                            (cachedName != null && !cachedName.isEmpty()) ? cachedName :
                                    (number != null && !number.isEmpty()) ? number : "Unknown";
                    long timeDifference = currentTime - date;
                    if (timeDifference < oneDayMillis) {
                        if (!todayAdded) {
                            mGroupedCallLogItems.add(getString(R.string.call_log_today));
                            todayAdded = true;
                        }
                    } else if (timeDifference < 2 * oneDayMillis) {
                        if (!yesterdayAdded) {
                            mGroupedCallLogItems.add(getString(R.string.call_log_yesterday));
                            yesterdayAdded = true;
                        }
                    } else {
                        if (!olderAdded) {
                            mGroupedCallLogItems.add(getString(R.string.call_log_older));
                            olderAdded = true;
                        }
                    }
                    mGroupedCallLogItems.add(new WatchCallLogItem(
                            name, number, type, date, duration));
                } while (cursor.moveToNext());

                Log.d(TAG, "Call logs fetched: " + mGroupedCallLogItems.size() + " items");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching call logs", e);
        } finally {
            if (mGroupedCallLogItems.isEmpty()) {
                mRecyclerView.setVisibility(View.GONE);
                mEmptyView.setVisibility(View.VISIBLE);
                mMakeCallButton.setVisibility(View.VISIBLE);
            } else {
                mRecyclerView.setVisibility(View.VISIBLE);
                mEmptyView.setVisibility(View.GONE);
                mMakeCallButton.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Refreshes the call log data and updates the adapter.
     */
    private void refreshCallLogs() {
        Log.d(TAG, "Refreshing call logs...");
        fetchCallLogs();
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Handles click events on individual call log items.
     */
    private void onCallLogItemClick(WatchCallLogItem item) {
        Intent intent = new Intent(this, WatchCallDetailsActivity.class);
        intent.putExtra(INTENT_EXTRA_NAME, item.getName());
        intent.putExtra(INTENT_EXTRA_NUMBER, item.getNumber());
        intent.putExtra(INTENT_EXTRA_TYPE, item.getType());
        intent.putExtra(INTENT_EXTRA_DATE, item.getDate());
        intent.putExtra(INTENT_EXTRA_DURATION, item.getDuration());
        startActivity(intent);
    }
}
