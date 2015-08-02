/*
 * This is the source code of Tsupport for Android v. 0.5.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Ruben Bermudez, 2014.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.android.NotificationCenter;
import org.telegram.android.TemplateSupport;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.TextColorCell;
import org.telegram.ui.Cells.TextInfoCell;
import org.telegram.ui.Cells.TextSettingsCell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;

public class TemplatesSettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate,
        DocumentSelectActivity.DocumentSelectActivityDelegate {
    private ListView listView;
    private ListAdapter listViewAdapter;
    private FrameLayout progressView;
    private boolean loading;
    private TextView emptyView;
    private ArrayList<String> templatesKeys = new ArrayList<>();
    private TreeMap<String, String> templates = new TreeMap<>();
    private String selectedTemplateKey;

    private final static int add_template = 1;
    private final static int reload_default = 2;
    private final static int import_templates = 3;
    private final static int export_templates = 4;
    private final static int delete_all_templates = 5;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateTemplatesNotification);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.exportTemplates);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.templatesDidUpdated);
        loadTemplates();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateTemplatesNotification);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.exportTemplates);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.templatesDidUpdated);

    }

    @Override
    public View createView(Context context, LayoutInflater inflater) {
        if (fragmentView == null) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setAllowOverlayTitle(true);
            ActionBarMenu menu = actionBar.createMenu();
            ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_other);
            item.addSubItem(add_template, LocaleController.getString("AddTemplate", R.string.AddTemplate), R.drawable.addmember);
            item.addSubItem(reload_default, LocaleController.getString("ReloadDefault", R.string.ReloadDefault), R.drawable.ic_refresh);
            item.addSubItem(import_templates, LocaleController.getString("ImportTemplates", R.string.ImportTemplates), R.drawable.ic_ab_doc);
            item.addSubItem(export_templates, LocaleController.getString("ExportTemplates", R.string.ExportTemplates), R.drawable.ic_export);
            item.addSubItem(delete_all_templates, LocaleController.getString("ClearButton", R.string.ClearButton), R.drawable.ic_ab_fwd_delete);
            actionBar.setTitle(LocaleController.getString("templates", R.string.templates));

            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == add_template) {
                        Bundle args = new Bundle();
                        args.putString("templateKey", "");
                        ChangeTemplateActivity fragment = new ChangeTemplateActivity(args);
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
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                updateView();
                            }
                        });
                        showDialog(builder.create());
                        emptyView.setVisibility(View.GONE);
                        listView.setVisibility(View.GONE);
                        progressView.setVisibility(View.VISIBLE);
                    } else if (id == import_templates) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString("loadtemplatesfromfile", R.string.loadtemplatesfromfile));
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                DocumentSelectActivity fragment = new DocumentSelectActivity();
                                fragment.setDelegate(TemplatesSettingsActivity.this);
                                presentFragment(fragment);
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder.create());
                    } else if (id == export_templates) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString("exportTemplates", R.string.exportTemplates));
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                TemplateSupport.exportTemplates();
                                Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("exporting", R.string.exporting), Toast.LENGTH_LONG);
                                toast.show();
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder.create());
                    } else if (id == delete_all_templates) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString("clearrAllTempaltes", R.string.clearrAllTempaltes));
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                TemplateSupport.removeAll();
                                Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("templatesRemoved", R.string.templatesRemoved), Toast.LENGTH_LONG);
                                toast.show();
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder.create());
                    }
                }
            });

            fragmentView = new FrameLayout(getParentActivity());
            FrameLayout frameLayout = (FrameLayout) fragmentView;

            listView = new ListView(getParentActivity());
            listView.setEmptyView(emptyView);
            listView.setVerticalScrollBarEnabled(false);
            listView.setDivider(null);
            listView.setDividerHeight(0);
            listView.setAdapter(listViewAdapter = new ListAdapter(getParentActivity()));
            if (Build.VERSION.SDK_INT >= 11) {
                listView.setVerticalScrollbarPosition(LocaleController.isRTL ? ListView.SCROLLBAR_POSITION_LEFT : ListView.SCROLLBAR_POSITION_RIGHT);
            }
            frameLayout.addView(listView);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            listView.setLayoutParams(layoutParams);

            progressView = new FrameLayout(getParentActivity());
            frameLayout.addView(progressView);
            layoutParams = (FrameLayout.LayoutParams) progressView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.gravity = Gravity.CENTER;
            progressView.setLayoutParams(layoutParams);

            ProgressBar progressBar = new ProgressBar(getParentActivity());
            progressView.addView(progressBar);
            layoutParams = (FrameLayout.LayoutParams) progressView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.gravity = Gravity.CENTER;
            progressView.setLayoutParams(layoutParams);

            FrameLayout.LayoutParams layoutParamsProgressView = (FrameLayout.LayoutParams) progressView.getLayoutParams();
            layoutParamsProgressView.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParamsProgressView.height = FrameLayout.LayoutParams.MATCH_PARENT;
            progressView.setLayoutParams(layoutParamsProgressView);

            emptyView = new TextView(inflater.getContext());
            emptyView.setTextColor(0xFF808080);
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
            emptyView.setText(LocaleController.getString("noTemplates", R.string.noTemplates));
            frameLayout.addView(emptyView);

            FrameLayout.LayoutParams layoutParamsEmptyView = (FrameLayout.LayoutParams)emptyView.getLayoutParams();
            layoutParamsEmptyView.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParamsEmptyView.height = FrameLayout.LayoutParams.MATCH_PARENT;
            emptyView.setLayoutParams(layoutParamsEmptyView);

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
                        presentFragment(new ChangeTemplateActivity(args));
                    }
                }
            });

            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (i >= templates.size() || getParentActivity() == null || TemplateSupport.isDefault(templatesKeys.get(i))) {
                        return true;
                    }
                    selectedTemplateKey = templatesKeys.get(i);

                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                    CharSequence[] items = new CharSequence[] {LocaleController.getString("Delete", R.string.Delete)};

                    builder.setItems(items, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (i == 0) {
                                TemplateSupport.removeTemplate(selectedTemplateKey);
                                templates.remove(selectedTemplateKey);
                                templatesKeys.remove(i);
                                listViewAdapter.notifyDataSetChanged();
                            }
                        }
                    });
                    showDialog(builder.create());

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
        AndroidUtilities.runOnUIThread(new Runnable() {
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
        templates.clear();
        templatesKeys.clear();
        templates.putAll(TemplateSupport.allTemplates);
        templatesKeys.addAll(TemplateSupport.allTemplates.keySet());
        Collections.sort(templatesKeys, new Comparator<String>() {
            @Override
            public int compare(String str1, String str2) {
                return str1.compareTo(str2);
            }
        });
        updateView();
    }

    private void updateView() {
        AndroidUtilities.runOnUIThread(new Runnable() {

            @Override
            public void run() {
                if (templates.size() > 0) {
                    if (emptyView != null) {
                        emptyView.setVisibility(View.GONE);
                    }
                    if (listView != null) {
                        listView.setVisibility(View.VISIBLE);
                    }
                    if (progressView != null) {
                        progressView.setVisibility(View.GONE);
                    }
                } else {
                    if (emptyView != null) {
                        emptyView.setVisibility(View.VISIBLE);
                    }
                    if (listView != null) {
                        listView.setVisibility(View.GONE);
                    }
                    if (progressView != null) {
                        progressView.setVisibility(View.GONE);
                    }
                }
                loading = false;
                loadTemplatesInternal();
            }
        });
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.updateTemplatesNotification) {
            loadTemplates();
        } else if (id == NotificationCenter.exportTemplates) {
            if (args.length > 0) {
                Uri uri = (Uri) args[0];
                Intent sendIntent = new Intent(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_SUBJECT, LocaleController.getString("templates", R.string.templates));
                sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
                sendIntent.setType("text/plain");

                getParentActivity().startActivity(sendIntent);
            }
        } else if (id == NotificationCenter.templatesDidUpdated) {
            if (args.length == 0) {
                Toast.makeText(getParentActivity().getApplicationContext(), LocaleController.getString("templatesUpdatedFromServer", R.string.templatesUpdatedFromServer), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    private void processLoadDocument(String path) {
        AndroidUtilities.runOnUIThread(new Runnable() {

            @Override
            public void run() {
                if (emptyView != null) {
                    emptyView.setVisibility(View.GONE);
                }
                if (listView != null) {
                    listView.setVisibility(View.GONE);
                }
                if (progressView != null) {
                    progressView.setVisibility(View.VISIBLE);
                }
            }
        });
        TemplateSupport.loadFile(path);
    }

    @Override
    public void didSelectFiles(DocumentSelectActivity activity, final ArrayList<String> files) {
        activity.finishFragment();
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (files.size() > 0) {
                    processLoadDocument(files.get(0));
                }
            }
        }).start();
    }

    @Override
    public void startDocumentSelectActivity() {
        try {
            Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
            photoPickerIntent.setType("*/*");
            getParentActivity().startActivityForResult(photoPickerIntent, 21);
        } catch (Exception e) {
            FileLog.e("tsupport", e);
        }
    }

    private class ListAdapter extends BaseFragmentAdapter {
        private static final int REDCOLOR = 0xffff0000;
        private static final int GREENCOLOR = 0xff00ff00;

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
            return (i != templates.size());
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
                    view = new TextColorCell(mContext);
                }
                if (templatesKeys.get(i) != null) {
                    if (TemplateSupport.isDefault(templatesKeys.get(i))) {
                        ((TextColorCell) view).setTextAndColor(templatesKeys.get(i), REDCOLOR, true);
                    } else {
                        ((TextColorCell) view).setTextAndColor(templatesKeys.get(i), GREENCOLOR, true);
                    }
                }
                return view;
            } else if (type == 1) {
                if (view == null) {
                    view = new TextInfoCell(mContext);
                    ((TextInfoCell) view).setText(LocaleController.getString("deleteTemplateText", R.string.deleteTemplateText));
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

