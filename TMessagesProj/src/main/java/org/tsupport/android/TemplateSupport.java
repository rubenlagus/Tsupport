package org.tsupport.android;

import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.tsupport.messenger.FileLog;
import org.tsupport.messenger.NotificationCenter;
import org.tsupport.messenger.R;
import org.tsupport.ui.ApplicationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
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
     * Static Map to keep pairs kay-values with the templates.
     */
    private static TreeMap<String,String> templates = new TreeMap<String, String>();;

    /**
     * Singleton Instance
     */
    private static volatile TemplateSupport Instance = null;


    private static Boolean modifing = false;
    /**
     * Get the instance of the class, creating one if it doesn't exists.
     * @return TemplateSupport  instance.
     */
    public static TemplateSupport getInstance() {
        TemplateSupport localInstance = Instance;
        if (localInstance == null) {
            synchronized (TemplateSupport.class) {
                if (localInstance == null) {
                    Instance = localInstance = new TemplateSupport(true);
                }
            }
        }
        return localInstance;
    }

    public static TemplateSupport getInstanceWithDefault() {
        TemplateSupport localInstance = Instance;
        if (localInstance == null) {
            synchronized (TemplateSupport.class) {
                if (localInstance == null) {
                    Instance = localInstance = new TemplateSupport(false);
                }
            }
        }
        return localInstance;
    }

    /**
     * Private constructor of the class.
     */
    private TemplateSupport(Boolean rebuild) {
        FileLog.e("TemplateSupport", "Creating TemplateSupport instance");
        if (rebuild)
            rebuildInstance();
    }

    /**
     * Check if a string is a key for a template
     * @param key String to search in templates Map
     * @return Template content or empty string if the key isn't inside the templates Map.
     */
    public String getTemplate(String key) {
        if(!key.startsWith("%")) {
            return "";
        }
        if(!key.endsWith("%")) {
            return "";
        }
        String cleanKey = key.replace("%","");
        if (templates.containsKey(cleanKey)) {
            return templates.get(cleanKey);
        }
        return "";
    }

    public static void rebuildInstance() {
        if (!modifing) {
            modifing = true;
            if (templates != null) {
                templates.clear();
                templates.putAll(MessagesStorage.getInstance().getTemplates());
                NotificationCenter.getInstance().postNotificationName(MessagesController.updateTemplatesNotification);
            }
            modifing = false;
        }
    }

    public static void loadDefaults() {
        FileLog.e("TemplateSupport", "Loading default templates");
        FileLog.e("TemplateSupport", "Loading templates for template_" + LocaleController.getCurrentLanguageCode() + ".json");
        InputStream is = null;
        FileLog.e("TemplateSupport", "Reading json");
        String fileName = "template_" + LocaleController.getCurrentLanguageCode().toLowerCase() + ".json";
        loadFile(fileName, true);
    }

    public static void loadFile(String fileName, boolean loadDefault) {
        String json = null;
        InputStream is = null;
        try {
            is = ApplicationLoader.applicationContext.getAssets().open(fileName);

            int size = is.available();

            byte[] buffer = new byte[size];

            is.read(buffer);

            is.close();
            FileLog.e("TemplateSupport", "Creating string");
            json = new String(buffer, "UTF-8");
            //InputStream is = ApplicationLoader.applicationContext.getAssets().open("template_es.json");
        } catch (IOException ex) {
            try {
                if (loadDefault) {
                    is = ApplicationLoader.applicationContext.getAssets().open("template_en.json");
                    int size = is.available();

                    byte[] buffer = new byte[size];

                    is.read(buffer);

                    is.close();
                    FileLog.e("TemplateSupport", "Creating string");
                    json = new String(buffer, "UTF-8");
                }
                else {
                    FileLog.e("TemplateSupport", "File not found");
                }
            } catch (IOException ex2) {
                json = "";
            }
        }



        modifing = true;
        try {
            FileLog.e("TemplateSupport", "Creating JSONObject");
            JSONObject obj = new JSONObject(json);
            FileLog.e("TemplateSupport", "Iterating throw keys");
            Iterator<String> iterator = obj.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                MessagesStorage.getInstance().putTemplate(key,obj.getString(key));
                templates.put(key, obj.getString(key));
            }
        } catch (JSONException e) {
            FileLog.e("TemplateSupport", "File not JSON");
        }
        modifing = false;
        rebuildInstance();
        FileLog.e("TemplateSupport", "Instance created");
    }

    public void putTemplate(String key, String value) {
        MessagesStorage.getInstance().putTemplate(key, value);
    }

    public void removeTemplate(String key) {
        MessagesStorage.getInstance().deleteTemplate(key);
    }

    public TreeMap<String, String> getAll() {
        return templates;
    }

    public static void removeAll() {
        for (String key: templates.keySet()) {
            MessagesStorage.getInstance().deleteTemplate(key);
        }
    }
}
