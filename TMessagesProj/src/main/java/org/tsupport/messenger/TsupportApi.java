package org.tsupport.messenger;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.AsyncTask;

import com.appspot.tsupport_android.ownedConversation.OwnedConversation;
import com.appspot.tsupport_android.ownedConversation.OwnedConversation.AddOwnedConversation;
import com.appspot.tsupport_android.ownedConversation.model.BooleanType;

import org.tsupport.ui.ApplicationLoader;

import java.io.IOException;


/**
 * Created by Rubenlagus on 18/08/14.
 */
public class TsupportApi {
    private static TsupportApi instance = null;
    public static final Integer ConversationOwned = 452;
    public static final Integer ConversationNotOwned = 453;

    private TsupportApi() {

    }

    public static TsupportApi getInstance() {
        if (instance == null)
            instance = new TsupportApi();
        return instance;

    }

    public void ownConversation(final Long dialogId) {
        AsyncTask<Void, Void, Boolean> getAndDisplayGreeting =
                new AsyncTask<Void, Void, Boolean> () {
                    @Override
                    protected Boolean doInBackground(Void... params) {
                        OwnedConversation apiService = AppContants.getApiServiceHandle();
                        SharedPreferences userNumberPreferences = ApplicationLoader.applicationContext.getSharedPreferences("userNumber", Activity.MODE_PRIVATE);
                        Long userId = userNumberPreferences.getLong("userId", new Long(0));
                        try {
                            AddOwnedConversation addOwnedConversation = apiService.addOwnedConversation(dialogId+"", userId+"");
                            BooleanType value = addOwnedConversation.execute();
                            return value.getBool();
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
        getAndDisplayGreeting.execute();
    }

}
