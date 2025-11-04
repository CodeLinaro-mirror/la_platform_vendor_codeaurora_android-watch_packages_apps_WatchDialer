/*
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.dialer.watch;

import android.content.Context;
import android.provider.CallLog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.android.dialer.R;

import java.util.List;

/**
 * Adapter for displaying call logs and section headers in a RecyclerView on Watch OS.
 * Supports two view types: section headers (String) and call log entries (WatchCallLogItem).
 */
public class WatchCallLogAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = WatchCallLogAdapter.class.getSimpleName();

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_CALL_LOG = 1;

    private final List<Object> items;
    private final OnItemClickListener onItemClickListener;

    /**
     * Listener interface for handling item click events.
     */
    public interface OnItemClickListener {
        void onItemClick(WatchCallLogItem item);
    }

    /**
     * Constructor for the adapter.
     *
     * @param items List containing headers (String) and call log items.
     * @param onItemClickListener Callback for item click events.
     */
    public WatchCallLogAdapter(List<Object> items, OnItemClickListener onItemClickListener) {
        this.items = items;
        this.onItemClickListener = onItemClickListener;
        Log.d(TAG, "Adapter initialized with " + items.size() + " items.");
    }

    @Override
    public int getItemViewType(int position) {
        int viewType =
                (items.get(position) instanceof String) ? VIEW_TYPE_HEADER : VIEW_TYPE_CALL_LOG;
        return viewType;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            View view = inflater.inflate(R.layout.watch_item_section_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.watch_item_call_log, parent, false);
            return new CallLogViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind((String) items.get(position));
        } else if (holder instanceof CallLogViewHolder) {
            ((CallLogViewHolder) holder).bind((WatchCallLogItem) items.get(position),
                    onItemClickListener);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * ViewHolder for section headers.
     */
    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView headerView;

        HeaderViewHolder(View itemView) {
            super(itemView);
            headerView = itemView.findViewById(R.id.section_header);
        }

        /**
         * Binds the header text to the TextView.
         *
         * @param header The header string to display.
         */
        void bind(String header) {
            headerView.setText(header);
        }
    }

    /**
     * ViewHolder for call log entries.
     */
    static class CallLogViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameView;
        private final ImageView callTypeIcon;

        CallLogViewHolder(View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.contact_name);
            callTypeIcon = itemView.findViewById(R.id.call_type_icon);
        }

        /**
         * Binds a WatchCallLogItem to the view, setting name/number and call type icon.
         *
         * @param item     The call log item.
         * @param listener The click listener for the item.
         */
        void bind(WatchCallLogItem item, OnItemClickListener listener) {
            Context context = itemView.getContext();
            callTypeIcon.setImageResource(getCallTypeIcon(item.getType(), context));

            // Determine display name or fallback to number or "Unknown Number"
            String displayName;
            boolean hasName = item.getName() != null && !item.getName().trim().isEmpty();
            boolean hasNumber = item.getNumber() != null && !item.getNumber().isEmpty();

            if (hasName) {
                displayName = item.getName();
            } else if (hasNumber) {
                displayName = item.getNumber();
            } else {
                displayName = context.getString(R.string.unknown_number);
            }

            nameView.setText(displayName);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }

        /**
         * Returns the appropriate icon resource and applies color based on call type.
         *
         * @param type    Call type from CallLog.Calls.
         * @param context Context for accessing resources.
         * @return Drawable resource ID for the call type icon.
         */
        private int getCallTypeIcon(int type, Context context) {
            int colorResId;
            int iconResId;

            switch (type) {
                case CallLog.Calls.INCOMING_TYPE:
                    colorResId = R.color.dialer_call_green;
                    iconResId = R.drawable.quantum_ic_call_received_vd_theme_24;
                    break;
                case CallLog.Calls.OUTGOING_TYPE:
                    colorResId = R.color.dialer_call_green;
                    iconResId = R.drawable.quantum_ic_call_made_vd_theme_24;
                    break;
                case CallLog.Calls.MISSED_TYPE:
                case CallLog.Calls.REJECTED_TYPE:
                    colorResId = R.color.dialer_red;
                    iconResId = R.drawable.quantum_ic_call_missed_vd_theme_24;
                    break;
                default:
                    colorResId = R.color.black;
                    iconResId = R.drawable.quantum_ic_call_missed_vd_theme_24;
                    break;
            }

            callTypeIcon.setColorFilter(ContextCompat.getColor(context, colorResId),
                    android.graphics.PorterDuff.Mode.SRC_IN);
            return iconResId;
        }
    }
}
