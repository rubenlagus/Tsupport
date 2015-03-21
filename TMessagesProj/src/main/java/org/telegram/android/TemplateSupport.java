package org.telegram.android;

import android.app.Activity;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * Class to provide support for templates that help Tsupport teams
 *
 * @author Rubenlagus
 * @version 1.0
 * @date 28/07/14
 */
public class TemplateSupport {

    public static class TemplateNotification {
        public int type;
        public ArrayList<String> keys = new ArrayList<>();
        public String value;
        public String question;
    }

    /**
     * Types of operations with default templates
     */
    public static final int ADDEDTEMPLATE = 1;
    public static final int REMOVEDTEMPLATE = 2;
    public static final int UPDATEDTEMPLATE = 3;

    /**
     * Queue to load templates
     */
    public static volatile DispatchQueue templatesQueue = new DispatchQueue("templatesQueue");

    /**
     * Base URL to fetch files
     */
    private static final String BASEURL = "http://translate.tsfkb.com/";

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
     * Templates regex patern
     */
    private static final Pattern TEMPLATESPATTERN = Pattern.compile("(?:\\{KEYS\\}\\n?([^{]+)\\n+\\{VALUE\\}\\n?([^{]+)\\n?|\\{QUESTION\\}\\n?([^{]+)\\n+\\{KEYS\\}\\n?([^{]+)\\n+\\{VALUE\\}\\n?([^{]+)\\n?)");

    /**
     * Templates regex patern
     */
    private static final Pattern KEYSPATTERN = Pattern.compile("((\\w+))");

    /**
     * Static Map to keep pairs kay-values with the templates.
     */
    public static TreeMap<String,String> templates = new TreeMap<String, String>();

    /**
     * Singleton Instance
     */
    private static volatile TemplateSupport Instance = null;

