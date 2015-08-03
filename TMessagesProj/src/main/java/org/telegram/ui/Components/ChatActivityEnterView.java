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
import android.os.PowerManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.AnimationCompat.AnimatorListenerAdapterProxy;
import org.telegram.android.AnimationCompat.AnimatorSetProxy;
import org.telegram.android.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.android.AnimationCompat.ViewProxy;
import org.telegram.android.Emoji;
import org.telegram.android.LocaleController;
import org.telegram.android.MessageObject;
import org.telegram.android.MessagesController;
import org.telegram.android.NotificationCenter;
import org.telegram.android.SendMessagesHelper;
import org.telegram.android.TemplateSupport;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.Locale;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatActivityEnterView extends FrameLayoutFixed implements NotificationCenter.NotificationCenterDelegate, SizeNotifierFrameLayout.SizeNotifierFrameLayoutDelegate {

    public interface ChatActivityEnterViewDelegate {
        void onMessageSend(String message);
        void needSendTyping();
        void onTextChanged(CharSequence text, boolean bigChange);
        void onAttachButtonHidden();
        void onAttachButtonShow();
        void onWindowSizeChanged(int size);
    }

    private AutoCompleteTextView messageEditText = null;
    private ImageView sendButton;
    private ImageView emojiButton;
    private EmojiView emojiView;
    private TemplateView templateView;
    private SizeNotifierFrameLayout sizeNotifierLayout;
    private LinearLayout attachButton;
    private static final Pattern pattern = Pattern.compile("((?:[^\\s(]+\\()|(?:\\.{2}[^\\s\\.]+\\.{2}))", Pattern.CASE_INSENSITIVE);
    private static final Pattern patternContact = Pattern.compile("^contact:(\\+[0-9]+)\\s*(\\S+)\\s*([^\\n]+)(\\n|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern patternIssue = Pattern.compile("^#issue:([\\w]+)$");
    private static final Pattern patternIssueSolved = Pattern.compile("^#solved:([\\w]+)$");
    private LinearLayout textFieldContainer;
    private View topView;

    private int currentPopupContentType = -1;

    private boolean isPaused;
    private boolean showKeyboardOnResume;

    private MessageObject botButtonsMessageObject;
    private TLRPC.TL_replyKeyboardMarkup botReplyMarkup;
    private int botCount;
    private boolean hasBotCommands;

    private PowerManager.WakeLock mWakeLock;

    private int keyboardHeight;
    private int keyboardHeightLand;
    private boolean keyboardVisible;
    private int emojiPadding;
    private boolean sendByEnter;
    private long lastTypingTimeSend;
    private boolean searchForTemplate = true;
    private boolean forceShowSendButton;
    private boolean allowStickers;

    private int lastSizeChangeValue1;
    private boolean lastSizeChangeValue2;

    private Activity parentActivity;
    private BaseFragment parentFragment;
    private long dialog_id;
    private boolean ignoreTextChange;
    private int innerTextChange;
    private MessageObject replyingMessageObject;
    private MessageObject botMessageObject;
    private TLRPC.WebPage messageWebPage;
    private boolean messageWebPageSearch = true;
    private ChatActivityEnterViewDelegate delegate;
    private TreeMap<String, String> templates = new TreeMap<>();

    private float topViewAnimation;
    private boolean topViewShowed;
    private boolean needShowTopView;
    private boolean allowShowTopView;
    private AnimatorSetProxy currentTopViewAnimation;

    private boolean waitingForKeyboardOpen;
    private Runnable openKeyboardRunnable = new Runnable() {
        @Override
        public void run() {
            if (messageEditText != null && waitingForKeyboardOpen && !keyboardVisible && !AndroidUtilities.usingHardwareInput) {
                messageEditText.requestFocus();
                AndroidUtilities.showKeyboard(messageEditText);
                AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
                AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100);
            }
        }
    };

    public ChatActivityEnterView(Activity context, SizeNotifierFrameLayout parent, BaseFragment fragment, boolean isChat) {
        super(context);
        setBackgroundResource(R.drawable.compose_panel);
        setFocusable(true);
        setFocusableInTouchMode(true);

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateTemplatesNotification);
        parentActivity = context;
        parentFragment = fragment;
        sizeNotifierLayout = parent;
        sizeNotifierLayout.setDelegate(this);
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        sendByEnter = preferences.getBoolean("send_by_enter", false);
        templates = TemplateSupport.getAll();

        textFieldContainer = new LinearLayout(context);
        textFieldContainer.setBackgroundColor(0xffffffff);
        textFieldContainer.setOrientation(LinearLayout.HORIZONTAL);
        addView(textFieldContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 2, 0, 0));

        FrameLayoutFixed frameLayout = new FrameLayoutFixed(context);
        textFieldContainer.addView(frameLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        emojiButton = new ImageView(context);
        emojiButton.setImageResource(R.drawable.ic_msg_panel_smiles);
        emojiButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        emojiButton.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(1), 0, 0);
        frameLayout.addView(emojiButton, LayoutHelper.createFrame(48, 48, Gravity.BOTTOM));
        emojiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isPopupShowing() || (currentPopupContentType != 0 && currentPopupContentType != 3)) {
                    showPopup(1, 0);
                } else {
                    openKeyboardInternal();
                }
            }
        });

        emojiButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (!isPopupShowing() || currentPopupContentType != 0 && currentPopupContentType != 1) {
                    showPopup(1, 3);
                    return true;
                } else {
                    openKeyboardInternal();
                }
                return true;
            }
        });

        messageEditText = new AutoCompleteTextView(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (isPopupShowing() && event.getAction() == MotionEvent.ACTION_DOWN) {
                    showPopup(AndroidUtilities.usingHardwareInput ? 0 : 2, 0);
                    openKeyboardInternal();
                }
                return super.onTouchEvent(event);
            }
        };
        messageEditText.setThreshold(1);
        messageEditText.setHint(LocaleController.getString("TypeMessage", R.string.TypeMessage));
        messageEditText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        messageEditText.setInputType(messageEditText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
        messageEditText.setSingleLine(false);
        messageEditText.setMaxLines(4);
        messageEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        messageEditText.setGravity(Gravity.BOTTOM);
        messageEditText.setPadding(0, AndroidUtilities.dp(11), 0, AndroidUtilities.dp(12));
        messageEditText.setBackgroundDrawable(null);
        AndroidUtilities.clearCursorDrawable(messageEditText);
        messageEditText.setTextColor(0xff000000);
        messageEditText.setHintTextColor(0xffb2b2b2);
		ArrayList<String> keys = new ArrayList<>();
        keys.addAll(templates.keySet());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.autocompletetemplaterow,keys);
        messageEditText.setAdapter(adapter);
        messageEditText.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String key = (String)parent.getItemAtPosition(position);
                messageEditText.setText(templates.get(key));
                messageEditText.setSelection(messageEditText.getText().length());
            }
        });

        frameLayout.addView(messageEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 52, 0, isChat ? 50 : 2, 0));
        messageEditText.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if (i == KeyEvent.KEYCODE_BACK && !keyboardVisible && isPopupShowing()) {
                    if (keyEvent.getAction() == 1) {
                        showPopup(0, 0);
                    }
                    return true;
                } else if (i == KeyEvent.KEYCODE_ENTER && sendByEnter && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                    sendMessage();
                    return true;
                }
                return false;
            }
        });

        messageEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
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
        messageEditText.addTextChangedListener(new TextWatcher() {
            boolean processChange = false;

            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                if (innerTextChange == 1) {
                    return;
                }
                
                String message = getTrimmedString(charSequence.toString());
                if (delegate != null) {
                    if (count > 2 || charSequence == null || charSequence.length() == 0) {
                        messageWebPageSearch = true;
                    }
                    delegate.onTextChanged(charSequence, before > count + 1 || (count - before) > 2);
                }
                if (innerTextChange != 2 && before != count && (count - before) > 1) {
                    processChange = true;
                }

                if (!message.startsWith("@")) {
                    if (searchForTemplate && message.length() > 0 && message.length() < 100) {
                        Matcher matcher = pattern.matcher(message);
                        String newMessage = message;
                        boolean found = false;
                        while (matcher.find()) {
                            String template = TemplateSupport.getTemplate(matcher.group(1));
                            if (template != null && template.length() != 0) {
                                newMessage = newMessage.replace(matcher.group(1), template);
                                found = true;
                            }
                        }
                        if (found) {
                            CharSequence localCharSequence = newMessage;
                            messageEditText.setText(messageEditText.getText().replace(0, start+count, localCharSequence));
                            int j = localCharSequence.length();
                            messageEditText.setSelection(j, j);
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
                if (innerTextChange != 0) {
                    return;
                }
                if (sendByEnter && editable.length() > 0 && editable.charAt(editable.length() - 1) == '\n') {
                    sendMessage();
                }
                if (processChange) {
                    ImageSpan[] spans = editable.getSpans(0, editable.length(), ImageSpan.class);
                    for (int i = 0; i < spans.length; i++) {
                        editable.removeSpan(spans[i]);
                    }
                    Emoji.replaceEmoji(editable, messageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
                    processChange = false;
                }
            }
        });

        if (isChat) {
            attachButton = new LinearLayout(context);
            attachButton.setOrientation(LinearLayout.HORIZONTAL);
            attachButton.setEnabled(false);
            ViewProxy.setPivotX(attachButton, AndroidUtilities.dp(48));
            frameLayout.addView(attachButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 48, Gravity.BOTTOM | Gravity.RIGHT));
        }

        FrameLayout frameLayout1 = new FrameLayout(context);
        textFieldContainer.addView(frameLayout1, LayoutHelper.createLinear(48, 48, Gravity.BOTTOM));

        sendButton = new ImageView(context);
        sendButton.setVisibility(View.VISIBLE);
        sendButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        sendButton.setImageResource(R.drawable.ic_send);
        sendButton.setSoundEffectsEnabled(false);
        frameLayout1.addView(sendButton, LayoutHelper.createFrame(48, 48));
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage();
            }
        });
        sendButton.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.readChatNotification, dialog_id, true);
                    }
                });
                return true;
            }
        });

        SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("emoji", Context.MODE_PRIVATE);
        keyboardHeight = sharedPreferences.getInt("kbd_height", AndroidUtilities.dp(200));
        keyboardHeightLand = sharedPreferences.getInt("kbd_height_land3", AndroidUtilities.dp(200));
    }

    public void addTopView(View view, int height) {
        if (view == null) {
            return;
        }
        topView = view;
        topView.setVisibility(GONE);
        addView(topView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, height, Gravity.TOP | Gravity.LEFT, 0, 2, 0, 0));
        needShowTopView = false;
    }

    public void setTopViewAnimation(float progress) {
        topViewAnimation = progress;
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

    public void setAllowStickers(boolean value) {
        allowStickers = value;
    }

    public void showTopView(boolean animated) {
        if (topView == null || topViewShowed) {
            return;
        }
        needShowTopView = true;
        topViewShowed = true;
        if (allowShowTopView) {
            topView.setVisibility(VISIBLE);
            if (currentTopViewAnimation != null) {
                currentTopViewAnimation.cancel();
                currentTopViewAnimation = null;
            }
            if (animated) {
                if (keyboardVisible || isPopupShowing()) {
                    currentTopViewAnimation = new AnimatorSetProxy();
                    currentTopViewAnimation.playTogether(
                            ObjectAnimatorProxy.ofFloat(ChatActivityEnterView.this, "topViewAnimation", 1.0f)
                    );
                    currentTopViewAnimation.addListener(new AnimatorListenerAdapterProxy() {
                        @Override
                        public void onAnimationEnd(Object animation) {
                            if (animation == currentTopViewAnimation) {
                                setTopViewAnimation(1.0f);
                                if (!forceShowSendButton) {
                                    openKeyboard();
                                }
                                currentTopViewAnimation = null;
                            }
                        }
                    });
                    currentTopViewAnimation.setDuration(200);
                    currentTopViewAnimation.start();
                } else {
                    setTopViewAnimation(1.0f);
                    if (!forceShowSendButton) {
                        openKeyboard();
                    }
                }
            } else {
                setTopViewAnimation(1.0f);
            }
        }
    }

    public void hideTopView(final boolean animated) {
        if (topView == null || !topViewShowed) {
            return;
        }

        topViewShowed = false;
        needShowTopView = false;
        if (allowShowTopView) {
            float resumeValue = 1.0f;
            if (currentTopViewAnimation != null) {
                resumeValue = topViewAnimation;
                currentTopViewAnimation.cancel();
                currentTopViewAnimation = null;
            }
            if (animated) {
                currentTopViewAnimation = new AnimatorSetProxy();
                currentTopViewAnimation.playTogether(
                        ObjectAnimatorProxy.ofFloat(ChatActivityEnterView.this, "topViewAnimation", resumeValue, 0.0f)
                );
                currentTopViewAnimation.addListener(new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Object animation) {
                        if (animation == currentTopViewAnimation) {
                            topView.setVisibility(GONE);
                            setTopViewAnimation(0.0f);
                            currentTopViewAnimation = null;
                        }
                    }
                });
                currentTopViewAnimation.setDuration(200);
                currentTopViewAnimation.start();
            } else {
                topView.setVisibility(GONE);
                setTopViewAnimation(0.0f);
            }
        }
    }

    public boolean isTopViewVisible() {
        return topView != null && topView.getVisibility() == VISIBLE;
    }

    private void onWindowSizeChanged() {
        int size = sizeNotifierLayout.getHeight();
        if (!keyboardVisible) {
            size -= emojiPadding;
        }
        if (delegate != null) {
            delegate.onWindowSizeChanged(size);
        }
        if (topView != null) {
            if (size < AndroidUtilities.dp(72) + ActionBar.getCurrentActionBarHeight()) {
                if (allowShowTopView) {
                    allowShowTopView = false;
                    if (needShowTopView) {
                        topView.setVisibility(View.GONE);
                        setTopViewAnimation(0.0f);
                    }
                }
            } else {
                if (!allowShowTopView) {
                    allowShowTopView = true;
                    if (needShowTopView) {
                        topView.setVisibility(View.VISIBLE);
                        setTopViewAnimation(1.0f);
                    }
                }
            }
        }
    }

    public void onDestroy() {
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateTemplatesNotification);
        if (mWakeLock != null) {
            try {
                mWakeLock.release();
                mWakeLock = null;
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
        if (sizeNotifierLayout != null) {
            sizeNotifierLayout.setDelegate(null);
        }
    }

    public void onPause() {
        isPaused = true;
    }

    public void onResume() {
        isPaused = false;
        if (showKeyboardOnResume) {
            showKeyboardOnResume = false;
            messageEditText.requestFocus();
            AndroidUtilities.showKeyboard(messageEditText);
            if (!AndroidUtilities.usingHardwareInput && !keyboardVisible) {
                waitingForKeyboardOpen = true;
                AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
                AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100);
            }
        }
    }

    public void setDialogId(long id) {
        dialog_id = id;
    }

    public void setReplyingMessageObject(MessageObject messageObject) {
        if (messageObject != null) {
            if (botMessageObject == null && botButtonsMessageObject != replyingMessageObject) {
                botMessageObject = botButtonsMessageObject;
            }
            replyingMessageObject = messageObject;
            setButtons(replyingMessageObject, true);
        } else if (messageObject == null && replyingMessageObject == botButtonsMessageObject) {
            replyingMessageObject = null;
            setButtons(botMessageObject, false);
            botMessageObject = null;
        } else {
            replyingMessageObject = messageObject;
        }
    }

    public void setWebPage(TLRPC.WebPage webPage, boolean searchWebPages) {
        messageWebPage = webPage;
        messageWebPageSearch = searchWebPages;
    }

    public boolean isMessageWebPageSearchEnabled() {
        return messageWebPageSearch;
    }

    private void sendMessage() {
        if (parentFragment != null) {
            String action;
            TLRPC.Chat currentChat;
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
        String message = messageEditText.getText().toString();
        if (processSendingText(message)) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.readChatNotification, dialog_id, false);
                }
            });
            messageEditText.setText("");
            messageEditText.setSelection(messageEditText.length());
            lastTypingTimeSend = 0;
            if (delegate != null) {
                delegate.onMessageSend(message);
            }
        } else if (forceShowSendButton) {
            if (delegate != null) {
                delegate.onMessageSend(null);
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
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.readChatNotification, dialog_id, false);
                }
            });
            messageEditText.setText("");
            messageEditText.setSelection(messageEditText.length());
            lastTypingTimeSend = 0;
            if (delegate != null) {
                delegate.onMessageSend(text);
            }
        } else if (forceShowSendButton) {
            if (delegate != null) {
                delegate.onMessageSend(null);
            }
        }
    }

    public boolean processSendingText(String text) {
        if (text.length() != 0) {
            Matcher matcher = patternIssue.matcher(text);
            if (matcher.find()) {
                String issueId = matcher.group(1);
                String message = LocaleController.getString("IssueNoted", R.string.IssueNoted).replace("@id",issueId);
                SendMessagesHelper.getInstance().sendMessage(message, dialog_id, replyingMessageObject, messageWebPage, messageWebPageSearch);
            } else {
                matcher = patternIssueSolved.matcher(text);
                if (matcher.find()) {
                    String issueId = matcher.group(1);
                    String message = LocaleController.getString("IssueSolved", R.string.IssueSolved).replace("@id", issueId);
                    SendMessagesHelper.getInstance().sendMessage(message, dialog_id, replyingMessageObject, messageWebPage, messageWebPageSearch);
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
                            SendMessagesHelper.getInstance().sendMessage(mess, dialog_id, replyingMessageObject, messageWebPage, messageWebPageSearch);
                            SendMessagesHelper.getInstance().sendMessageSupport(number, name, surname, dialog_id, "");
                        } else {
                            SendMessagesHelper.getInstance().sendMessage(mess, dialog_id, replyingMessageObject, messageWebPage, messageWebPageSearch);
                        }
                    }
                }
            }
            return true;
        }
        return false;

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

    private void updateFieldRight(int attachVisible) {
        if (messageEditText == null) {
            return;
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) messageEditText.getLayoutParams();
        if (attachVisible == 1) {
            layoutParams.rightMargin = AndroidUtilities.dp(50);
        } else if (attachVisible == 2) {
            if (layoutParams.rightMargin != AndroidUtilities.dp(2)) {
                layoutParams.rightMargin = AndroidUtilities.dp(50);
            }
        } else {
            layoutParams.rightMargin = AndroidUtilities.dp(2);
        }
        messageEditText.setLayoutParams(layoutParams);
    }

    public void setDelegate(ChatActivityEnterViewDelegate delegate) {
        this.delegate = delegate;
    }

    public void setCommand(MessageObject messageObject, String command) {
        if (command == null) {
            return;
        }
        TLRPC.User user = messageObject != null && (int) dialog_id < 0 ? MessagesController.getInstance().getUser(messageObject.messageOwner.from_id) : null;
        if (botCount != 1 && user != null && (user.flags & TLRPC.USER_FLAG_BOT) != 0 && !command.contains("@")) {
            SendMessagesHelper.getInstance().sendMessage(String.format(Locale.US, "%s@%s", command, user.username), dialog_id, null, null, false);
        } else {
            SendMessagesHelper.getInstance().sendMessage(command, dialog_id, null, null, false);
        }
        /*String text = messageEditText.getText().toString();
        text = command + " " + text.replaceFirst("^/[a-zA-Z@\\d_]{1,255}(\\s|$)", "");
        ignoreTextChange = true;
        messageEditText.setText(text);
        messageEditText.setSelection(messageEditText.getText().length());
        ignoreTextChange = false;*/
    }

    public void setFieldText(String text) {
        if (messageEditText == null) {
            return;
        }
        ignoreTextChange = true;
        messageEditText.setText(text);
        messageEditText.setSelection(messageEditText.getText().length());
        ignoreTextChange = false;
        if (delegate != null) {
            delegate.onTextChanged(messageEditText.getText(), true);
        }
    }

    public int getCursorPosition() {
        if (messageEditText == null) {
            return 0;
        }
        return messageEditText.getSelectionStart();
    }

    public void replaceWithText(int start, int len, String text) {
        try {
            StringBuilder builder = new StringBuilder(messageEditText.getText());
            builder.replace(start, start + len, text);
            messageEditText.setText(builder);
            messageEditText.setSelection(start + text.length());
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public void setFieldFocused(boolean focus) {
        if (messageEditText == null) {
            return;
        }
        if (focus) {
            if (!messageEditText.isFocused()) {
                messageEditText.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (messageEditText != null) {
                            try {
                                messageEditText.requestFocus();
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            }
                        }
                    }
                }, 600);
            }
        } else {
            if (messageEditText.isFocused() && !keyboardVisible) {
                messageEditText.clearFocus();
            }
        }
    }

    public boolean hasText() {
        return messageEditText != null && messageEditText.length() > 0;
    }

    public String getFieldText() {
        if (messageEditText != null && messageEditText.length() > 0) {
            return messageEditText.getText().toString();
        }
        return null;
    }

    public void addToAttachLayout(View view) {
        if (attachButton == null) {
            return;
        }
        if (view.getParent() != null) {
            ViewGroup viewGroup = (ViewGroup) view.getParent();
            viewGroup.removeView(view);
        }
        attachButton.addView(view, LayoutHelper.createLinear(48, 48));
    }

    public void setBotsCount(int count, boolean hasCommands) {
        botCount = count;
        if (hasBotCommands != hasCommands) {
            hasBotCommands = hasCommands;
        }
    }

    public void setButtons(MessageObject messageObject) {
        setButtons(messageObject, true);
    }

    public void setButtons(MessageObject messageObject, boolean openKeyboard) {
        if (replyingMessageObject != null && replyingMessageObject == botButtonsMessageObject && replyingMessageObject != messageObject) {
            botMessageObject = messageObject;
            return;
        }
        if (botButtonsMessageObject != null && botButtonsMessageObject == messageObject || botButtonsMessageObject == null && messageObject == null) {
            return;
        }
        botButtonsMessageObject = messageObject;
        botReplyMarkup = messageObject != null && messageObject.messageOwner.reply_markup instanceof TLRPC.TL_replyKeyboardMarkup ? (TLRPC.TL_replyKeyboardMarkup) messageObject.messageOwner.reply_markup : null;

        if (botReplyMarkup != null) {
            if (botButtonsMessageObject != replyingMessageObject && (botReplyMarkup.flags & 2) != 0) {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                if (preferences.getInt("answered_" + dialog_id, 0) == messageObject.getId()) {
                    return;
                }
            }
            if (messageEditText.length() == 0 && !isPopupShowing()) {
                showPopup(1, 1);
            }
        } else {
            if (isPopupShowing() && currentPopupContentType == 1) {
                if (openKeyboard) {
                    openKeyboardInternal();
                } else {
                    showPopup(0, 1);
                }
            } else if (isPopupShowing() && currentPopupContentType == 3) {
                if (openKeyboard) {
                    openKeyboardInternal();
                } else {
                    showPopup(0, 3);
                }
            }
        }
    }

    public boolean isPopupView(View view) {
        return view == emojiView || view == templateView;
    }

    private void showPopup(int show, int contentType) {
        if (show == 1) {
            if (contentType == 0 && emojiView == null) {
                if (parentActivity == null) {
                    return;
                }
                emojiView = new EmojiView(allowStickers, parentActivity);
                emojiView.setVisibility(GONE);
                emojiView.setListener(new EmojiView.Listener() {
                    public boolean onBackspace() {
                        if (messageEditText.length() == 0) {
                            return false;
                        }
                        messageEditText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                        return true;
                    }

                    public void onEmojiSelected(String symbol) {
                        int i = messageEditText.getSelectionEnd();
                        if (i < 0) {
                            i = 0;
                        }
                        try {
                            innerTextChange = 2;
                            CharSequence localCharSequence = Emoji.replaceEmoji(symbol/* + "\uFE0F"*/, messageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
                            messageEditText.setText(messageEditText.getText().insert(i, localCharSequence));
                            int j = i + localCharSequence.length();
                            messageEditText.setSelection(j, j);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        } finally {
                            innerTextChange = 0;
                        }
                    }

                    public void onStickerSelected(TLRPC.Document sticker) {
                        SendMessagesHelper.getInstance().sendSticker(sticker, dialog_id, replyingMessageObject);
                        if (delegate != null) {
                            delegate.onMessageSend(null);
                        }
                    }
                });
                sizeNotifierLayout.addView(emojiView);
            } else if (contentType == 3 && templateView == null) {
                if (parentActivity == null) {
                    return;
                }
                templateView = new TemplateView(parentActivity, templates);
                templateView.setVisibility(GONE);
                templateView.setListener(new TemplateView.Listener() {
                    public boolean onBackspace() {
                        if (messageEditText.length() == 0) {
                            return false;
                        }
                        messageEditText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                        return true;
                    }

                    public void onTemplateSelected(String template) {
                        try {
                            int i = messageEditText.getSelectionEnd();
                            if (i < 0) {
                                i = 0;
                            }
                            CharSequence localCharSequence = template;
                            messageEditText.setText(messageEditText.getText().insert(i, localCharSequence));
                            int j = i + localCharSequence.length();
                            messageEditText.setSelection(j, j);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                });
                sizeNotifierLayout.addView(templateView);
            }

            View currentView = null;
            if (contentType == 0) {
                emojiView.setVisibility(VISIBLE);
                currentView = emojiView;
            } else if (contentType == 1) {
                if (emojiView != null && emojiView.getVisibility() != GONE) {
                    emojiView.setVisibility(GONE);
                }
            } else if (contentType == 3) {
                templateView.setVisibility(VISIBLE);
                currentView = templateView;
            }
            currentPopupContentType = contentType;

            if (keyboardHeight <= 0) {
                keyboardHeight = ApplicationLoader.applicationContext.getSharedPreferences("emoji", Context.MODE_PRIVATE).getInt("kbd_height", AndroidUtilities.dp(200));
            }
            if (keyboardHeightLand <= 0) {
                keyboardHeightLand = ApplicationLoader.applicationContext.getSharedPreferences("emoji", Context.MODE_PRIVATE).getInt("kbd_height_land3", AndroidUtilities.dp(200));
            }
            int currentHeight = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight;

            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) currentView.getLayoutParams();
            layoutParams.width = AndroidUtilities.displaySize.x;
            layoutParams.height = currentHeight;
            currentView.setLayoutParams(layoutParams);
            AndroidUtilities.hideKeyboard(messageEditText);
            if (sizeNotifierLayout != null) {
                emojiPadding = currentHeight;
                sizeNotifierLayout.requestLayout();
                if (contentType == 0 || contentType == 3) {
                    emojiButton.setImageResource(R.drawable.ic_msg_panel_kb);
                } else if (contentType == 1) {
                    emojiButton.setImageResource(R.drawable.ic_msg_panel_smiles);
                }
                onWindowSizeChanged();
            }
        } else {
            if (emojiButton != null) {
                emojiButton.setImageResource(R.drawable.ic_msg_panel_smiles);
            }
            if (emojiView != null) {
                emojiView.setVisibility(GONE);
            }
            if (templateView != null) {
                templateView.setVisibility(GONE);
            }
            if (sizeNotifierLayout != null) {
                if (show == 0) {
                    emojiPadding = 0;
                }
                sizeNotifierLayout.requestLayout();
                onWindowSizeChanged();
            }
        }
    }

    public void hidePopup() {
        if (isPopupShowing()) {
            showPopup(0, 0);
        }
    }

    private void openKeyboardInternal() {
        showPopup(AndroidUtilities.usingHardwareInput || isPaused ? 0 : 2, 0);
        messageEditText.requestFocus();
        AndroidUtilities.showKeyboard(messageEditText);
        if (isPaused) {
            showKeyboardOnResume = true;
        } else if (!AndroidUtilities.usingHardwareInput && !keyboardVisible) {
            waitingForKeyboardOpen = true;
            AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
            AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100);
        }
    }

    public void openKeyboard() {
        AndroidUtilities.showKeyboard(messageEditText);
    }

    public boolean isPopupShowing() {
        return emojiView != null && emojiView.getVisibility() == VISIBLE || templateView != null && templateView.getVisibility() == VISIBLE;
    }

    @Override
    public void onSizeChanged(int height, boolean isWidthGreater) {
        if (height > AndroidUtilities.dp(50) && keyboardVisible) {
            if (isWidthGreater) {
                keyboardHeightLand = height;
                ApplicationLoader.applicationContext.getSharedPreferences("emoji", 0).edit().putInt("kbd_height_land3", keyboardHeightLand).commit();
            } else {
                keyboardHeight = height;
                ApplicationLoader.applicationContext.getSharedPreferences("emoji", 0).edit().putInt("kbd_height", keyboardHeight).commit();
            }
        }

        if (isPopupShowing()) {
            int newHeight = isWidthGreater ? keyboardHeightLand : keyboardHeight;

            View currentView = null;
            if (currentPopupContentType == 0) {
                currentView = emojiView;
            } else if (currentPopupContentType == 3) {
                currentView = templateView;
            }

            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) currentView.getLayoutParams();
            if (layoutParams.width != AndroidUtilities.displaySize.x || layoutParams.height != newHeight) {
                layoutParams.width = AndroidUtilities.displaySize.x;
                layoutParams.height = newHeight;
                currentView.setLayoutParams(layoutParams);
                if (sizeNotifierLayout != null) {
                    emojiPadding = layoutParams.height;
                    sizeNotifierLayout.requestLayout();
                    onWindowSizeChanged();
                }
            }
        }

        if (lastSizeChangeValue1 == height && lastSizeChangeValue2 == isWidthGreater) {
            onWindowSizeChanged();
            return;
        }
        lastSizeChangeValue1 = height;
        lastSizeChangeValue2 = isWidthGreater;

        boolean oldValue = keyboardVisible;
        keyboardVisible = height > 0;
        if (keyboardVisible && isPopupShowing()) {
            showPopup(0, currentPopupContentType);
        }
        if (emojiPadding != 0 && !keyboardVisible && keyboardVisible != oldValue && !isPopupShowing()) {
            emojiPadding = 0;
            sizeNotifierLayout.requestLayout();
        }
        if (keyboardVisible && waitingForKeyboardOpen) {
            waitingForKeyboardOpen = false;
            AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
        }
        onWindowSizeChanged();
    }

    public int getEmojiPadding() {
        return emojiPadding;
    }

    public int getEmojiHeight() {
        if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
            return keyboardHeightLand;
        } else {
            return keyboardHeight;
        }
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.emojiDidLoaded) {
            if (emojiView != null) {
                emojiView.invalidateViews();
            }
        } else if (id == NotificationCenter.closeChats) {
            if (messageEditText != null && messageEditText.isFocused()) {
                AndroidUtilities.hideKeyboard(messageEditText);
            }
        }  else if (id == NotificationCenter.updateTemplatesNotification) {
            templates.putAll(TemplateSupport.getAll());
            if (templateView != null) {
                templateView.setTemplates(templates);
            }
        }
    }
}
