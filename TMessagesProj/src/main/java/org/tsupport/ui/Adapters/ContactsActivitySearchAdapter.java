/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.tsupport.ui.Adapters;

import android.content.Context;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.tsupport.android.AndroidUtilities;
import org.tsupport.android.ContactsController;
import org.tsupport.android.LocaleController;
import org.tsupport.android.MessagesController;
import org.tsupport.messenger.ConnectionsManager;
import org.tsupport.messenger.FileLog;
import org.tsupport.messenger.R;
import org.tsupport.messenger.RPCRequest;
import org.tsupport.messenger.TLObject;
import org.tsupport.messenger.TLRPC;
import org.tsupport.messenger.UserConfig;
import org.tsupport.messenger.Utilities;
import org.tsupport.ui.Cells.ChatOrUserCell;
import org.tsupport.messenger.FileLog;
import org.tsupport.android.MessagesController;
import org.tsupport.messenger.UserConfig;
import org.tsupport.messenger.Utilities;
import org.tsupport.ui.Cells.ChatOrUserCell;
import org.tsupport.ui.Views.SettingsSectionLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class ContactsActivitySearchAdapter extends BaseContactsSearchAdapter {
    private Context mContext;
    private HashMap<Integer, TLRPC.User> ignoreUsers;
    private Timer searchTimer;
    private boolean allowUsernameSearch;

    public ContactsActivitySearchAdapter(Context context, HashMap<Integer, TLRPC.User> arg1, boolean usernameSearch) {
        mContext = context;
        ignoreUsers = arg1;
        allowUsernameSearch = usernameSearch;
    }

    public void searchDialogs(final String query) {
       // Disabled
    }

    private void processSearch(final String query) {
        // Disabled
    }

    private void updateSearchResults(final ArrayList<TLRPC.User> users, final ArrayList<CharSequence> names) {
        // Disabled
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int i) {
        return i != MessagesController.getInstance().usersSearched.size();
    }

    @Override
    public int getCount() {
        int count = MessagesController.getInstance().usersSearched.size();
        int globalCount = MessagesController.getInstance().globalSearched.size();
        if (globalCount != 0) {
            count += globalCount + 1;
        }
        return count;
    }

    public boolean isGlobalSearch(int i) {
        int localCount = MessagesController.getInstance().usersSearched.size();
        int globalCount = MessagesController.getInstance().globalSearched.size();
        if (i >= 0 && i < localCount) {
            return false;
        } else if (i > localCount && i <= globalCount + localCount) {
            return true;
        }
        return false;
    }

    @Override
    public TLRPC.User getItem(int i) {
        int localCount = MessagesController.getInstance().usersSearched.size();
        int globalCount = MessagesController.getInstance().globalSearched.size();
        if (i >= 0 && i < localCount) {
            return MessagesController.getInstance().usersSearched.get(i);
        } else if (i > localCount && i <= globalCount + localCount) {
            return MessagesController.getInstance().globalSearched.get(i - localCount - 1);
        }
        return null;
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
        if (i == MessagesController.getInstance().usersSearched.size()) {
            if (view == null) {
                view = new SettingsSectionLayout(mContext);
                ((SettingsSectionLayout) view).setText(LocaleController.getString("GlobalSearch", R.string.GlobalSearch));
            }
        } else {
            if (view == null) {
                view = new ChatOrUserCell(mContext);
                ((ChatOrUserCell) view).usePadding = false;
            }

            ((ChatOrUserCell) view).useSeparator = (i != getCount() - 1 && i != MessagesController.getInstance().usersSearched.size() - 1);
            TLRPC.User user = getItem(i);
            if (user != null) {
                CharSequence username = null;
                CharSequence name = null;
                if (i < MessagesController.getInstance().usersSearched.size()) {
                    name = Utilities.generateSearchName(user.first_name, user.last_name, "");
                    if (name != null && user != null && user.username != null && user.username.length() > 0) {
                        if (name.toString().startsWith("@" + user.username)) {
                            username = name;
                            name = null;
                        }
                    }
                } else if (i > MessagesController.getInstance().usersSearched.size() && user.username != null) {
                    try {
                        username = Html.fromHtml(String.format("<font color=\"#357aa8\">@%s</font>%s", user.username.substring(0, lastFoundUsername.length()), user.username.substring(lastFoundUsername.length())));
                    } catch (Exception e) {
                        username = user.username;
                        FileLog.e("tmessages", e);
                    }
                }

                ((ChatOrUserCell) view).setData(user, null, null, name, username);

                if (ignoreUsers != null) {
                    if (ignoreUsers.containsKey(user.id)) {
                        ((ChatOrUserCell) view).drawAlpha = 0.5f;
                    } else {
                        ((ChatOrUserCell) view).drawAlpha = 1.0f;
                    }
                }
            }
        }
        return view;
    }

    @Override
    public int getItemViewType(int i) {
        if (i == MessagesController.getInstance().usersSearched.size()) {
            return 1;
        }
        return 0;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public boolean isEmpty() {
        return MessagesController.getInstance().usersSearched.isEmpty() && MessagesController.getInstance().globalSearched.isEmpty();
    }
}
