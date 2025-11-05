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

import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.location.GeoUtil;
import com.android.dialer.R;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

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

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final int EXECUTOR_SHUTDOWN_TIMEOUT = 500;

    // Define the projection as a static class member
    private static final String[] CALL_LOG_PROJECTION = {
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
    };

    private enum CallLogResult {
        LOADING,
        LOADED,
        EMPTY,
        ERROR
    }

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

        // Set the initial state to LOADING
        setResult(CallLogResult.LOADING);

        mAdapter = new WatchCallLogAdapter(mGroupedCallLogItems, this::onCallLogItemClick, this);
        mRecyclerView.setAdapter(mAdapter);

        fetchCallLogs();

        // Register observer for real-time updates
        mCallLogObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                fetchCallLogs();
            }
        };
        getContentResolver().registerContentObserver(CallLog.Calls.CONTENT_URI,
                true, mCallLogObserver);
    }

    @Override
    protected void onResume() {
        super.onResume();

        fetchCallLogs();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCallLogObserver != null) {
            getContentResolver().unregisterContentObserver(mCallLogObserver);
        }

        // Shutdown the executor service
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown(); // Initiates an orderly shutdown
            try {
                if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT,
                                        TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow(); // Force shutdown if not terminated
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Executor shutdown interrupted: ", e);
                executor.shutdownNow(); // Force shutdown on interruption
                Thread.currentThread().interrupt(); // Preserve interrupt status
            }
        }
    }

    /**
     * Fetches recent call logs from the CallLog content provider and groups them by time
     * (Today, Yesterday, Older). It performs the database query and contact lookup in a
     * background thread using ExecutorService to avoid blocking the UI thread. Once the
     * data is processed, it updates the UI on the main thread.
     */
    private void fetchCallLogs() {
        try {
            executor.execute(() -> {
                List<Object> groupedCallLogItems = new ArrayList<>();
                ContentResolver resolver = getContentResolver();

                try (Cursor cursor = resolver.query(
                        CallLog.Calls.CONTENT_URI,
                        CALL_LOG_PROJECTION,
                        null, null, CallLog.Calls.DATE + " DESC")) {
                    if (cursor != null) {
                        processCallLogs(cursor, groupedCallLogItems);
                    }

                    // Handle success or empty state
                    runOnUiThread(() -> {
                        if (isDestroyed() || isFinishing()) {
                            Log.d(TAG, "Activity no longer active, skipping UI update");
                            return;
                        }
                        if (groupedCallLogItems.isEmpty()) {
                            setResult(CallLogResult.EMPTY);
                        } else {
                            mGroupedCallLogItems.clear();
                            mGroupedCallLogItems.addAll(groupedCallLogItems);
                            if (mAdapter != null) {
                                mAdapter.notifyDataSetChanged();
                            }
                            setResult(CallLogResult.LOADED);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error fetching call logs", e);

                    // Handle error state
                    runOnUiThread(() -> {
                        if (isDestroyed() || isFinishing()) {
                            Log.d(TAG, "Activity no longer active, skipping UI update");
                            return;
                        }
                        setResult(CallLogResult.ERROR);
                    });
                }
            });
        } catch (RejectedExecutionException e) {
            Log.e(TAG, "Task submission rejected", e);
            runOnUiThread(() -> setResult(CallLogResult.ERROR));
        }
    }


    /**
     * Processes the call logs from the given cursor and groups them by time.
     *
     * @param cursor The cursor containing call log data.
     * @param groupedCallLogItems The list to store grouped call log items.
     */
    private void processCallLogs(Cursor cursor, List<Object> groupedCallLogItems) {
        if (cursor == null) {
            Log.e(TAG, "Received null cursor in processCallLogs");
            return;
        }

        long currentTime = System.currentTimeMillis();
        long oneDayMillis = 24 * 60 * 60 * 1000;
        boolean todayAdded = false;
        boolean yesterdayAdded = false;
        boolean olderAdded = false;

        Map<String, String> contactNameCache = new HashMap<>();
        Set<String> numbersToLookup = new HashSet<>();

        // Column indices
        int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
        int cachedNameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
        int typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE);
        int dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE);
        int durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION);

        // First pass: collect all unique numbers
        while (cursor.moveToNext()) {
            String number = cursor.getString(numberIndex);
            if (number != null && !number.isEmpty() && !contactNameCache.containsKey(number)) {
                numbersToLookup.add(number);
            }
        }

        // Get country ISO once for all entries
        String countryIso = GeoUtil.getCurrentCountryIso(WatchRecentsActivity.this);

        // Perform batch contact lookups
        for (String number : numbersToLookup) {
            try (Cursor contactCursor = getContentResolver().query(
                    Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                            Uri.encode(number)),
                    new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},
                    null, null, null)) {
                if (contactCursor != null && contactCursor.moveToFirst()) {
                    int displayNameIndex =
                        contactCursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                    String name = contactCursor.getString(displayNameIndex);
                    contactNameCache.put(number, name);
                } else {
                    // Cache formatted number as fallback
                    contactNameCache.put(number, formatPhoneNumber(number, countryIso));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error performing contact lookup for number: " + number, e);
                contactNameCache.put(number, formatPhoneNumber(number, countryIso));
            }
        }

        // Reset cursor position
        cursor.moveToPosition(-1);

        // Second pass: process call logs with cached contact information
        while (cursor.moveToNext()) {
            String number = cursor.getString(numberIndex);
            String cachedName = cursor.getString(cachedNameIndex);
            int type = cursor.getInt(typeIndex);
            long date = cursor.getLong(dateIndex);
            long duration = cursor.getLong(durationIndex);

            // Resolve the display name
            String name = resolveDisplayName(contactNameCache.get(number),
                    cachedName, formatPhoneNumber(number, countryIso));

            // Add time label string as an item which will be shown in the calllog list:
            // If current call log date is of today, add "Today" time label if not added.
            // else If current call log date is one day older, add "Yesterday" time label if not
            // added. else If current call log date is older than 2 days, add "Older" time label
            // if not added.
            if (!olderAdded) {
                long timeDifference = currentTime - date;
                if (timeDifference < oneDayMillis) {
                    if (!todayAdded) {
                        groupedCallLogItems.add(getString(R.string.call_log_today));
                        todayAdded = true;
                    }
                } else if (timeDifference < 2 * oneDayMillis) {
                    if (!yesterdayAdded) {
                        groupedCallLogItems.add(getString(R.string.call_log_yesterday));
                        yesterdayAdded = true;
                    }
                } else {
                    if (!olderAdded) {
                        groupedCallLogItems.add(getString(R.string.call_log_older));
                        olderAdded = true;
                    }
                }
            }

            groupedCallLogItems.add(new WatchCallLogItem(name, formatPhoneNumber(
                        number, countryIso), type, date, duration));
        }
    }

    /**
     * Formats a phone number using PhoneNumberHelper. If formatting fails, logs the error
     * and returns the original number.
     *
     * @param number The phone number to format.
     * @param countryIso The country ISO code for formatting.
     * @return The formatted phone number, or the original number if formatting fails.
     */
    private String formatPhoneNumber(String number, String countryIso) {
        if (number == null || number.isEmpty()) {
            return getString(R.string.unknown_number);
        }
        try {
            String formatted = null;
            if (countryIso != null) {
                formatted = PhoneNumberHelper.formatNumber(this, number, null, countryIso);
            } else {
                // Try formatting without country code.
                // By default US country code will be taken
                formatted = PhoneNumberHelper.formatNumber(this, number, null, null);
            }
            return formatted != null ? formatted : number;
        } catch (Exception e) {
            Log.e(TAG, "Error formatting phone number: " + number, e);
            return number; // Return the original number if formatting fails
        }
    }

    /**
     * Resolves the display name by checking multiple name options in order.
     * Returns the first non-null and non-empty name, or "Unknown" if none are valid.
     */
    private String resolveDisplayName(String... nameOptions) {
        if (nameOptions == null || nameOptions.length == 0) {
            return getString(R.string.unknown);
        }
        for (String name : nameOptions) {
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        return getString(R.string.unknown);
    }

    /**
     * Updates the UI based on the current state of call log fetching.
     *
     * @param result The current state of call log fetching.
     */
    private void setResult(CallLogResult result) {
        switch (result) {
            case LOADING:
                mEmptyView.setText(R.string.loading_call_logs);
                mEmptyView.setVisibility(View.VISIBLE);
                mRecyclerView.setVisibility(View.GONE);
                mMakeCallButton.setVisibility(View.GONE);
                break;

            case LOADED:
                mEmptyView.setVisibility(View.GONE);
                mRecyclerView.setVisibility(View.VISIBLE);
                mMakeCallButton.setVisibility(View.GONE);
                break;

            case EMPTY:
                mEmptyView.setText(R.string.no_call_logs);
                mEmptyView.setVisibility(View.VISIBLE);
                mRecyclerView.setVisibility(View.GONE);
                mMakeCallButton.setVisibility(View.VISIBLE);
                break;

            case ERROR:
                mEmptyView.setText(R.string.error_task_rejected);
                mEmptyView.setVisibility(View.VISIBLE);
                mRecyclerView.setVisibility(View.GONE);
                mMakeCallButton.setVisibility(View.GONE);
                break;
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
