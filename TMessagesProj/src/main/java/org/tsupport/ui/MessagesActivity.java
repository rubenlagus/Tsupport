/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.tsupport.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
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

import org.tsupport.android.AndroidUtilities;
import org.tsupport.android.ContactsController;
import org.tsupport.android.LocaleController;
import org.tsupport.android.MessagesController;
import org.tsupport.android.MessagesStorage;
import org.tsupport.messenger.FileLog;
import org.tsupport.messenger.NotificationCenter;
import org.tsupport.messenger.R;
import org.tsupport.messenger.TLObject;
import org.tsupport.messenger.TLRPC;
import org.tsupport.messenger.UserConfig;
import org.tsupport.messenger.Utilities;
import org.tsupport.ui.Adapters.BaseFragmentAdapter;
import org.tsupport.ui.Cells.ChatOrUserCell;
import org.tsupport.ui.Cells.DialogCell;
import org.tsupport.ui.Views.ActionBar.ActionBarLayer;
import org.tsupport.ui.Views.ActionBar.ActionBarMenu;
import org.tsupport.ui.Views.ActionBar.ActionBarMenuItem;
import org.tsupport.ui.Views.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MessagesActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private ListView messagesListView;
    private MessagesAdapter messagesListViewAdapter;
    private TextView searchEmptyView;
    private View progressView;
    private View empryView;
    private String selectAlertString;
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
    private static int searchType = 0; // 0=nothing 1=messages 2=users

    private Timer searchTimer;

    private MessagesActivityDelegate delegate;

    // Remove unneeded options
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
        NotificationCenter.getInstance().addObserver(this, MessagesController.dialogsNeedReload);
        NotificationCenter.getInstance().addObserver(this, 999);
        NotificationCenter.getInstance().addObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, MessagesController.reloadSearchResults);
        NotificationCenter.getInstance().addObserver(this, MessagesController.encryptedChatUpdated);
        NotificationCenter.getInstance().addObserver(this, MessagesController.contactsDidLoaded);
        NotificationCenter.getInstance().addObserver(this, MessagesController.reloadSearchChatResults);
        NotificationCenter.getInstance().addObserver(this, MessagesController.reloadSearchUserResults);
        NotificationCenter.getInstance().addObserver(this, 1234);
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
            ContactsController.getInstance().checkAppAccount();
            dialogsLoaded = true;
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, MessagesController.dialogsNeedReload);
        NotificationCenter.getInstance().removeObserver(this, 999);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.reloadSearchResults);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.encryptedChatUpdated);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.contactsDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.reloadSearchChatResults);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.reloadSearchUserResults);
        NotificationCenter.getInstance().removeObserver(this, 1234);
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
                    if (messagesListView != null) {
                        messagesListView.setEmptyView(searchEmptyView);
                    }
                    if (empryView != null) {
                        empryView.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onSearchCollapse() {
                    searchDialogs(null, 0);
                    searching = false;
                    searchWas = false;
                    refreshItem.setVisibility(View.VISIBLE);
                    searchUserItem.setVisibility(View.GONE);
                    searchMessagesItem.setVisibility(View.GONE);
                    if (messagesListView != null) {
                        messagesListView.setEmptyView(empryView);
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
                        searchDialogs(searchQuery, 1);
                        if (searchQuery.length() != 0) {
                            searchWas = true;
                            requestSearch = true;
                            if (messagesListViewAdapter != null) {
                                messagesListViewAdapter.notifyDataSetChanged();
                            }
                            if (searchEmptyView != null) {
                                messagesListView.setEmptyView(searchEmptyView);
                                empryView.setVisibility(View.GONE);
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
                //item.addSubItem(messages_list_menu_new_chat, LocaleController.getString("NewGroup", R.string.NewGroup), 0);
                //item.addSubItem(messages_list_menu_new_secret_chat, LocaleController.getString("NewSecretChat", R.string.NewSecretChat), 0);
                //item.addSubItem(messages_list_menu_new_broadcast, LocaleController.getString("NewBroadcastList", R.string.NewBroadcastList), 0);
                //item.addSubItem(messages_list_menu_contacts, LocaleController.getString("Contacts", R.string.Contacts), 0);
                item.addSubItem(messages_list_menu_settings, LocaleController.getString("Settings", R.string.Settings), 0);
            }
            actionBarLayer.setBackOverlay(R.layout.updating_state_layout);

            actionBarLayer.setActionBarMenuOnItemClick(new ActionBarLayer.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == messages_list_menu_settings) {
                        presentFragment(new SettingsActivity());
                    } else if (id == messages_list_menu_refresh) {
                        NotificationCenter.getInstance().postNotificationName(MessagesController.dialogsNeedReload);
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
                        searchDialogs(searchQuery, 2);
                        if (searchQuery.length() != 0) {
                            searchWas = true;
                            requestSearch = true;
                            if (messagesListViewAdapter != null) {
                                messagesListViewAdapter.notifyDataSetChanged();
                            }
                            if (searchEmptyView != null) {
                                messagesListView.setEmptyView(searchEmptyView);
                                empryView.setVisibility(View.GONE);
                            }
                        }
                    } else if(id == search_list_messages) {
                        searchUserItem.setVisibility(View.VISIBLE);
                        searchMessagesItem.setVisibility(View.GONE);
                        searchEmptyView.setText(LocaleController.getString("searching", R.string.searching));
                        searchDialogs(searchQuery, 1);
                        if (searchQuery.length() != 0) {
                            searchWas = true;
                            requestSearch = true;
                            if (messagesListViewAdapter != null) {
                                messagesListViewAdapter.notifyDataSetChanged();
                            }
                            if (searchEmptyView != null) {
                                messagesListView.setEmptyView(searchEmptyView);
                                empryView.setVisibility(View.GONE);
                            }
                        }
                    }
                    /*else if (id == messages_list_menu_new_messages) {
                        Bundle args = new Bundle();
                        args.putBoolean("onlyUsers", true);
                        args.putBoolean("destroyAfterSelect", true);
                        args.putBoolean("usersAsSections", true);
                        presentFragment(new ContactsActivity(args));
                    } else if (id == messages_list_menu_new_secret_chat) {
                        Bundle args = new Bundle();
                        args.putBoolean("onlyUsers", true);
                        args.putBoolean("destroyAfterSelect", true);
                        args.putBoolean("usersAsSections", true);
                        args.putBoolean("createSecretChat", true);
                        presentFragment(new ContactsActivity(args));
                    }*//* else if (id == messages_list_menu_new_chat) {
                        presentFragment(new GroupCreateActivity());
                    }*/ else if (id == -1) {
                        if (onlySelect) {
                            finishFragment();
                        }
                    } /*else if (id == messages_list_menu_new_broadcast) {
                        Bundle args = new Bundle();
                        args.putBoolean("broadcast", true);
                        presentFragment(new GroupCreateActivity(args));
                    }*/
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
            searchEmptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
            empryView = fragmentView.findViewById(R.id.list_empty_view);
            TextView textView = (TextView)fragmentView.findViewById(R.id.list_empty_view_text1);
            textView.setText(LocaleController.getString("NoChats", R.string.NoChats));
            textView = (TextView)fragmentView.findViewById(R.id.list_empty_view_text2);
            textView.setText(LocaleController.getString("NoChats", R.string.NoChatsHelp));

            if (MessagesController.getInstance().loadingDialogs && MessagesController.getInstance().dialogs.isEmpty()) {
                messagesListView.setEmptyView(null);
                searchEmptyView.setVisibility(View.GONE);
                empryView.setVisibility(View.GONE);
                progressView.setVisibility(View.VISIBLE);
            } else {
                if (searching && searchWas) {
                    searchEmptyView.setText(LocaleController.getString("searching", R.string.searching));
                    messagesListView.setEmptyView(searchEmptyView);
                    empryView.setVisibility(View.GONE);
                } else {
                    messagesListView.setEmptyView(empryView);
                    searchEmptyView.setVisibility(View.GONE);
                }
                progressView.setVisibility(View.GONE);
            }

            messagesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (searching && searchWas) {
                        Bundle args = new Bundle();
                        if (searchType == 1) {
                            if (i >= MessagesController.getInstance().usersFromSearchOrdered.size()) {
                                return;
                            }
                            args.putInt("user_id", MessagesController.getInstance().usersFromSearchOrdered.get(i).id);
                        } else if (searchType == 2) {
                            args.putInt("user_id", MessagesController.getInstance().usersSearched.get(i).id);
                        } else {
                            return;
                        }
                        args.putString("query", searchQuery);
                        ChatActivity chatActivity = new ChatActivity(args);
                        chatActivity.setDelegate(MessagesActivity.this);
                        presentFragment(new ChatActivity(args));
                    } else {
                        long dialog_id = 0;
                        if (serverOnly) {
                            if (i >= MessagesController.getInstance().dialogsServerOnly.size()) {
                                return;
                            }
                            TLRPC.TL_dialog dialog = MessagesController.getInstance().dialogsServerOnly.get(i);
                            dialog_id = dialog.id;
                        } else {
                            if (i >= MessagesController.getInstance().dialogs.size()) {
                                return;
                            }
                            TLRPC.TL_dialog dialog = MessagesController.getInstance().dialogs.get(i);
                            dialog_id = dialog.id;
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
                            ChatActivity chatActivity = new ChatActivity(args);
                            chatActivity.setDelegate(MessagesActivity.this);
                            presentFragment(chatActivity);
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
                                            MessagesController.getInstance().deleteUserFromChat((int) -selectedDialog, MessagesController.getInstance().users.get(UserConfig.getClientUserId()), null);
                                            MessagesController.getInstance().deleteDialog(selectedDialog, 0, false);
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
                                    searchDialogs(searchQuery, searchType);
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
            Utilities.RunOnUIThread(new Runnable() {
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
                    if (searchQuery.contains("#")) {
                        searchUserItem.setVisibility(View.GONE);
                        searchMessagesItem.setVisibility(View.GONE);
                        refreshItem.setVisibility(View.GONE);
                    }
                    if (previousSearch.compareToIgnoreCase("") == 0) {
                        if (searchType == 1) {
                            searchDialogs(searchQuery, 1);
                        } else if (searchType == 2) {
                            searchDialogs(searchQuery, 2);
                        } else {
                            searchDialogs(searchQuery, 1);
                        }
                        if (searchQuery.length() != 0) {
                            searchWas = true;
                            if (messagesListViewAdapter != null) {
                                messagesListViewAdapter.notifyDataSetChanged();
                            }
                            if (searchEmptyView != null) {

                                messagesListView.setEmptyView(searchEmptyView);
                                empryView.setVisibility(View.GONE);
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
        if (id == MessagesController.dialogsNeedReload) {
            if (messagesListViewAdapter != null) {
                messagesListViewAdapter.notifyDataSetChanged();
            }
            if (messagesListView != null) {
                if (MessagesController.getInstance().loadingDialogs && MessagesController.getInstance().dialogs.isEmpty()) {
                    if (messagesListView.getEmptyView() != null) {
                        messagesListView.setEmptyView(null);
                    }
                    searchEmptyView.setVisibility(View.GONE);
                    empryView.setVisibility(View.GONE);
                    progressView.setVisibility(View.VISIBLE);
                } else {
                    if (messagesListView.getEmptyView() == null) {
                        if (searching && searchWas) {
                            messagesListView.setEmptyView(searchEmptyView);
                            empryView.setVisibility(View.GONE);
                        } else {
                            messagesListView.setEmptyView(empryView);
                            searchEmptyView.setVisibility(View.GONE);
                        }
                    }
                    progressView.setVisibility(View.GONE);
                }
            }
        } else if (id == 999) {
            if (messagesListView != null) {
                updateVisibleRows(0);
            }
        } else if (id == MessagesController.updateInterfaces) {
            updateVisibleRows(0);
        } else if (id == MessagesController.reloadSearchResults) {
            int token = (Integer)args[0];
            if (token == activityToken) {
                updateSearchResults((ArrayList<TLObject>) args[1], (ArrayList<CharSequence>) args[2], (ArrayList<TLRPC.User>) args[3]);
            }
        } else if (id == 1234) {
            dialogsLoaded = false;
        } else if (id == MessagesController.encryptedChatUpdated) {
            updateVisibleRows(0);
        } else if (id == MessagesController.contactsDidLoaded) {
            updateVisibleRows(0);
        } else if (id == MessagesController.reloadSearchChatResults) {
            int token = (Integer)args[0];
            if (token == activityToken) {
                final TLRPC.messages_Messages result = (TLRPC.messages_Messages) args[1];
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (MessagesController.getInstance().usersFromSearchOrdered.size() <= 0) {
                            FileLog.d("tsupportSearch", "No results");
                            searchEmptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
                        } else {
                            FileLog.d("tsupportSearch", "Yes Results: " + result.count);
                            searchWas = true;
                            searching = true;
                        }
                        searchUserItem.setVisibility(View.VISIBLE);
                        searchMessagesItem.setVisibility(View.GONE);
                        messagesListViewAdapter.notifyDataSetChanged();
                    }
                });
            }
        } else if (id == MessagesController.reloadSearchUserResults) {
            int token = (Integer)args[0];
            if (token == activityToken) {
                Utilities.RunOnUIThread(new Runnable() {
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
                ((DialogCell) child).update(mask);
            } else if (child instanceof ChatOrUserCell) {
                ((ChatOrUserCell) child).update(mask);
            }
        }
    }

    public void setDelegate(MessagesActivityDelegate delegate) {
        this.delegate = delegate;
    }

    private void didSelectResult(final long dialog_id, boolean useAlert, final boolean param) {
        if (useAlert && selectAlertString != null) {
            if (getParentActivity() == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            int lower_part = (int)dialog_id;
            int high_id = (int)(dialog_id >> 32);
            if (lower_part != 0) {
                if (high_id == 1) {
                    TLRPC.Chat chat = MessagesController.getInstance().chats.get(lower_part);
                    if (chat == null) {
                        return;
                    }
                    builder.setMessage(LocaleController.formatStringSimple(selectAlertString, chat.title));
                } else {
                    if (lower_part > 0) {
                        TLRPC.User user = MessagesController.getInstance().users.get(lower_part);
                        if (user == null) {
                            return;
                        }
                        builder.setMessage(LocaleController.formatStringSimple(selectAlertString, Utilities.formatName(user.first_name, user.last_name)));
                    } else if (lower_part < 0) {
                        TLRPC.Chat chat = MessagesController.getInstance().chats.get(-lower_part);
                        if (chat == null) {
                            return;
                        }
                        builder.setMessage(LocaleController.formatStringSimple(selectAlertString, chat.title));
                    }
                }
            } else {
                TLRPC.EncryptedChat chat = MessagesController.getInstance().encryptedChats.get(high_id);
                TLRPC.User user = MessagesController.getInstance().users.get(chat.user_id);
                if (user == null) {
                    return;
                }
                builder.setMessage(LocaleController.formatStringSimple(selectAlertString, Utilities.formatName(user.first_name, user.last_name)));
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

    public void updateSearchResults(final ArrayList<TLObject> result, final ArrayList<CharSequence> names, final ArrayList<TLRPC.User> encUsers) {
//        Utilities.RunOnUIThread(new Runnable() {
//            @Override
//            public void run() {
//                for (TLObject obj : result) {
//                    if (obj instanceof TLRPC.User) {
//                        TLRPC.User user = (TLRPC.User) obj;
//                        MessagesController.getInstance().users.putIfAbsent(user.id, user);
//                    } else if (obj instanceof TLRPC.Chat) {
//                        TLRPC.Chat chat = (TLRPC.Chat) obj;
//                        MessagesController.getInstance().chats.putIfAbsent(chat.id, chat);
//                    } else if (obj instanceof TLRPC.EncryptedChat) {
//                        TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat) obj;
//                        MessagesController.getInstance().encryptedChats.putIfAbsent(chat.id, chat);
//                    }
//                }
//                for (TLRPC.User user : encUsers) {
//                    MessagesController.getInstance().users.putIfAbsent(user.id, user);
//                }
//                searchResult = result;
//                searchResultNames = names;
//                if (searching) {
//                    messagesListViewAdapter.notifyDataSetChanged();
//                }
//            }
//        });
    }

    public void searchDialogs(final String query, final int type) {
        if (query == null || query.compareToIgnoreCase(previousSearch) != 0 || query.compareToIgnoreCase("") == 0 || searchType == 0) {
            MessagesController.getInstance().searchOffset = 0;
            MessagesController.getInstance().canContinueSearch = true;
            return;
        }

        if (type == 1) {
            if (searchQuery == null || query == null) {
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
            }
        } else if (type == 2) {
            if (searchQuery == null || query == null) {
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
                    }
                }, 1000, 1000);
            }
        }
    }

    private class MessagesAdapter extends BaseFragmentAdapter {
        private Context mContext;

        public MessagesAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int i) {
            return true;
        }

        @Override
        public int getCount() {
            if (searching && searchWas) {
                if (searchType == 1) {
                    if (MessagesController.getInstance().usersFromSearch == null || MessagesController.getInstance().usersFromSearch.size() <= 0) {
                        return 0;
                    }
                    return MessagesController.getInstance().usersFromSearchOrdered.size();
                } else if (searchType == 2) {
                    if (MessagesController.getInstance().usersSearched == null || MessagesController.getInstance().usersSearched.size() <= 0) {
                        return 0;
                    }
                    return MessagesController.getInstance().usersSearched.size();
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
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            if (searching && searchWas) {
                if (view == null) {
                    view = new ChatOrUserCell(mContext);
                }
                TLRPC.User user = null;
                TLRPC.Chat chat = null;
                CharSequence name = null;
                if (searchType == 1) { // Searching messages
                    if (i >= MessagesController.getInstance().usersFromSearchOrdered.size()) {
                        return view;
                    }
                    user = MessagesController.getInstance().usersFromSearchOrdered.get(i);
                    name = Utilities.generateSearchName(user.first_name, user.last_name, searchQuery);
                } else if (searchType == 2) { // Searching users
                    if (i >= MessagesController.getInstance().usersSearched.size()) {
                        return view;
                    }
                    user = MessagesController.getInstance().usersSearched.get(i);
                    name = MessagesController.getInstance().usersSearchedNames.get(i);
                } else { // Other
                    return view;
                }
                ((ChatOrUserCell)view).setData(user, chat, null, name, null);
                return view;
            }
            int type = getItemViewType(i);
            if (type == 1) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.loading_more_layout, viewGroup, false);
                }
                return view;
            }

            if (view == null) {
                view = new DialogCell(mContext);
            }
            if (serverOnly) {
                ((DialogCell)view).setDialog(MessagesController.getInstance().dialogsServerOnly.get(i));
            } else {
                ((DialogCell)view).setDialog(MessagesController.getInstance().dialogs.get(i));
            }

            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (searching && searchWas) {
                return 2;
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
                    return MessagesController.getInstance().usersSearched == null || MessagesController.getInstance().usersSearched.size() <= 0;
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
