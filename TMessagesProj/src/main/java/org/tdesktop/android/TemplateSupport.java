package org.tdesktop.android;

import android.widget.Adapter;

import org.json.JSONException;
import org.json.JSONObject;
import org.tdesktop.messenger.FileLog;
import org.tdesktop.messenger.NotificationCenter;
import org.tdesktop.ui.ApplicationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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
    private static HashMap<String,String> templates = new HashMap<String,String>();

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
     * Private constructor of the class that read the template JSON file.
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
        FileLog.e("TemplateSupport", "Searching for: " + key);
        if(!key.startsWith("%")) {
            FileLog.e("TemplateSupport", "Not %");
            return "";
        }
        if(!key.endsWith("%")) {
            FileLog.e("TemplateSupport", "Not  end %");
            return "";
        }
        String cleanKey = key.replace("%","");
        if (templates.containsKey(cleanKey)) {
            FileLog.e("TemplateSupport", "is template: " + templates.get(key));
            return templates.get(cleanKey);
        }
        FileLog.e("TemplateSupport", "No template");
        return "";
    }

    public static void rebuildInstance() {
        if (!modifing) {
            modifing = true;
            templates.clear();
            templates.putAll(MessagesStorage.getInstance().getTemplates());
            NotificationCenter.getInstance().postNotificationName(MessagesController.updateTemplatesNotification);
            modifing = false;
        }
    }

    public static void loadDefaults() {
        FileLog.e("TemplateSupport", "Loading default templates");
        String json = null;
        FileLog.e("TemplateSupport", "Loading templates for template_" + LocaleController.getCurrentLanguageCode() + ".json");
        try {
            FileLog.e("TemplateSupport", "Reading json");
            //InputStream is = ApplicationLoader.applicationContext.getAssets().open("template_" + LocaleController.getCurrentLanguageCode() + ".json");
            InputStream is = ApplicationLoader.applicationContext.getAssets().open("template_tl.json");
            int size = is.available();

            byte[] buffer = new byte[size];

            is.read(buffer);

            is.close();
            FileLog.e("TemplateSupport", "Creating string");
            json = new String(buffer, "UTF-8");

        } catch (IOException ex) {
            ex.printStackTrace();
            json = "{}";
        } finally {
            if (json == null)
                json = "{}";
        }
        modifing = true;
        try {
            FileLog.e("TemplateSupport", "Creating JSONObject");
            JSONObject obj = new JSONObject(json);
            FileLog.e("TemplateSupport", "Iterating throw keys");
            Iterator<String> iterator = obj.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                FileLog.e("TemplateSupport", "Key: " + key + " --> Value:" + obj.getString(key));
                MessagesStorage.getInstance().putTemplate(key,obj.getString(key));
                templates.put(key, obj.getString(key));
            }
        } catch (JSONException e) {
            e.printStackTrace();
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

    public HashMap<String, String> getAll() {
        return templates;
    }
}
