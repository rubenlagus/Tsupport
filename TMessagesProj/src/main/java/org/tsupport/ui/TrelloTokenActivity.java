package org.tsupport.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.tsupport.messenger.BuildVars;
import org.tsupport.messenger.FileLog;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by ruben on 19/10/14.
 */
public class TrelloTokenActivity extends Activity {

    private static final Pattern tokenPattern = Pattern.compile("<pre>\\s*([^\\n<]+)\\s*<\\/pre>");

    private class MyJavaScriptInterface {

        private Context ctx;

        MyJavaScriptInterface(Context ctx) {
            this.ctx = ctx;
        }

        public void showHTML(String html) {
            Matcher matcher = tokenPattern.matcher(html);
            if (matcher.find()) {
                FileLog.d("tsupportTrello", "Request found: " + matcher.group(1));
                endActivity(matcher.group(1), true);
            }
        }

    }

    private static final String trelloAuthURL = "https://trello.com/1/authorize?key=@myapikey@&name=TsupportAndroid&expiration=never&response_type=token";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final WebView webview= new WebView(this);
        FileLog.d("tsupportTrello", "Request: ");
        WebSettings settings = webview.getSettings();
        settings.setJavaScriptEnabled(true);
        webview.addJavascriptInterface(new MyJavaScriptInterface(this), "HtmlViewer");
        WebViewClient webViewClient = new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url)
            {
                if (url.compareToIgnoreCase("https://trello.com/1/token/approve") == 0) {
                    webview.loadUrl("javascript:window.HtmlViewer.showHTML" +
                            "('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');");
                } else if (url.compareToIgnoreCase("") == 0) {
                    endActivity("", false);
                }
            }
        };

        webview.setWebViewClient(webViewClient);
        FileLog.d("tsupportTrello", "Request: " + trelloAuthURL.replace("@myapikey@", BuildVars.TRELLO_API_KEY));
        webview.loadUrl(trelloAuthURL.replace("@myapikey@", BuildVars.TRELLO_API_KEY));


        setContentView(webview);
    }

    public void endActivity(String token, boolean success) {
        Intent returnIntent = new Intent();
        if (success) {
            FileLog.d("tsupportTrello", "Request success");
            returnIntent.putExtra("token", token);
            setResult(Activity.RESULT_OK, returnIntent);
        } else {
            FileLog.d("tsupportTrello", "Request cancelled");
            returnIntent.putExtra("token", "");
            setResult(Activity.RESULT_CANCELED, returnIntent);
        }
        finish();
    }
}
