package org.telegram.messenger;

import com.appspot.tsupport_android.users.Users;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;

public class AppConstantsUser {

    /**
     * Class instance of the JSON factory.
     */
    public static final JsonFactory JSON_FACTORY = new AndroidJsonFactory();

    /**
     * Class instance of the HTTP transport.
     */
    public static final HttpTransport HTTP_TRANSPORT = AndroidHttp.newCompatibleTransport();


    public static Users getApiServiceHandle() {
        Users.Builder users = new Users.Builder(AppConstantsUser.HTTP_TRANSPORT,
                AppConstantsUser.JSON_FACTORY,null);

        return users.build();
    }
}
