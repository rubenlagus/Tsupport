package org.tsupport.android;

import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.tsupport.messenger.FileLog;
import org.tsupport.messenger.NotificationCenter;
import org.tsupport.messenger.R;
import org.tsupport.ui.ApplicationLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;
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
    /**
     * Static Map to keep pairs kay-values with the templates.
     */
    private static TreeMap<String,String> templates = new TreeMap<String, String>();;

    /**
     * Singleton Instance
     */
    private static volatile TemplateSupport Instance = null;


    public static int modifing = 0;
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
        if (rebuild)
            rebuildInstance();
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

    public static void rebuildInstance() {
        if (modifing == 0) {
            modifing++;
            if (templates != null) {
                templates.clear();
                templates.putAll(MessagesStorage.getInstance().getTemplates());
                NotificationCenter.getInstance().postNotificationName(MessagesController.updateTemplatesNotification);
            }
            modifing--;
        }
    }

    public static void loadDefaults() {
        InputStream is = null;
        String fileName = "template_" + LocaleController.getCurrentLanguageCode().toLowerCase() + ".txt";
        loadFile(fileName, true);
    }

    public static void loadFile(String fileName, boolean loadDefault) {
        ArrayList<ArrayList<String>> lines = new ArrayList<ArrayList<String>>();
        ArrayList<String> templine = new ArrayList<String>();
        try {
            InputStream is;
            if (loadDefault)
                is = ApplicationLoader.applicationContext.getAssets().open(fileName);
            else {
                File f = new File(fileName);
                is = new FileInputStream(f);
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            if (br.ready()) {
                String line;
                boolean started = false;
                while ((line = br.readLine()) != null) {
                    if (line.contains("KEYS")) {
                        lines.add(templine);
                        templine = new ArrayList<String>();
                        if (!started) {
                            started = true;
                        }
                    } else {
                        if (started) {
                            templine.add(line);
                        }
                    }
                }
                br.close();
                int i = 0;
                ArrayList<String> keys = new ArrayList<String>();
                Pattern patternEmptyLine = Pattern.compile("^\\s*$");
                for (ArrayList<String> line2 : lines) {
                    keys.clear();
                    String value = "";
                    boolean inkeys = true;
                    for (String inline : line2) {
                        if (inline.contains("{VALUE}"))
                            inkeys = false;
                        else if (inkeys) {
                            keys.add(inline);
                        } else if (!inkeys) {
                            value = value + inline + "\n";
                        }
                    }

                    for (String key : keys) {
                        System.out.println(key);
                        if (!patternEmptyLine.matcher(key).find()){
                            modifing++;
                            MessagesStorage.getInstance().putTemplate(key, value);
                            templates.put(key.replace("\n", ""), value.replaceAll("\\n{3,}","\\n\\n").replaceAll("^\\s*","").replaceAll("\\s*$",""));
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            FileLog.e("TemplateSupport", "File not found");
        } catch (IOException e) {
            FileLog.e("TemplateSupport", "File IO Exception");
        }
    }

    public void putTemplate(String key, String value) {
        modifing++;
        MessagesStorage.getInstance().putTemplate(key, value);
    }

    public void removeTemplate(String key) {
        modifing++;
        MessagesStorage.getInstance().deleteTemplate(key);
    }

    public TreeMap<String, String> getAll() {
        return templates;
    }

    public static void removeAll() {
        for (String key: templates.keySet()) {
            modifing++;
            MessagesStorage.getInstance().deleteTemplate(key);
        }
    }
}
