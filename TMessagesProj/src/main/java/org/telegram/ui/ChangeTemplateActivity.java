/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.android.TemplateSupport;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;

public class ChangeTemplateActivity extends BaseFragment {
    private String key;
    private String value;
    private EditText keyField;
    private EditText valueField;
    private View doneButton;
    private Boolean keyExists;

    private final static int done_button = 1;

    public ChangeTemplateActivity(Bundle args) {
        super(args);
    }

    public boolean onFragmentCreate() {
        key = arguments.getString("templateKey");
        if (key == null)
            key = "";
        value = arguments.getString("templateValue");
        if (value == null)
            value = "";
        return super.onFragmentCreate();
    }

    @Override
    public View createView(LayoutInflater inflater) {
        if (fragmentView == null) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setAllowOverlayTitle(true);
            actionBar.setTitle(LocaleController.getString("template", R.string.template));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == done_button) {
                        String keyText = keyField.getText().toString().trim();
                        String valueText = valueField.getText().toString();
                        if (keyText.length() != 0 && valueText.length() != 0 && !TemplateSupport.isDefault(keyText)) {
                            saveTemplate();
                            finishFragment();
                        }
                    }
                }
            });

            ActionBarMenu menu = actionBar.createMenu();
            doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

            fragmentView = new ScrollView(getParentActivity());
            ScrollView scrollView = (ScrollView) fragmentView;
            ScrollView.LayoutParams scrollViewLayout = new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT,ScrollView.LayoutParams.MATCH_PARENT);
            scrollView.setLayoutParams(scrollViewLayout);

            LinearLayout linearLayout = new LinearLayout(getParentActivity());
            scrollView.addView(linearLayout);
            FrameLayout.LayoutParams linearLayoutParams = (FrameLayout.LayoutParams) linearLayout.getLayoutParams();
            linearLayoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
            linearLayoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT;
            linearLayout.setLayoutParams(linearLayoutParams);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            linearLayout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(0));


            keyField = new EditText(getParentActivity());
            linearLayout.addView(keyField);
            keyField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            keyField.setHintTextColor(0xff979797);
            keyField.setHint(LocaleController.getString("templateKey", R.string.templateKey));
            keyField.setMaxLines(1);
            keyField.setSingleLine(true);
            keyField.setTypeface(null, Typeface.BOLD);
            keyField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            keyField.setTextColor(0xff212121);
            keyField.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            keyField.setPadding(AndroidUtilities.dp(15), AndroidUtilities.dp(0), AndroidUtilities.dp(15), AndroidUtilities.dp(15));
            AndroidUtilities.clearCursorDrawable(keyField);
            keyField.setGravity(LocaleController.isRTL ? Gravity.END : Gravity.START);

            LinearLayout.LayoutParams keyFieldLayoutParams = (LinearLayout.LayoutParams) keyField.getLayoutParams();
            keyFieldLayoutParams.topMargin = AndroidUtilities.dp(15);
            keyFieldLayoutParams.gravity = Gravity.CENTER_VERTICAL;
            keyFieldLayoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
            keyFieldLayoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            keyField.setLayoutParams(keyFieldLayoutParams);
            keyField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    if (i == EditorInfo.IME_ACTION_NEXT) {
                        if (!TemplateSupport.isDefault(keyField.getText().toString().trim().toLowerCase())) {
                            valueField.requestFocus();
                            valueField.setSelection(valueField.length());
                        }
                        return true;
                    }
                    return false;
                }
            });

            valueField = new EditText(getParentActivity());
            linearLayout.addView(valueField);
            valueField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            valueField.setHintTextColor(0xff979797);
            valueField.setHint(LocaleController.getString("templateValue", R.string.templateValue));
            valueField.setMaxLines(15);
            valueField.setMinLines(5);
            valueField.setSingleLine(false);
            valueField.setInputType(valueField.getInputType() |InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            valueField.setTextColor(0xff212121);
            valueField.setBackgroundDrawable(null);
            valueField.setImeOptions(EditorInfo.IME_ACTION_DONE);
            valueField.setPadding(AndroidUtilities.dp(15), AndroidUtilities.dp(0), AndroidUtilities.dp(15), AndroidUtilities.dp(15));
            AndroidUtilities.clearCursorDrawable(valueField);
            valueField.setGravity(Gravity.BOTTOM);

            LinearLayout.LayoutParams valueFieldLayoutParams = (LinearLayout.LayoutParams) valueField.getLayoutParams();
            valueFieldLayoutParams.topMargin = AndroidUtilities.dp(10);
            valueFieldLayoutParams.gravity = Gravity.CENTER_VERTICAL;
            valueFieldLayoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
            valueFieldLayoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            valueField.setLayoutParams(valueFieldLayoutParams);
            valueField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    if (i == EditorInfo.IME_ACTION_DONE) {
                        doneButton.performClick();
                        return true;
                    }
                    return false;
                }
            });

            if (key != null && key.length() != 0) { // If key has content
                keyField.setText(key);  // Set text in textfield
                keyField.setEnabled(false); // Disable field
                keyExists = true;
            } else { // If key hasn't
                keyExists = false;
            }
            if (value != null && value.length() != 0) {
                valueField.setText(value);
                if (TemplateSupport.isDefault(key)) {
                    valueField.setEnabled(false);
                }
            }
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        boolean animations = preferences.getBoolean("view_animations", false);
        if (!animations) {
            if (!TemplateSupport.isDefault(keyField.getText().toString().trim().toLowerCase())) {
                if (!keyExists) {
                    keyField.requestFocus();
                    AndroidUtilities.showKeyboard(keyField);
                } else {
                    valueField.requestFocus();
                    AndroidUtilities.showKeyboard(valueField);
                }
            }
        }
    }

    private void saveTemplate() {
        String keyText = keyField.getText().toString().trim();
        String valueText = valueField.getText().toString();
        if (keyText.length() <= 0 || valueText.length() <= 0) {
            return;
        }
        TemplateSupport.putTemplate(keyText.toLowerCase(), valueText);
    }

    @Override
    public void onOpenAnimationEnd() {
        if (!TemplateSupport.isDefault(keyField.getText().toString().trim().toLowerCase())) {
            if (!keyExists) {
                keyField.requestFocus();
                AndroidUtilities.showKeyboard(keyField);
            } else {
                valueField.requestFocus();
                AndroidUtilities.showKeyboard(valueField);
            }
        }
    }
}
