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
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.tsupport.android.AndroidUtilities;
import org.tsupport.android.LocaleController;
import org.tsupport.messenger.R;
import org.tsupport.ui.ActionBar.ActionBar;
import org.tsupport.ui.ActionBar.ActionBarMenu;
import org.tsupport.ui.ActionBar.BaseFragment;

import java.util.Date;

public class PasswordView extends BaseFragment {
    private EditText passwordField;
    private TextView problemText;
    private boolean donePressed = false;
    private View doneButton;

    private final static int done_button = 1;

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
            //actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setAllowOverlayTitle(true);

            actionBar.setTitle(LocaleController.getString("Password", R.string.Password));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        //finishFragment();
                    } else if (id == done_button) {
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
                            finishFragment();
                        } else {
                            problemText.setText(LocaleController.getString("IncorrectPassword", R.string.IncorrectPassword));
                            problemText.setVisibility(View.VISIBLE);
                            donePressed = false;
                        }
                    }
                }
            });

            fragmentView = new RelativeLayout(inflater.getContext());
            fragmentView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            fragmentView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });


            ActionBarMenu menu = actionBar.createMenu();
            doneButton = menu.addItem(done_button, R.drawable.ic_done);

            passwordField = new EditText(inflater.getContext());
            passwordField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            passwordField.setHintTextColor(0xffa3a3a3);
            passwordField.setCursorVisible(false);
            passwordField.setHint(LocaleController.getString("Password", R.string.Password));
            passwordField.setHintTextColor(0xFF979797);
            passwordField.setTextColor(0xff000000);
            passwordField.setPadding(AndroidUtilities.dp(15), 0, AndroidUtilities.dp(15), AndroidUtilities.dp(15));
            passwordField.setMaxLines(1);

            passwordField.setMinWidth(AndroidUtilities.dp(110));
            passwordField.setMaxWidth(AndroidUtilities.dp(160));
            passwordField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            passwordField.setInputType(InputType.TYPE_CLASS_NUMBER);
            passwordField.setTransformationMethod(PasswordTransformationMethod.getInstance());
            passwordField.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            passwordField.setGravity(Gravity.CENTER_HORIZONTAL);
            AndroidUtilities.clearCursorDrawable(passwordField);
            passwordField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    if (i == EditorInfo.IME_ACTION_DONE && doneButton != null) {
                        doneButton.performClick();
                        return true;
                    }
                    return false;
                }
            });

            ((RelativeLayout) fragmentView).addView(passwordField);
            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)passwordField.getLayoutParams();
            layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
            layoutParams.topMargin = AndroidUtilities.dp(20);
            layoutParams.leftMargin = AndroidUtilities.dp(0);
            layoutParams.rightMargin = AndroidUtilities.dp(0);
            layoutParams.bottomMargin = AndroidUtilities.dp(0);
            layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            passwordField.setLayoutParams(layoutParams);


            problemText = new TextView(inflater.getContext());
            problemText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            problemText.setTextColor(0xFF316F9F);
            problemText.setLineSpacing(2,1);
            problemText.setGravity(Gravity.CENTER);
            problemText.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(12));
            ((RelativeLayout) fragmentView).addView(problemText);

            RelativeLayout.LayoutParams layoutParamsProblemText = (RelativeLayout.LayoutParams)problemText.getLayoutParams();
            layoutParamsProblemText.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
            layoutParamsProblemText.topMargin = AndroidUtilities.dp(10);
            layoutParamsProblemText.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParamsProblemText.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            problemText.setLayoutParams(layoutParamsProblemText);
            problemText.setVisibility(View.GONE);
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
