/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.tsupport.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.tsupport.android.AndroidUtilities;
import org.tsupport.android.ContactsController;
import org.tsupport.android.LocaleController;
import org.tsupport.android.MessageObject;
import org.tsupport.android.MessagesController;
import org.tsupport.android.MessagesStorage;
import org.tsupport.android.NotificationCenter;
import org.tsupport.messenger.FileLog;
import org.tsupport.messenger.R;
import org.tsupport.messenger.TLRPC;
import org.tsupport.messenger.UserConfig;
import org.tsupport.ui.ActionBar.ActionBar;
import org.tsupport.ui.ActionBar.MenuDrawable;
import org.tsupport.ui.Adapters.BaseFragmentAdapter;
import org.tsupport.ui.Adapters.DialogsAdapter;
import org.tsupport.ui.Adapters.DialogsSearchAdapter;
import org.tsupport.ui.AnimationCompat.ObjectAnimatorProxy;
import org.tsupport.ui.AnimationCompat.ViewProxy;
import org.tsupport.ui.Cells.DialogCell;
import org.tsupport.ui.ActionBar.ActionBarMenu;
import org.tsupport.ui.ActionBar.ActionBarMenuItem;
import org.tsupport.ui.ActionBar.BaseFragment;
import org.tsupport.ui.Cells.UserCell;

import java.util.ArrayList;

