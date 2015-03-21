/**
 * Copyright (C) 2011-2013, Karsten Priegnitz
 *
 * Permission to use, copy, modify, and distribute this piece of software
 * for any purpose with or without fee is hereby granted, provided that
 * the above copyright notice and this permission notice appear in the
 * source code of all copies.
 *
 * It would be appreciated if you mention the author in your change log,
 * contributors list or the like.
 *
 * @author: Karsten Priegnitz
 * @see: http://code.google.com/p/android-change-log/
 */
package org.telegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.view.ContextThemeWrapper;
import android.webkit.WebView;

import org.telegram.android.LocaleController;
import org.telegram.android.TemplateSupport;
import org.telegram.messenger.R;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class TemplatesChangeLog {

    private static final String firstPart = "<html>\n" +
            "  <head>\n" +
            "    <style type='text/css'>\n" +
            "      a            { color:#a0a0e0 }\n" +
            "      div.title    { \n" +
            "          color:#C0F0C0; \n" +
            "          font-size:1.2em; \n" +
            "          font-weight:bold; \n" +
            "          margin-top:1em; \n" +
            "          margin-bottom:0.5em; \n" +
            "          text-align:center }\n" +
            "      div.subtitle { \n" +
            "          color:#C0F0C0; \n" +
            "          font-size:0.8em; \n" +
            "          margin-bottom:1em; \n" +
            "          text-align:center }\n" +
            "      div.freetext { color:#F0F0F0 }\n" +
            "      div.list     { color:#C0C0F0 }\n" +
            "    </style>\n" +
            "  </head>\n" +
            "  <body>";

    private static final String lastPart = " </body>\n" +
            "</html>";

    private final Context context;
    private ArrayList<TemplateSupport.TemplateNotification> notifications = new ArrayList<TemplateSupport.TemplateNotification>();
    private String thisVersion;

    // this is the key for storing the version name in SharedPreferences
    private static final String VERSION_KEY = "PREFS_VERSION_KEY";

    /**
     * Constructor
     *
     * Retrieves the version names and stores the new version name in
     * SharedPreferences
     *
     * @param context
     */
    public TemplatesChangeLog(Context context, ArrayList<TemplateSupport.TemplateNotification> notifications) {
        this(context, PreferenceManager.getDefaultSharedPreferences(context));
        this.notifications = notifications;
    }

    /**
     * Constructor
     *
     * Retrieves the version names and stores the new version name in
     * SharedPreferences
     *
     * @param context
     * @param sp
     *            the shared preferences to store the last version name into
     */
    public TemplatesChangeLog(Context context, SharedPreferences sp) {
        this.context = context;

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
        // get version numbers
        this.thisVersion = sdf.format(new Date());
    }

    /**
     * @return an AlertDialog with a full change log displayed
     */
    public AlertDialog getFullLogDialog() {
        return this.getDialog(true);
    }

    private AlertDialog getDialog(boolean full) {
        WebView wv = new WebView(this.context);

        wv.setBackgroundColor(Color.WHITE);
        wv.loadDataWithBaseURL(null, this.getLog(full), "text/html", "UTF-8",
                null);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(
                context.getResources().getString(
                        full ? R.string.changelog_full_title
                                : R.string.changelog_title))
                .setView(wv)
                .setCancelable(false)
                        // OK button
                .setPositiveButton(
                        context.getResources().getString(
                                R.string.changelog_ok_button),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                updateVersionInPreferences();
                            }
                        });

        if (!full) {
            // "more ..." button
            builder.setNegativeButton(R.string.changelog_show_full,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            getFullLogDialog().show();
                        }
                    });
        }

        return builder.create();
    }

    private void updateVersionInPreferences() {
        // save new version number to preferences
        SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(VERSION_KEY, thisVersion);
        editor.commit();
    }

    /**
     * @return HTML displaying the changes since the previous installed version
     *         of your app (what's new)
     */
    public String getLog() {
        return this.getLog(false);
    }

    /**
     * @return HTML which displays full change log
     */
    public String getFullLog() {
        return this.getLog(true);
    }

    public String getChangeLog() {
        String changelog = firstPart;
        String added = "% ADDED\n";
        String updated = "% UPDATED\n";
        String removed = "% REMOVED\n";

        if (notifications.isEmpty()) {
            for (TemplateSupport.TemplateNotification notification : notifications) {
                switch (notification.type) {
                    case TemplateSupport.ADDEDTEMPLATE:
                        added += "- QUESTION: " + notification.question + "\n";
                        added += "- KEYS: " + notification.keys + "\n";
                        added += "! " + notification.value + "\n";
                        break;
                    case TemplateSupport.UPDATEDTEMPLATE:
                        updated += "- QUESTION: " + notification.question + "\n";
                        updated += "- KEYS: " + notification.keys + "\n";
                        updated += "! " + notification.value + "\n";
                        break;
                    case TemplateSupport.REMOVEDTEMPLATE:
                        removed += "- QUESTION: " + notification.question + "\n";
                        removed += "- KEYS: " + notification.keys + "\n";
                        removed += "! " + notification.value + "\n";
                        break;
                }
            }
            changelog += added + "\n";
            changelog += updated + "\n";
            changelog += removed + "\n";
        } else {
            changelog += LocaleController.getString("defaultTemplatesUptodate", R.string.defaultTemplatesUptodate);
        }
        changelog += "\n" + lastPart;
        return changelog;
    }

    /** modes for HTML-Lists (bullet, numbered) */
    private enum Listmode {
        NONE, ORDERED, UNORDERED,
    };

    private Listmode listMode = Listmode.NONE;
    private StringBuffer sb = null;
    private static final String EOCL = "END_OF_CHANGE_LOG";

    private String getLog(boolean full) {
        // read changelog.txt file
        sb = new StringBuffer();
        try {
            InputStream ins = new ByteArrayInputStream(getChangeLog().getBytes());
            BufferedReader br = new BufferedReader(new InputStreamReader(ins));

            String line = null;
            boolean advanceToEOVS = false; // if true: ignore further version
            // sections
            while ((line = br.readLine()) != null) {
                line = line.trim();
                char marker = line.length() > 0 ? line.charAt(0) : 0;
                if (marker == '$') {
                    // begin of a version section
                    this.closeList();
                    String version = line.substring(1).trim();
                    // stop output?
                    if (!full) {
                        if (version.equals(EOCL)) {
                            advanceToEOVS = false;
                        }
                    }
                } else if (!advanceToEOVS) {
                    switch (marker) {
                        case '%':
                            // line contains version title
                            this.closeList();
                            sb.append("<div class='title'>"
                                    + line.substring(1).trim() + "</div>\n");
                            break;
                        case '_':
                            // line contains version title
                            this.closeList();
                            sb.append("<div class='subtitle'>"
                                    + line.substring(1).trim() + "</div>\n");
                            break;
                        case '!':
                            // line contains free text
                            this.closeList();
                            sb.append("<div class='freetext'>"
                                    + line.substring(1).trim() + "</div>\n");
                            break;
                        case '#':
                            // line contains numbered list item
                            this.openList(Listmode.ORDERED);
                            sb.append("<li>" + line.substring(1).trim() + "</li>\n");
                            break;
                        case '*':
                            // line contains bullet list item
                            this.openList(Listmode.UNORDERED);
                            sb.append("<li>" + line.substring(1).trim() + "</li>\n");
                            break;
                        default:
                            // no special character: just use line as is
                            this.closeList();
                            sb.append(line + "\n");
                    }
                }
            }
            this.closeList();
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return sb.toString();
    }

    private void openList(Listmode listMode) {
        if (this.listMode != listMode) {
            closeList();
            if (listMode == Listmode.ORDERED) {
                sb.append("<div class='list'><ol>\n");
            } else if (listMode == Listmode.UNORDERED) {
                sb.append("<div class='list'><ul>\n");
            }
            this.listMode = listMode;
        }
    }

    private void closeList() {
        if (this.listMode == Listmode.ORDERED) {
            sb.append("</ol></div>\n");
        } else if (this.listMode == Listmode.UNORDERED) {
            sb.append("</ul></div>\n");
        }
        this.listMode = Listmode.NONE;
    }
}