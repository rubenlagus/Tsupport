package org.tsupport.android;

import org.tsupport.messenger.FileLog;
import org.tsupport.ui.ApplicationLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateTemplatesNotification);
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
        try {
            InputStream is;
            if (loadDefault)
                is = ApplicationLoader.applicationContext.getAssets().open(fileName);
            else {
                File f = new File(fileName);
                is = new FileInputStream(f);
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String fileString = "";
            if (br.ready()) {
                String line;
                boolean started = false;

                while ((line = br.readLine()) != null) {
                    fileString += line;
                    fileString += "\n";
                }
            }
            Pattern mainPattern = Pattern.compile("[{]KEYS[}]\\n([^{]+)[{]VALUE[}]\\n([^{]+)");
            Pattern keysPattern = Pattern.compile("((\\w+))");
            Matcher mainMatcher = mainPattern.matcher(fileString);
            FileLog.e("TemplateSupport", "File: " + fileString);
            FileLog.e("TemplateSupport", "Step 1");
            while(mainMatcher.find()) {
                String keys = mainMatcher.group(1);
                String value = mainMatcher.group(2).replaceAll("\\n{3,}","\\n\\n").replaceAll("^\\s*","").replaceAll("\\s*$","");
                Matcher keysMatcher = keysPattern.matcher(keys);
                while(keysMatcher.find()) {
                    String key = keysMatcher.group(0).replace("\n","");
                    if (key.compareToIgnoreCase("") != 0){
                        FileLog.e("TemplateSupport", "Adding" + key);
                        modifing++;
                        MessagesStorage.getInstance().putTemplate(key, value);
                        templates.put(key, value);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            FileLog.e("TemplateSupport", "File not found");
        } catch (IOException e) {
            FileLog.e("TemplateSupport", "File IO Exception");
        }
        FileLog.e("TemplateSupport", "Step 2");
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
