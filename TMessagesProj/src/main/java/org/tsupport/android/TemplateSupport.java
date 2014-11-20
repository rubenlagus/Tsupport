package org.tsupport.android;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import org.tsupport.messenger.FileLog;
import org.tsupport.messenger.R;
import org.tsupport.ui.ApplicationLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
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
    public static TreeMap<String,String> templates = new TreeMap<String, String>();

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

    /**
     * Recreate the list of templates from memory
     */
    public static void rebuildInstance() {
        if (modifing == 0) {
            modifing++;
            if (templates == null) {
                templates = new TreeMap<String, String>();
            }
            templates.clear();
            templates.putAll(MessagesStorage.getInstance().getTemplates());
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateTemplatesNotification);
            modifing--;
        }
    }

    /**
     * Load templates from default file
     */
    public static void loadDefaults() {
        InputStream is = null;
        String fileName = "template_" + LocaleController.getCurrentLanguageCode().toLowerCase() + ".txt";
        FileLog.d("tsupportTemplates", "Loading: " + fileName);
        loadFile(fileName, true);
    }

    /**
     * Load templates from a file
     * @param fileName Name of the file
     * @param loadDefault if loading default file
     */
    public static void loadFile(String fileName, boolean loadDefault) {
        if (modifing>0) {
            return;
        }
        try {
            modifing++;
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
            Pattern mainPattern = Pattern.compile("[{]KEYS[}]\\n?([^{]+)[{]VALUE[}]\\n?([^{]*)");
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
                        //modifing++;
                        //MessagesStorage.getInstance().putTemplate(key, value);
                        templates.put(key, value);
                    }
                }
            }
            MessagesStorage.getInstance().putTemplates(templates);
        } catch (FileNotFoundException e) {
            FileLog.e("TemplateSupport", "File not found");
            modifing--;
        } catch (IOException e) {
            modifing--;
            FileLog.e("TemplateSupport", "File IO Exception");
        }
        FileLog.e("TemplateSupport", "Step 2");
    }

    /**
     * Add a template
     * @param key Key of the template
     * @param value Value of the template
     */
    public void putTemplate(String key, String value) {
        modifing++;
        MessagesStorage.getInstance().putTemplate(key, value);
    }

    /**
     * Remove a template
     * @param key Key of the template to remove
     */
    public void removeTemplate(String key) {
        modifing++;
        MessagesStorage.getInstance().deleteTemplate(key);
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
    public static void removeAll() {
        modifing++;
        MessagesStorage.getInstance().clearTemplates();
    }

    /**
     * Export templates
     */
    public static void exportAll() {
        File file = null;
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
