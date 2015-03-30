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
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.android.LocaleController;
import org.telegram.messenger.R;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TemplatesChangeLog {

    private static final String TYPEADD = "ADDEDTEMPLATE";
    private static final String TYPEUPDATE = "UPDATEDTEMPLATE";
    private static final String TYPEREMOVE = "DELETEDTEMPLATE";

    private static final String firstPart = "<html>\n" +
            "  <head>\n" +
            "    <style type='text/css'>\n" +
            "a { color: #a0a0e0 }\n" +
            "\tdiv.title {\n" +
            "\t\tcolor: #48C;\n" +
            "\t\tfont-size: 1.3em;\n" +
            "\t\tfont-weight: bold;\n" +
            "\t\tmargin: 1em 0 -0.3em 0;\n" +
            "\t\ttext-align: center;\n" +
            "\t\tborder-bottom: 1.5px solid #7AE;\n" +
            "\t}\n" +
            "\tdiv.subtitle {\n" +
            "\t\tcolor: #59D;\n" +
            "\t\tfont-size: 1em;\n" +
            "\t\ttext-align: center;\n" +
            "\t\tmargin-top: 1.2em;\n" +
            "\t\ttext-shadow: 0 0 0.5px #7AD;\n" +
            "\t}\n" +
            "\tdiv.list {\n" +
            "\t\tcolor: #555;\n" +
            "\t\tfont-size: 0.9em;\n" +
            "\t\ttext-align: left;\n" +
            "\t\tmargin-top: -0.5em;\n" +
            "\t}\n" +
            "\tdiv.list ul {\n" +
            "\t\tpadding: 0 2em 0 2em;\n" +
            "\t}\n" +
            "\tdiv.freetext {\n" +
            "\t\tcolor: #888;\n" +
            "\t\tfont-size: 0.9em;\n" +
            "\t\tmargin-top: -0.5em;\n" +
            "\t\tpadding: 0 2em 0 2em;\n" +
            "\t\ttext-align: justify;\n" +
            "\t}" +
            "    </style>\n" +
            "  </head>\n" +
            "  <body>\n\n";

    private static final String lastPart = " </body>\n" +
            "</html>";

    private final Context context;
    private JSONArray notifications = new JSONArray();
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
     * @param notifications
     */
    public TemplatesChangeLog(Context context, JSONArray notifications) {
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

        String changelog = "";
        try {
            changelog = firstPart;
            String addedTitle = "% ADDED\n";
            String updatedTitle = "% UPDATED\n";
            String removedTitle = "% REMOVED\n";
            List<String> added = new ArrayList<>();
            List<String> updated = new ArrayList<>();
            List<String> removed = new ArrayList<>();

            JSONObject templateLog;
            JSONObject template;
            String change;
            if (notifications.length() > 0) {
                for (int i = 0; i < notifications.length(); i++) {
                    templateLog = notifications.getJSONObject(i);
                    template = templateLog.getJSONObject("template");
                    switch (templateLog.getString("type")) {
                        case TYPEADD:
                            change = "";
                            change += "_ " + template.getString("question") + "\n";
                            change += "* KEYS: " + template.getString("keys") + "\n";
                            change += "! " +template.getString("value") + "\n";
                            added.add(change);
                            added.add("\n");
                            break;
                        case TYPEUPDATE:
                            change = "";
                            change += "_ " + template.getString("question") + "\n";
                            change += "* KEYS: " + template.getString("keys") + "\n";
                            change += "! " + template.getString("value") + "\n";
                            updated.add(change);
                            updated.add("\n");
                            break;
                        case TYPEREMOVE:
                            change = "";
                            change += "_ " + template.getString("question") + "\n";
                            change += "* KEYS: " + template.getString("keys") + "\n";
                            change += "! " + template.getString("value") + "\n";
                            removed.add(change);
                            removed.add("\n");
                            break;
                    }
                }
                if (!added.isEmpty()) {
                    changelog += addedTitle + "\n";
                    for (String string : added) {
                        changelog += string + "\n";
                    }
                }
                if (!updated.isEmpty()) {
                    changelog += "\n\n" + updatedTitle + "\n";
                    for (String string : updated) {
                        changelog += string + "\n";
                    }
                }
                if (!removed.isEmpty()) {
                    changelog += "\n\n" + removedTitle + "\n";
                    for (String string : removed) {
                        changelog += string + "\n";
                    }
                }
            } else {
                changelog += "_ " + LocaleController.getString("defaultTemplatesUptodate", R.string.defaultTemplatesUptodate);
            }
            changelog += "\n\n" + lastPart;
        } catch (JSONException e) {
            e.printStackTrace();
        }
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