/* Copyright (c) 2017-2018, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/**
 * Changes from Qualcomm Technologies, Inc. are provided under the following license:
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.incallui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetDialogFragment;
import android.util.Log;
import android.view.ContextThemeWrapper;
// QTI_BEGIN: 04-11-2025: Fix cropped call transfer option & align to circular UI
import android.view.Gravity;
// QTI_END: 04-11-2025: Fix cropped call transfer option & align to circular UI
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.dialer.common.FragmentUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

// QTI_BEGIN: 04-11-2025: Fix cropped call transfer option & align to circular UI
import com.android.incallui.SystemFeatures;
// QTI_END: 04-11-2025: Fix cropped call transfer option & align to circular UI

/**
 * Common bottom sheet used to place extention feature options.
 * Called from AnswerFragment, InCallFragment and VideoCallFragment.
 */
public class ExtBottomSheetFragment extends BottomSheetDialogFragment {

  private static final String OPTIONS = "options";
  /**
   * This is the number of options shown per row of the bottomsheet.
   * If the options are not in multiple of 4 , the remainder will be
   * equally distributed in the last row of the bottomsheet.
   */
  private final int OPTIONS_PER_ROW = 4;
  private final int PADDING_SHEET_ITEM = 48;
  private final int PADDING_SHEET_ITEM_VERTICAL = 24;
  private final String COLOR_TEXT = "#E5E4E2";
  private final String COLOR_ITEM_BACKGROUND = "#0041C2";
  private final String COLOR_ITEM_BORDER = "#E5E4E2";
  private Context mContext;
  // QTI_BEGIN: 04-11-2025: Fix cropped call transfer option & align to circular UI
  private boolean mIsRoundWatch;
  // QTI_END: 04-11-2025: Fix cropped call transfer option & align to circular UI

  /** Callback interface */
  public interface ExtBottomSheetActionCallback {
    /*
     * Provides callback to the parent when an option is
     * selected on ExtBottomSheetFragment
     */
    void optionSelected(@Nullable String text);

    /*
     * Provides callback to the parent when ExtBottomSheetFragment
     * is dismissed.
     */
    void sheetDismissed();
  }

  /* Creates a new instance of ExtBottomSheetFragment */
  public static ExtBottomSheetFragment newInstance(
      @Nullable ConcurrentHashMap<String,Boolean> optionsMap) {
    logi("newInstance with values : " + optionsMap);
    ExtBottomSheetFragment fragment = new ExtBottomSheetFragment();
    Bundle args = new Bundle();
    ConcurrentHashMap<String,Boolean> map = new ConcurrentHashMap<String,Boolean>();
    map.putAll(optionsMap);
    map.values().removeAll(Collections.singleton(Boolean.FALSE));
    ArrayList<String> options = new ArrayList<>(map.keySet());
    args.putStringArrayList(OPTIONS, options);
    fragment.setArguments(args);
    map = null;
    return fragment;
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater layoutInflater, @Nullable ViewGroup viewGroup, @Nullable Bundle bundle) {
    logi("onCreateView");
    LinearLayout layout = new LinearLayout(getContext());
    final List<String> options = getArguments().getStringArrayList(OPTIONS);
    layout.setOrientation(LinearLayout.VERTICAL);

    if (options != null) {
      // QTI_BEGIN: 04-11-2025: Fix cropped call transfer option & align to circular UI
      if (mIsRoundWatch) {
        // Each option is displayed in its own row centered horizontally
        // The background spans the full width of the screen.
        for (String option : options) {
            LinearLayout rowLayout = new LinearLayout(getContext());
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                                          LayoutParams.WRAP_CONTENT));
            TextView optionView = newTextView(option);
            rowLayout.addView(optionView);
            layout.addView(rowLayout);
        }
        // QTI_END: 04-11-2025: Fix cropped call transfer option & align to circular UI
      } else {
          int count = 0;
          int numOfOptions = options.size();
          int numberOfRows =
              ((numOfOptions % OPTIONS_PER_ROW) == 0)
              ? (numOfOptions/OPTIONS_PER_ROW) : (numOfOptions/OPTIONS_PER_ROW) +1;
          logi("onCreateView: numOfOptions = "+numOfOptions+" numberOfRows: "+numberOfRows);
          for (int i = 0; i < numberOfRows; i++) {
            LinearLayout ll = new LinearLayout(getContext());
            int j = 0;
            while ((j < OPTIONS_PER_ROW) && (count < numOfOptions)) {
              ll.addView(newTextView(options.get(count)));
              j++;
              count++;
            }
            layout.addView(ll);
          }
        }
    }

    layout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                                            LayoutParams.MATCH_PARENT));
    return layout;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    logi("onAttach");
    FragmentUtils.checkParent(this, ExtBottomSheetActionCallback.class);
    mContext = context;
    // QTI_BEGIN: 04-11-2025: Fix cropped call transfer option & align to circular UI
    SystemFeatures.getInstance().init(getContext());
    mIsRoundWatch = SystemFeatures.getInstance().isWatchScreenRound();
    // QTI_END: 04-11-2025: Fix cropped call transfer option & align to circular UI
  }

  /*
   * Dynamically adds new TextViews based on the number of options
   * we plan to show in the sheet. A new text view is created for each option.
   */
  private TextView newTextView(@Nullable final String text) {
    TextView textView = new TextView(mContext);
    if (text != null) {
        textView.setText(text);
    }

    textView.setBackground(textViewBorder());
    textView.setTextColor(Color.parseColor(COLOR_TEXT));

    // QTI_BEGIN: 04-11-2025: Fix cropped call transfer option & align to circular UI
    if (mIsRoundWatch) {
        textView.setPadding(PADDING_SHEET_ITEM, PADDING_SHEET_ITEM_VERTICAL,
                            PADDING_SHEET_ITEM, PADDING_SHEET_ITEM_VERTICAL);
        // TextView spans the full width of the screen.
        // The text is centered within the TextView, and the background spans the full width.
        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        textView.setLayoutParams(params);
        textView.setGravity(Gravity.CENTER);
    } else {
        int padding = PADDING_SHEET_ITEM;
        textView.setPadding(padding, padding, padding, padding);
        LayoutParams params =
            new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT,1f);
        textView.setLayoutParams(params);
    }
    // QTI_END: 04-11-2025: Fix cropped call transfer option & align to circular UI

    textView.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            dismiss();
            FragmentUtils.getParentUnsafe(ExtBottomSheetFragment.this,
                    ExtBottomSheetActionCallback.class).optionSelected(text);
          }
        });
    return textView;
  }

  private GradientDrawable textViewBorder() {
     GradientDrawable border = new GradientDrawable();
     border.setColor(Color.parseColor(COLOR_ITEM_BACKGROUND));
     border.setStroke(1,Color.parseColor(COLOR_ITEM_BORDER));
     return border;
  }

  @Override
  public void onDismiss(DialogInterface dialogInterface) {
    super.onDismiss(dialogInterface);
    FragmentUtils.getParentUnsafe(this, ExtBottomSheetActionCallback.class).sheetDismissed();
  }

  private static void logi(String msg) {
    Log.i("ExtBottomSheet",msg);
  }

  private void loge(String msg) {
    Log.e("ExtBottomSheet",msg);
  }
}
