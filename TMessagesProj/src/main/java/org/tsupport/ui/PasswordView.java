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
import org.tsupport.messenger.R;
import org.tsupport.ui.Views.ActionBar.BaseFragment;

import java.util.Date;

public class PasswordView extends BaseFragment {
    private EditText passwordField;
    private TextView problemText;
    private boolean donePressed = false;
    private View doneButton;

    public PasswordView(Bundle args) {
        super(args);
    }

    public PasswordView() {
        super();
    }



    public boolean onFragmentCreate() {
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
                    getParentActivity().finish();
                }
            });

            doneButton = actionBarLayer.findViewById(R.id.done_button);
            doneButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (donePressed) {
                        return;
                    }
                    donePressed = true;
                    if (checkCode()) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putLong("passwordTimeStampt", (new Date()).getTime());
                        editor.commit();
                        presentFragment(new MessagesActivity(null), true);
                    } else {
                        problemText.setText(LocaleController.getString("IncorrectPassword", R.string.IncorrectPassword));
                        problemText.setVisibility(View.VISIBLE);
                        donePressed = false;
                    }
                }
            });

            cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
            TextView textView = (TextView)doneButton.findViewById(R.id.done_button_text);
            textView.setText(LocaleController.getString("Done", R.string.Done).toUpperCase());

            fragmentView = inflater.inflate(R.layout.password_layout, container, false);

            passwordField = (EditText) fragmentView.findViewById(R.id.password_field);

            passwordField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    if (i == EditorInfo.IME_ACTION_NEXT) {
                        doneButton.performClick();
                    }
                    return false;
                }
            });
            problemText = (TextView)fragmentView.findViewById(R.id.password_problem);
            problemText.setVisibility(View.GONE);

            TextView headerLabel = (TextView)fragmentView.findViewById(R.id.settings_section_text);
            headerLabel.setText(LocaleController.getString("Password", R.string.Password));
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
        passwordField.requestFocus();
        AndroidUtilities.showKeyboard(passwordField);
    }

    private boolean checkCode() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        int code = preferences.getInt("password", 0);
        if (code == 0)
            return true;
        int insertedCode = 0;
        try {
            insertedCode = Integer.parseInt(passwordField.getText().toString());
        } catch (Exception e) {
            return false;
        }
        if (code == insertedCode) {
            return true;
        }
        return false;
    }
}
