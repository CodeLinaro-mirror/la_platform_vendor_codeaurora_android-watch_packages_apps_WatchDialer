/*
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.dialer.watch;

/**
 * WatchCallLogItem represents a single call log entry on Watch OS.
 * It holds details such as contact name, phone number, call type, date, and duration.
 */
public class WatchCallLogItem {

    private static final String TAG = WatchCallLogItem.class.getSimpleName();

    private String name;
    private final String number;
    private final int type;
    private final long date;
    private final long duration;

    public WatchCallLogItem(String name, String number, int type, long date, long duration) {
        this.name = name;
        this.number = number;
        this.type = type;
        this.date = date;
        this.duration = duration;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNumber() {
        return number;
    }

    public int getType() {
        return type;
    }

    public long getDate() {
        return date;
    }

    public long getDuration() {
        return duration;
    }
}
