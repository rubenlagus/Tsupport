/*
 * This is the source code of Tsupport for Android v. 0.5.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Ruben Bermudez, 2014.
 */

package org.tsupport.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.tsupport.android.AndroidUtilities;
import org.tsupport.android.LocaleController;
import org.tsupport.android.MessagesController;
import org.tsupport.android.NotificationCenter;
import org.tsupport.android.TrelloSupport;
import org.tsupport.messenger.FileLog;
import org.tsupport.messenger.R;
import org.tsupport.ui.Adapters.BaseFragmentAdapter;
import org.tsupport.ui.Views.ActionBar.ActionBarLayer;
import org.tsupport.ui.Views.ActionBar.ActionBarMenu;
import org.tsupport.ui.Views.ActionBar.BaseFragment;

public class IssuesActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private ListView listView;
    private ListAdapter listViewAdapter;
    private boolean loading;
    private boolean loaded;
    private View progressView;
    private TextView emptyView;
    private IssueSelectActivityDelegate delegate;
    private boolean open = false;

    private final static int reload_default = 1;

    public static abstract interface IssueSelectActivityDelegate {
        public void didSelectIssue(IssuesActivity activity, String issueId, boolean open);
    }

    public IssuesActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        int value = arguments.getInt("open", 0);
        if (value == 0) {
            open = false;
        } else if (value == 1) {
            open = true;
        }
        super.onFragmentCreate();
        NotificationCenter.getInstance().addObserver(this,NotificationCenter.trelloLoaded);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.trelloLoaded);
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            ActionBarMenu menu = actionBarLayer.createMenu();
            actionBarLayer.setDisplayHomeAsUpEnabled(true, R.drawable.ic_ab_back);

            actionBarLayer.setBackOverlay(R.layout.updating_state_layout);
            if (open) {
                actionBarLayer.setTitle(LocaleController.getString("openIssues", R.string.openIssues));
            } else {
                actionBarLayer.setTitle(LocaleController.getString("SolvedIssues", R.string.SolvedIssues));
            }

            actionBarLayer.setActionBarMenuOnItemClick(new ActionBarLayer.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == reload_default) {
                        loading = true;
                        progressView.setVisibility(View.VISIBLE);
                        emptyView.setVisibility(View.GONE);
                        listView.setEmptyView(null);
                        TrelloSupport.getInstance().loadIssuesAsync();
                    }
                }
            });


            fragmentView = inflater.inflate(R.layout.settings_blocked_users_layout, container, false);
            listViewAdapter = new ListAdapter(getParentActivity());
            listView = (ListView)fragmentView.findViewById(R.id.listView);
            progressView = fragmentView.findViewById(R.id.progressLayout);
            emptyView = (TextView)fragmentView.findViewById(R.id.searchEmptyView);
            emptyView.setText(LocaleController.getString("noIssues", R.string.noIssues));
            if (loading) {
                progressView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
                listView.setEmptyView(null);
            } else {
                progressView.setVisibility(View.GONE);
                listView.setEmptyView(emptyView);
            }
            listView.setAdapter(listViewAdapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    final int index = i;
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    if (open) {
                        builder.setMessage(LocaleController.getString("AreYouSentIssue", R.string.AreYouSentIssue).replace("@title@", TrelloSupport.getInstance().openIssuesList.get(index).getValue()));
                    } else {
                        builder.setMessage(LocaleController.getString("AreYouSentClosedIssue", R.string.AreYouSentClosedIssue).replace("@title@", TrelloSupport.getInstance().closedIssuesList.get(index).getValue()));
                    }
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (open && i < TrelloSupport.getInstance().openIssuesList.size()) {
                                delegate.didSelectIssue(IssuesActivity.this, TrelloSupport.getInstance().openIssuesList.get(index).getKey(), open);
                            } else if (!open && i < TrelloSupport.getInstance().closedIssuesList.size()) {
                                delegate.didSelectIssue(IssuesActivity.this, TrelloSupport.getInstance().closedIssuesList.get(index).getKey(), open);
                            }
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showAlertDialog(builder);
                }
            });
            if (open) {
                if (TrelloSupport.getInstance().openIssuesList.size() > 0) {
                    AndroidUtilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            listViewAdapter.notifyDataSetChanged();
                        }
                    });
                } else {
                    loading = true;
                    progressView.setVisibility(View.VISIBLE);
                    emptyView.setVisibility(View.GONE);
                    listView.setEmptyView(null);
                    TrelloSupport.getInstance().loadIssuesAsync();
                }
            } else {
                if (TrelloSupport.getInstance().closedIssuesList.size() > 0) {
                    AndroidUtilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            listViewAdapter.notifyDataSetChanged();
                        }
                    });
                } else {
                    loading = true;
                    progressView.setVisibility(View.VISIBLE);
                    emptyView.setVisibility(View.GONE);
                    listView.setEmptyView(null);
                    TrelloSupport.getInstance().loadIssuesAsync();
                }
            }
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    public void setDelegate(IssueSelectActivityDelegate delegate) {
        this.delegate = delegate;
    }

    public IssueSelectActivityDelegate getDelegate() {
        return delegate;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.trelloLoaded) {
            AndroidUtilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    loading = false;
                    progressView.setVisibility(View.GONE);
                    listView.setEmptyView(emptyView);
                    listViewAdapter.notifyDataSetChanged();
                }
            });
;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            AndroidUtilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    listViewAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    private class ListAdapter extends BaseFragmentAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
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
            if (open) {
                return TrelloSupport.getInstance().openIssuesList.size();
            } else {
                return TrelloSupport.getInstance().closedIssuesList.size();
            }
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
            int type = getItemViewType(i);
            if (type == 0) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_template_row_layout, null);
                }
                if (open) {
                    FileLog.d("tsupportTrello", "Key to draw: " + TrelloSupport.getInstance().openIssuesList.get(i).getKey());
                    if (TrelloSupport.getInstance().openIssuesList.get(i) != null) {
                        TextView textView = (TextView) view.findViewById(R.id.info_text_view);
                        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                        textView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
                        textView.setEllipsize(TextUtils.TruncateAt.END);
                        textView.setMaxLines(2);
                        if (textView != null) {
                            try {
                                FileLog.d("tsupportTrello", "Content to draw: " + TrelloSupport.getInstance().openIssuesList.get(i).getValue());
                                textView.setText(TrelloSupport.getInstance().openIssuesList.get(i).getValue());
                            } catch (Exception e) {
                                FileLog.d("tsupportTrello", "Exception: " + e);
                            }
                        }
                    }
                } else {
                    FileLog.d("tsupportTrello", "Key to draw: " + TrelloSupport.getInstance().closedIssuesList.get(i).getKey());
                    if (TrelloSupport.getInstance().closedIssuesList.get(i) != null) {
                        TextView textView = (TextView) view.findViewById(R.id.info_text_view);
                        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                        textView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
                        textView.setEllipsize(TextUtils.TruncateAt.END);
                        textView.setMaxLines(2);
                        if (textView != null) {
                            try {
                                FileLog.d("tsupportTrello", "Content to draw: " + TrelloSupport.getInstance().closedIssuesList.get(i).getValue());
                                textView.setText(TrelloSupport.getInstance().closedIssuesList.get(i).getValue());
                            } catch (Exception e) {
                                FileLog.d("tsupportTrello", "Exception: " + e);
                            }
                        }
                    }
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            if (open) {
                return TrelloSupport.getInstance().openIssuesList.isEmpty();
            } else {
                return TrelloSupport.getInstance().closedIssuesList.isEmpty();
            }
        }
    }
}

