/*
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.dialer.watch;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.support.v7.app.AppCompatActivity;
import android.telephony.PhoneNumberUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.precall.PreCall;
import com.android.dialer.R;

import java.util.Locale;

/**
 * WatchCallDetailsActivity displays detailed information about a specific call log entry.
 * It allows users to view call details, make a call, send a message, copy the number,
 * or add/view the contact associated with the number.
 */
public class WatchCallDetailsActivity extends AppCompatActivity {

    private static final String TAG = WatchCallDetailsActivity.class.getSimpleName();
    private static final int CALL_PERMISSION_REQUEST_CODE = 1001;
    private static final String INTENT_EXTRA_NAME = "name";
    private static final String INTENT_EXTRA_NUMBER = "number";
    private static final String INTENT_EXTRA_DATE = "date";
    private static final String INTENT_EXTRA_DURATION = "duration";
    private static final String INTENT_EXTRA_TYPE = "type";

    private String mNumber;
    private String mName;
    private TextView mNameView;
    private TextView mNumberView;
    private ImageButton mAddViewContactIcon;
    private ImageButton mMessageButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_call_details);

        // Retrieve call log data from the Intent
        Intent intent = getIntent();
        mName = intent.getStringExtra(INTENT_EXTRA_NAME);
        mNumber = intent.getStringExtra(INTENT_EXTRA_NUMBER);
        long date = intent.getLongExtra(INTENT_EXTRA_DATE, 0);
        long duration = intent.getLongExtra(INTENT_EXTRA_DURATION, 0);
        int callType = intent.getIntExtra(INTENT_EXTRA_TYPE, -1);

        // Set up the header (e.g., name, number, date, time, and duration)
        mNameView = findViewById(R.id.call_details_name);
        mNumberView = findViewById(R.id.call_details_number);
        mAddViewContactIcon = findViewById(R.id.add_view_contact_icon);
        mMessageButton = findViewById(R.id.message_button);
        TextView typeView = findViewById(R.id.call_details_type);
        TextView dateView = findViewById(R.id.call_details_date);
        TextView durationView = findViewById(R.id.call_details_duration);
        ImageButton callButton = findViewById(R.id.call_button);
        ImageButton copyNumberIcon = findViewById(R.id.copy_number_icon);

        // Common UI setup - applies to both emergency and normal calls
        setupCommonUiElements(typeView, dateView, durationView, date, duration, callType);
        setupCommonButtonListeners(callButton, copyNumberIcon);

        // Check if this is an emergency call
        boolean isEmergency = isEmergencyCall();

        if (isEmergency) {
            handleEmergencyCallUI();
        } else {
            handleNormalCallUi();
        }
    }

    /**
     * Sets up UI elements that are common to both emergency and normal calls
     */
    private void setupCommonUiElements(TextView typeView, TextView dateView,
                                    TextView durationView, long date,
                                    long duration, int callType) {
        typeView.setText(getCallTypeString(callType));
        dateView.setText(date != 0 ? formatDate(date) : getString(R.string.unknown_date));

        if (duration >= 0) {
            durationView.setText(formatDuration(duration));
        } else {
            durationView.setVisibility(TextView.GONE);
        }
    }

    /**
     * Sets up button listeners that are common to both emergency and normal calls
     */
    private void setupCommonButtonListeners(ImageButton callButton,
                                        ImageButton copyNumberIcon) {
        callButton.setOnClickListener(v -> makeCall());

        copyNumberIcon.setOnClickListener(v -> {
            if (mNumber != null && !mNumber.isEmpty()) {
                copyNumberToClipboard();
            } else {
                showToast(getString(R.string.no_number_to_copy));
            }
        });
    }

    /**
     * Handles UI specific to emergency calls
     */
    private void handleEmergencyCallUI() {
        // Handle emergency call display - SHOW ONLY "Emergency" TEXT, HIDE NUMBER
        mNameView.setText(getString(R.string.emergency_number));
        mNameView.setVisibility(View.VISIBLE);
        mNumberView.setVisibility(View.GONE);

        // Hide buttons not applicable for emergency calls
        mMessageButton.setVisibility(View.GONE);
        mAddViewContactIcon.setVisibility(View.INVISIBLE);
    }

    /**
     * Handles UI specific to normal calls
     */
    private void handleNormalCallUi() {
        updateCallDetailsInfoUI(false);

        // Set up Message button specific to normal calls
        mMessageButton.setVisibility(View.VISIBLE);
        mMessageButton.setOnClickListener(v -> sendMessage());
    }

    /**
     * Updates contact information UI elements with appropriate styling
     */
    private void updateCallDetailsInfoUI(boolean applyBoldStyling) {
        // Handle name visibility
        if (mName != null && !mName.isEmpty()) {
            mNameView.setText(mName);
            mNameView.setVisibility(View.VISIBLE);

            // Apply bold styling if requested
            if (applyBoldStyling) {
                mNameView.setTypeface(null, android.graphics.Typeface.BOLD);
            }

            // Update "View Contact" button
            mAddViewContactIcon.setImageResource(R.drawable.quantum_ic_people_vd_theme_24);
            mAddViewContactIcon.setOnClickListener(v -> viewContact());
            mAddViewContactIcon.setVisibility(View.VISIBLE);
        } else {
            mNameView.setVisibility(View.GONE);

            // Update "Add to Contacts" button
            mAddViewContactIcon.setImageResource(R.drawable.quantum_ic_person_add_white_24);
            mAddViewContactIcon.setOnClickListener(v -> addToContacts());
            mAddViewContactIcon.setVisibility(View.VISIBLE);
        }

        // Handle number visibility
        if (mNumber != null && !mNumber.isEmpty()) {
            mNumberView.setText(mNumber);
            mNumberView.setVisibility(View.VISIBLE);

            // Apply bold styling if requested and there's no name
            if (applyBoldStyling) {
                mNumberView.setTypeface(null, mName == null || mName.isEmpty() ?
                        android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            }
        } else {
            mNumberView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshContactDetails();
    }

    private void refreshContactDetails() {
        // Only refresh if we have a number to look up
        if (mNumber == null || mNumber.isEmpty()) {
            return;
        }

        boolean contactFound = false;
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(mNumber));
        Cursor contactCursor = null;

        try {
            contactCursor = getContentResolver().query(
                    uri,
                    new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME,
                            ContactsContract.PhoneLookup.NUMBER},
                    null,
                    null,
                    null);

            if (contactCursor != null && contactCursor.moveToFirst()) {
                // Update the name and number if they have changed
                String updatedName = contactCursor.getString(contactCursor.getColumnIndex(
                        ContactsContract.PhoneLookup.DISPLAY_NAME));
                String updatedNumber = contactCursor.getString(contactCursor.getColumnIndex(
                        ContactsContract.PhoneLookup.NUMBER));

                // Check if the number has changed
                if (!mNumber.equals(updatedNumber)) {
                    mNumber = updatedNumber; // Update the number
                }

                mName = updatedName; // Update the name
                contactFound = true; // Mark that the contact was found
            }
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing contact details", e);
        } finally {
            if (contactCursor != null) {
                contactCursor.close();
            }
        }

        // If the contact is not found, clear the name
        if (!contactFound) {
            mName = null;
        }

        // Update the UI with the refreshed details
        updateUI();
    }

    private void updateUI() {
        boolean isEmergency = isEmergencyCall();

        if (isEmergency) {
            handleEmergencyCallUI();
        } else {
            updateCallDetailsInfoUI(true);
        }
    }

    /**
     * Check if this call represents an emergency call.
     */
    private boolean isEmergencyCall() {
        return WatchCallLogAdapter.isEmergencyCall(mNumber);
    }

    /**
     * Returns a string representation of the call type.
     *
     * @param type The call type from CallLog.Calls.
     * @return A string representing the call type and updates the call type icon.
     */
    private String getCallTypeString(int type) {
        ImageView callTypeIcon = findViewById(R.id.call_details_type_icon);

        switch (type) {
            case CallLog.Calls.INCOMING_TYPE:
                callTypeIcon.setImageResource(R.drawable.quantum_ic_call_received_vd_theme_24);
                callTypeIcon.setColorFilter(getResources().getColor(R.color.dialer_call_green),
                        android.graphics.PorterDuff.Mode.SRC_IN);
                return getString(R.string.incoming_call);
            case CallLog.Calls.OUTGOING_TYPE:
                callTypeIcon.setImageResource(R.drawable.quantum_ic_call_made_vd_theme_24);
                callTypeIcon.setColorFilter(getResources().getColor(R.color.dialer_call_green),
                        android.graphics.PorterDuff.Mode.SRC_IN);
                return getString(R.string.outgoing_call);
            case CallLog.Calls.MISSED_TYPE:
                callTypeIcon.setImageResource(R.drawable.quantum_ic_call_missed_vd_theme_24);
                callTypeIcon.setColorFilter(getResources().getColor(R.color.dialer_red),
                        android.graphics.PorterDuff.Mode.SRC_IN);
                return getString(R.string.missed_call);
            case CallLog.Calls.REJECTED_TYPE:
                callTypeIcon.setImageResource(R.drawable.quantum_ic_call_missed_vd_theme_24);
                callTypeIcon.setColorFilter(getResources().getColor(R.color.dialer_red),
                        android.graphics.PorterDuff.Mode.SRC_IN);
                return getString(R.string.declined_call);
            default:
                callTypeIcon.setImageResource(R.drawable.quantum_ic_call_missed_vd_theme_24);
                return getString(R.string.unknown_call_type);
        }
    }

    /**
     * Initiates a phone call to the current number.
     */
    private void makeCall() {
        if (checkSelfPermission(
                android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            CallIntentBuilder callIntentBuilder =
                new CallIntentBuilder(mNumber, CallInitiationType.Type.CALL_DETAILS);
            PreCall.start(this, callIntentBuilder);
        } else {
            // Request the permission
            requestPermissions(new String[]{android.Manifest.permission.CALL_PHONE},
                    CALL_PERMISSION_REQUEST_CODE);
            showToast(getString(R.string.call_permission_not_granted));
        }
    }

    // Add permission result handler
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        if (requestCode == CALL_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                makeCall();
            } else {
                showToast(getString(R.string.call_permission_not_granted));
            }
        }
    }

    /**
     * Sends a message to the current number.
     */
    private void sendMessage() {
        Intent messageIntent = new Intent(Intent.ACTION_VIEW);
        messageIntent.setData(Uri.parse("sms:" + mNumber));
        startActivity(messageIntent);
    }

    /**
     * Copies the current number to the clipboard.
     */
    private void copyNumberToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText(getString(R.string.phone_number_label), mNumber);
            clipboard.setPrimaryClip(clip);
            showToast(getString(R.string.number_copied, mNumber));
        } else {
            showToast(getString(R.string.unable_to_access_clipboard));
        }
    }

    /**
     * Adds the current number to the contacts.
     */
    private void addToContacts() {
        Intent intent = new Intent(Intent.ACTION_INSERT);
        intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
        intent.putExtra(ContactsContract.Intents.Insert.PHONE, mNumber);
        startActivity(intent);
    }

    /**
     * Views the contact associated with the current number.
     */
    private void viewContact() {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(mNumber));

        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[]{ContactsContract.PhoneLookup.LOOKUP_KEY},
                null,
                null,
                null)) {

            if (cursor != null && cursor.moveToFirst()) {
                int lookupKeyIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY);
                if (lookupKeyIndex != -1) {
                    String lookupKey = cursor.getString(lookupKeyIndex);

                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    Uri contactUri = Uri.withAppendedPath(
                            ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey);
                    intent.setData(contactUri);
                    startActivity(intent);
                } else {
                    showToast(getString(R.string.contact_not_found));
                }
            } else {
                showToast(getString(R.string.contact_not_found));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error viewing contact", e);
            showToast(getString(R.string.contact_not_found));
        }
    }

    /**
     * Formats the date into a readable string.
     *
     * @param date The date in milliseconds.
     * @return A formatted date string.
     */
    private String formatDate(long date) {
        return DateUtils.formatDateTime(
            this,
            date,
            DateUtils.FORMAT_SHOW_TIME
            | DateUtils.FORMAT_SHOW_DATE
            | DateUtils.FORMAT_SHOW_YEAR
        );
    }

    /**
     * Formats the duration into a readable string
     *
     * @param elapsedSeconds The duration in seconds.
     * @return A formatted duration string.
     */
    private String formatDuration(long elapsedSeconds) {
        long minutes = elapsedSeconds / 60;
        long seconds = elapsedSeconds % 60;

        // Cap the minutes at 99 for UI display
        if (minutes > 99) {
            minutes = 99;
        }

        // Format like CallLogDurations - "9m 55s" or "55s"
        if (elapsedSeconds >= 60) {
            // For durations >= 60 seconds, show minutes and seconds
            return String.format(Locale.getDefault(), "%dm %02ds", minutes, seconds);
        } else {
            // For durations < 60 seconds, show only seconds
            return String.format(Locale.getDefault(), "%ds", seconds);
        }
    }

    /**
     * Displays a custom toast message.
     *
     * @param message The message to display.
     */
    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }
}
