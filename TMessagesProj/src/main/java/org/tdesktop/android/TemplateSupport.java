package org.tdesktop.android;

import org.json.JSONException;
import org.json.JSONObject;
import org.tdesktop.messenger.FileLog;
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
    private static Map<String,String> templates = new HashMap<String,String>();

    /**
     * Singleton Instance
     */
    private static volatile TemplateSupport Instance = null;

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
     * Private constructor of the class that read the template JSON file.
     */
    private TemplateSupport() {
        FileLog.e("TemplateSupport", "Creating TemplateSupport instance");
        String json = null;
        try {
            FileLog.e("TemplateSupport", "Reading json");
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

        try {
            FileLog.e("TemplateSupport", "Creating JSONObject");
            JSONObject obj = new JSONObject(json);
            FileLog.e("TemplateSupport", "Iterating throw keys");
            Iterator<String> iterator = obj.keys();
            while(iterator.hasNext()) {
                String key = iterator.next();
                FileLog.e("TemplateSupport", "Key: " + key + " --> Value:" + obj.getString(key));
                templates.put(key, obj.getString(key));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        FileLog.e("TemplateSupport", "Instance created");
    }

    /**
     * Check if a string is a key for a template
     * @param key String to search in templates Map
     * @return Template content or empty string if the key isn't inside the templates Map.
     */
    public String getTemplate(String key) {
        FileLog.e("TemplateSupport", "Searching for: " + key);
        if(!key.startsWith("%%")) {
            FileLog.e("TemplateSupport", "Not %%");
            return "";
        }
        if (templates.containsKey(key.replace("%%",""))) {
            FileLog.e("TemplateSupport", "is template: " + templates.get(key));
            return templates.get(key.replace("%%",""));
        }
        FileLog.e("TemplateSupport", "No template");
        return "";
    }
}
