package org.telegram.android;

import android.app.Activity;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 *
 * Class to provide support for templates that help Tsupport teams
 *
 * @author Rubenlagus
 * @version 1.0
 * @date 28/07/14
 */
public class TemplateSupport {
    /**
     * Types of operations with default templates
     */
    public static final int ADDEDTEMPLATE = 1;
    public static final int REMOVEDTEMPLATE = 2;
    public static final int UPDATEDTEMPLATE = 3;

    private final static String HEADER_CONTENT_TYPE = "Content-Type";

    /**
     * Queue to load templates
     */
    public static volatile DispatchQueue templatesQueue = new DispatchQueue("templatesQueue");

    /**
     * Base URL to fetch files
     */
    private static final String BASEURL = "http://sa.laagacht.net:9992/bot/templates/";

    /**
     * Name of preference file for default template values
     */
    private static final String DEFAULTTEMPLATES = "templatesDefault";

    /**
     * Name of preference file for default template
     */
    private static final String DEFAULTTEMPLATESQUESTIONS = "templatesDefaultQuestions";

    /**
     * Name of preference file for custom templates
     */
    private static final String CUSTOMTEMPLATES = "templatesCustom";

    /**
     * Static Map to keep pairs kay-values with the templates.
     */
    public static TreeMap<String,String> allTemplates = new TreeMap<>();
    public static TreeMap<String,String> defaultTemplates = new TreeMap<>();