public class MessagesActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private ListView messagesListView;
    private DialogsAdapter dialogsAdapter;
    private DialogsSearchAdapter dialogsSearchAdapter;
    private View searchEmptyView;
    private View progressView;
    private View emptyView;
    private String currentSearch = "";
    private ImageView floatingButton;
    private int prevPosition;
    private int prevTop;
    private boolean scrollUpdated;
    //private boolean floatingHidden;
    private final AccelerateDecelerateInterpolator floatingInterpolator = new AccelerateDecelerateInterpolator();

    private String selectAlertString;
    private String selectAlertStringGroup;
    private boolean serverOnly = false;

    private static boolean dialogsLoaded = false;
    private boolean searching = false;
    private boolean searchWas = false;
    private boolean onlySelect = false;
    private long selectedDialog;

    private MessagesActivityDelegate delegate;

    private long openedDialogId = 0;

    public static interface MessagesActivityDelegate {
        public abstract void didSelectDialog(MessagesActivity fragment, long dialog_id, boolean param);
    }

    public MessagesActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.dialogsNeedReload);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.encryptedChatUpdated);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.contactsDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.appDidLogout);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.openedChatChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagesRead);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.readChatNotification);

        if (getArguments() != null) {
            onlySelect = arguments.getBoolean("onlySelect", false);
            serverOnly = arguments.getBoolean("serverOnly", false);
            selectAlertString = arguments.getString("selectAlertString");
            selectAlertStringGroup = arguments.getString("selectAlertStringGroup");
        }
        if (!dialogsLoaded) {
            MessagesController.getInstance().loadDialogs(0, 0, 100, true);
            dialogsLoaded = true;
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.dialogsNeedReload);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.encryptedChatUpdated);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.contactsDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.appDidLogout);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.openedChatChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagesRead);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.readChatNotification);
        delegate = null;
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            ActionBarMenu menu = actionBar.createMenu();
            menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                @Override
                public void onSearchExpand() {
                    searching = true;
                    if (messagesListView != null) {
                        messagesListView.setEmptyView(searchEmptyView);
                        emptyView.setVisibility(View.GONE);
                        progressView.setVisibility(View.GONE);
                        if (!onlySelect) {
                            //floatingButton.setVisibility(View.GONE);
                        }
                    }
                }

                @Override
                public void onSearchCollapse() {
                    searching = false;
                    searchWas = false;
                    if (messagesListView != null) {
                        if (MessagesController.getInstance().loadingDialogs && MessagesController.getInstance().dialogs.isEmpty()) {
                            searchEmptyView.setVisibility(View.GONE);
                            emptyView.setVisibility(View.GONE);
                            progressView.setVisibility(View.VISIBLE);
                            messagesListView.setEmptyView(progressView);
                        } else {
                            messagesListView.setEmptyView(emptyView);
                            searchEmptyView.setVisibility(View.GONE);
                            progressView.setVisibility(View.GONE);
                        }
                        if (!onlySelect) {
                            //floatingButton.setVisibility(View.VISIBLE);
                            //floatingHidden = true;
                            //ViewProxy.setTranslationY(floatingButton, AndroidUtilities.dp(100));
                            //hideFloatingButton(false);
                        }
                        if (messagesListView.getAdapter() != dialogsAdapter) {
                            messagesListView.setAdapter(dialogsAdapter);
                            dialogsAdapter.notifyDataSetChanged();
                        }
                    }
                    if (dialogsSearchAdapter != null) {
                        dialogsSearchAdapter.searchDialogs(null, 2);
                    }
                }

                @Override
                public void onTextChanged(EditText editText) {
                    String text = editText.getText().toString();
                    if (text.length() != 0) {
                        searchWas = true;
                        if (dialogsSearchAdapter != null) {
                            messagesListView.setAdapter(dialogsSearchAdapter);
                            dialogsSearchAdapter.notifyDataSetChanged();
                        }
                        if (searchEmptyView != null && messagesListView.getEmptyView() == emptyView) {
                            messagesListView.setEmptyView(searchEmptyView);
                            emptyView.setVisibility(View.GONE);
                            progressView.setVisibility(View.GONE);
                        }
                    }
                    if (dialogsSearchAdapter != null) {
                        currentSearch = text;
                        dialogsSearchAdapter.searchDialogs(text, 2);
                    }
                }
            });
            if (onlySelect) {
                actionBar.setBackButtonImage(R.drawable.ic_ab_back);
                actionBar.setTitle(LocaleController.getString("SelectChat", R.string.SelectChat));
            } else {
                actionBar.setBackButtonDrawable(new MenuDrawable());
                actionBar.setTitle(LocaleController.getString("AppName", R.string.AppName));
            }
            actionBar.setAllowOverlayTitle(true);

            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
		            if (id == -1) {
                        if (searching) {
                            currentSearch = "";
                        }
                        if (onlySelect) {
                            finishFragment();
                        } else if (parentLayout != null) {
                            parentLayout.getDrawerLayoutContainer().openDrawer(false);
                        }
                    }
                }
            });

            searching = false;
            searchWas = false;

            fragmentView = inflater.inflate(R.layout.messages_list, container, false);

            dialogsAdapter = new DialogsAdapter(getParentActivity(), serverOnly);
            if (AndroidUtilities.isTablet() && openedDialogId != 0) {
                dialogsAdapter.setOpenedDialogId(openedDialogId);
            }
            dialogsSearchAdapter = new DialogsSearchAdapter(getParentActivity(), !onlySelect);
            dialogsSearchAdapter.setDelegate(new DialogsSearchAdapter.MessagesActivitySearchAdapterDelegate() {
                @Override
                public void searchStateChanged(boolean search) {
                    if (searching && searchWas && messagesListView != null) {
                        progressView.setVisibility(search ? View.VISIBLE : View.GONE);
                        searchEmptyView.setVisibility(search ? View.GONE : View.VISIBLE);
                        messagesListView.setEmptyView(search ? progressView : searchEmptyView);
                    }
                }
            });

            messagesListView = (ListView)fragmentView.findViewById(R.id.messages_list_view);
            messagesListView.setAdapter(dialogsAdapter);
            if (Build.VERSION.SDK_INT >= 11) {
                messagesListView.setVerticalScrollbarPosition(LocaleController.isRTL ? ListView.SCROLLBAR_POSITION_LEFT : ListView.SCROLLBAR_POSITION_RIGHT);
            }

            progressView = fragmentView.findViewById(R.id.progressLayout);
            dialogsAdapter.notifyDataSetChanged();
            searchEmptyView = fragmentView.findViewById(R.id.search_empty_view);
            searchEmptyView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });
            emptyView = fragmentView.findViewById(R.id.list_empty_view);
            emptyView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });


            TextView textView = (TextView)fragmentView.findViewById(R.id.list_empty_view_text1);
            textView.setText(LocaleController.getString("NoChats", R.string.NoChats));
            textView = (TextView)fragmentView.findViewById(R.id.list_empty_view_text2);
            textView.setText(LocaleController.getString("NoChatsHelp", R.string.NoChatsHelp));
            textView = (TextView)fragmentView.findViewById(R.id.search_empty_text);
            textView.setText(LocaleController.getString("NoResult", R.string.NoResult));

            floatingButton = (ImageView)fragmentView.findViewById(R.id.floating_button);
            floatingButton.setVisibility(View.GONE);

            if (MessagesController.getInstance().loadingDialogs && MessagesController.getInstance().dialogs.isEmpty()) {
                searchEmptyView.setVisibility(View.GONE);
                emptyView.setVisibility(View.GONE);
                progressView.setVisibility(View.VISIBLE);
                messagesListView.setEmptyView(progressView);
            } else {
                messagesListView.setEmptyView(emptyView);
                searchEmptyView.setVisibility(View.GONE);
                progressView.setVisibility(View.GONE);
            }

            messagesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (messagesListView == null || messagesListView.getAdapter() == null) {
                        return;
                    }
                    long dialog_id = 0;
                    int message_id = 0;
                    BaseFragmentAdapter adapter = (BaseFragmentAdapter)messagesListView.getAdapter();
                    Bundle args = new Bundle();
                    if (adapter == dialogsAdapter) {
                        TLRPC.TL_dialog dialog = dialogsAdapter.getItem(i);
                        if (dialog == null) {
                            return;
                        }
                        dialog_id = dialog.id;
                    } else if (adapter == dialogsSearchAdapter) {
                        Object obj = dialogsSearchAdapter.getItem(i);
                        args.putString("searchQuery", currentSearch);
                        if (obj instanceof TLRPC.User) {
                            dialog_id = ((TLRPC.User) obj).id;
                            if (dialogsSearchAdapter.isGlobalSearch(i)) {
                                ArrayList<TLRPC.User> users = new ArrayList<TLRPC.User>();
                                users.add((TLRPC.User)obj);
                                MessagesController.getInstance().putUsers(users, false);
                                MessagesStorage.getInstance().putUsersAndChats(users, null, false, true);
                            }
                        } else if (obj instanceof TLRPC.Chat) {
                            if (((TLRPC.Chat) obj).id > 0) {
                                dialog_id = -((TLRPC.Chat) obj).id;
                            } else {
                                dialog_id = AndroidUtilities.makeBroadcastId(((TLRPC.Chat) obj).id);
                            }
                        } else if (obj instanceof TLRPC.EncryptedChat) {
                            dialog_id = ((long)((TLRPC.EncryptedChat) obj).id) << 32;
                        } else if (obj instanceof MessageObject) {
                            MessageObject messageObject = (MessageObject)obj;
                            dialog_id = messageObject.getDialogId();
                            message_id = messageObject.messageOwner.id;
                        }
                    }

                    if (dialog_id == 0) {
                        return;
                    }

                    if (onlySelect) {
                        didSelectResult(dialog_id, true, false);
                    } else {
                        int lower_part = (int)dialog_id;
                        int high_id = (int)(dialog_id >> 32);
                        if (lower_part != 0) {
                            if (high_id == 1) {
                                args.putInt("chat_id", lower_part);
                            } else {
                                if (lower_part > 0) {
                                    args.putInt("user_id", lower_part);
                                } else if (lower_part < 0) {
                                    args.putInt("chat_id", -lower_part);
                                }
                            }
                        } else {
                            args.putInt("enc_id", high_id);
                        }
                        if (message_id != 0) {
                            args.putInt("message_id", message_id);
                        }
                        if (AndroidUtilities.isTablet()) {
                            if (openedDialogId == dialog_id) {
                                return;
                            }
                            dialogsAdapter.setOpenedDialogId(openedDialogId = dialog_id);
                            updateVisibleRows(MessagesController.UPDATE_MASK_SELECT_DIALOG);
                        }
                    ChatActivity chatActivity = new ChatActivity(args);
                    chatActivity.setDelegate(MessagesActivity.this);
                    presentFragment(chatActivity);
                    }
                }
            });

            messagesListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (onlySelect || searching && searchWas || getParentActivity() == null) {
                        return false;
                    }
                    TLRPC.TL_dialog dialog;
                    if (serverOnly) {
                        if (i >= MessagesController.getInstance().dialogsServerOnly.size()) {
                            return false;
                        }
                        dialog = MessagesController.getInstance().dialogsServerOnly.get(i);
                    } else {
                        if (i >= MessagesController.getInstance().dialogs.size()) {
                            return false;
                        }
                        dialog = MessagesController.getInstance().dialogs.get(i);
                    }
                    selectedDialog = dialog.id;

                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));

                    int lower_id = (int)selectedDialog;
                    int high_id = (int)(selectedDialog >> 32);

                    final boolean isChat = lower_id < 0 && high_id != 1;
                    builder.setItems(new CharSequence[]{LocaleController.getString("ClearHistory", R.string.ClearHistory),
                            isChat ? LocaleController.getString("DeleteChat", R.string.DeleteChat) : LocaleController.getString("Delete", R.string.Delete)}, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, final int which) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            if (which == 0) {
                                builder.setMessage(LocaleController.getString("AreYouSureClearHistory", R.string.AreYouSureClearHistory));
                            } else {
                                if (isChat) {
                                    builder.setMessage(LocaleController.getString("AreYouSureDeleteAndExit", R.string.AreYouSureDeleteAndExit));
                                } else {
                                    builder.setMessage(LocaleController.getString("AreYouSureDeleteThisChat", R.string.AreYouSureDeleteThisChat));
                                }
                            }
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    MessagesController.getInstance().deleteDialog(selectedDialog, 0, which == 0);
                                    if (which != 0) {
                                        if (isChat) {
                                            MessagesController.getInstance().deleteUserFromChat((int) -selectedDialog, MessagesController.getInstance().getUser(UserConfig.getClientUserId()), null);
                                        }
                                        if (AndroidUtilities.isTablet()) {
                                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats, selectedDialog);
                                        }
                                    }
                                }
                            });
                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            showAlertDialog(builder);
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showAlertDialog(builder);
                    return true;
                }
            });

            messagesListView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView absListView, int i) {
                    if (i == SCROLL_STATE_TOUCH_SCROLL && searching && searchWas) {
                        AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                    }
                }

                @Override
                public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (searching && searchWas) {
                        if (visibleItemCount > 0 && absListView.getLastVisiblePosition() == totalItemCount - 5 && !dialogsSearchAdapter.isMessagesSearchEndReached()) {
                            dialogsSearchAdapter.loadMoreSearchMessages();
                        }
                        return;
                    }
                    if (visibleItemCount > 0) {
                        if (absListView.getLastVisiblePosition() == MessagesController.getInstance().dialogs.size() && !serverOnly || absListView.getLastVisiblePosition() == MessagesController.getInstance().dialogsServerOnly.size() && serverOnly) {
                            MessagesController.getInstance().loadDialogs(MessagesController.getInstance().dialogs.size(), MessagesController.getInstance().dialogsServerOnly.size(), 100, true);
                        }
                    }

                    /*if (floatingButton.getVisibility() != View.GONE) {
                        final View topChild = absListView.getChildAt(0);
                        int firstViewTop = 0;
                        if (topChild != null) {
                            firstViewTop = topChild.getTop();
                        }
                        boolean goingDown;
                        boolean changed = true;
                        if (prevPosition == firstVisibleItem) {
                            final int topDelta = prevTop - firstViewTop;
                            goingDown = firstViewTop < prevTop;
                            changed = Math.abs(topDelta) > 1;
                        } else {
                            goingDown = firstVisibleItem > prevPosition;
                        }
                        if (changed && scrollUpdated) {
                            hideFloatingButton(goingDown);
                        }
                        prevPosition = firstVisibleItem;
                        prevTop = firstViewTop;
                        scrollUpdated = true;
                    }*/
                }
            });
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }

    if (currentSearch != null && currentSearch.compareToIgnoreCase("") != 0) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() { //TODO
                String temp = currentSearch;
                final ActionBarMenu menu = actionBar.createMenu();
                if (menu.getItem(0).listener != null) {
                    actionBar.onSearchFieldVisibilityChanged(menu.getItem(0).toggleSearch());
                    menu.getItem(0).searchField.setText(temp);
                    AndroidUtilities.showKeyboard(menu.getItem(0).searchField);
                    menu.getItem(0).listener.onTextChanged(menu.getItem(0).searchField);
                    FileLog.d("tsupportSearch", "Search returned: " + temp);
                }
            }
        });
    }
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (dialogsAdapter != null) {
            dialogsAdapter.notifyDataSetChanged();
        }
        if (dialogsSearchAdapter != null) {
            dialogsSearchAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        /*if (!onlySelect && floatingButton != null) {
            floatingButton.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    ViewProxy.setTranslationY(floatingButton, floatingHidden ? AndroidUtilities.dp(100) : 0);
                    floatingButton.setClickable(!floatingHidden);
                    if (floatingButton != null) {
                        if (Build.VERSION.SDK_INT < 16) {
                            floatingButton.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        } else {
                            floatingButton.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                    }
                }
            });
        }*/
    }

    @Override
    @SuppressWarnings("unchecked")
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.dialogsNeedReload) {
            if (dialogsAdapter != null) {
                dialogsAdapter.notifyDataSetChanged();
            }
            if (dialogsSearchAdapter != null) {
                dialogsSearchAdapter.notifyDataSetChanged();
            }
            if (messagesListView != null) {
                if (MessagesController.getInstance().loadingDialogs && MessagesController.getInstance().dialogs.isEmpty()) {
                    searchEmptyView.setVisibility(View.GONE);
                    emptyView.setVisibility(View.GONE);
                    messagesListView.setEmptyView(progressView);
                } else {
                    if (searching && searchWas) {
                        messagesListView.setEmptyView(searchEmptyView);
                        emptyView.setVisibility(View.GONE);
                    } else {
                        messagesListView.setEmptyView(emptyView);
                        searchEmptyView.setVisibility(View.GONE);
                    }
                    progressView.setVisibility(View.GONE);
                }
            }
        } else if (id == NotificationCenter.emojiDidLoaded) {
            if (messagesListView != null) {
                updateVisibleRows(0);
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            updateVisibleRows((Integer)args[0]);
        } else if (id == NotificationCenter.messagesRead) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (dialogsAdapter != null) {
                        dialogsAdapter.notifyDataSetChanged();
                    }
                }
            });
        } else if (id == NotificationCenter.appDidLogout) {
            dialogsLoaded = false;
        } else if (id == NotificationCenter.encryptedChatUpdated) {
            updateVisibleRows(0);
        } else if (id == NotificationCenter.contactsDidLoaded) {
            updateVisibleRows(0);
        } else if (id == NotificationCenter.openedChatChanged) {
            if (!serverOnly && AndroidUtilities.isTablet()) {
                boolean close = (Boolean)args[1];
                long dialog_id = (Long)args[0];
                if (close) {
                    if (dialog_id == openedDialogId) {
                        openedDialogId = 0;
                    }
                } else {
                    openedDialogId = dialog_id;
                }
                if (dialogsAdapter != null) {
                    dialogsAdapter.setOpenedDialogId(openedDialogId);
                }
                updateVisibleRows(MessagesController.UPDATE_MASK_SELECT_DIALOG);
            }
        } else if (id == NotificationCenter.readChatNotification) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (dialogsAdapter != null) {
                        dialogsAdapter.notifyDataSetChanged();
                    }
                }
            });
        }
    }

    private void hideFloatingButton(boolean hide) {
        /*if (floatingHidden == hide) {
            return;
        }
        floatingHidden = hide;
        ObjectAnimatorProxy animator = ObjectAnimatorProxy.ofFloatProxy(floatingButton, "translationY", floatingHidden ? AndroidUtilities.dp(100) : 0).setDuration(300);
        animator.setInterpolator(floatingInterpolator);
        floatingButton.setClickable(!hide);
        animator.start();*/
    }

    private void updateVisibleRows(int mask) {
        if (messagesListView == null) {
            return;
        }
        int count = messagesListView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = messagesListView.getChildAt(a);
            if (child instanceof DialogCell) {
                DialogCell cell = (DialogCell) child;
                if ((mask & MessagesController.UPDATE_MASK_SELECT_DIALOG) != 0) {
                    if (!serverOnly && AndroidUtilities.isTablet()) {
                        if (cell.getDialogId() == openedDialogId) {
                            child.setBackgroundColor(0x0f000000);
                        } else {
                            child.setBackgroundColor(0);
                        }
                    }
                } else {
                    cell.update(mask);
                }
            } else if (child instanceof UserCell) {
                ((UserCell) child).update(mask);
            }
        }
    }

    public void setDelegate(MessagesActivityDelegate delegate) {
        this.delegate = delegate;
    }

    public MessagesActivityDelegate getDelegate() {
        return delegate;
    }

    private void didSelectResult(final long dialog_id, boolean useAlert, final boolean param) {
        if (useAlert && selectAlertString != null && selectAlertStringGroup != null) {
            if (getParentActivity() == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            int lower_part = (int)dialog_id;
            int high_id = (int)(dialog_id >> 32);
            if (lower_part != 0) {
                if (high_id == 1) {
                    TLRPC.Chat chat = MessagesController.getInstance().getChat(lower_part);
                    if (chat == null) {
                        return;
                    }
                    builder.setMessage(LocaleController.formatStringSimple(selectAlertStringGroup, chat.title));
                } else {
                    if (lower_part > 0) {
                        TLRPC.User user = MessagesController.getInstance().getUser(lower_part);
                        if (user == null) {
                            return;
                        }
                        builder.setMessage(LocaleController.formatStringSimple(selectAlertString, ContactsController.formatName(user.first_name, user.last_name)));
                    } else if (lower_part < 0) {
                        TLRPC.Chat chat = MessagesController.getInstance().getChat(-lower_part);
                        if (chat == null) {
                            return;
                        }
                        builder.setMessage(LocaleController.formatStringSimple(selectAlertStringGroup, chat.title));
                    }
                }
            } else {
                TLRPC.EncryptedChat chat = MessagesController.getInstance().getEncryptedChat(high_id);
                TLRPC.User user = MessagesController.getInstance().getUser(chat.user_id);
                if (user == null) {
                    return;
                }
                builder.setMessage(LocaleController.formatStringSimple(selectAlertString, ContactsController.formatName(user.first_name, user.last_name)));
            }
            CheckBox checkBox = null;
            /*if (delegate instanceof ChatActivity) {
                checkBox = new CheckBox(getParentActivity());
                checkBox.setText(LocaleController.getString("ForwardFromMyName", R.string.ForwardFromMyName));
                checkBox.setChecked(false);
                builder.setView(checkBox);
            }*/
            final CheckBox checkBoxFinal = checkBox;
            builder.setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    didSelectResult(dialog_id, false, checkBoxFinal != null && checkBoxFinal.isChecked());
                }
            });
            builder.setNegativeButton(R.string.Cancel, null);
            showAlertDialog(builder);
            if (checkBox != null) {
                ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams)checkBox.getLayoutParams();
                if (layoutParams != null) {
                    layoutParams.rightMargin = layoutParams.leftMargin = AndroidUtilities.dp(10);
                    checkBox.setLayoutParams(layoutParams);
                }
            }
        } else {
            if (delegate != null) {
                delegate.didSelectDialog(MessagesActivity.this, dialog_id, param);
                delegate = null;
            } else {
                finishFragment();
            }
        }
    }

    public void setSearchQuery(String query) {
        currentSearch = query;
    }

    @Override
    public boolean onBackPressed() {
        if (searching) {
            currentSearch = "";
        }
        return super.onBackPressed();
    }
}
