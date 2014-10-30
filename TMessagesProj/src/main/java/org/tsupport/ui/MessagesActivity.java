/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.tsupport.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.appspot.tsupport_android.users.model.User;

import org.tsupport.android.AndroidUtilities;
import org.tsupport.android.ContactsController;
import org.tsupport.android.LocaleController;
import org.tsupport.android.MessageObject;
import org.tsupport.android.MessagesController;
import org.tsupport.android.MessagesStorage;
import org.tsupport.android.NotificationCenter;
import org.tsupport.android.TrelloSupport;
import org.tsupport.messenger.FileLog;
import org.tsupport.messenger.R;
import org.tsupport.messenger.TLObject;
import org.tsupport.messenger.TLRPC;
import org.tsupport.messenger.UserConfig;
import org.tsupport.messenger.Utilities;
import org.tsupport.ui.Adapters.BaseContactsSearchAdapter;
import org.tsupport.ui.Adapters.BaseFragmentAdapter;
import org.tsupport.ui.Cells.ChatOrUserCell;
import org.tsupport.ui.Cells.DialogCell;
import org.tsupport.ui.Views.ActionBar.ActionBarLayer;
import org.tsupport.ui.Views.ActionBar.ActionBarMenu;
import org.tsupport.ui.Views.ActionBar.ActionBarMenuItem;
import org.tsupport.ui.Views.ActionBar.BaseFragment;
import org.tsupport.ui.Views.SettingsSectionLayout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MessagesActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private ListView messagesListView;
    private MessagesAdapter messagesListViewAdapter;
    private TextView searchEmptyView;
    private View progressView;
    private View emptyView;
    private String selectAlertString;
    private String selectAlertStringGroup;
    private boolean serverOnly = false;
    private ActionBarMenuItem searchUserItem;
    private ActionBarMenuItem searchMessagesItem;
    private ActionBarMenuItem refreshItem;

    private static boolean dialogsLoaded = false;
    private boolean searching = false;
    private boolean searchWas = false;
    private boolean onlySelect = false;
    private int activityToken = (int)(Utilities.random.nextDouble() * Integer.MAX_VALUE);
    private long selectedDialog;
    private static boolean requestSearch = false;
    private static String searchQuery = "";
    private static String previousSearch = "";
    private static Integer searchType = 0; // 0=nothing 1=messages 2=users

    private MessagesActivityDelegate delegate;

    private long openedDialogId = 0;
    private final static int messages_list_menu_refresh = 1;
    private final static int search_list_users = 2;
    private final static int search_list_messages = 3;
    /*private final static int messages_list_menu_new_chat = 2;
    private final static int messages_list_menu_other = 6;
    private final static int messages_list_menu_new_secret_chat = 3;
    private final static int messages_list_menu_contacts = 4;*/
    private final static int messages_list_menu_settings = 5;
    //private final static int messages_list_menu_new_broadcast = 6;

    public static interface MessagesActivityDelegate {
        public abstract void didSelectDialog(MessagesActivity fragment, long dialog_id, boolean param);
    }

    public static interface MessagesActivitySearchDelegate {
        public abstract void setSearchQuery(String query);
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
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.reloadSearchResults);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.encryptedChatUpdated);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.contactsDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.appDidLogout);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.openedChatChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.reloadSearchChatResults);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.reloadSearchUserResults);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagesRead);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.readChatNotification);

        if (getArguments() != null) {
            if (arguments.containsKey("query")) {
                requestSearch = true;
                searchQuery = arguments.getString("query");
            } else {
                requestSearch = false;
                searchQuery = "";
                onlySelect = arguments.getBoolean("onlySelect", false);
                serverOnly = arguments.getBoolean("serverOnly", false);
                selectAlertString = arguments.getString("selectAlertString");
            }
        } else {
            requestSearch = false;
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
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.reloadSearchResults);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.encryptedChatUpdated);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.contactsDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.appDidLogout);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.openedChatChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.reloadSearchChatResults);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.reloadSearchUserResults);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagesRead);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.readChatNotification);
        delegate = null;
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            final ActionBarMenu menu = actionBarLayer.createMenu();
            menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                @Override
                public void onSearchExpand() {
                    searching = true;
                    requestSearch = true;
                    refreshItem.setVisibility(View.GONE);
                    searchUserItem.setVisibility(View.VISIBLE);
                    searchMessagesItem.setVisibility(View.GONE);
                    if (messagesListView != null) {
                        messagesListView.setEmptyView(searchEmptyView);
                    }
                    if (emptyView != null) {
                        emptyView.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onSearchCollapse() {
                    messagesListViewAdapter.searchDialogs(null, 0);
                    searching = false;
                    searchWas = false;
                    refreshItem.setVisibility(View.VISIBLE);
                    searchUserItem.setVisibility(View.GONE);
                    searchMessagesItem.setVisibility(View.GONE);
                    if (messagesListView != null) {
                        messagesListView.setEmptyView(emptyView);
                        searchEmptyView.setVisibility(View.GONE);
                    }
                    if (messagesListViewAdapter != null) {
                        messagesListViewAdapter.notifyDataSetChanged();
                    }
                }

                @Override
                public void onTextChanged(EditText editText) {
                    searching = true;
                    requestSearch = true;
                }
            });
            menu.getItem(0).getSearchField().setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if ( EditorInfo.IME_NULL == actionId || ( event != null && event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_SEARCH)) {
                        searchEmptyView.setText(LocaleController.getString("searching", R.string.searching));
                        refreshItem.setVisibility(View.GONE);
                        searchQuery = v.getText().toString();
                        messagesListViewAdapter.searchDialogs(searchQuery, 1);
                        if (searchQuery.length() != 0) {
                            searchWas = true;
                            requestSearch = true;
                            if (messagesListViewAdapter != null) {
                                messagesListViewAdapter.notifyDataSetChanged();
                            }
                            if (searchEmptyView != null) {
                                messagesListView.setEmptyView(searchEmptyView);
                                emptyView.setVisibility(View.GONE);
                            }
                        }
                    }
                    return false;
                }
            });

            if (onlySelect) {
                actionBarLayer.setDisplayHomeAsUpEnabled(true, R.drawable.ic_ab_back);
                actionBarLayer.setTitle(LocaleController.getString("SelectChat", R.string.SelectChat));
            } else {
                actionBarLayer.setDisplayUseLogoEnabled(true, R.drawable.ic_ab_logo);
                actionBarLayer.setTitle(LocaleController.getString("AppName", R.string.AppName));
                refreshItem = menu.addItem(messages_list_menu_refresh, R.drawable.ic_refresh);
                searchUserItem = menu.addItem(search_list_users, R.drawable.grouplist);
                searchMessagesItem = menu.addItem(search_list_messages, R.drawable.ic_profile_send_message);
                searchUserItem.setVisibility(View.GONE);
                searchMessagesItem.setVisibility(View.GONE);
                ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_other);
                item.addSubItem(messages_list_menu_settings, LocaleController.getString("Settings", R.string.Settings), 0);
            }
            actionBarLayer.setBackOverlay(R.layout.updating_state_layout);

            actionBarLayer.setActionBarMenuOnItemClick(new ActionBarLayer.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == messages_list_menu_settings) {
                        presentFragment(new SettingsActivity());
                    } else if (id == messages_list_menu_refresh) {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                        MessagesController.getInstance().loadDialogs(0, 0, 110, false);
                        MessagesController.getInstance().getDifference();
                        MessagesController.getInstance().reloadDialogs();
                        Toast.makeText( getParentActivity().getApplicationContext(), LocaleController.getString("searching", R.string.searching) , Toast.LENGTH_SHORT).show();
                        if (messagesListViewAdapter != null) {
                            messagesListViewAdapter.notifyDataSetChanged();
                        }
                    } else if(id == search_list_users) {
                        searchMessagesItem.setVisibility(View.VISIBLE);
                        searchUserItem.setVisibility(View.GONE);
                        searchEmptyView.setText(LocaleController.getString("searching", R.string.searching));
                        messagesListViewAdapter.searchDialogs(searchQuery, 2);
                        Toast.makeText( getParentActivity().getApplicationContext(), LocaleController.getString("searching", R.string.searching) , Toast.LENGTH_SHORT).show();
                        if (searchQuery.length() != 0) {
                            searchWas = true;
                            requestSearch = true;
                            if (messagesListViewAdapter != null) {
                                messagesListViewAdapter.notifyDataSetChanged();
                            }
                            if (searchEmptyView != null) {
                                messagesListView.setEmptyView(searchEmptyView);
                                emptyView.setVisibility(View.GONE);
                            }
                        }
                    } else if(id == search_list_messages) {
                        searchUserItem.setVisibility(View.VISIBLE);
                        searchMessagesItem.setVisibility(View.GONE);
                        searchEmptyView.setText(LocaleController.getString("searching", R.string.searching));
                        messagesListViewAdapter.searchDialogs(searchQuery, 1);
                        Toast.makeText( getParentActivity().getApplicationContext(), LocaleController.getString("searching", R.string.searching) , Toast.LENGTH_SHORT).show();
                        if (searchQuery.length() != 0) {
                            searchWas = true;
                            requestSearch = true;
                            if (messagesListViewAdapter != null) {
                                messagesListViewAdapter.notifyDataSetChanged();
                            }
                            if (searchEmptyView != null) {
                                messagesListView.setEmptyView(searchEmptyView);
                                emptyView.setVisibility(View.GONE);
                            }
                        }
                    } else if (id == -1) {
                        if (onlySelect) {
                            finishFragment();
                        }
                    }
                }
            });

            if (actionBarLayer.backButtonFrameLayout != null) {
                actionBarLayer.backButtonFrameLayout.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean visible = false;
                        if (menu.getItem(0).isSearchFieldVisible()) {
                            visible = true;
                        }
                        requestSearch = false;
                        searchQuery = "";
                        previousSearch = "";
                        messagesListViewAdapter.searchDialogs(null, searchType);
                        actionBarLayer.onSearchFieldVisibilityChanged(menu.getItem(0).toggleSearch());
                        searchUserItem.setVisibility(View.GONE);
                        searchMessagesItem.setVisibility(View.GONE);
                        refreshItem.setVisibility(View.VISIBLE);
                        if (visible) {
                            actionBarLayer.titleTextView.setVisibility(View.VISIBLE);
                            actionBarLayer.backButtonImageView.setVisibility(View.GONE);
                        }
                    }
                });
            }

            searching = false;
            searchWas = false;

            fragmentView = inflater.inflate(R.layout.messages_list, container, false);

            messagesListViewAdapter = new MessagesAdapter(getParentActivity());

            messagesListView = (ListView)fragmentView.findViewById(R.id.messages_list_view);
            messagesListView.setAdapter(messagesListViewAdapter);

            progressView = fragmentView.findViewById(R.id.progressLayout);
            messagesListViewAdapter.notifyDataSetChanged();
            searchEmptyView = (TextView)fragmentView.findViewById(R.id.searchEmptyView);
            searchEmptyView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });
            searchEmptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
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
            textView.setText(LocaleController.getString("NoChats", R.string.NoChatsHelp));

            if (MessagesController.getInstance().loadingDialogs && MessagesController.getInstance().dialogs.isEmpty()) {
                messagesListView.setEmptyView(null);
                searchEmptyView.setVisibility(View.GONE);
                emptyView.setVisibility(View.GONE);
                progressView.setVisibility(View.VISIBLE);
            } else {
                if (searching && searchWas) {
                    searchEmptyView.setText(LocaleController.getString("searching", R.string.searching));
                    messagesListView.setEmptyView(searchEmptyView);
                    emptyView.setVisibility(View.GONE);
                } else {
                    messagesListView.setEmptyView(emptyView);
                    searchEmptyView.setVisibility(View.GONE);
                }
                progressView.setVisibility(View.GONE);
            }

            messagesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (messagesListViewAdapter == null) {
                        return;
                    }
                    TLObject obj = messagesListViewAdapter.getItem(i);
                    if (obj == null) {
                        return;
                    }
                    if (searching && searchWas) {
                        TLRPC.User user = (TLRPC.User) obj;
                        Bundle args = new Bundle();
                        args.putInt("user_id", user.id);
                        args.putString("query", searchQuery);
                        ChatActivity chatActivity = new ChatActivity(args);
                        chatActivity.setDelegate(MessagesActivity.this);
                        presentFragment(chatActivity);
                    } else {
                        long dialog_id = 0;
                        if (obj instanceof TLRPC.User) {
                            dialog_id = ((TLRPC.User) obj).id;
                            if (messagesListViewAdapter.isGlobalSearch(i)) {
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
                        } else if (obj instanceof TLRPC.TL_dialog) {
                            dialog_id = ((TLRPC.TL_dialog) obj).id;
                        }

                        if (onlySelect) {
                            didSelectResult(dialog_id, true, false);
                        } else {
                            Bundle args = new Bundle();
                            int lower_part = (int) dialog_id;
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
                            if (AndroidUtilities.isTablet()) {
                                if (openedDialogId == dialog_id) {
                                    return;
                                }
                                openedDialogId = dialog_id;
                            }
                            ChatActivity chatActivity = new ChatActivity(args);
                            chatActivity.setDelegate(MessagesActivity.this);
                            presentFragment(chatActivity);
                            updateVisibleRows(0);
                        }
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

                    if (lower_id < 0 && high_id != 1) {
                        builder.setItems(new CharSequence[]{LocaleController.getString("ClearHistory", R.string.ClearHistory), LocaleController.getString("DeleteChat", R.string.DeleteChat)}, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    MessagesController.getInstance().deleteDialog(selectedDialog, 0, true);
                                } else if (which == 1) {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                    builder.setMessage(LocaleController.getString("AreYouSureDeleteAndExit", R.string.AreYouSureDeleteAndExit));
                                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            MessagesController.getInstance().deleteUserFromChat((int) -selectedDialog, MessagesController.getInstance().getUser(UserConfig.getClientUserId()), null);
                                            MessagesController.getInstance().deleteDialog(selectedDialog, 0, false);
                                            if (AndroidUtilities.isTablet()) {
                                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats, selectedDialog);
                                            }
                                        }
                                    });
                                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                                    showAlertDialog(builder);
                                }
                            }
                        });
                    } else {
                        builder.setItems(new CharSequence[]{LocaleController.getString("ClearHistory", R.string.ClearHistory), LocaleController.getString("Delete", R.string.Delete)}, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    MessagesController.getInstance().deleteDialog(selectedDialog, 0, true);
                                } else {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                    builder.setMessage(LocaleController.getString("AreYouSureDeleteThisChat", R.string.AreYouSureDeleteThisChat));
                                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            MessagesController.getInstance().deleteDialog(selectedDialog, 0, false);
                                            if (AndroidUtilities.isTablet()) {
                                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats, selectedDialog);
                                            }
                                        }
                                    });
                                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                                    showAlertDialog(builder);
                                }
                            }
                        });
                    }
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
                    if (visibleItemCount > 0) {
                        if (searching && searchWas && searchType == 1) {
                            if (absListView.getLastVisiblePosition() == MessagesController.getInstance().usersFromSearch.size()-3) {
                                if (MessagesController.getInstance().canContinueSearch) {
                                    messagesListViewAdapter.searchDialogs(searchQuery, searchType);
                                } else {
                                    Toast.makeText( getParentActivity().getApplicationContext(), LocaleController.getString("NoMoreResults", R.string.NoMoreResults) , Toast.LENGTH_SHORT).show();
                                }
                            }
                        } else {
                            if (absListView.getLastVisiblePosition() == MessagesController.getInstance().dialogs.size() && !serverOnly || absListView.getLastVisiblePosition() == MessagesController.getInstance().dialogsServerOnly.size() && serverOnly) {
                                MessagesController.getInstance().loadDialogs(MessagesController.getInstance().dialogs.size(), MessagesController.getInstance().dialogsServerOnly.size(), 100, true);
                            }
                        }
                    }
                }
            });
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        if (requestSearch && searchQuery.compareToIgnoreCase("") != 0) {
            AndroidUtilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    final ActionBarMenu menu = actionBarLayer.createMenu();
                    menu.getItem(0).searchField.setVisibility(View.VISIBLE);
                    menu.getItem(0).setVisibility(View.GONE);
                    menu.getItem(0).searchField.requestFocus();
                    if (searchQuery.compareToIgnoreCase("") != 0) {
                        AndroidUtilities.showKeyboard(menu.getItem(0).searchField);
                    }
                    actionBarLayer.setDisplayUseLogoEnabled(false, R.drawable.ic_ab_logo);
                    actionBarLayer.setDisplayHomeAsUpEnabled(true, R.drawable.ic_ab_back);
                    actionBarLayer.onSearchFieldVisibilityChanged(true);

                    if (menu.getItem(0).listener != null)
                        menu.getItem(0).listener.onSearchExpand();
                    searching = true;
                    menu.getItem(0).searchField.setText(searchQuery);
                    refreshItem.setVisibility(View.GONE);
                    if (previousSearch.compareToIgnoreCase("") == 0) {
                        if (searchType == 1) {
                            messagesListViewAdapter.searchDialogs(searchQuery, 1);
                            searchUserItem.setVisibility(View.VISIBLE);
                            searchMessagesItem.setVisibility(View.GONE);
                        } else if (searchType == 2) {
                            messagesListViewAdapter.searchDialogs(searchQuery, 2);
                            searchUserItem.setVisibility(View.GONE);
                            searchMessagesItem.setVisibility(View.VISIBLE);
                        } else {
                            messagesListViewAdapter.searchDialogs(searchQuery, 1);
                            searchUserItem.setVisibility(View.VISIBLE);
                            searchMessagesItem.setVisibility(View.GONE);
                        }
                        if (searchQuery.length() != 0) {
                            searchWas = true;
                            if (messagesListViewAdapter != null) {
                                messagesListViewAdapter.notifyDataSetChanged();
                            }
                            if (searchEmptyView != null) {
                                messagesListView.setEmptyView(searchEmptyView);
                                emptyView.setVisibility(View.GONE);
                            }
                        }
                    } else {
                        if (MessagesController.getInstance().usersFromSearch.size() >= 0) {
                            searchType = 1;
                            searchUserItem.setVisibility(View.VISIBLE);
                            searchMessagesItem.setVisibility(View.GONE);
                        } else if (MessagesController.getInstance().usersSearched.size() >= 0) {
                            searchType = 2;
                            searchUserItem.setVisibility(View.GONE);
                            searchMessagesItem.setVisibility(View.VISIBLE);
                        }
                        if (searchQuery.length() != 0) {
                            searchWas = false;
                            if (messagesListViewAdapter != null) {
                                messagesListViewAdapter.notifyDataSetChanged();
                            }
                            if (searchEmptyView != null) {
                                messagesListView.setEmptyView(searchEmptyView);
                                emptyView.setVisibility(View.GONE);
                            }
                        }
                    }
                }
            });
        }
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        showActionBar();
        if (messagesListViewAdapter != null) {
            messagesListViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.dialogsNeedReload) {
            if (messagesListViewAdapter != null) {
                messagesListViewAdapter.notifyDataSetChanged();
            }
            if (messagesListView != null) {
                if (MessagesController.getInstance().loadingDialogs && MessagesController.getInstance().dialogs.isEmpty()) {
                    if (messagesListView.getEmptyView() != null) {
                        messagesListView.setEmptyView(null);
                    }
                    searchEmptyView.setVisibility(View.GONE);
                    emptyView.setVisibility(View.GONE);
                    progressView.setVisibility(View.VISIBLE);
                } else {
                    if (messagesListView.getEmptyView() == null) {
                        if (searching && searchWas) {
                            messagesListView.setEmptyView(searchEmptyView);
                            emptyView.setVisibility(View.GONE);
                        } else {
                            messagesListView.setEmptyView(emptyView);
                            searchEmptyView.setVisibility(View.GONE);
                        }
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
        } else if (id == NotificationCenter.reloadSearchResults) {
        } else if (id == NotificationCenter.messagesRead) {
            /*ArrayList<Integer> markAsReadMessages = (ArrayList<Integer>)args[0];
            boolean updated = false;
            for (Integer ids : markAsReadMessages) {
                MessagesController.getInstance().
                TLRPC.TL_dialog obj = MessagesController.getInstance().dialogs.get(ids);
                if (obj != null) {
                    if (obj.unread_count > 0) {
                        FileLog.d("tsupportRead", "set to 0");
                        obj.unread_count = 0;
                        MessagesController.getInstance().dialogs.add(ids, obj);
                    }
                    updated = true;
                }
                obj = MessagesController.getInstance().dialogsServerOnly.get(ids);
                if (obj != null) {
                    if (obj.unread_count > 0) {
                        FileLog.d("tsupportRead", "set to 0 server only");
                        obj.unread_count = 0;
                        MessagesController.getInstance().dialogsServerOnly.add(ids, obj);
                    }
                    updated = true;
                }
            }
            if (updated) {*/
                AndroidUtilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (messagesListViewAdapter != null) {
                            messagesListViewAdapter.notifyDataSetChanged();
                        }
                    }
                });
           // }
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
                updateVisibleRows(0);
            }
        } else if (id == NotificationCenter.reloadSearchChatResults) {
            int token = (Integer)args[0];
            if (token == activityToken) {
                AndroidUtilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (MessagesController.getInstance().usersFromSearch.size() <= 0) {
                            FileLog.d("tsupportSearch", "No results");
                            searchEmptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
                        } else {
                            FileLog.d("tsupportSearch", "Yes Results: " + MessagesController.getInstance().usersFromSearch.size());
                            searchWas = true;
                            searching = true;
                        }
                        searchUserItem.setVisibility(View.VISIBLE);
                        searchMessagesItem.setVisibility(View.GONE);
                        messagesListViewAdapter.notifyDataSetChanged();
                    }
                });
            }
        } else if (id == NotificationCenter.reloadSearchUserResults) {
            int token = (Integer)args[0];
            if (token == activityToken) {
                AndroidUtilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (MessagesController.getInstance().usersSearched.size() <= 0) {
                            FileLog.d("tsupportSearch", "No results");
                            searchEmptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
                        } else {
                            FileLog.d("tsupportSearch", "Yes Results: " + MessagesController.getInstance().usersSearched.size());
                            searchWas = true;
                            searching = true;
                        }
                        searchMessagesItem.setVisibility(View.VISIBLE);
                        searchUserItem.setVisibility(View.GONE);
                        messagesListViewAdapter.notifyDataSetChanged();
                    }
                });
            }
        } else if (id == NotificationCenter.readChatNotification) {

            AndroidUtilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (messagesListViewAdapter != null) {
                        messagesListViewAdapter.notifyDataSetChanged();
                    }
                }
            });
        }
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
                if (!serverOnly && AndroidUtilities.isTablet() && cell.getDialog() != null) {
                    if (cell.getDialog().id == openedDialogId) {
                        child.setBackgroundColor(0x0f000000);
                    } else {
                        child.setBackgroundColor(0);
                    }
                }
                cell.update(mask);
            } else if (child instanceof ChatOrUserCell) {
                ((ChatOrUserCell) child).update(mask);
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
        searchQuery = query;
        requestSearch = true;
    }

    private class MessagesAdapter extends BaseContactsSearchAdapter {
        private Context mContext;
        private Timer searchTimer;

        public MessagesAdapter(Context context) {
            mContext = context;
        }

        public boolean isGlobalSearch(int i) {
            if (searching && searchWas) {
                int localCount = MessagesController.getInstance().usersSearched.size();
                int globalCount = MessagesController.getInstance().globalSearched.size();
                if (i >= 0 && i < localCount) {
                    return false;
                } else if (i > localCount && i <= globalCount + localCount) {
                    return true;
                }
            }
            return false;
        }

        public void searchDialogs(final String query, final int type) {
            if (query == null || query.compareToIgnoreCase(previousSearch) != 0 || query.compareToIgnoreCase("") == 0 || searchType == 0) {
                FileLog.e("tsupportSearch", "clearing search");
                MessagesController.getInstance().searchOffset = 0;
                MessagesController.getInstance().canContinueSearch = true;
                MessagesController.getInstance().globalSearched.clear();
                MessagesController.getInstance().usersSearched.clear();
                MessagesController.getInstance().usersFromSearch.clear();
                if (query == null || type == 0 || query.compareToIgnoreCase("") == 0) {
                    FileLog.e("tsupportSearch", "Leaving search fast");
                    return;
                }
                notifyDataSetChanged();
            }

            if (type == 1) {
                try {
                    if (searchTimer != null) {
                        searchTimer.cancel();
                    }
                } catch (Exception e) {
                    FileLog.e("tsupport", e);
                }
                searchTimer = new Timer();
                searchTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            searchTimer.cancel();
                            searchTimer = null;
                        } catch (Exception e) {
                            FileLog.e("tsupport", e);
                        }
                        FileLog.e("tsupportSearch", "Calling search: " + query);
                        previousSearch = query;
                        searchType = type;
                        MessagesController.getInstance().searchDialogs(activityToken, query, classGuid);
                    }
                }, 1000, 1000);
            } else if (type == 2) {
                try {
                    if (searchTimer != null) {
                        searchTimer.cancel();
                    }
                } catch (Exception e) {
                    FileLog.e("tsupport", e);
                }
                searchTimer = new Timer();
                searchTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            searchTimer.cancel();
                            searchTimer = null;
                        } catch (Exception e) {
                            FileLog.e("tsupport", e);
                        }
                        FileLog.e("tsupportSearch", "Calling users search: " + query);
                        previousSearch = query;
                        searchType = type;
                        MessagesStorage.getInstance().searchDialogs(activityToken, query, classGuid);
                        AndroidUtilities.RunOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                queryServerSearch(query);
                            }
                        });
                    }
                }, 1000, 1000);
            }
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int i) {
            if (searchType == 1) {
                return !(searching && searchWas) || i != MessagesController.getInstance().usersFromSearch.size();
            } else {
                return !(searching && searchWas) || i != MessagesController.getInstance().usersSearched.size();
            }
        }

        @Override
        public int getCount() {
            if (searching && searchWas) {
                if (searchType == 1) {
                    if (MessagesController.getInstance().usersFromSearch == null || MessagesController.getInstance().usersFromSearch.size() <= 0) {
                        return 0;
                    }
                    return MessagesController.getInstance().usersFromSearch.size();
                } else if (searchType == 2) {
                    return (MessagesController.getInstance().usersSearched == null ? 0 : MessagesController.getInstance().usersSearched.size()) +
                            (MessagesController.getInstance().globalSearched == null ? 0 : MessagesController.getInstance().globalSearched.size()+1);
                } else {
                    return 0;
                }
            }
            int count;
            if (serverOnly) {
                count = MessagesController.getInstance().dialogsServerOnly.size();
            } else {
                count = MessagesController.getInstance().dialogs.size();
            }
            if (count == 0 && MessagesController.getInstance().loadingDialogs) {
                return 0;
            }
            if (!MessagesController.getInstance().dialogsEndReached) {
                count++;
            }
            return count;
        }

        @Override
        public TLObject getItem(int i) {
            if (searching && searchWas) {
                if (searchType == 1) {
                    if (i > MessagesController.getInstance().usersFromSearch.size()) {
                        return null;
                    }
                    return MessagesController.getInstance().usersFromSearch.get(i);
                } else if (searchType == 2) {
                    if (i >= MessagesController.getInstance().usersSearched.size()) {
                        int globalSearchIndex = i - MessagesController.getInstance().usersSearched.size();
                        if (globalSearchIndex < MessagesController.getInstance().globalSearched.size()) {
                            return MessagesController.getInstance().globalSearched.get(globalSearchIndex);
                        } else {
                            return null;
                        }
                    } else {
                        return MessagesController.getInstance().usersSearched.get(i);
                    }
                }
                return null;
            }
            if (serverOnly) {
                if (i < 0 || i >= MessagesController.getInstance().dialogsServerOnly.size()) {
                    return null;
                }
                return MessagesController.getInstance().dialogsServerOnly.get(i);
            } else {
                if (i < 0 || i >= MessagesController.getInstance().dialogs.size()) {
                    return null;
                }
                return MessagesController.getInstance().dialogs.get(i);
            }
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            int type = getItemViewType(i);
            if (type == 3) {
                if (view == null) {
                    view = new SettingsSectionLayout(mContext);
                    ((SettingsSectionLayout) view).setText(LocaleController.getString("GlobalSearch", R.string.GlobalSearch));
                    view.setPadding(AndroidUtilities.dp(11), 0, AndroidUtilities.dp(11), 0);
                }
            } else if (type == 2) {
                if (view == null) {
                    view = new ChatOrUserCell(mContext);
                }
                if (searching && searchWas) {
                    TLRPC.User user = null;
                    TLRPC.Chat chat = null;
                    TLRPC.EncryptedChat encryptedChat = null;
                    CharSequence username = null;
                    CharSequence name = null;

                    TLObject obj = getItem(i);
                    if (obj instanceof TLRPC.User) {
                        if (user == null) {
                            user = (TLRPC.User) obj;
                        }
                    }

                    if (searchType == 1 && user != null) {
                        ((ChatOrUserCell) view).useSeparator = (i != getCount() - 1 && i != MessagesController.getInstance().usersFromSearch.size() - 1);
                        if (i < MessagesController.getInstance().usersFromSearch.size()) {
                            name = Utilities.generateSearchName(user.first_name, user.last_name, searchQuery);
                            if (name != null && user != null && user.username != null && user.username.length() > 0) {
                                if (name.toString().startsWith("@" + user.username)) {
                                    username = name;
                                    name = null;
                                }
                            }
                        }
                    } else if (searchType == 2 && user != null) {
                        ((ChatOrUserCell) view).useSeparator = (i != getCount() - 1 && i != MessagesController.getInstance().usersSearched.size() - 1);
                        if (i < MessagesController.getInstance().usersSearched.size()) {
                            name = Utilities.generateSearchName(user.first_name, user.last_name, searchQuery);
                            if (name != null && user != null && user.username != null && user.username.length() > 0) {
                                if (name.toString().startsWith("@" + user.username)) {
                                    username = name;
                                    name = null;
                                }
                            }
                        } else if (i > MessagesController.getInstance().usersSearched.size() && user != null && user.username != null) {
                            try {
                                username = Html.fromHtml(String.format("<font color=\"#357aa8\">@%s</font>%s", user.username.substring(0, lastFoundUsername.length()), user.username.substring(lastFoundUsername.length())));
                            } catch (Exception e) {
                                username = user.username;
                                FileLog.e("tmessages", e);
                            }
                        }
                    }
                    ((ChatOrUserCell) view).setData(user, chat, encryptedChat, name, username);
                }
            } else if (type == 1) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.loading_more_layout, viewGroup, false);
                }
            } else if (type == 0) {
                if (view == null) {
                    view = new DialogCell(mContext);
                }
                ((DialogCell) view).useSeparator = (i != getCount() - 1);
                if (serverOnly) {
                    ((DialogCell) view).setDialog(MessagesController.getInstance().dialogsServerOnly.get(i));
                } else {
                    TLRPC.TL_dialog dialog = MessagesController.getInstance().dialogs.get(i);
                    if (AndroidUtilities.isTablet()) {
                        if (dialog.id == openedDialogId) {
                            view.setBackgroundColor(0x0f000000);
                        } else {
                            view.setBackgroundColor(0);
                        }
                    }
                    ((DialogCell) view).setDialog(dialog);
                }
            }

            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (searching && searchWas) {
                if (searchType == 1) {
                    if (i <= MessagesController.getInstance().usersFromSearch.size()) {
                        return 2;
                    } else {
                        return 0;
                    }
                } else if (searchType == 2) {
                    if (i == MessagesController.getInstance().usersSearched.size()) {
                        return 3;
                    }
                    return 2;
                }
            }
            if (serverOnly && i == MessagesController.getInstance().dialogsServerOnly.size() || !serverOnly && i == MessagesController.getInstance().dialogs.size()) {
                return 1;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 4;
        }

        @Override
        public boolean isEmpty() {
            if (searching && searchWas) {
                if (searchType == 1) {
                    return MessagesController.getInstance().usersFromSearch == null || MessagesController.getInstance().usersFromSearch.size() <= 0;
                } else if (searchType == 2) {
                    return (MessagesController.getInstance().usersSearched == null || MessagesController.getInstance().usersSearched.size() <= 0) &&
                            (MessagesController.getInstance().globalSearched == null || MessagesController.getInstance().globalSearched.size() <= 0);
                } else {
                    return true;
                }
            }
            if (MessagesController.getInstance().loadingDialogs && MessagesController.getInstance().dialogs.isEmpty()) {
                return false;
            }
            int count;
            if (serverOnly) {
                count = MessagesController.getInstance().dialogsServerOnly.size();
            } else {
                count = MessagesController.getInstance().dialogs.size();
            }
            if (count == 0 && MessagesController.getInstance().loadingDialogs) {
                return true;
            }
            if (!MessagesController.getInstance().dialogsEndReached) {
                count++;
            }
            return count == 0;
        }
    }

    /**
     *
     * @return
     */
    @Override
    public boolean onBackPressed() {
        if (searching || searchWas || requestSearch) {
            searching = searchWas = requestSearch = false;
            searchQuery = "";
            searchUserItem.setVisibility(View.GONE);
            searchMessagesItem.setVisibility(View.GONE);
        }
        return super.onBackPressed();
    }
}
