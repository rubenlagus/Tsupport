/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.tsupport.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.tsupport.android.AndroidUtilities;
import org.tsupport.android.LocaleController;
import org.tsupport.android.TemplateSupport;
import org.tsupport.messenger.FileLog;
import org.tsupport.messenger.R;
import org.tsupport.ui.ActionBar.ActionBar;
import org.tsupport.ui.ActionBar.ActionBarMenu;
import org.tsupport.ui.ActionBar.BaseFragment;

public class ModifyTemplateActivity extends BaseFragment {
    private String key;
    private String value;
    private EditText keyField;
    private EditText valueField;
    private View doneButton;
    private Boolean keyExists;

    private final static int done_button = 1;

    public ModifyTemplateActivity(Bundle args) {
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
    public View createView(LayoutInflater inflater, ViewGroup container) {
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
                        if (keyField.getText().length() != 0 && valueField.getText().length() != 0) {
                            saveTemplate();
                            finishFragment();
                        }
                    }
                }
            });

            ActionBarMenu menu = actionBar.createMenu();
            doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

            fragmentView = new ScrollView(inflater.getContext());
            fragmentView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            fragmentView.setBackgroundColor(0xFFFFFFFF);
            getParentActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);


            LinearLayout linearView = new LinearLayout(inflater.getContext());
            linearView.setOrientation(LinearLayout.VERTICAL);
            linearView.setPadding(AndroidUtilities.dp(16),AndroidUtilities.dp(8), AndroidUtilities.dp(16), 0);
            linearView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });
            ((ScrollView) fragmentView).addView(linearView);

            ScrollView.LayoutParams linearViewLayoutParams = (ScrollView.LayoutParams)linearView.getLayoutParams();
            linearViewLayoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
            linearViewLayoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            linearView.setLayoutParams(linearViewLayoutParams);

            keyField = new EditText(inflater.getContext());
            keyField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            keyField.setHintTextColor(0xff979797);
            keyField.setTextColor(0xff212121);
            keyField.setPadding(0, 0, 0, 0);
            keyField.setMaxLines(1);
            keyField.setLines(1);
            keyField.setSingleLine(true);
            keyField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            keyField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            keyField.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            keyField.setHint(LocaleController.getString("templateKey", R.string.templateKey));
            keyField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    if (i == EditorInfo.IME_ACTION_NEXT) {
                        valueField.requestFocus();
                        valueField.setSelection(valueField.length());
                        return true;
                    }
                    return false;
                }
            });
            AndroidUtilities.clearCursorDrawable(keyField);
            linearView.addView(keyField);
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams)keyField.getLayoutParams();
            layoutParams.topMargin = AndroidUtilities.dp(24);
            layoutParams.height = AndroidUtilities.dp(36);
            layoutParams.leftMargin = AndroidUtilities.dp(24);
            layoutParams.rightMargin = AndroidUtilities.dp(24);
            layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            keyField.setLayoutParams(layoutParams);

            valueField = new EditText(inflater.getContext());
            valueField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
            valueField.setHintTextColor(0xffa3a3a3);
            valueField.setTextColor(0xff000000);
            valueField.setPadding(AndroidUtilities.dp(15), 0, AndroidUtilities.dp(15), AndroidUtilities.dp(15));
            valueField.setMaxLines(1);
            valueField.setLines(1);
            valueField.setSingleLine(true);
            valueField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            valueField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            valueField.setImeOptions(EditorInfo.IME_ACTION_DONE);
            valueField.setHint(LocaleController.getString("templateValue", R.string.templateValue));
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
            AndroidUtilities.clearCursorDrawable(valueField);
            linearView.addView(valueField);
            layoutParams = (LinearLayout.LayoutParams)valueField.getLayoutParams();
            layoutParams.topMargin = AndroidUtilities.dp(10);
            layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            valueField.setLayoutParams(layoutParams);

            if (key != null && key.compareToIgnoreCase("")!=0) { // If key has content
                keyField.setText(key);  // Set text in textfield
                keyField.setEnabled(false); // Disable field
                keyExists = true;
            }
            else { // If key hasn't
                keyField.setSelection(keyField.length()); // Can edit
                keyExists = false;
            }
            if (value != null && value.compareToIgnoreCase("") != 0) {
                valueField.setText(value);
                if(keyExists)
                    valueField.setSelection(valueField.length());
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
        boolean animations = preferences.getBoolean("view_animations", true);
        if (!animations) {
            if (!keyExists) {
                keyField.requestFocus();
                AndroidUtilities.showKeyboard(keyField);
            }
            else {
                valueField.requestFocus();
                AndroidUtilities.showKeyboard(valueField);
            }
        }
    }

    private void saveTemplate() {
        FileLog.e("tsupport", "Key: " + keyField.getText().toString() + " --> Value: " + valueField.getText().toString());
        if (keyField.getText() == null || valueField.getText() == null || keyField.getText().length() <= 0 || valueField.getText().length() <= 0) {
            return;
        }
        TemplateSupport.getInstance().putTemplate(keyField.getText().toString(), valueField.getText().toString());
    }

    @Override
    public void onOpenAnimationEnd() {
        if (!keyExists) {
            keyField.requestFocus();
            AndroidUtilities.showKeyboard(keyField);
        }
        else {
            valueField.requestFocus();
            AndroidUtilities.showKeyboard(valueField);
        }
    }
}
