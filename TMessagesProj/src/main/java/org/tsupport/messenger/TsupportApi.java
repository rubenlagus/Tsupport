package org.tsupport.messenger;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.AsyncTask;

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
    private static com.appspot.tsupport_us.ownedConversation.OwnedConversation apiServiceOwnConversationUS = null;
    private static com.appspot.tsupport_nl.ownedConversation.OwnedConversation apiServiceOwnConversationNL = null;
    private static com.appspot.tsupport_es.ownedConversation.OwnedConversation apiServiceOwnConversationES = null;
    private static com.appspot.tsupport_it.ownedConversation.OwnedConversation apiServiceOwnConversationIT = null;
    private static com.appspot.tsupport_de.ownedConversation.OwnedConversation apiServiceOwnConversationDE = null;
    private static com.appspot.tsupport_la.ownedConversation.OwnedConversation apiServiceOwnConversationLA = null;
    private static com.appspot.tsupport_mx.ownedConversation.OwnedConversation apiServiceOwnConversationMX = null;
    private static com.appspot.tsupport_sg.ownedConversation.OwnedConversation apiServiceOwnConversationSG = null;
    private static com.appspot.tsupport_android.ownedConversation.OwnedConversation apiServiceOwnConversationOT = null;

    private static com.appspot.tsupport_us.users.Users apiServiceUsersUS = null;
    private static com.appspot.tsupport_nl.users.Users apiServiceUsersNL = null;
    private static com.appspot.tsupport_es.users.Users apiServiceUsersES = null;
    private static com.appspot.tsupport_it.users.Users apiServiceUsersIT = null;
    private static com.appspot.tsupport_de.users.Users apiServiceUsersDE = null;
    private static com.appspot.tsupport_la.users.Users apiServiceUsersLA = null;
    private static com.appspot.tsupport_mx.users.Users apiServiceUsersMX = null;
    private static com.appspot.tsupport_sg.users.Users apiServiceUsersSG = null;
    private static com.appspot.tsupport_android.users.Users apiServiceUsersOT = null;
    private static String userId = "";
    private static String country = null;


    private TsupportApi() {
        apiServiceOwnConversationUS = AppContantsOwnConversation.getApiServiceHandleUS();
        apiServiceOwnConversationNL = AppContantsOwnConversation.getApiServiceHandleNL();
        apiServiceOwnConversationES = AppContantsOwnConversation.getApiServiceHandleES();
        apiServiceOwnConversationIT = AppContantsOwnConversation.getApiServiceHandleIT();
        apiServiceOwnConversationDE = AppContantsOwnConversation.getApiServiceHandleDE();
        apiServiceOwnConversationLA = AppContantsOwnConversation.getApiServiceHandleLA();
        apiServiceOwnConversationMX = AppContantsOwnConversation.getApiServiceHandleMX();
        apiServiceOwnConversationSG = AppContantsOwnConversation.getApiServiceHandleSG();
        apiServiceOwnConversationOT = AppContantsOwnConversation.getApiServiceHandleOT();

        apiServiceUsersUS = AppContantsUser.getApiServiceHandleUS();
        apiServiceUsersNL = AppContantsUser.getApiServiceHandleNL();
        apiServiceUsersES = AppContantsUser.getApiServiceHandleES();
        apiServiceUsersIT = AppContantsUser.getApiServiceHandleIT();
        apiServiceUsersDE = AppContantsUser.getApiServiceHandleDE();
        apiServiceUsersLA = AppContantsUser.getApiServiceHandleLA();
        apiServiceUsersMX = AppContantsUser.getApiServiceHandleMX();
        apiServiceUsersSG = AppContantsUser.getApiServiceHandleSG();
        apiServiceUsersOT = AppContantsUser.getApiServiceHandleOT();

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
            else
                country = "OT"; // Other
        }
        if (instance == null)
            instance = new TsupportApi();
        return instance;

    }

    public void addUser() {
        AsyncTask<Void, Void, Void> addUser =
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        try {
                            if (country.compareToIgnoreCase("US") == 0)
                                apiServiceUsersUS.addUser(userId);
                            else if (country.compareToIgnoreCase("NL") == 0)
                                apiServiceUsersNL.addUser(userId);
                            else if (country.compareToIgnoreCase("ES") == 0)
                                apiServiceUsersES.addUser(userId);
                            else if (country.compareToIgnoreCase("IT") == 0)
                                apiServiceUsersIT.addUser(userId);
                            else if (country.compareToIgnoreCase("DE") == 0)
                                apiServiceUsersDE.addUser(userId);
                            else if (country.compareToIgnoreCase("LA") == 0)
                                apiServiceUsersLA.addUser(userId);
                            else if (country.compareToIgnoreCase("MX") == 0)
                                apiServiceUsersMX.addUser(userId);
                            else if (country.compareToIgnoreCase("SG") == 0)
                                apiServiceUsersSG.addUser(userId);
                            else
                                apiServiceUsersOT.addUser(userId);
                        } catch (IOException e) {
                            return null;
                        }
                        return null;
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
                            if (country.compareToIgnoreCase("US") == 0)
                                value = apiServiceOwnConversationUS.addOwnedConversation(dialogId + "", userId).execute().getBool();
                            else if (country.compareToIgnoreCase("NL") == 0)
                                value = apiServiceOwnConversationNL.addOwnedConversation(dialogId + "", userId).execute().getBool();
                            else if (country.compareToIgnoreCase("ES") == 0)
                                value = apiServiceOwnConversationES.addOwnedConversation(dialogId + "", userId).execute().getBool();
                            else if (country.compareToIgnoreCase("IT") == 0)
                                value = apiServiceOwnConversationIT.addOwnedConversation(dialogId + "", userId).execute().getBool();
                            else if (country.compareToIgnoreCase("DE") == 0)
                                value = apiServiceOwnConversationDE.addOwnedConversation(dialogId + "", userId).execute().getBool();
                            else if (country.compareToIgnoreCase("LA") == 0)
                                value = apiServiceOwnConversationLA.addOwnedConversation(dialogId + "", userId).execute().getBool();
                            else if (country.compareToIgnoreCase("MX") == 0)
                                value = apiServiceOwnConversationMX.addOwnedConversation(dialogId + "", userId).execute().getBool();
                            else if (country.compareToIgnoreCase("SG") == 0)
                                value = apiServiceOwnConversationSG.addOwnedConversation(dialogId + "", userId).execute().getBool();
                            else
                                value = apiServiceOwnConversationOT.addOwnedConversation(dialogId + "", userId).execute().getBool();

                            return value;
                        } catch (IOException e) {
                            return false;
                        }
                    }

                    @Override
                    protected void onPostExecute(Boolean result) {
                        if (result)
                            NotificationCenter.getInstance().postNotificationName(ConversationOwned);
                        else
                            NotificationCenter.getInstance().postNotificationName(ConversationNotOwned);
                    }

                };
        ownConversation.execute();
    }

}
