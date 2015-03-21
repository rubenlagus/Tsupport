/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Components;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.PowerManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.Emoji;
import org.telegram.android.LocaleController;
import org.telegram.android.MediaController;
import org.telegram.android.MessageObject;
import org.telegram.android.MessagesController;
import org.telegram.android.SendMessagesHelper;
import org.telegram.android.TemplateSupport;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.AnimationCompat.AnimatorListenerAdapterProxy;
import org.telegram.ui.AnimationCompat.AnimatorSetProxy;
import org.telegram.ui.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.ui.AnimationCompat.ViewProxy;
import org.telegram.messenger.ApplicationLoader;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatActivityEnterView extends FrameLayoutFixed implements NotificationCenter.NotificationCenterDelegate, SizeNotifierRelativeLayout.SizeNotifierRelativeLayoutDelegate {

    public interface ChatActivityEnterViewDelegate {
        void onMessageSend();
        void needSendTyping();
        void onTextChanged(CharSequence text);
        void onAttachButtonHidden();
        void onAttachButtonShow();
        void onWindowSizeChanged(int size);
    }

    private AutoCompleteTextView messsageEditText = null;
    private ImageView sendButton;
    private PopupWindow emojiPopup;
    private PopupWindow templatePopup;
    private ImageView emojiButton;
    private EmojiView emojiView;
    private TemplateView templateView;
    private SizeNotifierRelativeLayout sizeNotifierRelativeLayout;
    private FrameLayout attachButton;
    private static final Pattern pattern = Pattern.compile("((?:[^\\s(]+\\()|(?:\\.{2}[^\\s\\.]+\\.{2}))");
    private static final Pattern patternContact = Pattern.compile("^contact:(\\+[0-9]+)\\s*(\\S+)\\s*([^\\n]+)(\\n|$)");
    private static final Pattern patternIssue = Pattern.compile("^#issue:([\\w]+)$");
    private static final Pattern patternIssueSolved = Pattern.compile("^#solved:([\\w]+)$");
    private LinearLayout textFieldContainer;
    private View topView;

    private PowerManager.WakeLock mWakeLock;
    private AnimatorSetProxy runningAnimation;
    private AnimatorSetProxy runningAnimation2;
    private ObjectAnimatorProxy runningAnimationAudio;
    private int runningAnimationType;
    private int audioInterfaceState;

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
    private boolean forceShowSendButton;

    private Activity parentActivity;
    private BaseFragment parentFragment;
    private long dialog_id;
    private boolean ignoreTextChange;
    private MessageObject replyingMessageObject;
    private ChatActivityEnterViewDelegate delegate;
    private TextWatcher textWatcher = null;
    private TreeMap<String, String> templates = new TreeMap<String, String>();

    private float topViewAnimation;
    private boolean needShowTopView;
    private boolean allowShowTopView;

    public ChatActivityEnterView(Activity context, SizeNotifierRelativeLayout parent, BaseFragment fragment, boolean isChat) {
        super(context);
        setBackgroundResource(R.drawable.compose_panel);
        setFocusable(true);
        setFocusableInTouchMode(true);

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.recordStarted);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.recordStartError);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.recordStopped);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.recordProgressChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioDidSent);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.hideEmojiKeyboard);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.hideTemplatesKeyboard);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioRouteChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateTemplatesNotification);
        parentActivity = context;
        parentFragment = fragment;
        sizeNotifierRelativeLayout = parent;
        sizeNotifierRelativeLayout.setDelegate(this);
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        sendByEnter = preferences.getBoolean("send_by_enter", false);
        templates = TemplateSupport.getInstance().getAll();

        textFieldContainer = new LinearLayout(context);
        textFieldContainer.setBackgroundColor(0xffffffff);
        textFieldContainer.setOrientation(LinearLayout.HORIZONTAL);
        addView(textFieldContainer);
        LayoutParams layoutParams2 = (LayoutParams) textFieldContainer.getLayoutParams();
        layoutParams2.gravity = Gravity.LEFT | Gravity.TOP;
        layoutParams2.width = LayoutParams.MATCH_PARENT;
        layoutParams2.height = LayoutParams.WRAP_CONTENT;
        layoutParams2.topMargin = AndroidUtilities.dp(2);
        textFieldContainer.setLayoutParams(layoutParams2);

        FrameLayoutFixed frameLayout = new FrameLayoutFixed(context);
        textFieldContainer.addView(frameLayout);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) frameLayout.getLayoutParams();
        layoutParams.width = 0;
        layoutParams.height = LayoutParams.WRAP_CONTENT;
        layoutParams.weight = 1;
        frameLayout.setLayoutParams(layoutParams);

        emojiButton = new ImageView(context);
        emojiButton.setImageResource(R.drawable.ic_msg_panel_smiles);
        emojiButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        emojiButton.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(1), 0, 0);
        frameLayout.addView(emojiButton);
        FrameLayout.LayoutParams layoutParams1 = (FrameLayout.LayoutParams) emojiButton.getLayoutParams();
        layoutParams1.width = AndroidUtilities.dp(48);
        layoutParams1.height = AndroidUtilities.dp(48);
        layoutParams1.gravity = Gravity.BOTTOM;
        emojiButton.setLayoutParams(layoutParams1);
        emojiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (templatePopup == null || !templatePopup.isShowing()) {
                    if (emojiPopup == null) {
                        showEmojiPopup(true);
                    } else {
                        showEmojiPopup(!emojiPopup.isShowing());
                    }
                } else {
                    showTemplatePopup(!templatePopup.isShowing());
                }
            }
        });

        emojiButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (templatePopup == null) {
                    showTemplatePopup(true);
                    return true;
                } else {
                    showTemplatePopup(!templatePopup.isShowing());
                    return true;
                }
            }
        });

        messsageEditText = new AutoCompleteTextView(context);
		messsageEditText.setThreshold(1);
        messsageEditText.setHint(LocaleController.getString("TypeMessage", R.string.TypeMessage));
        messsageEditText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        messsageEditText.setInputType(messsageEditText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
        messsageEditText.setSingleLine(false);
        messsageEditText.setMaxLines(4);
        messsageEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        messsageEditText.setGravity(Gravity.BOTTOM);
        messsageEditText.setPadding(0, AndroidUtilities.dp(11), 0, AndroidUtilities.dp(12));
        messsageEditText.setBackgroundDrawable(null);
        AndroidUtilities.clearCursorDrawable(messsageEditText);
        messsageEditText.setTextColor(0xff000000);
        messsageEditText.setHintTextColor(0xffb2b2b2);
		ArrayList<String> keys = new ArrayList<String>();
        keys.addAll(templates.keySet());
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, R.layout.autocompletetemplaterow,keys);
        messsageEditText.setAdapter(adapter);
        messsageEditText.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String key = (String)parent.getItemAtPosition(position);
                messsageEditText.setText(templates.get(key));
                messsageEditText.setSelection(messsageEditText.getText().length());
            }
        });
        frameLayout.addView(messsageEditText);
        layoutParams1 = (FrameLayout.LayoutParams) messsageEditText.getLayoutParams();
        layoutParams1.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams1.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams1.gravity = Gravity.BOTTOM;
        layoutParams1.leftMargin = AndroidUtilities.dp(52);
        layoutParams1.rightMargin = AndroidUtilities.dp(isChat ? 50 : 2);
        messsageEditText.setLayoutParams(layoutParams1);
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
                } else if (i == KeyEvent.KEYCODE_ENTER && sendByEnter && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                    sendMessage();
                    return true;
                }
                return false;
            }
        });
        messsageEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (messsageEditText.getText().length() < 10) {
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
        messsageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                String message = getTrimmedString(charSequence.toString());

                if (delegate != null && (message.startsWith(":") || message.startsWith("@"))) {
                    delegate.onTextChanged(charSequence);
                } else {
                    if (searchForTemplate && message.length() > 0 && message.length() < 100) {
                        Matcher matcher = pattern.matcher(message);
                        String newMessage = message;
                        boolean found = false;
                        while (matcher.find()) {
                            String template = TemplateSupport.getInstance().getTemplate(matcher.group(1));
                            if (template != null && template.compareToIgnoreCase("") != 0) {
                                newMessage = newMessage.replace(matcher.group(1), template);
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
                }
                if (message.length() != 0 && lastTypingTimeSend < System.currentTimeMillis() - 5000 && !ignoreTextChange) {
                    int currentTime = ConnectionsManager.getInstance().getCurrentTime();
                    TLRPC.User currentUser = null;
                    if ((int) dialog_id > 0) {
                        currentUser = MessagesController.getInstance().getUser((int) dialog_id);
                    }
                    if (currentUser != null && (currentUser.id == UserConfig.getClientUserId() || currentUser.status != null && currentUser.status.expires < currentTime)) {
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
        });

        if (isChat) {
            attachButton = new FrameLayout(context);
            attachButton.setEnabled(false);
            ViewProxy.setPivotX(attachButton, AndroidUtilities.dp(48));
            frameLayout.addView(attachButton);
            layoutParams1 = (FrameLayout.LayoutParams) attachButton.getLayoutParams();
            layoutParams1.width = AndroidUtilities.dp(48);
            layoutParams1.height = AndroidUtilities.dp(48);
            layoutParams1.gravity = Gravity.BOTTOM | Gravity.RIGHT;
            attachButton.setLayoutParams(layoutParams1);
        }



        FrameLayout frameLayout1 = new FrameLayout(context);
        textFieldContainer.addView(frameLayout1);
        layoutParams = (LinearLayout.LayoutParams) frameLayout1.getLayoutParams();
        layoutParams.width = AndroidUtilities.dp(48);
        layoutParams.height = AndroidUtilities.dp(48);
        layoutParams.gravity = Gravity.BOTTOM;
        frameLayout1.setLayoutParams(layoutParams);

        sendButton = new ImageView(context);
        sendButton.setVisibility(View.VISIBLE);
        sendButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        sendButton.setImageResource(R.drawable.ic_send);
        //ViewProxy.setScaleX(sendButton, 0.1f);
        //ViewProxy.setScaleY(sendButton, 0.1f);
        //ViewProxy.setAlpha(sendButton, 0.0f);
        //sendButton.clearAnimation();
        frameLayout1.addView(sendButton);
        layoutParams1 = (FrameLayout.LayoutParams) sendButton.getLayoutParams();
        layoutParams1.width = AndroidUtilities.dp(48);
        layoutParams1.height = AndroidUtilities.dp(48);
        sendButton.setLayoutParams(layoutParams1);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage();
            }
        });
    }

    public void addTopView(View view, int height) {
        if (view == null) {
            return;
        }
        addView(view, 0);
        topView = view;
        topView.setVisibility(GONE);
        needShowTopView = false;
        LayoutParams layoutParams = (LayoutParams) topView.getLayoutParams();
        layoutParams.width = RelativeLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = height;
        layoutParams.topMargin = AndroidUtilities.dp(2);
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        topView.setLayoutParams(layoutParams);
    }

    public void setTopViewAnimation(float progress) {
        LayoutParams layoutParams2 = (LayoutParams) textFieldContainer.getLayoutParams();
        layoutParams2.topMargin = AndroidUtilities.dp(2) + (int) (topView.getLayoutParams().height * progress);
        textFieldContainer.setLayoutParams(layoutParams2);
    }

    public float getTopViewAnimation() {
        return topViewAnimation;
    }

    public void setForceShowSendButton(boolean value, boolean animated) {
        forceShowSendButton = value;
    }

    public void showTopView(boolean animated) {
        if (topView == null) {
            return;
        }
        needShowTopView = true;
        if (allowShowTopView) {
            topView.setVisibility(VISIBLE);
            if (animated) {
                AnimatorSetProxy animatorSetProxy = new AnimatorSetProxy();
                animatorSetProxy.playTogether(
                        ObjectAnimatorProxy.ofFloat(ChatActivityEnterView.this, "topViewAnimation", 0.0f, 1.0f)
                );
                animatorSetProxy.addListener(new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Object animation) {
                        LayoutParams layoutParams2 = (LayoutParams) textFieldContainer.getLayoutParams();
                        layoutParams2.topMargin = AndroidUtilities.dp(2) + topView.getLayoutParams().height;
                        textFieldContainer.setLayoutParams(layoutParams2);
                        if (!forceShowSendButton) {
                            openKeyboard();
                        }
                    }
                });
                animatorSetProxy.setDuration(200);
                animatorSetProxy.start();
            } else {
                LayoutParams layoutParams2 = (LayoutParams) textFieldContainer.getLayoutParams();
                layoutParams2.topMargin = AndroidUtilities.dp(2) + topView.getLayoutParams().height;
                textFieldContainer.setLayoutParams(layoutParams2);
            }
        }
    }

    public void hideTopView(boolean animated) {
        if (topView == null) {
            return;
        }

        needShowTopView = false;
        if (allowShowTopView) {
            if (animated) {
                AnimatorSetProxy animatorSetProxy = new AnimatorSetProxy();
                animatorSetProxy.playTogether(
                        ObjectAnimatorProxy.ofFloat(ChatActivityEnterView.this, "topViewAnimation", 1.0f, 0.0f)
                );
                animatorSetProxy.addListener(new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Object animation) {
                        topView.setVisibility(GONE);
                        LayoutParams layoutParams2 = (LayoutParams) textFieldContainer.getLayoutParams();
                        layoutParams2.topMargin = AndroidUtilities.dp(2);
                        textFieldContainer.setLayoutParams(layoutParams2);
                    }
                });
                animatorSetProxy.setDuration(200);
                animatorSetProxy.start();
            } else {
                topView.setVisibility(GONE);
                LayoutParams layoutParams2 = (LayoutParams) textFieldContainer.getLayoutParams();
                layoutParams2.topMargin = AndroidUtilities.dp(2);
                textFieldContainer.setLayoutParams(layoutParams2);
            }
        }
    }

    public boolean isTopViewVisible() {
        return topView != null && topView.getVisibility() == VISIBLE;
    }

    private void onWindowSizeChanged(int size) {
        if (delegate != null) {
            delegate.onWindowSizeChanged(size);
        }
        if (topView != null) {
            if (size < AndroidUtilities.dp(72) + AndroidUtilities.getCurrentActionBarHeight()) {
                if (allowShowTopView) {
                    allowShowTopView = false;
                    if (needShowTopView) {
                        topView.setVisibility(View.GONE);
                        LayoutParams layoutParams2 = (LayoutParams) textFieldContainer.getLayoutParams();
                        layoutParams2.topMargin = AndroidUtilities.dp(2);
                        textFieldContainer.setLayoutParams(layoutParams2);
                    }
                }
            } else {
                if (!allowShowTopView) {
                    allowShowTopView = true;
                    if (needShowTopView) {
                        topView.setVisibility(View.VISIBLE);
                        LayoutParams layoutParams2 = (LayoutParams) textFieldContainer.getLayoutParams();
                        layoutParams2.topMargin = AndroidUtilities.dp(2) + topView.getLayoutParams().height;
                        textFieldContainer.setLayoutParams(layoutParams2);
                    }
                }
            }
        }
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
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.audioRouteChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateTemplatesNotification);
        if (mWakeLock != null) {
            try {
                mWakeLock.release();
                mWakeLock = null;
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
        if (sizeNotifierRelativeLayout != null) {
            sizeNotifierRelativeLayout.setDelegate(null);
        }
    }

    public void setDialogId(long id) {
        dialog_id = id;
    }

    public void setReplyingMessageObject(MessageObject messageObject) {
        replyingMessageObject = messageObject;
    }

    private void sendMessage() {
        if (parentFragment != null) {
            String action = null;
            TLRPC.Chat currentChat = null;
            if ((int) dialog_id < 0) {
                currentChat = MessagesController.getInstance().getChat(-(int) dialog_id);
                if (currentChat != null && currentChat.participants_count > MessagesController.getInstance().groupBigSize) {
                    action = "bigchat_message";
                } else {
                    action = "chat_message";
                }
            } else {
                action = "pm_message";
            }
            if (!MessagesController.isFeatureEnabled(action, parentFragment)) {
                return;
            }
        }
        if (processSendingText(messsageEditText.getText().toString())) {
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.readChatNotification, dialog_id);
            messsageEditText.setText("");
            messsageEditText.setSelection(messsageEditText.length());
            lastTypingTimeSend = 0;
            if (delegate != null) {
                delegate.onMessageSend();
            }
        }
    }

    public void sendMessage(String text) {
        if (parentFragment != null) {
            String action = null;
            TLRPC.Chat currentChat = null;
            if ((int) dialog_id < 0) {
                currentChat = MessagesController.getInstance().getChat(-(int) dialog_id);
                if (currentChat != null && currentChat.participants_count > MessagesController.getInstance().groupBigSize) {
                    action = "bigchat_message";
                } else {
                    action = "chat_message";
                }
            } else {
                action = "pm_message";
            }
            if (!MessagesController.isFeatureEnabled(action, parentFragment)) {
                return;
            }
        }
        if (processSendingText(text)) {
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.readChatNotification, dialog_id);
            messsageEditText.setText("");
            messsageEditText.setSelection(messsageEditText.length());
            lastTypingTimeSend = 0;
            if (delegate != null) {
                delegate.onMessageSend();
            }
        } else if (forceShowSendButton) {
            if (delegate != null) {
                delegate.onMessageSend();
            }
        }
    }

    public boolean processSendingText(String text) {
        if (text.length() != 0) {
            Matcher matcher = patternIssue.matcher(text);
            if (matcher.find()) {
                String issueId = matcher.group(1);
                String message = LocaleController.getString("IssueNoted", R.string.IssueNoted).replace("@id",issueId);
                SendMessagesHelper.getInstance().sendMessage(message, dialog_id, replyingMessageObject);
            } else {
                matcher = patternIssueSolved.matcher(text);
                if (matcher.find()) {
                    String issueId = matcher.group(1);
                    String message = LocaleController.getString("IssueSolved", R.string.IssueSolved).replace("@id", issueId);
                    SendMessagesHelper.getInstance().sendMessage(message, dialog_id, replyingMessageObject);
                } else {
                    int count = (int) Math.ceil(text.length() / 2048.0f);
                    for (int a = 0; a < count; a++) {
                        String mess = text.substring(a * 2048, Math.min((a + 1) * 2048, text.length()));
                        matcher = patternContact.matcher(mess);
                        if (matcher.find()) {
                            String number = matcher.group(1);
                            String name = matcher.group(2);
                            String surname = matcher.group(3);
                            mess = mess.replace(matcher.group(), "");
                            SendMessagesHelper.getInstance().sendMessage(mess, dialog_id, replyingMessageObject);
                            SendMessagesHelper.getInstance().sendMessageSupport(number, name, surname, dialog_id, "");
                        } else {
                            SendMessagesHelper.getInstance().sendMessage(mess, dialog_id, replyingMessageObject);
                        }
                    }
                }
            }
            return true;
        } else { // Mark as read but send nothing
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

    private void showEmojiPopup(boolean show) {
        if (show) {
            if (emojiPopup == null) {
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
                            FileLog.e("tmessages", e);
                        }
                    }
                });
                emojiPopup = new PopupWindow(emojiView);

                if (Build.VERSION.SDK_INT >= 21) {
                    try {
                        Field field = PopupWindow.class.getDeclaredField("mWindowLayoutType");
                        field.setAccessible(true);
                        field.set(emojiPopup, WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
                    } catch (Exception e) {
                        /* ignored */
                    }
                }
            }
            int currentHeight;
            WindowManager wm = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
            int rotation = wm.getDefaultDisplay().getRotation();
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
                    onWindowSizeChanged(sizeNotifierRelativeLayout.getHeight() - sizeNotifierRelativeLayout.getPaddingBottom());
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
            try {
                emojiPopup.dismiss();
            } catch (Exception e) {
                //don't promt
            }
        }
        if (sizeNotifierRelativeLayout != null) {
            sizeNotifierRelativeLayout.post(new Runnable() {
                public void run() {
                    if (sizeNotifierRelativeLayout != null) {
                        sizeNotifierRelativeLayout.setPadding(0, 0, 0, 0);
                        onWindowSizeChanged(sizeNotifierRelativeLayout.getHeight() - sizeNotifierRelativeLayout.getPaddingBottom());
                    }
                }
            });
        }
    }

    private void showTemplatePopup(boolean show) {
        if (show) {
            if (templatePopup == null) {
                if (parentActivity == null) {
                    return;
                }
                templateView = new TemplateView(parentActivity, templates);
                templateView.setListener(new TemplateView.Listener() {
                    public void onBackspace() {
                        messsageEditText.dispatchKeyEvent(new KeyEvent(0, 67));
                    }

                    public void onTemplateSelected(String template) {
                        try {
                            int i = messsageEditText.getSelectionEnd();
                            if (i < 0) {
                                i = 0;
                            }
                            CharSequence localCharSequence = template;
                            messsageEditText.setText(messsageEditText.getText().insert(i, localCharSequence));
                            int j = i + localCharSequence.length();
                            messsageEditText.setSelection(j, j);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                });
                templatePopup = new PopupWindow(templateView);

                if (Build.VERSION.SDK_INT >= 21) {
                    try {
                        Field field = PopupWindow.class.getDeclaredField("mWindowLayoutType");
                        field.setAccessible(true);
                        field.set(emojiPopup, WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
                    } catch (Exception e) {
                        /* ignored */
                    }
                }
            }
            int currentHeight;
            WindowManager wm = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
            int rotation = wm.getDefaultDisplay().getRotation();
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
            if (sizeNotifierRelativeLayout != null) {
                templatePopup.setWidth(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, View.MeasureSpec.EXACTLY));
            }

            templatePopup.showAtLocation(parentActivity.getWindow().getDecorView(), Gravity.BOTTOM | Gravity.LEFT, 0, 0);
            if (!keyboardVisible) {
                if (sizeNotifierRelativeLayout != null) {
                    sizeNotifierRelativeLayout.setPadding(0, 0, 0, currentHeight);
                    emojiButton.setImageResource(R.drawable.ic_msg_panel_hide);
                    onWindowSizeChanged(sizeNotifierRelativeLayout.getHeight() - sizeNotifierRelativeLayout.getPaddingBottom());
                }
                return;
            }
            emojiButton.setImageResource(R.drawable.ic_msg_panel_kb);
            return;
        }
        if (emojiButton != null) {
            emojiButton.setImageResource(R.drawable.ic_msg_panel_smiles);
        }
        if (templatePopup != null) {
            try {
                templatePopup.dismiss();
            } catch (Exception e) {
                // don't prompt
            }
        }
        if (sizeNotifierRelativeLayout != null) {
            sizeNotifierRelativeLayout.post(new Runnable() {
                public void run() {
                    if (sizeNotifierRelativeLayout != null) {
                        sizeNotifierRelativeLayout.setPadding(0, 0, 0, 0);
                        onWindowSizeChanged(sizeNotifierRelativeLayout.getHeight() - sizeNotifierRelativeLayout.getPaddingBottom());
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

    public void openKeyboard() {
        AndroidUtilities.showKeyboard(messsageEditText);
    }

    public void setDelegate(ChatActivityEnterViewDelegate delegate) {
        this.delegate = delegate;
    }

    public void setFieldText(String text) {
        if (messsageEditText == null) {
            return;
        }
        ignoreTextChange = true;
        messsageEditText.setText(text);
        messsageEditText.setSelection(messsageEditText.getText().length());
        ignoreTextChange = false;
        if (delegate != null) {
            delegate.onTextChanged(messsageEditText.getText());
        }
    }

    public int getCursorPosition() {
        if (messsageEditText == null) {
            return 0;
        }
        return messsageEditText.getSelectionStart();
    }

    public void replaceWithText(int start, int len, String text) {
        try {
            StringBuilder builder = new StringBuilder(messsageEditText.getText());
            builder.replace(start, start + len, text);
            messsageEditText.setText(builder);
            messsageEditText.setSelection(messsageEditText.length());
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
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
                                FileLog.e("tmessages", e);
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

    public boolean isTemplatePopupShowing() {
        return templatePopup != null && templatePopup.isShowing();
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
        layoutParams.width = AndroidUtilities.dp(48);
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        view.setLayoutParams(layoutParams);
    }

    @Override
    public void onSizeChanged(int height) {
        WindowManager wm = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
        if (wm == null || wm.getDefaultDisplay() == null) {
            return;
        }
        int rotation = wm.getDefaultDisplay().getRotation();

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
                                onWindowSizeChanged(sizeNotifierRelativeLayout.getHeight() - sizeNotifierRelativeLayout.getPaddingBottom());
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
            final WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) templatePopup.getContentView().getLayoutParams();
            if (layoutParams.width != AndroidUtilities.displaySize.x || layoutParams.height != newHeight) {
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
                                onWindowSizeChanged(sizeNotifierRelativeLayout.getHeight() - sizeNotifierRelativeLayout.getPaddingBottom());
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
            showTemplatePopup(false);
        } else if (!keyboardVisible && keyboardVisible != oldValue && templatePopup != null && templatePopup.isShowing()) {
            showTemplatePopup(false);
            showEmojiPopup(false);
        }
        onWindowSizeChanged(sizeNotifierRelativeLayout.getHeight() - sizeNotifierRelativeLayout.getPaddingBottom());
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
        } else if (id == NotificationCenter.closeChats) {
            if (messsageEditText != null && messsageEditText.isFocused()) {
                AndroidUtilities.hideKeyboard(messsageEditText);
            }
        } else if (id == NotificationCenter.recordStartError || id == NotificationCenter.recordStopped) {
            if (recordingAudio) {
                recordingAudio = false;
            }
        } else if (id == NotificationCenter.recordStarted) {
            if (!recordingAudio) {
                recordingAudio = true;
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
        } else if (id == NotificationCenter.audioRouteChanged) {
            if (parentActivity != null) {
                boolean frontSpeaker = (Boolean) args[0];
                parentActivity.setVolumeControlStream(frontSpeaker ? AudioManager.STREAM_VOICE_CALL : AudioManager.USE_DEFAULT_STREAM_TYPE);
            }
        }
    }
}
