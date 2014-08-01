/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.tsupport.ui.Views;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.tsupport.messenger.FileLog;
import org.tsupport.messenger.R;

import java.util.ArrayList;
import java.util.TreeMap;

public class TemplateView extends LinearLayout {
    private Listener listener;
    private ArrayList<String> templatesKeys = new ArrayList<String>();
    private TreeMap<String, String> templates = new TreeMap<String, String>();
    private ListView myView;

    public TemplateView(Context paramContext, TreeMap<String, String> newTemplates) {
        super(paramContext);
        init();
        setTemplates(newTemplates);
    }

    public TemplateView(Context paramContext, AttributeSet paramAttributeSet, TreeMap<String, String> newTemplates) {
        super(paramContext, paramAttributeSet);
        init();
        setTemplates(newTemplates);
    }

    public TemplateView(Context paramContext, AttributeSet paramAttributeSet, int paramInt, TreeMap<String, String> newTemplates) {
        super(paramContext, paramAttributeSet, paramInt);
        init();
        setTemplates(newTemplates);
    }

    public void setTemplates(TreeMap<String, String> newTemplates) {
        templates.clear();
        templatesKeys.clear();
        for (String key: newTemplates.keySet()) {
            templatesKeys.add(key);
            templates.put(key,newTemplates.get(key));
        }
        invalidateViews();
    }

    /*
    private void addToRecent(long paramLong) {
        if (this.pager.getCurrentItem() == 0) {
            return;
        }
        ArrayList<Long> localArrayList = new ArrayList<Long>();
        long[] currentRecent = Emoji.data[0];
        boolean was = false;
        for (long aCurrentRecent : currentRecent) {
            if (paramLong == aCurrentRecent) {
                localArrayList.add(0, paramLong);
                was = true;
            } else {
                localArrayList.add(aCurrentRecent);
            }
        }
        if (!was) {
            localArrayList.add(0, paramLong);
        }
        Emoji.data[0] = new long[Math.min(localArrayList.size(), 50)];
        for (int q = 0; q < Emoji.data[0].length; q++) {
            Emoji.data[0][q] = localArrayList.get(q);
        }
        adapters.get(0).data = Emoji.data[0];
        adapters.get(0).notifyDataSetChanged();
        saveRecents();
    }

    private String convert(long paramLong) {
        String str = "";
        for (int i = 0; ; i++) {
            if (i >= 4) {
                return str;
            }
            int j = (int)(0xFFFF & paramLong >> 16 * (3 - i));
            if (j != 0) {
                str = str + (char)j;
            }
        }
    }*/

    private void init() {
        setOrientation(LinearLayout.VERTICAL);
        myView = new ListView(getContext());
        TemplateArrayAdapter arrayAdapter = new TemplateArrayAdapter();
        myView.setAdapter(arrayAdapter);

        setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{-14145496, -16777216}));
        addView(myView);
    }

/*    private void saveRecents() {
        ArrayList<Long> localArrayList = new ArrayList<Long>();
        long[] arrayOfLong = Emoji.data[0];
        int i = arrayOfLong.length;
        for (int j = 0; ; j++) {
            if (j >= i) {
                getContext().getSharedPreferences("emoji", 0).edit().putString("recents", TextUtils.join(",", localArrayList)).commit();
                return;
            }
            localArrayList.add(arrayOfLong[j]);
        }
    }*/

    /*public void loadRecents() {
        String str = getContext().getSharedPreferences("emoji", 0).getString("recents", "");
        String[] arrayOfString = null;
        if ((str != null) && (str.length() > 0)) {
            arrayOfString = str.split(",");
            Emoji.data[0] = new long[arrayOfString.length];
        }
        if (arrayOfString != null) {
            for (int i = 0; i < arrayOfString.length; i++) {
                Emoji.data[0][i] = Long.parseLong(arrayOfString[i]);
            }
            adapters.get(0).data = Emoji.data[0];
            adapters.get(0).notifyDataSetChanged();
        }
    }*/

    public void onMeasure(int paramInt1, int paramInt2) {
        super.onMeasure(View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(paramInt1), MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(paramInt2), MeasureSpec.EXACTLY));
    }

    public void setListener(Listener paramListener) {
        this.listener = paramListener;
    }

    public void invalidateViews() {
        if (myView != null)
            myView.invalidateViews();
    }

    private class TemplateArrayAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return templates.size();
        }

        @Override
        public String getItem(int position) {
            return templates.get(templatesKeys.get(position));
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater li = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = li.inflate(R.layout.chat_template_view, null);
            }
            if (templatesKeys.get(position) != null) {
                final TextView keyView = (TextView) convertView.findViewById(R.id.key_text_view);
                final TextView valueView = (TextView) convertView.findViewById(R.id.value_text_view);
                LinearLayout mainView = (LinearLayout) convertView.findViewById(R.id.main_grid);
                if (keyView != null && valueView != null && mainView != null) {
                    try {
                        keyView.setText(templatesKeys.get(position));
                        valueView.setText(templates.get(templatesKeys.get(position)));
                        mainView.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (TemplateView.this.listener != null) {
                                    TemplateView.this.listener.onTemplateSelected(keyView.getText().toString());
                                }
                            }
                        });
                    } catch(Exception e) {
                        FileLog.e("tsupport", "Exception creating template keyboard");
                    }
                }
            }
            return convertView;
        }
    }

    public static abstract interface Listener {
        public abstract void onBackspace();
        public abstract void onTemplateSelected(String paramString);
    }
}