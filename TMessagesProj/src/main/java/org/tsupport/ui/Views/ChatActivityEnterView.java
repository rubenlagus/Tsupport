/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.tsupport.ui.Views;

import android.animation.Animator;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.PowerManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.tsupport.android.AndroidUtilities;
import org.tsupport.android.Emoji;
import org.tsupport.android.LocaleController;
import org.tsupport.android.MediaController;
import org.tsupport.android.MessagesController;
import org.tsupport.android.NotificationCenter;
import org.tsupport.android.SendMessagesHelper;
import org.tsupport.android.TemplateSupport;
import org.tsupport.messenger.ConnectionsManager;
import org.tsupport.messenger.FileLog;
import org.tsupport.messenger.R;
import org.tsupport.messenger.TLRPC;
import org.tsupport.ui.AnimationCompat.AnimatorListenerAdapterProxy;
import org.tsupport.ui.AnimationCompat.AnimatorSetProxy;
import org.tsupport.ui.AnimationCompat.ObjectAnimatorProxy;
import org.tsupport.ui.AnimationCompat.ViewProxy;
import org.tsupport.ui.ApplicationLoader;

import java.util.ArrayList;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatActivityEnterView implements NotificationCenter.NotificationCenterDelegate, SizeNotifierRelativeLayout.SizeNotifierRelativeLayoutDelegate {

    public static interface ChatActivityEnterViewDelegate {
        public abstract void onMessageSend();
        public abstract void needSendTyping();
        public abstract void onAttachButtonHidden();
        public abstract void onAttachButtonShow();
    }

    private AutoCompleteTextView messsageEditText = null;
    private ImageButton sendButton;
    private PopupWindow emojiPopup;
    private PopupWindow templatePopup;
    private ImageView emojiButton;
    private EmojiView emojiView;
    private TemplateView templateView;
    private TextView recordTimeText;
    //private ImageButton audioSendButton;
    private View recordPanel;
    private View slideText;
    private PowerManager.WakeLock mWakeLock;
    private SizeNotifierRelativeLayout sizeNotifierRelativeLayout;
    private FrameLayout attachButton;
    private AnimatorSetProxy runningAnimation;
    private AnimatorSetProxy runningAnimation2;
    private ObjectAnimatorProxy runningAnimationAudio;
    private int runningAnimationType;
    private int audioInterfaceState;
    private static final Pattern pattern = Pattern.compile("((?:[^\\s(]+\\()|(?:\\.{2}[^\\s\\.]+\\.{2}))");
    private static final Pattern patternContact = Pattern.compile("^contact:(\\+[0-9]+)\\s*(\\S+)\\s*([^\\n]+)(\\n|$)");
    private static final Pattern patternIssue = Pattern.compile("^#issue:([\\w]+)$");
    private static final Pattern patternIssueSolved = Pattern.compile("^#solved:([\\w]+)$");

    private int keyboardHeight;
    private int keyboardHeightLand;
    private boolean keyboardVisible;
    private boolean sendByEnter;
    private long lastTypingTimeSend;
    private String lastTimeString;
    private float startedDraggingX = -1;
    private float distCanMove = AndroidUtilities.dp(80);
    private boolean recordingAudio;
    private boolean searchForTemplate = true;

    private Activity parentActivity;
    private long dialog_id;
    private boolean ignoreTextChange;
    private ChatActivityEnterViewDelegate delegate;
    private TextWatcher textWatcher = null;
    private TreeMap<String, String> templates = new TreeMap<String, String>();

    public ChatActivityEnterView() {
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.recordStarted);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.recordStartError);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.recordStopped);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.recordProgressChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioDidSent);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.hideEmojiKeyboard);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateTemplatesNotification);
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        sendByEnter = preferences.getBoolean("send_by_enter", false);
        templates = TemplateSupport.getInstance().getAll();
    }

    public void onDestroy() {
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.recordStarted);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.recordStartError);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.recordStopped);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.recordProgressChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.audioDidSent);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.hideEmojiKeyboard);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateTemplatesNotification);
        if (mWakeLock != null) {
            try {
                mWakeLock.release();
                mWakeLock = null;
            } catch (Exception e) {
                FileLog.e("tsupport", e);
            }
        }
        if (sizeNotifierRelativeLayout != null) {
            sizeNotifierRelativeLayout.delegate = null;
        }
    }

    public void setContainerView(Activity activity, View containerView) {
        parentActivity = activity;

        sizeNotifierRelativeLayout = (SizeNotifierRelativeLayout) containerView.findViewById(R.id.chat_layout);
        sizeNotifierRelativeLayout.delegate = this;

        messsageEditText = (AutoCompleteTextView)containerView.findViewById(R.id.chat_text_edit);
        messsageEditText.setThreshold(1);
        messsageEditText.setHint(LocaleController.getString("TypeMessage", R.string.TypeMessage));
        ArrayList<String> keys = new ArrayList<String>();
        keys.addAll(templates.keySet());
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(containerView.getContext(),R.layout.autocompletetemplaterow,keys);
        messsageEditText.setAdapter(adapter);
        messsageEditText.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String key = (String)parent.getItemAtPosition(position);
                messsageEditText.setText(templates.get(key));
                messsageEditText.setSelection(messsageEditText.getText().length());
            }
        });
        attachButton = (FrameLayout) containerView.findViewById(R.id.chat_attach_button);
        attachButton.setVisibility(View.VISIBLE);
        if (attachButton != null) {
            ViewProxy.setPivotX(attachButton, AndroidUtilities.dp(48));
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) messsageEditText.getLayoutParams();
            layoutParams.rightMargin = AndroidUtilities.dp(50);
            messsageEditText.setLayoutParams(layoutParams);
        }

        sendButton = (ImageButton) containerView.findViewById(R.id.chat_send_button);
        sendButton.setVisibility(View.VISIBLE);
        emojiButton = (ImageView) containerView.findViewById(R.id.chat_smile_button);
        //audioSendButton = (ImageButton) containerView.findViewById(R.id.chat_audio_send_button);
        //if (audioSendButton != null) {
        //    ViewProxy.setPivotX(audioSendButton, AndroidUtilities.dp(48));
        //}
        recordPanel = containerView.findViewById(R.id.record_panel);
        recordTimeText = (TextView) containerView.findViewById(R.id.recording_time_text);
        slideText = containerView.findViewById(R.id.slideText);
        TextView textView = (TextView) containerView.findViewById(R.id.slideToCancelTextView);
        textView.setText(LocaleController.getString("SlideToCancel", R.string.SlideToCancel));

        emojiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (templatePopup == null || !templatePopup.isShowing()) {
                    if (emojiPopup == null) {
                        showEmojiPopup(true);
                    } else {
                        showEmojiPopup(!emojiPopup.isShowing());
                    }
                }
            }
        });

        emojiButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (templatePopup == null) {
                    showTemplatePopup(true);
                    return true;
                } else {
                    showTemplatePopup(!templatePopup.isShowing());
                    return true;
                }
            }
        });

        messsageEditText.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if (i == 4 && !keyboardVisible && emojiPopup != null && emojiPopup.isShowing()) {
                    if (keyEvent.getAction() == 1) {
                        showEmojiPopup(false);
                    }
                    return true;
                } else if (i == 4 && !keyboardVisible && templatePopup != null && templatePopup.isShowing()) {
                    if (keyEvent.getAction() == 1) {
                        showTemplatePopup(false);
                    }
                }
                else if (i == KeyEvent.KEYCODE_ENTER && sendByEnter && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                    sendMessage();
                    return true;
                }
                return false;
            }
        });

        messsageEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (messsageEditText.getText().length() < 5) {
                    searchForTemplate = true;
                }
                if (emojiPopup != null && emojiPopup.isShowing()) {
                    showEmojiPopup(false);
                }
                if (templatePopup != null && templatePopup.isShowing()) {
                    showTemplatePopup(false);
                }
            }
        });

        messsageEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_SEND) {
                    sendMessage();
                    return true;
                } else if (sendByEnter) {
                    if (keyEvent != null && i == EditorInfo.IME_NULL && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                        sendMessage();
                        return true;
                    }
                }
                return false;
            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage();
            }
        });

        /*audioSendButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    startedDraggingX = -1;
                    MediaController.getInstance().startRecording(dialog_id);
                    updateAudioRecordIntefrace();
                    audioSendButton.getParent().requestDisallowInterceptTouchEvent(true);
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                    startedDraggingX = -1;
                    MediaController.getInstance().stopRecording(true);
                    recordingAudio = false;
                    updateAudioRecordIntefrace();
                } else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE && recordingAudio) {
                    float x = motionEvent.getX();
                    if (x < -distCanMove) {
                        MediaController.getInstance().stopRecording(false);
                        recordingAudio = false;
                        updateAudioRecordIntefrace();
                    }

                    x = x + ViewProxy.getX(audioSendButton);
                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) slideText.getLayoutParams();
                    if (startedDraggingX != -1) {
                        float dist = (x - startedDraggingX);
                        params.leftMargin = AndroidUtilities.dp(30) + (int) dist;
                        slideText.setLayoutParams(params);
                        float alpha = 1.0f + dist / distCanMove;
                        if (alpha > 1) {
                            alpha = 1;
                        } else if (alpha < 0) {
                            alpha = 0;
                        }
                        ViewProxy.setAlpha(slideText, alpha);
                    }
                    if (x <= ViewProxy.getX(slideText) + slideText.getWidth() + AndroidUtilities.dp(30)) {
                        if (startedDraggingX == -1) {
                            startedDraggingX = x;
                            distCanMove = (recordPanel.getMeasuredWidth() - slideText.getMeasuredWidth() - AndroidUtilities.dp(48)) / 2.0f;
                            if (distCanMove <= 0) {
                                distCanMove = AndroidUtilities.dp(80);
                            } else if (distCanMove > AndroidUtilities.dp(80)) {
                                distCanMove = AndroidUtilities.dp(80);
                            }
                        }
                    }
                    if (params.leftMargin > AndroidUtilities.dp(30)) {
                        params.leftMargin = AndroidUtilities.dp(30);
                        slideText.setLayoutParams(params);
                        ViewProxy.setAlpha(slideText, 1);
                        startedDraggingX = -1;
                    }
                }
                view.onTouchEvent(motionEvent);
                return true;
            }
        });*/

        textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                String message = getTrimmedString(charSequence.toString());
                checkSendButton(true);
                if (searchForTemplate && message.length() > 0 && message.length() < 100) {
                    Matcher matcher = pattern.matcher(message);
                    String newMessage = message;
                    boolean found = false;
                    while(matcher.find()) {
                        String template = TemplateSupport.getInstance().getTemplate(matcher.group(1));
                        if (template != null && template.compareToIgnoreCase("") != 0) {
                            newMessage = newMessage.replace(matcher.group(1),template);
                            found = true;
                        }
                    }
                    if (found) {
                        messsageEditText.removeTextChangedListener(textWatcher);
                        messsageEditText.setText(newMessage);
                        messsageEditText.setSelection(messsageEditText.getText().length());
                        messsageEditText.addTextChangedListener(textWatcher);
                        searchForTemplate = false;
                    }
                }
                if (message.length() != 0 && lastTypingTimeSend < System.currentTimeMillis() - 5000 && !ignoreTextChange) {
                    int currentTime = ConnectionsManager.getInstance().getCurrentTime();
                    TLRPC.User currentUser = null;
                    if ((int) dialog_id > 0) {
                        currentUser = MessagesController.getInstance().getUser((int) dialog_id);
                    }
                    if (currentUser != null && currentUser.status != null && currentUser.status.expires < currentTime) {
                        return;
                    }
                    lastTypingTimeSend = System.currentTimeMillis();
                    if (delegate != null) {
                        delegate.needSendTyping();
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (sendByEnter && editable.length() > 0 && editable.charAt(editable.length() - 1) == '\n') {
                    sendMessage();
                }
                int i = 0;
                ImageSpan[] arrayOfImageSpan = editable.getSpans(0, editable.length(), ImageSpan.class);
                int j = arrayOfImageSpan.length;
                while (true) {
                    if (i >= j) {
                        Emoji.replaceEmoji(editable, messsageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20));
                        return;
                    }
                    editable.removeSpan(arrayOfImageSpan[i]);
                    i++;
                }
            }
        };
        messsageEditText.addTextChangedListener(textWatcher);

        checkSendButton(false);
    }

    private void sendMessage() {
        if (processSendingText(messsageEditText.getText().toString())) {
			NotificationCenter.getInstance().postNotificationName(NotificationCenter.readChatNotification, dialog_id);
            messsageEditText.setText("");
            lastTypingTimeSend = 0;
            if (delegate != null) {
                delegate.onMessageSend();
            }
        }
    }

    public void sendMessage(String text) {
        if (processSendingText(text)) {
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.readChatNotification, dialog_id);
            messsageEditText.setText("");
            lastTypingTimeSend = 0;
            if (delegate != null) {
                delegate.onMessageSend();
            }
        }
    }

    public boolean processSendingText(String text) {
        String templateText = TemplateSupport.getInstance().getTemplate(text); // Check in Template file
        if (templateText.compareToIgnoreCase("") != 0)
            text = getTrimmedString(templateText);
        else
            text = getTrimmedString(text);
        if (text.length() != 0) {
            int count = (int) Math.ceil(text.length() / 4096.0f);
            Matcher matcher = patternIssue.matcher(text);
            if (matcher.find()) {
                String issueId = matcher.group(1);
                String message = LocaleController.getString("IssueNoted", R.string.IssueNoted).replace("@id",issueId);
                SendMessagesHelper.getInstance().sendMessage(message, dialog_id);
            } else {
                matcher = patternIssueSolved.matcher(text);
                if (matcher.find()) {
                    String issueId = matcher.group(1);
                    String message = LocaleController.getString("IssueSolved", R.string.IssueSolved).replace("@id", issueId);
                    SendMessagesHelper.getInstance().sendMessage(message, dialog_id);
                } else {
                    for (int a = 0; a < count; a++) {
                        String mess = text.substring(a * 4096, Math.min((a + 1) * 4096, text.length()));
                        matcher = patternContact.matcher(mess);
                        if (matcher.find()) {
                            String number = matcher.group(1);
                            String name = matcher.group(2);
                            String surname = matcher.group(3);
                            mess = mess.replace(matcher.group(), "");
                            SendMessagesHelper.getInstance().sendMessage(mess, dialog_id);
                            SendMessagesHelper.getInstance().sendMessageSupport(number, name, surname, dialog_id, "");
                        } else {
                            SendMessagesHelper.getInstance().sendMessage(mess, dialog_id);
                        }
                    }
                }
            }
            return true;
        }
        else { // Mark as read but send nothing
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.readChatNotification, dialog_id);
            return false;
        }

    }

    private String getTrimmedString(String src) {
        String result = src.trim();
        if (result.length() == 0) {
            return result;
        }
        while (src.startsWith("\n")) {
            src = src.substring(1);
        }
        while (src.endsWith("\n")) {
            src = src.substring(0, src.length() - 1);
        }
        return src;
    }

    private void checkSendButton(final boolean animated) {
        String message = getTrimmedString(messsageEditText.getText().toString());
        if (message.length() > 0) {
            if (attachButton.getVisibility() == View.VISIBLE) {
                if (animated) {
                    if (runningAnimationType == 1) {
                        return;
                    }
                    if (runningAnimation != null) {
                        runningAnimation.cancel();
                        runningAnimation = null;
                    }
                    if (runningAnimation2 != null) {
                        runningAnimation2.cancel();
                        runningAnimation2 = null;
                    }

                    if (attachButton != null) {
                        runningAnimation2 = new AnimatorSetProxy();
                        runningAnimation2.playTogether(
                                ObjectAnimatorProxy.ofFloat(attachButton, "alpha", 0.0f),
                                ObjectAnimatorProxy.ofFloat(attachButton, "scaleX", 0.0f)
                        );
                        runningAnimation2.setDuration(100);
                        runningAnimation2.addListener(new AnimatorListenerAdapterProxy() {
                            @Override
                            public void onAnimationEnd(Object animation) {
                                if (runningAnimation2.equals(animation)) {
                                    attachButton.setVisibility(View.GONE);
                                    attachButton.clearAnimation();
                                }
                            }
                        });
                        runningAnimation2.start();

                        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) messsageEditText.getLayoutParams();
                        layoutParams.rightMargin = AndroidUtilities.dp(0);
                        messsageEditText.setLayoutParams(layoutParams);

                        delegate.onAttachButtonHidden();
                    }

                    runningAnimation = new AnimatorSetProxy();
                    runningAnimationType = 1;

                    runningAnimation.setDuration(150);
                    runningAnimation.addListener(new AnimatorListenerAdapterProxy() {
                        @Override
                        public void onAnimationEnd(Object animation) {
                            if (runningAnimation.equals(animation)) {
                                runningAnimation = null;
                                runningAnimationType = 0;
                            }
                        }
                    });
                    runningAnimation.start();
                } else {
                    if (attachButton != null) {
                        attachButton.setVisibility(View.GONE);
                        attachButton.clearAnimation();

                        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) messsageEditText.getLayoutParams();
                        layoutParams.rightMargin = AndroidUtilities.dp(0);
                        messsageEditText.setLayoutParams(layoutParams);
                    }
                }
            }
        } else if (attachButton.getVisibility() == View.GONE || attachButton.getVisibility() == View.VISIBLE) {
            if (animated) {
                if (runningAnimationType == 2) {
                    return;
                }

                if (runningAnimation != null) {
                    runningAnimation.cancel();
                    runningAnimation = null;
                }
                if (runningAnimation2 != null) {
                    runningAnimation2.cancel();
                    runningAnimation2 = null;
                }

                if (attachButton != null) {
                    attachButton.setVisibility(View.VISIBLE);
                    runningAnimation2 = new AnimatorSetProxy();
                    runningAnimation2.playTogether(
                            ObjectAnimatorProxy.ofFloat(attachButton, "alpha", 1.0f),
                            ObjectAnimatorProxy.ofFloat(attachButton, "scaleX", 1.0f)
                    );
                    runningAnimation2.setDuration(100);
                    runningAnimation2.start();

                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) messsageEditText.getLayoutParams();
                    layoutParams.rightMargin = AndroidUtilities.dp(50);
                    messsageEditText.setLayoutParams(layoutParams);

                    delegate.onAttachButtonShow();
                }

                runningAnimation = new AnimatorSetProxy();
                runningAnimationType = 2;

                runningAnimation.setDuration(150);
                runningAnimation.addListener(new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Object animation) {
                        if (runningAnimation.equals(animation)) {
                            runningAnimation = null;
                            runningAnimationType = 0;
                        }
                    }
                });
                runningAnimation.start();
            } else {
                if (attachButton != null) {
                    attachButton.setVisibility(View.VISIBLE);
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) messsageEditText.getLayoutParams();
                    layoutParams.rightMargin = AndroidUtilities.dp(50);
                    messsageEditText.setLayoutParams(layoutParams);
                }
            }
        }
    }

    private void updateAudioRecordIntefrace() {
        if (recordingAudio) {
            if (audioInterfaceState == 1) {
                return;
            }
            audioInterfaceState = 1;
            try {
                if (mWakeLock == null) {
                    PowerManager pm = (PowerManager) ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
                    mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "audio record lock");
                    mWakeLock.acquire();
                }
            } catch (Exception e) {
                FileLog.e("tsupport", e);
            }
            AndroidUtilities.lockOrientation(parentActivity);

            recordPanel.setVisibility(View.VISIBLE);
            recordTimeText.setText("00:00");
            lastTimeString = null;

            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) slideText.getLayoutParams();
            params.leftMargin = AndroidUtilities.dp(30);
            slideText.setLayoutParams(params);
            ViewProxy.setAlpha(slideText, 1);
            ViewProxy.setX(recordPanel, AndroidUtilities.displaySize.x);
            if (runningAnimationAudio != null) {
                runningAnimationAudio.cancel();
            }
            runningAnimationAudio = ObjectAnimatorProxy.ofFloatProxy(recordPanel, "translationX", 0).setDuration(300);
            runningAnimationAudio.addListener(new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationEnd(Object animator) {
                    if (runningAnimationAudio != null && runningAnimationAudio.equals(animator)) {
                        ViewProxy.setX(recordPanel, 0);
                    }
                }
            });
            runningAnimationAudio.setInterpolator(new AccelerateDecelerateInterpolator());
            runningAnimationAudio.start();
        } else {
            if (mWakeLock != null) {
                try {
                    mWakeLock.release();
                    mWakeLock = null;
                } catch (Exception e) {
                    FileLog.e("tsupport", e);
                }
            }
            AndroidUtilities.unlockOrientation(parentActivity);
            if (audioInterfaceState == 0) {
                return;
            }
            audioInterfaceState = 0;

            if (runningAnimationAudio != null) {
                runningAnimationAudio.cancel();
            }
            runningAnimationAudio = ObjectAnimatorProxy.ofFloatProxy(recordPanel, "translationX", AndroidUtilities.displaySize.x).setDuration(300);
            runningAnimationAudio.addListener(new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationEnd(Object animator) {
                    if (runningAnimationAudio != null && runningAnimationAudio.equals(animator)) {
                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) slideText.getLayoutParams();
                        params.leftMargin = AndroidUtilities.dp(30);
                        slideText.setLayoutParams(params);
                        ViewProxy.setAlpha(slideText, 1);
                        recordPanel.setVisibility(View.GONE);
                    }
                }
            });
            runningAnimationAudio.setInterpolator(new AccelerateDecelerateInterpolator());
            runningAnimationAudio.start();
        }
    }

    private void showEmojiPopup(boolean show) {
        if (show) {
            if (emojiPopup == null) {
                createEmojiPopup();
            }
            int currentHeight;
            WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
            int rotation = manager.getDefaultDisplay().getRotation();
            if (keyboardHeight <= 0) {
                keyboardHeight = ApplicationLoader.applicationContext.getSharedPreferences("emoji", 0).getInt("kbd_height", AndroidUtilities.dp(200));
            }
            if (keyboardHeightLand <= 0) {
                keyboardHeightLand = ApplicationLoader.applicationContext.getSharedPreferences("emoji", 0).getInt("kbd_height_land3", AndroidUtilities.dp(200));
            }
            if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                currentHeight = keyboardHeightLand;
            } else {
                currentHeight = keyboardHeight;
            }
            emojiPopup.setHeight(View.MeasureSpec.makeMeasureSpec(currentHeight, View.MeasureSpec.EXACTLY));
            if (sizeNotifierRelativeLayout != null) {
                emojiPopup.setWidth(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, View.MeasureSpec.EXACTLY));
            }

            emojiPopup.showAtLocation(parentActivity.getWindow().getDecorView(), Gravity.BOTTOM | Gravity.LEFT, 0, 0);
            if (!keyboardVisible) {
                if (sizeNotifierRelativeLayout != null) {
                    sizeNotifierRelativeLayout.setPadding(0, 0, 0, currentHeight);
                    emojiButton.setImageResource(R.drawable.ic_msg_panel_hide);
                }
                return;
            }
            emojiButton.setImageResource(R.drawable.ic_msg_panel_kb);
            return;
        }
        if (emojiButton != null) {
            emojiButton.setImageResource(R.drawable.ic_msg_panel_smiles);
        }
        if (emojiPopup != null) {
            emojiPopup.dismiss();
        }
        if (sizeNotifierRelativeLayout != null) {
            sizeNotifierRelativeLayout.post(new Runnable() {
                public void run() {
                    if (sizeNotifierRelativeLayout != null) {
                        sizeNotifierRelativeLayout.setPadding(0, 0, 0, 0);
                    }
                }
            });
        }
    }

    private void showTemplatePopup(boolean show) {
        if (show) {
            if (templatePopup == null) {
                createTemplatePopup();
            }
            int currentHeight;
            WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
            int rotation = manager.getDefaultDisplay().getRotation();
            if (keyboardHeight <= 0) {
                keyboardHeight = ApplicationLoader.applicationContext.getSharedPreferences("emoji", 0).getInt("kbd_height", AndroidUtilities.dp(200));
            }
            if (keyboardHeightLand <= 0) {
                keyboardHeightLand = ApplicationLoader.applicationContext.getSharedPreferences("emoji", 0).getInt("kbd_height_land3", AndroidUtilities.dp(200));
            }
            if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                currentHeight = keyboardHeightLand;
            } else {
                currentHeight = keyboardHeight;
            }
            templatePopup.setHeight(View.MeasureSpec.makeMeasureSpec(currentHeight, View.MeasureSpec.EXACTLY));
            templatePopup.setWidth(View.MeasureSpec.makeMeasureSpec(sizeNotifierRelativeLayout.getWidth(), View.MeasureSpec.EXACTLY));
            if (sizeNotifierRelativeLayout != null) {
                templatePopup.setWidth(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, View.MeasureSpec.EXACTLY));
            }

            templatePopup.showAtLocation(parentActivity.getWindow().getDecorView(), Gravity.BOTTOM | Gravity.LEFT, 0, 0);
            if (!keyboardVisible) {
                if (sizeNotifierRelativeLayout != null) {
                    sizeNotifierRelativeLayout.setPadding(0, 0, 0, currentHeight);
                    if (emojiButton != null) {
                        emojiButton.setImageResource(R.drawable.ic_msg_panel_hide);
                    }
                }
                return;
            }
        }
        if (emojiButton != null) {
            emojiButton.setImageResource(R.drawable.ic_msg_panel_smiles);
        }
        if (templatePopup != null) {
            templatePopup.dismiss();
        }
        if (sizeNotifierRelativeLayout != null) {
            sizeNotifierRelativeLayout.post(new Runnable() {
                public void run() {
                    if (sizeNotifierRelativeLayout != null) {
                        sizeNotifierRelativeLayout.setPadding(0, 0, 0, 0);
                    }
                }
            });
        }
    }

    public void hideEmojiPopup() {
        if (emojiPopup != null && emojiPopup.isShowing()) {
            showEmojiPopup(false);
        }
    }

    public void hideTemplatePopup() {
        if (templatePopup != null && templatePopup.isShowing()) {
            showTemplatePopup(false);
        }
    }

    private void createEmojiPopup() {
        if (parentActivity == null) {
            return;
        }
        emojiView = new EmojiView(parentActivity);
        emojiView.setListener(new EmojiView.Listener() {
            public void onBackspace() {
                messsageEditText.dispatchKeyEvent(new KeyEvent(0, 67));
            }

            public void onEmojiSelected(String symbol) {
                int i = messsageEditText.getSelectionEnd();
                if (i < 0) {
                    i = 0;
                }
                try {
                    CharSequence localCharSequence = Emoji.replaceEmoji(symbol, messsageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20));
                    messsageEditText.setText(messsageEditText.getText().insert(i, localCharSequence));
                    int j = i + localCharSequence.length();
                    messsageEditText.setSelection(j, j);
                } catch (Exception e) {
                    FileLog.e("tsupport", e);
                }
            }
        });
        emojiPopup = new PopupWindow(emojiView);
    }

    private void updateEditText(String value) {
        int i = messsageEditText.getSelectionEnd();
        FileLog.e("templateselected", value);
        messsageEditText.setText(messsageEditText.getText().insert(i,value));
        int j = i + value.length();
        messsageEditText.setSelection(j, j);
    }

    private void createTemplatePopup() {
        if (parentActivity == null) {
            return;
        }
        templateView = new TemplateView(parentActivity, templates);
        templateView.setListener(new TemplateView.Listener() {
            public void onBackspace() {
                messsageEditText.dispatchKeyEvent(new KeyEvent(0, 67));
            }

            public void onTemplateSelected(String paramAnonymousString) {
                int i = messsageEditText.getSelectionEnd();
                String value = templates.get(paramAnonymousString);
                CharSequence localCharSequence = value;
                messsageEditText.removeTextChangedListener(textWatcher);
                messsageEditText.setText(messsageEditText.getText().insert(i, localCharSequence));
                messsageEditText.addTextChangedListener(textWatcher);
                int j = i + localCharSequence.length();
                messsageEditText.setSelection(j, j);
            }
        });
        templatePopup = new PopupWindow(templateView);
    }

    public void setDelegate(ChatActivityEnterViewDelegate delegate) {
        this.delegate = delegate;
    }

    public void setDialogId(long id) {
        dialog_id = id;
    }

    public void setFieldText(String text) {
        if (messsageEditText == null) {
            return;
        }
        ignoreTextChange = true;
        messsageEditText.setText(text);
        messsageEditText.setSelection(messsageEditText.getText().length());
        ignoreTextChange = false;
    }

    public void setFieldFocused(boolean focus) {
        if (messsageEditText == null) {
            return;
        }
        if (focus) {
            if (!messsageEditText.isFocused()) {
                messsageEditText.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (messsageEditText != null) {
                            try {
                                messsageEditText.requestFocus();
                            } catch (Exception e) {
                                FileLog.e("tsupport", e);
                            }
                        }
                    }
                }, 600);
            }
        } else {
            if (messsageEditText.isFocused() && !keyboardVisible) {
                messsageEditText.clearFocus();
            }
        }
    }

    public boolean hasText() {
        return messsageEditText != null && messsageEditText.length() > 0;
    }

    public String getFieldText() {
        if (messsageEditText != null && messsageEditText.length() > 0) {
            return messsageEditText.getText().toString();
        }
        return null;
    }

    public boolean isEmojiPopupShowing() {
        return emojiPopup != null && emojiPopup.isShowing();
    }

    public void addToAttachLayout(View view) {
        if (attachButton == null) {
            return;
        }
        if (view.getParent() != null) {
            ViewGroup viewGroup = (ViewGroup) view.getParent();
            viewGroup.removeView(view);
        }
        attachButton.addView(view);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();
        layoutParams.gravity = Gravity.CENTER;
        layoutParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        view.setLayoutParams(layoutParams);
    }


    public boolean isTemplatePopupShowing() {
        return templatePopup != null && templatePopup.isShowing();
    }

    @Override
    public void onSizeChanged(int height) {
        Rect localRect = new Rect();
        parentActivity.getWindow().getDecorView().getWindowVisibleDisplayFrame(localRect);

        WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
        if (manager == null || manager.getDefaultDisplay() == null) {
            return;
        }
        int rotation = manager.getDefaultDisplay().getRotation();

        if (height > AndroidUtilities.dp(50) && keyboardVisible) {
            if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                keyboardHeightLand = height;
                ApplicationLoader.applicationContext.getSharedPreferences("emoji", 0).edit().putInt("kbd_height_land3", keyboardHeightLand).commit();
            } else {
                keyboardHeight = height;
                ApplicationLoader.applicationContext.getSharedPreferences("emoji", 0).edit().putInt("kbd_height", keyboardHeight).commit();
            }
        }

        if (emojiPopup != null && emojiPopup.isShowing()) {
            int newHeight = 0;
            if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                newHeight = keyboardHeightLand;
            } else {
                newHeight = keyboardHeight;
            }
            final WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) emojiPopup.getContentView().getLayoutParams();
            if (layoutParams.width != AndroidUtilities.displaySize.x || layoutParams.height != newHeight) {
                WindowManager wm = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
                layoutParams.width = AndroidUtilities.displaySize.x;
                layoutParams.height = newHeight;
                wm.updateViewLayout(emojiPopup.getContentView(), layoutParams);
                if (!keyboardVisible) {
                    sizeNotifierRelativeLayout.post(new Runnable() {
                        @Override
                        public void run() {
                            if (sizeNotifierRelativeLayout != null) {
                                sizeNotifierRelativeLayout.setPadding(0, 0, 0, layoutParams.height);
                                sizeNotifierRelativeLayout.requestLayout();
                            }
                        }
                    });
                }
            }
        }
        if (templatePopup != null && templatePopup.isShowing()) {
            int newHeight = 0;
            if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                newHeight = keyboardHeightLand;
            } else {
                newHeight = keyboardHeight;
            }
            final WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams)templatePopup.getContentView().getLayoutParams();
            if (layoutParams.width != AndroidUtilities.displaySize.x || layoutParams.height != newHeight) {
                WindowManager wm = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
                layoutParams.width = AndroidUtilities.displaySize.x;
                layoutParams.height = newHeight;
                wm.updateViewLayout(templatePopup.getContentView(), layoutParams);
                if (!keyboardVisible) {
                    sizeNotifierRelativeLayout.post(new Runnable() {
                        @Override
                        public void run() {
                            if (sizeNotifierRelativeLayout != null) {
                                sizeNotifierRelativeLayout.setPadding(0, 0, 0, layoutParams.height);
                                sizeNotifierRelativeLayout.requestLayout();
                            }
                        }
                    });
                }
            }
        }

        boolean oldValue = keyboardVisible;
        keyboardVisible = height > 0;
        if (keyboardVisible && sizeNotifierRelativeLayout.getPaddingBottom() > 0) {
            showEmojiPopup(false);
            showTemplatePopup(false);
        } else if (!keyboardVisible && keyboardVisible != oldValue && emojiPopup != null && emojiPopup.isShowing()) {
            showEmojiPopup(false);
        } else if (!keyboardVisible && keyboardVisible != oldValue && templatePopup != null && templatePopup.isShowing()) {
            showEmojiPopup(false);
        }
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.emojiDidLoaded) {
            if (emojiView != null) {
                emojiView.invalidateViews();
            }
        } else if (id == NotificationCenter.recordProgressChanged) {
            Long time = (Long) args[0] / 1000;
            String str = String.format("%02d:%02d", time / 60, time % 60);
            if (lastTimeString == null || !lastTimeString.equals(str)) {
                if (recordTimeText != null) {
                    recordTimeText.setText(str);
                }
            }
        } else if (id == NotificationCenter.closeChats) {
            if (messsageEditText != null && messsageEditText.isFocused()) {
                AndroidUtilities.hideKeyboard(messsageEditText);
            }
        } else if (id == NotificationCenter.recordStartError || id == NotificationCenter.recordStopped) {
            if (recordingAudio) {
                recordingAudio = false;
                updateAudioRecordIntefrace();
            }
        } else if (id == NotificationCenter.recordStarted) {
            if (!recordingAudio) {
                recordingAudio = true;
                updateAudioRecordIntefrace();
            }
        } else if (id == NotificationCenter.audioDidSent) {
            if (delegate != null) {
                delegate.onMessageSend();
            }
        } else if (id == NotificationCenter.updateTemplatesNotification) {
            templates.putAll(TemplateSupport.getInstance().getAll());
            if (templateView != null) {
                templateView.setTemplates(templates);
            }
        } else if (id == NotificationCenter.hideEmojiKeyboard) {
            hideEmojiPopup();
        } else if (id == NotificationCenter.hideTemplatesKeyboard) {
            hideTemplatePopup();
        }
    }
}
