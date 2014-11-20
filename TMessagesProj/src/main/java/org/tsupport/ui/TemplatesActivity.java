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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.tsupport.android.AndroidUtilities;
import org.tsupport.android.LocaleController;
import org.tsupport.android.MessagesStorage;
import org.tsupport.android.NotificationCenter;
import org.tsupport.android.TemplateSupport;
import org.tsupport.messenger.FileLog;
import org.tsupport.messenger.R;
import org.tsupport.messenger.TLRPC;
import org.tsupport.ui.ActionBar.ActionBar;
import org.tsupport.ui.ActionBar.ActionBarMenuItem;
import org.tsupport.ui.Adapters.BaseFragmentAdapter;
import org.tsupport.ui.ActionBar.ActionBarMenu;
import org.tsupport.ui.ActionBar.BaseFragment;
import org.tsupport.ui.Cells.TextInfoCell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;

public class TemplatesActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate,
        DocumentSelectActivity.DocumentSelectActivityDelegate {
    private ListView listView;
    private ListAdapter listViewAdapter;
    private FrameLayout progressView;
    private boolean loading;
    private boolean loaded;
    private TextView emptyView;
    private ArrayList<String> templatesKeys = new ArrayList<String>();
    private TreeMap<String, String> templates = new TreeMap<String, String>();
    private String selectedTemplateKey;

    private final static int add_template = 1;
    private final static int reload_default = 2;
    private final static int attach_document = 3;
    private final static int export_templates = 4;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateTemplatesNotification);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.exportTemplates);
        loadTemplates();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateTemplatesNotification);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.exportTemplates);

    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setAllowOverlayTitle(true);
            ActionBarMenu menu = actionBar.createMenu();
            ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_other);
            item.addSubItem(add_template, LocaleController.getString("AddTemplate", R.string.AddTemplate), R.drawable.addmember);
            item.addSubItem(reload_default, LocaleController.getString("ReloadDefault", R.string.ReloadDefault), R.drawable.ic_refresh);
            item.addSubItem(attach_document, LocaleController.getString("ImportTemplates", R.string.ImportTemplates), R.drawable.ic_ab_doc);
            item.addSubItem(export_templates, LocaleController.getString("ExportTemplates", R.string.ExportTemplates), R.drawable.ic_external_storage);
            actionBar.setTitle(LocaleController.getString("templates", R.string.templates));

            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == add_template) {
                        Bundle args = new Bundle();
                        args.putString("templateKey", "");
                        ModifyTemplateActivity fragment = new ModifyTemplateActivity(args);
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
                    } else if (id == attach_document) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString("loadtemplatesfromfile", R.string.loadtemplatesfromfile));
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                DocumentSelectActivity fragment = new DocumentSelectActivity();
                                fragment.setDelegate(TemplatesActivity.this);
                                presentFragment(fragment);
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    } else if (id == export_templates) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString("exportTemplates", R.string.exportTemplates));
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                TemplateSupport.getInstance().exportAll();
                                Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("exporting", R.string.exporting), Toast.LENGTH_LONG);
                                toast.show();
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
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
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
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
                        presentFragment(new ModifyTemplateActivity(args));
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
        templates.putAll(TemplateSupport.templates);
        templatesKeys.addAll(TemplateSupport.templates.keySet());
        Collections.sort(templatesKeys, new Comparator<String>() {
            @Override
            public int compare(String str1, String str2) {
                return str1.compareTo(str2);
            }
        });
        loading = false;
        loadTemplatesInternal();
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
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didSelectFile(DocumentSelectActivity activity, final String path) {
        activity.finishFragment();
        new Thread(new Runnable() {
            @Override
            public void run() {
                processLoadDocument(path);
            }
        }).start();

    }

    private void processLoadDocument(String path) {
        TemplateSupport.loadFile(path, false);
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

