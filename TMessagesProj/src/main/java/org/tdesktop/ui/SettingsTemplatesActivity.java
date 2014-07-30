/*
 * This is the source code of Tsupport for Android v. 0.5.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Ruben Bermudez, 2014.
 */

package org.tdesktop.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import org.tdesktop.android.LocaleController;
import org.tdesktop.android.MessagesController;
import org.tdesktop.android.MessagesStorage;
import org.tdesktop.android.TemplateSupport;
import org.tdesktop.messenger.FileLog;
import org.tdesktop.messenger.NotificationCenter;
import org.tdesktop.messenger.R;
import org.tdesktop.messenger.Utilities;
import org.tdesktop.ui.Adapters.BaseFragmentAdapter;
import org.tdesktop.ui.Views.ActionBar.ActionBarLayer;
import org.tdesktop.ui.Views.ActionBar.ActionBarMenu;
import org.tdesktop.ui.Views.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.HashMap;

public class SettingsTemplatesActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private ListView listView;
    private ListAdapter listViewAdapter;
    private boolean loading;
    private boolean loaded;
    private View progressView;
    private TextView emptyView;
    private ArrayList<String> templatesKeys = new ArrayList<String>();
    private HashMap<String, String> templates = new HashMap<String, String>();
    private String selectedTemplateKey;

    private final static int add_template = 1;
    private final static int reload_default = 2;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance().addObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, MessagesController.updateTemplatesNotification);
        loadTemplates();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.updateTemplatesNotification);

    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            ActionBarMenu menu = actionBarLayer.createMenu();
            actionBarLayer.setDisplayHomeAsUpEnabled(true, R.drawable.ic_ab_back);
            menu.addItem(add_template, R.drawable.addmember);
            menu.addItem(reload_default,R.drawable.ic_refresh);
            actionBarLayer.setBackOverlay(R.layout.updating_state_layout);
            actionBarLayer.setTitle(LocaleController.getString("templates", R.string.templates));

            actionBarLayer.setActionBarMenuOnItemClick(new ActionBarLayer.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == add_template) {
                        Bundle args = new Bundle();
                        args.putString("templateKey", "");
                        SettingsChangeTemplateActivity fragment = new SettingsChangeTemplateActivity(args);
                        presentFragment(fragment);
                    } else if (id == reload_default) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString("loaddefaulttemplates", R.string.loaddefaulttemplates));
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                TemplateSupport.loadDefaults();
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    }
                }
            });


            fragmentView = inflater.inflate(R.layout.settings_blocked_users_layout, container, false);
            listViewAdapter = new ListAdapter(getParentActivity());
            listView = (ListView)fragmentView.findViewById(R.id.listView);
            progressView = fragmentView.findViewById(R.id.progressLayout);
            emptyView = (TextView)fragmentView.findViewById(R.id.searchEmptyView);
            emptyView.setText(LocaleController.getString("noTemplates", R.string.noTemplates));
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
                    if (i < templates.size()) {
                        Bundle args = new Bundle();
                        args.putString("templateKey", templatesKeys.get(i));
                        args.putString("templateValue", templates.get(templatesKeys.get(i)));
                        presentFragment(new SettingsChangeTemplateActivity(args));
                    }
                }
            });

            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (i >= templates.size() || getParentActivity() == null) {
                        return true;
                    }
                    selectedTemplateKey = templatesKeys.get(i);

                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                    CharSequence[] items = new CharSequence[] {LocaleController.getString("Delete", R.string.Delete)};

                    builder.setItems(items, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (i == 0) {
                                TemplateSupport.getInstance().removeTemplate(selectedTemplateKey);
                                templates.remove(selectedTemplateKey);
                                templatesKeys.remove(i);
                                listViewAdapter.notifyDataSetChanged();
                            }
                        }
                    });
                    showAlertDialog(builder);

                    return true;
                }
            });
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    private void loadTemplatesInternal() {
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (progressView != null) {
                    progressView.setVisibility(View.GONE);
                }
                if (listView != null && listView.getEmptyView() == null) {
                    listView.setEmptyView(emptyView);
                }
                if (listViewAdapter != null) {
                    listViewAdapter.notifyDataSetChanged();
                }
            }
        });
    }
    private void loadTemplates() {
        if (loading) {
            return;
        }
        loading = true;
        MessagesStorage.getInstance().storageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                templates.clear();
                templatesKeys.clear();
                HashMap<String, String> mapaux = TemplateSupport.getInstance().getAll();
                if (mapaux != null) {
                    templates.putAll(TemplateSupport.getInstance().getAll());
                    templatesKeys.addAll(TemplateSupport.getInstance().getAll().keySet());
                    loading = false;
                    loadTemplatesInternal();
                }
            }
        });

    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == MessagesController.updateTemplatesNotification) {
            loadTemplates();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    private class ListAdapter extends BaseFragmentAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int i) {
            return i != templates.size();
        }

        @Override
        public int getCount() {
            if (templates.isEmpty()) {
                return 0;
            }
            return templates.size() + 1;
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

                FileLog.d("tsupport", "Key to draw: " + templatesKeys.get(i));
                if (templatesKeys.get(i) != null) {
                    TextView textView = (TextView)view.findViewById(R.id.info_text_view);
                    if (textView != null) {
                        try {
                            textView.setText(templatesKeys.get(i));
                        } catch (Exception e) {
                            FileLog.d("tsupport", "Exception: " + e);
                        }
                    }
                }
            } else if (type == 1) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_unblock_info_row_layout, viewGroup, false);
                    TextView textView = (TextView)view.findViewById(R.id.info_text_view);
                    textView.setText(LocaleController.getString("deleteTemplateText", R.string.deleteTemplateText));
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if(i == templates.size()) {
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
            return templates.isEmpty();
        }
    }
}

