package org.tsupport.messenger;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;

import com.appspot.tsupport_android.ownedConversation.OwnedConversation;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.common.GooglePlayServicesUtil;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;

/**
 * Created by ruben on 18/08/14.
 */
public class AppContantsOwnConversation {

     /**
     * Class instance of the JSON factory.
     */
    public static final JsonFactory JSON_FACTORY = new AndroidJsonFactory();

    /**
     * Class instance of the HTTP transport.
     */
    public static final HttpTransport HTTP_TRANSPORT = AndroidHttp.newCompatibleTransport();

    public static OwnedConversation getApiServiceHandle() {
        OwnedConversation.Builder ownedConversation = new OwnedConversation.Builder(AppContantsOwnConversation.HTTP_TRANSPORT,
                AppContantsOwnConversation.JSON_FACTORY, null);

        return ownedConversation.build();
    }
}
