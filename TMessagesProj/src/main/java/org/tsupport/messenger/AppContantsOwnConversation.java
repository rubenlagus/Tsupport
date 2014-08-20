package org.tsupport.messenger;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;

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

    public static final String WEB_CLIENT_ID = "YOUR WEB CLIENT HERE";

    public static final String AUDIENCE = "server:client_id:" + WEB_CLIENT_ID;

    public static final String EMAIL_ID = "YOUR EMAIL HERE";

     /**
     * Class instance of the JSON factory.
     */
    public static final JsonFactory JSON_FACTORY = new AndroidJsonFactory();

    /**
     * Class instance of the HTTP transport.
     */
    public static final HttpTransport HTTP_TRANSPORT = AndroidHttp.newCompatibleTransport();


    public static com.appspot.tsupport_us.ownedConversation.OwnedConversation getApiServiceHandleUS() {
        com.appspot.tsupport_us.ownedConversation.OwnedConversation.Builder ownedConversation = new com.appspot.tsupport_us.ownedConversation.OwnedConversation.Builder(AppContantsOwnConversation.HTTP_TRANSPORT,
                AppContantsOwnConversation.JSON_FACTORY, null);

        return ownedConversation.build();
    }

    public static com.appspot.tsupport_nl.ownedConversation.OwnedConversation getApiServiceHandleNL() {
        com.appspot.tsupport_nl.ownedConversation.OwnedConversation.Builder ownedConversation = new com.appspot.tsupport_nl.ownedConversation.OwnedConversation.Builder(AppContantsOwnConversation.HTTP_TRANSPORT,
                AppContantsOwnConversation.JSON_FACTORY, null);

        return ownedConversation.build();
    }

    public static com.appspot.tsupport_es.ownedConversation.OwnedConversation getApiServiceHandleES() {
        com.appspot.tsupport_es.ownedConversation.OwnedConversation.Builder ownedConversation = new com.appspot.tsupport_es.ownedConversation.OwnedConversation.Builder(AppContantsOwnConversation.HTTP_TRANSPORT,
                AppContantsOwnConversation.JSON_FACTORY, null);

        return ownedConversation.build();
    }

    public static com.appspot.tsupport_it.ownedConversation.OwnedConversation getApiServiceHandleIT() {
        com.appspot.tsupport_it.ownedConversation.OwnedConversation.Builder ownedConversation = new com.appspot.tsupport_it.ownedConversation.OwnedConversation.Builder(AppContantsOwnConversation.HTTP_TRANSPORT,
                AppContantsOwnConversation.JSON_FACTORY, null);

        return ownedConversation.build();
    }

    public static com.appspot.tsupport_de.ownedConversation.OwnedConversation getApiServiceHandleDE() {
        com.appspot.tsupport_de.ownedConversation.OwnedConversation.Builder ownedConversation = new com.appspot.tsupport_de.ownedConversation.OwnedConversation.Builder(AppContantsOwnConversation.HTTP_TRANSPORT,
                AppContantsOwnConversation.JSON_FACTORY, null);

        return ownedConversation.build();
    }

    public static com.appspot.tsupport_la.ownedConversation.OwnedConversation getApiServiceHandleLA() {
        com.appspot.tsupport_la.ownedConversation.OwnedConversation.Builder ownedConversation = new com.appspot.tsupport_la.ownedConversation.OwnedConversation.Builder(AppContantsOwnConversation.HTTP_TRANSPORT,
                AppContantsOwnConversation.JSON_FACTORY, null);

        return ownedConversation.build();
    }

    public static com.appspot.tsupport_mx.ownedConversation.OwnedConversation getApiServiceHandleMX() {
        com.appspot.tsupport_mx.ownedConversation.OwnedConversation.Builder ownedConversation = new com.appspot.tsupport_mx.ownedConversation.OwnedConversation.Builder(AppContantsOwnConversation.HTTP_TRANSPORT,
                AppContantsOwnConversation.JSON_FACTORY, null);

        return ownedConversation.build();
    }

    public static com.appspot.tsupport_sg.ownedConversation.OwnedConversation getApiServiceHandleSG() {
        com.appspot.tsupport_sg.ownedConversation.OwnedConversation.Builder ownedConversation = new com.appspot.tsupport_sg.ownedConversation.OwnedConversation.Builder(AppContantsOwnConversation.HTTP_TRANSPORT,
                AppContantsOwnConversation.JSON_FACTORY, null);

        return ownedConversation.build();
    }

    public static com.appspot.tsupport_android.ownedConversation.OwnedConversation getApiServiceHandleOT() {
        com.appspot.tsupport_android.ownedConversation.OwnedConversation.Builder ownedConversation = new com.appspot.tsupport_android.ownedConversation.OwnedConversation.Builder(AppContantsOwnConversation.HTTP_TRANSPORT,
                AppContantsOwnConversation.JSON_FACTORY, null);

        return ownedConversation.build();
    }

    public static int countGoogleAccounts(Context context) {
        AccountManager am = AccountManager.get(context);
        Account[] accounts = am.getAccountsByType(GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE);
        if (accounts == null || accounts.length < 1) {
            return 0;
        } else {
            return accounts.length;
        }
    }

    public static boolean checkGooglePlayServicesAvailable(Activity activity) {
        final int connectionStatusCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(activity);
        if (GooglePlayServicesUtil.isUserRecoverableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(activity, connectionStatusCode);
            return false;
        }
        return true;
    }

    public static void showGooglePlayServicesAvailabilityErrorDialog(final Activity activity,
                                                                     final int connectionStatusCode) {
        final int REQUEST_GOOGLE_PLAY_SERVICES = 0;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Dialog dialog = GooglePlayServicesUtil.getErrorDialog(
                        connectionStatusCode, activity, REQUEST_GOOGLE_PLAY_SERVICES);
                dialog.show();
            }
        });
    }

}
