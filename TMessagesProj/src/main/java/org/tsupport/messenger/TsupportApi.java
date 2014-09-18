package org.tsupport.messenger;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.AsyncTask;

import com.appspot.tsupport_android.ownedConversation.OwnedConversation;
import com.appspot.tsupport_android.ownedConversation.model.BooleanType;
import com.appspot.tsupport_android.users.Users;
import com.appspot.tsupport_android.users.model.User;

import org.tsupport.ui.ApplicationLoader;

import java.io.IOException;


/**
 * Created by Rubenlagus on 18/08/14.
 */
public class TsupportApi {
    private static TsupportApi instance = null;
    public static final Integer ConversationOwned = 452;
    public static final Integer ConversationNotOwned = 453;
    public static final Integer ConversationOwnedNotSupported = 454;
    public static final Integer ConversationOwnedDeleted = 455;

    private static OwnedConversation apiServiceOwnConversation = null;
    private static Users apiServiceUsers = null;
    private static String userId = "";
    private static String country = null;
    private static String RSA = "";


    private TsupportApi() {
        apiServiceOwnConversation = AppContantsOwnConversation.getApiServiceHandle();
        apiServiceUsers = AppContantsUser.getApiServiceHandle();

    }

    public static TsupportApi getInstance() {
        if (userId.compareToIgnoreCase("") == 0) {
            SharedPreferences userNumberPreferences = ApplicationLoader.applicationContext.getSharedPreferences("userNumber", Activity.MODE_PRIVATE);
            userId = userNumberPreferences.getString("userId", "");
            if (userId.startsWith("42410"))
                country = "US"; // North America
            else if (userId.startsWith("42431"))
                country = "NL"; // Netherlands
            else if (userId.startsWith("42434"))
                country = "ES"; // Spain
            else if (userId.startsWith("42439"))
                country = "IT"; // Italy
            else if (userId.startsWith("42449"))
                country = "DE"; // Germany, Austria, Liechtenstein and Switzerland
            else if (userId.startsWith("42450"))
                country = "LA"; // Latin America
            else if (userId.startsWith("42452"))
                country = "MX"; // Mexico
            else if (userId.startsWith("42460"))
                country = "SG"; // Malaysia, Singapore and Indonesia
            else if (userId.startsWith("42470"))
                country = "RU"; // Russia
            else if (userId.startsWith("42490"))
                country = "IN"; // India
            else if (userId.startsWith("42497"))
                country = "AR"; // Arabian world
            else
                country = "OT"; // Other
        }
        if (RSA.compareToIgnoreCase("") == 0) {
            SharedPreferences userNumberPreferences = ApplicationLoader.applicationContext.getSharedPreferences("userNumber", Activity.MODE_PRIVATE);
            RSA = userNumberPreferences.getString("RSA", "");
        }
        if (instance == null)
            instance = new TsupportApi();
        return instance;

    }

    public void addUser(final String userId) {
        AsyncTask<Void, Void, String> addUser =
                new AsyncTask<Void, Void, String>() {
                    @Override
                    protected String doInBackground(Void... params) {
                        try {
                            User user = apiServiceUsers.addUser(userId.replace(" ", "")).execute();
                            return user.getRsa();
                        } catch (IOException e) {
                            return null;
                        }
                    }

                    @Override
                    protected void onPostExecute(String result) {
                        if (result != null && result.compareToIgnoreCase("") != 0){
                            SharedPreferences userNumberPreferences = ApplicationLoader.applicationContext.getSharedPreferences("userNumber", Activity.MODE_PRIVATE);
                            SharedPreferences.Editor editor = userNumberPreferences.edit();
                            editor.putString("RSA", result);
                            editor.commit();
                            RSA = result;
                        }

                    }
                };
        addUser.execute();
    }


    public void ownConversation(final Long dialogId) {
        AsyncTask<Void, Void, Boolean> ownConversation =
                new AsyncTask<Void, Void, Boolean>() {
                    @Override
                    protected Boolean doInBackground(Void... params) {
                        try {
                            Boolean value = null;
                            value = apiServiceOwnConversation.addOwnedConversation(RSA, dialogId + "").execute().getBool();
                            return value;
                        } catch (IOException e) {
                            return false;
                        }
                    }

                    @Override
                    protected void onPostExecute(Boolean result) {
                        if (result)
                            NotificationCenter.getInstance().postNotificationName(ConversationOwned, dialogId);
                        else
                            NotificationCenter.getInstance().postNotificationName(ConversationNotOwned, dialogId);
                    }

                };
        ownConversation.execute();
    }

    public void deleteOwnedConversation(final Long dialogId) {
        AsyncTask<Void, Void, Boolean> ownConversation =
                new AsyncTask<Void, Void, Boolean>() {
                    @Override
                    protected Boolean doInBackground(Void... params) {
                        try {
                            BooleanType value = apiServiceOwnConversation.deleteOwnedConversation(RSA, dialogId + "").execute();
                            return value.getBool();
                        } catch (IOException e) {
                            return false;
                        }
                    }

                    @Override
                    protected void onPostExecute(Boolean result) {
                        if (result)
                            NotificationCenter.getInstance().postNotificationName(ConversationOwnedDeleted, dialogId);
                        else
                            NotificationCenter.getInstance().postNotificationName(ConversationOwned, dialogId);
                    }

                };
        ownConversation.execute();
    }

}