    static {
        if (allTemplates == null) {
            allTemplates = new TreeMap<>();
        }
        if (defaultTemplates == null) {
            defaultTemplates = new TreeMap<>();
        }
        loadTemplates();
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateTemplatesNotification);
    }

    /**
     * Save a custom template
     * @param key Key of the new template
     * @param value Value of the new template
     */
    private static void saveCustomTemplate(String key, String value) {
        SharedPreferences customTemplatesPreferences = ApplicationLoader.applicationContext.getSharedPreferences(CUSTOMTEMPLATES, Activity.MODE_PRIVATE);
        SharedPreferences.Editor customTemplatesEditor = customTemplatesPreferences.edit();
        customTemplatesEditor.putString(key, value);
        allTemplates.put(key, value);
        customTemplatesEditor.commit();
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateTemplatesNotification);

    }

    /**
     * Delete a template
     * @param key Key of the template to delete
     */
    private static void deleteTemplate(final String key) {
        SharedPreferences customTemplatesPreferences = ApplicationLoader.applicationContext.getSharedPreferences(CUSTOMTEMPLATES, Activity.MODE_PRIVATE);
        if (customTemplatesPreferences.contains(key)) {
            SharedPreferences.Editor customTemplatesEditor = customTemplatesPreferences.edit();
            customTemplatesEditor.remove(key);
            allTemplates.remove(key);
            customTemplatesEditor.commit();
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateTemplatesNotification);
        } else {
            SharedPreferences defaultTemplatesPreferences = ApplicationLoader.applicationContext.getSharedPreferences(DEFAULTTEMPLATES, Activity.MODE_PRIVATE);
            if (defaultTemplatesPreferences.contains(key)) {
                SharedPreferences.Editor defaultTemplatesEditor = defaultTemplatesPreferences.edit();
                defaultTemplatesEditor.remove(key);
                allTemplates.remove(key);
                defaultTemplatesEditor.commit();
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateTemplatesNotification);
            }
        }
    }

    /**
     * Remove all custom templates
     */
    private static void clearCustomTemplates() {
        SharedPreferences customTemplatesPreferences = ApplicationLoader.applicationContext.getSharedPreferences(CUSTOMTEMPLATES, Activity.MODE_PRIVATE);
        SharedPreferences.Editor customTemplatesEditor = customTemplatesPreferences.edit();
        customTemplatesEditor.clear();
        customTemplatesEditor.commit();
        allTemplates.clear();
        loadTemplatesInternal();
    }

    /**
     * Remove all default templates
     */
    private static void clearDefaultTemplates() {
        SharedPreferences customTemplatesPreferences = ApplicationLoader.applicationContext.getSharedPreferences(DEFAULTTEMPLATES, Activity.MODE_PRIVATE);
        SharedPreferences.Editor customTemplatesEditor = customTemplatesPreferences.edit();
        customTemplatesEditor.clear();
        customTemplatesEditor.apply();
        SharedPreferences customTemplatesQuestionsPreferences = ApplicationLoader.applicationContext.getSharedPreferences(DEFAULTTEMPLATESQUESTIONS, Activity.MODE_PRIVATE);
        SharedPreferences.Editor customTemplatesQuestionsEditor = customTemplatesQuestionsPreferences.edit();
        customTemplatesQuestionsEditor.clear();
        customTemplatesQuestionsEditor.apply();
        allTemplates.clear();
        defaultTemplates.clear();
        loadTemplatesInternal();
    }

    /**
     * Load templates internal steps
     */
    private static void loadTemplatesInternal() {
        templatesQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                allTemplates.clear();
                defaultTemplates.clear();
                SharedPreferences customTemplatesPreferences = ApplicationLoader.applicationContext.getSharedPreferences(CUSTOMTEMPLATES, Activity.MODE_PRIVATE);
                Map<String, String> customTemplates = (Map<String, String>) customTemplatesPreferences.getAll();
                for (Map.Entry<String, String> entry: customTemplates.entrySet()) {
                    allTemplates.put(entry.getKey(), entry.getValue());
                }
                SharedPreferences defaultTemplatesPreferences = ApplicationLoader.applicationContext.getSharedPreferences(DEFAULTTEMPLATES, Activity.MODE_PRIVATE);
                Map<String, String> defaultTemplatesNew = (Map<String, String>) defaultTemplatesPreferences.getAll();
                for (Map.Entry<String, String> entry: defaultTemplatesNew.entrySet()) {
                    allTemplates.put(entry.getKey(), entry.getValue());
                    defaultTemplates.put(entry.getKey(), entry.getValue());
                }
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateTemplatesNotification);
            }
        });

    }

    /**
     * Load template from memory
     */
    private static void loadTemplates() {
        loadTemplatesInternal();
    }

    /**
     * Check if a string is a key for a template
     * @param key String to search in templates Map
     * @return Template content or empty string if the key isn't inside the templates Map.
     */
    public static String getTemplate(String key) {
        String cleanKey = key.replace("..","").replace("(","");
        if (allTemplates.containsKey(cleanKey)) {
            return allTemplates.get(cleanKey);
        }
        return "";
    }

    /**
     * Load templates from default file
     */
    public static void loadDefaults() {
        final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        templatesQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                loadDefaultFileInternal(preferences.getString("languageSupport", "en"));
            }
        });
    }

    /**
     * Load templates from default file
     */
    public static void loadFile(final String path) {
        templatesQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                loadFileInternal(path);
            }
        });
    }

    /**
     * Load templates from a file
     * @param path Name of the file
     */
    public static void loadFileInternal(String path) {
        SharedPreferences customtemplatesPreferences = ApplicationLoader.applicationContext.getSharedPreferences(CUSTOMTEMPLATES, Activity.MODE_PRIVATE);
        SharedPreferences.Editor customtemplatesEditor = null;
        try {
            customtemplatesEditor = customtemplatesPreferences.edit();
            File f = new File(path);
            FileInputStream inputStream = new FileInputStream(f);

            StringBuffer fileContent = new StringBuffer("");

            byte[] buffer = new byte[1024];
            int n;
            while ((n = inputStream.read(buffer)) != -1)
            {
                fileContent.append(new String(buffer, 0, n));
            }

            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost = new HttpPost(BASEURL + "process");
            httppost.addHeader(HEADER_CONTENT_TYPE, "text/plain; charset=UTF-8");
            httppost.setEntity(new ByteArrayEntity(fileContent.toString().getBytes("UTF8")));
            HttpResponse response = httpclient.execute(httppost);
            HttpEntity ht = response.getEntity();

            BufferedHttpEntity buf = new BufferedHttpEntity(ht);
            InputStream resultStream = buf.getContent();

            StringBuffer responseContent = new StringBuffer("");

            while ((n = resultStream.read(buffer)) != -1)
            {
                responseContent.append(new String(buffer, 0, n));
            }

            JSONArray jsonArray = new JSONArray(responseContent.toString());
            if (jsonArray.length() == 0) {
                throw new InvalidObjectException("File not valid");
            }
            String value;
            String key;
            JSONArray keys;
            JSONObject template;
            for (int i=0; i < jsonArray.length(); i++) {
                template = jsonArray.getJSONObject(i);
                value = template.getString("value");
                keys = template.getJSONArray("keys");
                for (int j=0; j < keys.length(); j++) {
                    key = keys.getString(j).toLowerCase();
                    if (key != null && key.length() != 0 && isDefault(key)){
                        customtemplatesEditor.putString(key, value);
                    }
                }
            }
            customtemplatesEditor.commit();
            loadTemplates();
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateTemplatesNotification);
        } catch (JSONException | InvalidObjectException e) {
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.errorTemplates, LocaleController.getString("FileNotCorrectFormat", R.string.FileNotCorrectFormat));
            FileLog.e("TemplateSupport", e);
        } catch (FileNotFoundException e) {
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.errorTemplates, LocaleController.getString("FileNotFound", R.string.FileNotFound));
            FileLog.e("TemplateSupport", e);
        } catch (UnsupportedEncodingException e) {
            FileLog.e("TemplateSupport", e);
        } catch (IOException e) {
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.errorTemplates, LocaleController.getString("NetworkErrorTemplates", R.string.NetworkErrorTemplates));
            FileLog.e("TemplateSupport", e);
        } finally {
            if (customtemplatesEditor != null) {
                customtemplatesEditor.apply();
            }
        }
    }

    /**
     * Load templates for a language
     * @param languageCode Name of the file
     */
    public static void loadDefaultFileInternal(String languageCode) {
        SharedPreferences defaulttemplatesQuestionsPreferences = ApplicationLoader.applicationContext.getSharedPreferences(DEFAULTTEMPLATESQUESTIONS, Activity.MODE_PRIVATE);
        SharedPreferences defaulttemplatesPreferences = ApplicationLoader.applicationContext.getSharedPreferences(DEFAULTTEMPLATES, Activity.MODE_PRIVATE);
        SharedPreferences.Editor defaulttemplatesQuestionsEditor = null;
        SharedPreferences.Editor defaulttemplatesEditor = null;
        try {
            defaulttemplatesQuestionsEditor = defaulttemplatesQuestionsPreferences.edit();
            defaulttemplatesEditor = defaulttemplatesPreferences.edit();
            HttpClient httpclient = new DefaultHttpClient();
            HttpGet httppost = new HttpGet(BASEURL + languageCode);
            HttpResponse response = httpclient.execute(httppost);
            HttpEntity ht = response.getEntity();

            BufferedHttpEntity buf = new BufferedHttpEntity(ht);
            InputStream inputStream = buf.getContent();

            StringBuffer responseContent = new StringBuffer("");
            byte[] buffer = new byte[1024];
            int n;
            while ((n = inputStream.read(buffer)) != -1)
            {
                responseContent.append(new String(buffer, 0, n));
            }

            JSONArray jsonArray = new JSONArray(responseContent.toString());

            String value;
            String question;
            String key;
            Set<String> keySet;
            JSONArray keys;
            JSONObject template;
            for (int i=0; i < jsonArray.length(); i++) {
                template = jsonArray.getJSONObject(i);
                value = template.getString("value");
                keys = template.getJSONArray("keys");
                question = template.getString("question");
                keySet = new HashSet<>();
                for (int j=0; j < keys.length(); j++) {
                    key = keys.getString(j);
                    if (key != null && key.length() != 0){
                        keySet.add(key);
                        defaulttemplatesEditor.putString(key, value);
                    }
                }
                if (question != null && question.length() != 0) {
                    defaulttemplatesQuestionsEditor.putStringSet(question, keySet);
                }
            }
            updateMaxHash(languageCode);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.templatesDidUpdated);
            allTemplates.clear();
            TemplateSupport.loadTemplatesInternal();
        } catch (FileNotFoundException e) {
            FileLog.e("TemplateSupport", "File not found");
        } catch (IOException e) {
            FileLog.e("TemplateSupport", "File IO Exception");
        } catch (JSONException e) {
            FileLog.e("TemplateSupport", e);
        } finally {
            if (defaulttemplatesQuestionsEditor != null) {
                defaulttemplatesQuestionsEditor.apply();
            }
            if (defaulttemplatesEditor != null) {
                defaulttemplatesEditor.apply();
            }
        }
    }

    /**
     * Update hash of last template file that was fetched from server
     * @param languageCode Language of the file
     */
    private static void updateMaxHash(String languageCode) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = null;
        try {
            HttpClient httpclient = new DefaultHttpClient();
            HttpGet httppost = new HttpGet(BASEURL + "maxHash/" + languageCode);
            HttpResponse response = httpclient.execute(httppost);
            HttpEntity ht = response.getEntity();

            BufferedHttpEntity buf = new BufferedHttpEntity(ht);
            InputStream inputStream = buf.getContent();

            StringBuffer responseContent = new StringBuffer("");
            byte[] buffer = new byte[1024];
            int n;
            while ((n = inputStream.read(buffer)) != -1)
            {
                responseContent.append(new String(buffer, 0, n));
            }

            JSONObject jsonObject = new JSONObject(responseContent.toString());
            String hash = jsonObject.getString("hash");
            editor = preferences.edit();
            editor.putString("templatesHash", hash);
            editor.commit();
        } catch (JSONException | IOException e) {
            FileLog.e("TemplateSupport", e);
        } finally {
            if (editor != null) {
                editor.commit();
            }
        }
    }

    /**
     * Load differences from server
     * @param languageCode Language code to query
     */
    public static void loadDifferences(final String languageCode) {
        templatesQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                loadDifferenceInternal(languageCode);
            }
        });
    }

    /**
     * Load templates for a language
     * @param languageCode Name of the file
     */
    private static void loadDifferenceInternal(String languageCode) {
        SharedPreferences defaulttemplatesQuestionsPreferences = ApplicationLoader.applicationContext.getSharedPreferences(DEFAULTTEMPLATESQUESTIONS, Activity.MODE_PRIVATE);
        SharedPreferences defaulttemplatesPreferences = ApplicationLoader.applicationContext.getSharedPreferences(DEFAULTTEMPLATES, Activity.MODE_PRIVATE);
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        String currentHash = preferences.getString("templatesHash","");
        SharedPreferences.Editor defaulttemplatesQuestionsEditor = null;
        SharedPreferences.Editor defaulttemplatesEditor = null;
        JSONArray jsonArray = new JSONArray();
        try {
            if (currentHash != null || !currentHash.equals("")) {
                defaulttemplatesQuestionsEditor = defaulttemplatesQuestionsPreferences.edit();
                defaulttemplatesEditor = defaulttemplatesPreferences.edit();
                HttpClient httpclient = new DefaultHttpClient();
                HttpGet httppost = new HttpGet(BASEURL + "difference/" + languageCode + "/" + currentHash);
                HttpResponse response = httpclient.execute(httppost);
                HttpEntity ht = response.getEntity();

                BufferedHttpEntity buf = new BufferedHttpEntity(ht);
                InputStream inputStream = buf.getContent();

                StringBuffer responseContent = new StringBuffer("");
                byte[] buffer = new byte[1024];
                int n;
                while ((n = inputStream.read(buffer)) != -1)
                {
                    responseContent.append(new String(buffer, 0, n));
                }

                jsonArray = new JSONArray(responseContent.toString());
                String value;
                String question;
                String key;
                Set<String> keySet;
                JSONArray keys;
                JSONObject template;
                JSONObject templateLog;
                for (int i = 0; i < jsonArray.length(); i++) {
                    templateLog = jsonArray.getJSONObject(i);
                    template = templateLog.getJSONObject("template");
                    value = template.getString("value");
                    keys = template.getJSONArray("keys");
                    question = template.getString("question");
                    keySet = new HashSet<>();
                    for (int j = 0; j < keys.length(); j++) {
                        key = keys.getString(j);
                        if (key != null && key.length()!= 0) {
                            keySet.add(key);
                            defaulttemplatesEditor.putString(key, value);
                        }
                    }
                    if (question != null && question.length() != 0) {
                        defaulttemplatesQuestionsEditor.putStringSet(question, keySet);
                    }
                }
            }
            updateMaxHash(languageCode);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.templatesDidUpdated, jsonArray);
            allTemplates.clear();
            TemplateSupport.loadTemplatesInternal();
        } catch (FileNotFoundException e) {
            FileLog.e("TemplateSupport", "File not found");
        } catch (IOException e) {
            FileLog.e("TemplateSupport", "File IO Exception");
        } catch (JSONException e) {
            FileLog.e("TemplateSupport", e);
        } finally {
            if (defaulttemplatesQuestionsEditor != null) {
                defaulttemplatesQuestionsEditor.apply();
            }
            if (defaulttemplatesEditor != null) {
                defaulttemplatesEditor.apply();
            }
        }
    }


    /**
     * Add a template
     * @param key Key of the template
     * @param value Value of the template
     */
    public static void putTemplate(String key, String value) {
        saveCustomTemplate(key, value);
    }

    /**
     * Remove a template
     * @param key Key of the template to remove
     */
    public static void removeTemplate(String key) {
        deleteTemplate(key);
    }

    /**
     * Get templates map
     * @return TreeMap with the templates as <key,value>
     */
    public static TreeMap<String, String> getAll() {
        return allTemplates;
    }

    /**
     * Remove all templates.
     */
    public static void removeAll() {
        clearCustomTemplates();
        clearDefaultTemplates();
        loadDefaults();
    }

    /**
     * Remove default templates.
     */
    public static void removeDefaultTemplates() {
        clearDefaultTemplates();
    }

    /**
     * Export custom templates
     */
    public static void exportTemplates() {
        exportCustomTemplates();
    }

    /**
     * Export all templates to a file
     */
    private static void exportCustomTemplates() {
        File file;
        File root = Environment.getExternalStorageDirectory();
        if (root.canWrite()) {
            File dir = new File(root.getAbsolutePath() + "/Tsupport");
            if (!dir.exists())
                dir.mkdirs();
            file = new File(dir, "template_" + LocaleController.getCurrentLanguageCode().toLowerCase() + ".txt");
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(file);
                for (String key : allTemplates.keySet()) {
                    out.write("{KEYS}\n".getBytes());
                    out.write(key.getBytes());
                    out.write("\n\n".getBytes());
                    out.write("{VALUE}\n".getBytes());
                    out.write(allTemplates.get(key).getBytes());
                    out.write("\n\n\n".getBytes());
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        FileLog.e("TemplateSupport", e);
                    }
                }
            }
            Uri uri = Uri.fromFile(file);

            NotificationCenter.getInstance().postNotificationName(NotificationCenter.exportTemplates, uri);
        }
    }

    /**
     * Check if a template key belong to one of the default templates
     * @param key Key to be checked
     * @return true if it is default, false otherwise
     */
    public static boolean isDefault(String key) {
        return defaultTemplates.containsKey(key);
    }
}
