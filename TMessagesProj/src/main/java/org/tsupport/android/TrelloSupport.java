package org.tsupport.android;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.AsyncTask;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.tsupport.messenger.BuildVars;
import org.tsupport.messenger.FileLog;
import org.tsupport.messenger.TLRPC;
import org.tsupport.ui.ApplicationLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Ruben Bermudez on 19/10/14.
 */
public class TrelloSupport {

    private static final String getCards = "https://trello.com/1/boards/ll9sbLcd/cards?fields=shortLink,labels,name,idList&key=@myapikey@&token=@mytoken@";
    private static final String iOSId = "5413f3adf0f537c87a136abf";
    private static final String androidId = "5413f3adf0f537c87a136abe";
    private static final String wpId = "5413f3adf0f537c87a136ac0";
    private static final String desktopId = "5413f3adf0f537c87a136ac1";
    private static final String osXId = "5413f3adf0f537c87a136ac2";
    private static final String webogramId = "5413f3adf0f537c87a136ac3";
    private static final String globalId = "542576c9918041a6c8d7f193";

    private String token = "";
    private boolean loading = false;
    public ArrayList<Map.Entry<String,String>> openIssuesList = new ArrayList<Map.Entry<String,String>>();
    public ArrayList<Map.Entry<String,String>> closedIssuesList = new ArrayList<Map.Entry<String,String>>();
    /**
     * Singleton Instance
     */
    private static volatile TrelloSupport Instance = null;

    class LoadIssuesAsync extends AsyncTask<Void, Void, Void> {

        private final ReentrantLock lock = new ReentrantLock();
        private final Condition tryAgain = lock.newCondition();

        @Override
        protected Void doInBackground(Void... params) {

            try {
                lock.lockInterruptibly();

                TrelloSupport.getInstance().loadIssues();

                tryAgain.await();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            tryAgain.signal();
            lock.unlock();
        }

        public void runAgain() {
            tryAgain.signal();
        }

        public void terminateTask() {
            lock.unlock();
        }

        @Override
        protected void onCancelled() {
            terminateTask();
        }
    }

    private LoadIssuesAsync loadIssuesAsync;

    public static TrelloSupport getInstance() {
        TrelloSupport localInstance = Instance;
        if (localInstance != null && localInstance.getToken().compareToIgnoreCase("") == 0)
            localInstance = null;
        if (localInstance == null) {
            synchronized (TrelloSupport.class) {
                if (localInstance == null) {
                    Instance = localInstance = new TrelloSupport();
                }
            }
        }
        return localInstance;
    }

    private TrelloSupport() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("trello", Activity.MODE_PRIVATE);
        token = preferences.getString("token", "");
        loadIssuesAsync = new LoadIssuesAsync();
        loadIssuesAsync();
    }

    public void loadIssues() {
        HashMap<String, String> openIssuesTemp = new HashMap<String, String>();
        HashMap<String, String> closedIssuesTemp = new HashMap<String, String>();
        StringBuilder builder = new StringBuilder();
        HttpClient client = new DefaultHttpClient();
        String getCardsURL = getCards.replace("@myapikey@", BuildVars.TRELLO_API_KEY).replace("@mytoken@", token);
        HttpGet httpGet = new HttpGet(getCardsURL);
        try{
            HttpResponse response = client.execute(httpGet);
            StatusLine statusLine = response.getStatusLine();
            int statusCode = statusLine.getStatusCode();
            if(statusCode == 200){
                HttpEntity entity = response.getEntity();
                InputStream content = entity.getContent();
                BufferedReader reader = new BufferedReader(new InputStreamReader(content));
                String line;
                while((line = reader.readLine()) != null){
                    builder.append(line);
                }
                try{
                    JSONArray jsonArray = new JSONArray(builder.toString());

                    for (int i=0; i<jsonArray.length(); i++) {
                        JSONObject jsonObject = jsonArray.getJSONObject(i);
                        if (jsonObject.getString("shortLink").compareToIgnoreCase("Xc5l7jiP") == 0) {
                            continue;
                        }
                        JSONArray labels = jsonObject.getJSONArray("labels");
                        String preline = "";
                        if(jsonObject.getString("idList").compareToIgnoreCase(iOSId) == 0 ) {
                            preline = "[IOS]";
                        } else if(jsonObject.getString("idList").compareToIgnoreCase(androidId) == 0 ) {
                            preline = "[AND]";
                        } else if(jsonObject.getString("idList").compareToIgnoreCase(wpId) == 0 ) {
                            preline = "[WP]";
                        } else if(jsonObject.getString("idList").compareToIgnoreCase(desktopId) == 0 ) {
                            preline = "[TDE]";
                        } else if(jsonObject.getString("idList").compareToIgnoreCase(osXId) == 0 ) {
                            preline = "[OSX]";
                        } else if(jsonObject.getString("idList").compareToIgnoreCase(webogramId) == 0 ) {
                            preline = "[WEB]";
                        } else if(jsonObject.getString("idList").compareToIgnoreCase(globalId) == 0 ) {
                            preline = "[GLO]";
                        }
                        for (int j=0; j<labels.length(); j++) {
                            JSONObject label = labels.getJSONObject(j);
                            if (label.getString("color").compareToIgnoreCase("green") == 0) {
                                closedIssuesTemp.put(jsonObject.getString("shortLink"), preline + " " +jsonObject.getString("name"));
                            } else if (label.getString("color").compareToIgnoreCase("blue") != 0){
                                openIssuesTemp.put(jsonObject.getString("shortLink"), preline + " " + jsonObject.getString("name"));
                            }
                        }
                    }

                    openIssuesList = new ArrayList<Map.Entry<String,String>>(openIssuesTemp.entrySet());

                    Collections.sort(openIssuesList, new Comparator<Map.Entry<String, String>>() {
                        // Note: this comparator imposes orderings that are inconsistent with equals.
                        @Override
                        public int compare(Map.Entry<String, String> entry1, Map.Entry<String, String> entry2) {
                            if (entry1.getValue().compareToIgnoreCase(entry2.getValue()) <= 0) {
                                return -1;
                            } else {
                                return 1;
                            }
                        }
                    });

                    closedIssuesList = new ArrayList<Map.Entry<String,String>>(closedIssuesTemp.entrySet());

                    Collections.sort(closedIssuesList, new Comparator<Map.Entry<String, String>>() {
                        @Override
                        public int compare(Map.Entry<String, String> entry1, Map.Entry<String, String> entry2) {
                            if (entry1.getValue().compareToIgnoreCase(entry2.getValue()) <= 0) {
                                return -1;
                            } else {
                                return 1;
                            }
                        }
                    });

                    FileLog.d("tsupportTrello", "Loading finished");
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.trelloLoaded);

                } catch(Exception e){
                    e.printStackTrace();
                    FileLog.e("tsupportTrello", "Error loading ", e);
                }

            }
        }catch(ClientProtocolException e){
            e.printStackTrace();
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    public void loadIssuesAsync() {
        AsyncTask.Status status = loadIssuesAsync.getStatus();
        if (status == AsyncTask.Status.RUNNING) {
            FileLog.d("tsupportTrello","Closed because of running");
            return;
        } else {
            FileLog.d("tsupportTrello","Executing");
            try {
                loadIssuesAsync.execute();
            } catch (IllegalStateException e) {
                FileLog.e("tsupportTrello", "Error launching task ", e);
            }
        }
    }

    public String getToken() {
        return token;
    }
}
