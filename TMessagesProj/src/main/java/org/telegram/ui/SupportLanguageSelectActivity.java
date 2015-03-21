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
import android.widget.Toast;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.android.NotificationCenter;
import org.telegram.android.TemplateSupport;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;

public class SupportLanguageSelectActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private EditText languageCodeField;
    private View doneButton;

    private final static int done_button = 1;

    public SupportLanguageSelectActivity() {
        super();
    }

    public boolean onFragmentCreate() {
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.notificationsSettingsUpdated);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateInterfaces);
        super.onFragmentDestroy();
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
                        if (languageCodeField.length() != 0) {
                            TemplateSupport.getInstance().removeAll();
                            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putString("languageSupport", languageCodeField.getText().toString().toLowerCase().trim());
                            editor.commit();
                            TemplateSupport.loadDefaults();
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


            languageCodeField = new EditText(getParentActivity());
            linearLayout.addView(languageCodeField);
            languageCodeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            languageCodeField.setHintTextColor(0xff979797);
            languageCodeField.setHint(LocaleController.getString("Language", R.string.Language));
            languageCodeField.setMaxLines(1);
            languageCodeField.setSingleLine(true);
            languageCodeField.setTypeface(null, Typeface.BOLD);
            languageCodeField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            languageCodeField.setTextColor(0xff212121);
            languageCodeField.setImeOptions(EditorInfo.IME_ACTION_DONE);
            languageCodeField.setPadding(AndroidUtilities.dp(15), AndroidUtilities.dp(0), AndroidUtilities.dp(15), AndroidUtilities.dp(15));
            AndroidUtilities.clearCursorDrawable(languageCodeField);
            languageCodeField.setGravity(LocaleController.isRTL ? Gravity.END : Gravity.START);

            LinearLayout.LayoutParams keyFieldLayoutParams = (LinearLayout.LayoutParams) languageCodeField.getLayoutParams();
            keyFieldLayoutParams.topMargin = AndroidUtilities.dp(15);
            keyFieldLayoutParams.gravity = Gravity.CENTER_VERTICAL;
            keyFieldLayoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
            keyFieldLayoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            languageCodeField.setLayoutParams(keyFieldLayoutParams);
            languageCodeField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    if (i == EditorInfo.IME_ACTION_DONE) {
                        doneButton.performClick();
                        return true;
                    }
                    return false;
                }
            });

            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            languageCodeField.setText(preferences.getString("languageSupport", "en"));

            languageCodeField.requestFocus();
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
            languageCodeField.requestFocus();
            AndroidUtilities.showKeyboard(languageCodeField);
        }
    }

    @Override
    public void onOpenAnimationEnd() {
        languageCodeField.requestFocus();
        AndroidUtilities.showKeyboard(languageCodeField);
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.updateTemplatesNotification) {
            Toast.makeText(getParentActivity().getApplicationContext(), LocaleController.getString("templatesUpdated", R.string.templatesUpdated), Toast.LENGTH_SHORT).show();

        }
    }
}
