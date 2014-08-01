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
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.tsupport.android.AndroidUtilities;
import org.tsupport.android.LocaleController;
import org.tsupport.android.TemplateSupport;
import org.tsupport.messenger.FileLog;
import org.tsupport.messenger.R;
import org.tsupport.ui.Views.ActionBar.BaseFragment;

public class SettingsChangeTemplateActivity extends BaseFragment {
    private String key;
    private String value;
    private EditText keyField;
    private EditText valueField;
    private View doneButton;
    private Boolean keyExists;

    public SettingsChangeTemplateActivity(Bundle args) {
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
            actionBarLayer.setCustomView(R.layout.settings_do_action_layout);
            Button cancelButton = (Button)actionBarLayer.findViewById(R.id.cancel_button);
            cancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    finishFragment();
                }
            });
            doneButton = actionBarLayer.findViewById(R.id.done_button);
            doneButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (keyField.getText().length() != 0 && valueField.getText().length() != 0) {
                        saveTemplate();
                        finishFragment();
                    }
                }
            });

            cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
            TextView textView = (TextView)doneButton.findViewById(R.id.done_button_text);
            textView.setText(LocaleController.getString("Done", R.string.Done).toUpperCase());

            fragmentView = inflater.inflate(R.layout.settings_change_template_layout, container, false);

            keyField = (EditText)fragmentView.findViewById(R.id.key_field);
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
            valueField = (EditText)fragmentView.findViewById(R.id.value_field);
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
            TextView headerLabel = (TextView)fragmentView.findViewById(R.id.settings_section_text);
            headerLabel.setText(LocaleController.getString("template", R.string.template));
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