    private static void saveCustomTemplates(final TreeMap<String,String> newTemplates) {
        SharedPreferences customTemplatesPreferences = ApplicationLoader.applicationContext.getSharedPreferences(CUSTOMTEMPLATES, Activity.MODE_PRIVATE);
        SharedPreferences.Editor customTemplatesEditor = customTemplatesPreferences.edit();
        for (Map.Entry<String, String> entry: newTemplates.entrySet()) {
            customTemplatesEditor.putString(entry.getKey(), entry.getValue());
            templates.put(entry.getKey(), entry.getValue());
        }
        customTemplatesEditor.commit();
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateTemplatesNotification);
    }

    private static void saveCustomTemplate(String key, String value) {
        SharedPreferences customTemplatesPreferences = ApplicationLoader.applicationContext.getSharedPreferences(CUSTOMTEMPLATES, Activity.MODE_PRIVATE);
        SharedPreferences.Editor customTemplatesEditor = customTemplatesPreferences.edit();
        customTemplatesEditor.putString(key, value);
        templates.put(key, value);
        customTemplatesEditor.commit();
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateTemplatesNotification);

    }

    private static String saveDefaultTemplate(String key, String value, SharedPreferences sharedPreferences) {
        String notification = "";
        boolean toSave = false;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (sharedPreferences.contains(key)) {
            if (sharedPreferences.getString(key, "").compareToIgnoreCase(value) != 0) {
                toSave = true;
            }
        } else {
            toSave = true;
        }

        if (toSave) {
            editor.putString(key, value);
            editor.apply();
        }
        return notification;
    }

    private static ArrayList<String> saveDefaultTemplates(TreeMap<String, String> newTemlates, SharedPreferences sharedPreferences) {
        ArrayList<String> notifications = new ArrayList<>();

        for (Map.Entry<String, String> entry: newTemlates.entrySet()) {
            notifications.add(saveDefaultTemplate(entry.getKey(), entry.getValue(), sharedPreferences));
        }

        return notifications;
    }


    private static ArrayList<TemplateNotification> generateTemplatesNotifications(TreeMap<String, String> newTemplates, Map<String, HashSet<String>> oldTemplatesQuestions) {
        ArrayList<TemplateNotification> notifications = new ArrayList<>();
        SharedPreferences defaulttemplatesQuestionsPreferences = ApplicationLoader.applicationContext.getSharedPreferences(DEFAULTTEMPLATESQUESTIONS, Activity.MODE_PRIVATE);
        SharedPreferences defaulttemplatesPreferences = ApplicationLoader.applicationContext.getSharedPreferences(DEFAULTTEMPLATES, Activity.MODE_PRIVATE);
        HashMap<String, HashSet<String>> newQuestions = (HashMap<String, HashSet<String>>) defaulttemplatesQuestionsPreferences.getAll();

        for (String question: oldTemplatesQuestions.keySet()) {
            if (newQuestions.containsKey(question)) {
                Set<String> newkeys = newQuestions.get(question);
                Set<String> oldKeys = oldTemplatesQuestions.get(question);
                String newValue = "";
                String oldValue = "";
                for (String key : oldKeys) {
                    oldValue = defaulttemplatesPreferences.getString(key, "");
                    break;
                }
                for (String key : newkeys) {
                    newValue = newTemplates.get(key);
                }
                if (!newkeys.equals(oldKeys) || oldValue.compareToIgnoreCase(newValue) != 0) {
                    TemplateNotification notification = new TemplateNotification();
                    notification.type = UPDATEDTEMPLATE;
                    notification.value = newValue;
                    notification.keys.addAll(newkeys);
                    notification.question = question;
                    notifications.add(notification);
                }
            } else {
                Set<String> keys = oldTemplatesQuestions.get(question);
                String value = "";
                for (String key : keys) {
                    value = defaulttemplatesPreferences.getString(key, "");
                    break;
                }
                TemplateNotification notification = new TemplateNotification();
                notification.type = REMOVEDTEMPLATE;
                notification.value = value;
                notification.keys.addAll(keys);
                notification.question = question;
                notifications.add(notification);
            }
        }
        for (String question: newQuestions.keySet()) {
            if (!oldTemplatesQuestions.containsKey(question)) {
                HashSet<String> keys = newQuestions.get(question);
                String value = "";
                for (String key: keys) {
                    value = newTemplates.get(key);
                }
                TemplateNotification notification = new TemplateNotification();
                notification.type = ADDEDTEMPLATE;
                notification.value = value;
                notification.keys.addAll(keys);
                notification.question = question;
                notifications.add(notification);
            }
        }

        return notifications;
    }

    private static void deleteTemplate(final String key) {
        SharedPreferences customTemplatesPreferences = ApplicationLoader.applicationContext.getSharedPreferences(CUSTOMTEMPLATES, Activity.MODE_PRIVATE);
        if (customTemplatesPreferences.contains(key)) {
            SharedPreferences.Editor customTemplatesEditor = customTemplatesPreferences.edit();
            customTemplatesEditor.remove(key);
            templates.remove(key);
            customTemplatesEditor.commit();
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateTemplatesNotification);
        } else {
            SharedPreferences defaultTemplatesPreferences = ApplicationLoader.applicationContext.getSharedPreferences(DEFAULTTEMPLATES, Activity.MODE_PRIVATE);
            if (defaultTemplatesPreferences.contains(key)) {
                SharedPreferences.Editor defaultTemplatesEditor = defaultTemplatesPreferences.edit();
                defaultTemplatesEditor.remove(key);
                templates.remove(key);
                defaultTemplatesEditor.commit();
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateTemplatesNotification);
            }
        }
    }

    private static void clearCustomTemplates() {
        SharedPreferences customTemplatesPreferences = ApplicationLoader.applicationContext.getSharedPreferences(CUSTOMTEMPLATES, Activity.MODE_PRIVATE);
        SharedPreferences.Editor customTemplatesEditor = customTemplatesPreferences.edit();
        customTemplatesEditor.clear();
        customTemplatesEditor.commit();
        templates.clear();
        loadTemplatesInternal();
    }

    private static void clearDefaultTemplates() {
        SharedPreferences customTemplatesPreferences = ApplicationLoader.applicationContext.getSharedPreferences(DEFAULTTEMPLATES, Activity.MODE_PRIVATE);
        SharedPreferences.Editor customTemplatesEditor = customTemplatesPreferences.edit();
        customTemplatesEditor.clear();
        customTemplatesEditor.apply();
        SharedPreferences customTemplatesQuestionsPreferences = ApplicationLoader.applicationContext.getSharedPreferences(DEFAULTTEMPLATESQUESTIONS, Activity.MODE_PRIVATE);
        SharedPreferences.Editor customTemplatesQuestionsEditor = customTemplatesQuestionsPreferences.edit();
        customTemplatesQuestionsEditor.clear();
        customTemplatesQuestionsEditor.apply();
        templates.clear();
        loadTemplatesInternal();
    }

    private static void loadTemplatesInternal() {
        templatesQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                SharedPreferences defaultTemplatesPreferences = ApplicationLoader.applicationContext.getSharedPreferences(DEFAULTTEMPLATES, Activity.MODE_PRIVATE);
                Map<String, String> defaultTemplates = (Map<String, String>) defaultTemplatesPreferences.getAll();
                for (Map.Entry<String, String> entry: defaultTemplates.entrySet()) {
                    templates.put(entry.getKey(), entry.getValue());
                }
                SharedPreferences customTemplatesPreferences = ApplicationLoader.applicationContext.getSharedPreferences(CUSTOMTEMPLATES, Activity.MODE_PRIVATE);
                Map<String, String> customTemplates = (Map<String, String>) customTemplatesPreferences.getAll();
                for (Map.Entry<String, String> entry: customTemplates.entrySet()) {
                    templates.put(entry.getKey(), entry.getValue());
                }
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateTemplatesNotification);
            }
        });
    }
    private void loadTemplates() {
        loadTemplatesInternal();
    }


    /**
     * Get the instance of the class, creating one if it doesn't exists.
     * @return TemplateSupport  instance.
     */
    public static TemplateSupport getInstance() {
        TemplateSupport localInstance = Instance;
        if (localInstance == null) {
            synchronized (TemplateSupport.class) {
                if (localInstance == null) {
                    Instance = localInstance = new TemplateSupport();
                }
            }
        }
        return localInstance;
    }

    /**
     * Private constructor of the class.
     */
    private TemplateSupport() {
        if (templates == null) {
            templates = new TreeMap<>();
        }
        templates.clear();
        loadTemplates();
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateTemplatesNotification);
    }

    /**
     * Check if a string is a key for a template
     * @param key String to search in templates Map
     * @return Template content or empty string if the key isn't inside the templates Map.
     */
    public String getTemplate(String key) {
        String cleanKey = key.replace("..","").replace("(","");
        if (templates.containsKey(cleanKey)) {
            return templates.get(cleanKey);
        }
        return "";
    }

    /**
     * Load templates from default file
     */
    public static void loadDefaults() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        final String fileName = "tl_general_" + preferences.getString("languageSupport", "en").toLowerCase() + ".txt";
        templatesQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                loadDefaultFileInternal(fileName);
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
            InputStream inputStream = new FileInputStream(f);

            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            String fileString = "";
            if (br.ready()) {
                String line;

                while ((line = br.readLine()) != null) {
                    fileString += line;
                    fileString += "\n";
                }
            }
            Matcher mainMatcher = TEMPLATESPATTERN.matcher(fileString);
            String keys;
            String value;
            while (mainMatcher.find()) {
                keys = mainMatcher.group(1) != null ? mainMatcher.group(1) : mainMatcher.group(4);
                value = mainMatcher.group(2) != null ? mainMatcher.group(2) : mainMatcher.group(5);
                value = value.replaceAll("\\n{3,}", "\\n\\n").replaceAll("^\\s*", "").replaceAll("\\s*$", "");
                Matcher keysMatcher = KEYSPATTERN.matcher(keys);
                while (keysMatcher.find()) {
                    String key = keysMatcher.group(0).replace("\n", "");
                    if (key.compareToIgnoreCase("") != 0) {
                        customtemplatesEditor.putString(key, value);
                    }
                }
            }
            templates.clear();
            TemplateSupport.getInstance().loadTemplates();
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateTemplatesNotification);
        } catch (IOException e) {
            FileLog.e("TemplateSupport", "File IO Exception");
        } finally {
            if (customtemplatesEditor != null) {
                customtemplatesEditor.apply();
            }
        }
    }

    /**
     * Load templates from a file
     * @param fileName Name of the file
     */
    public static void loadDefaultFileInternal(String fileName) {
        SharedPreferences defaulttemplatesQuestionsPreferences = ApplicationLoader.applicationContext.getSharedPreferences(DEFAULTTEMPLATESQUESTIONS, Activity.MODE_PRIVATE);
        SharedPreferences.Editor defaulttemplatesQuestionsEditor = null;
        try {
            defaulttemplatesQuestionsEditor = defaulttemplatesQuestionsPreferences.edit();
            HttpClient httpclient = new DefaultHttpClient();
            HttpGet httppost = new HttpGet(BASEURL + fileName);
            HttpResponse response = httpclient.execute(httppost);
            HttpEntity ht = response.getEntity();

            BufferedHttpEntity buf = new BufferedHttpEntity(ht);
            InputStream inputStream = buf.getContent();

            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            String fileString = "";
            if (br.ready()) {
                String line;

                while ((line = br.readLine()) != null) {
                    fileString += line;
                    fileString += "\n";
                }
            }
            Matcher mainMatcher = TEMPLATESPATTERN.matcher(fileString);
            String keys;
            String question;
            String value;
            Set<String> keySet;
            Set<String> oldKeys = templates.keySet();
            Map<String, HashSet<String>> oldTemplatesQuestions = (Map<String, HashSet<String>>) defaulttemplatesQuestionsPreferences.getAll();
            defaulttemplatesQuestionsEditor.clear();
            TreeMap<String, String> newTemplates = new TreeMap<>();
            while(mainMatcher.find()) {
                keys = mainMatcher.group(1) != null ? mainMatcher.group(1) : mainMatcher.group(4);
                value = mainMatcher.group(2) != null ? mainMatcher.group(2) : mainMatcher.group(5);
                question = mainMatcher.group(3);
                value = value.replaceAll("\\n{3,}", "\\n\\n").replaceAll("^\\s*","").replaceAll("\\s*$","");

                Matcher keysMatcher = KEYSPATTERN.matcher(keys);
                keySet = new HashSet<>();
                while(keysMatcher.find()) {
                    String key = keysMatcher.group(0).replace("\n","");
                    if (key.compareToIgnoreCase("") != 0){
                        keySet.add(key);
                        newTemplates.put(key, value);
                    }
                }
                if (question != null && question.compareToIgnoreCase("") != 0) {
                    defaulttemplatesQuestionsEditor.putStringSet(question, keySet);
                }
            }
            defaulttemplatesQuestionsEditor.commit();

            //ArrayList<TemplateNotification> modifiedTemplates = generateTemplatesNotifications(newTemplates, oldTemplatesQuestions);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.templatesDidUpdated);
            templates.clear();
            TemplateSupport.loadTemplatesInternal();
        } catch (FileNotFoundException e) {
            FileLog.e("TemplateSupport", "File not found");
        } catch (IOException e) {
            FileLog.e("TemplateSupport", "File IO Exception");
        } finally {
            if (defaulttemplatesQuestionsEditor != null) {
                defaulttemplatesQuestionsEditor.apply();
            }
        }
    }

    /**
     * Add a template
     * @param key Key of the template
     * @param value Value of the template
     */
    public void putTemplate(String key, String value) {
        saveCustomTemplate(key, value);
    }

    /**
     * Remove a template
     * @param key Key of the template to remove
     */
    public void removeTemplate(String key) {
        deleteTemplate(key);
    }

    /**
     * Get templates map
     * @return TreeMap with the templates as <key,value>
     */
    public TreeMap<String, String> getAll() {
        return templates;
    }

    /**
     * Remove all templates.
     */
    public void removeAll() {
        clearCustomTemplates();
        clearDefaultTemplates();
    }

    /**
     * Export custom templates
     */
    public void exportTemplates() {
        exportCustomTemplates();
    }

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
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            try {
                for (String key : templates.keySet()) {
                    out.write("{KEYS}\n".getBytes());
                    out.write(key.getBytes());
                    out.write("\n\n".getBytes());
                    out.write("{VALUE}\n".getBytes());
                    out.write(templates.get(key).getBytes());
                    out.write("\n\n\n".getBytes());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Uri uri = null;
            uri = Uri.fromFile(file);


            NotificationCenter.getInstance().postNotificationName(NotificationCenter.exportTemplates, uri);
        }
    }
}
