package org.tsupport.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.tsupport.messenger.BuildVars;
import org.tsupport.messenger.FileLog;

import java.io.IOException;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by ruben on 19/10/14.
 */
public class TrelloTokenActivity extends Activity {

    private static final Pattern tokenPattern = Pattern.compile("<pre>\\s*([^\\n<]+)\\s*<\\/pre>");
    private static final Pattern tokenUnicodePattern = Pattern.compile("[\\\\u003C<]pre[\\\\u003E>º]\\s*([^\\sn\\\\]+).*");
    private static final Pattern approvedPattern = Pattern.compile("approve");
    private static boolean loading = false;
    private String url = "";

    private class MyJavaScriptInterface {

        private Context ctx;

        MyJavaScriptInterface(Context ctx) {
            this.ctx = ctx;
        }

        public void showHTML(String html) {
            html = Normalizer.normalize(html, Normalizer.Form.NFKD);
            Matcher matcher = tokenPattern.matcher(html);
            if (matcher.find()) {
                FileLog.d("tsupportTrello", "Request found: " + matcher.group(1));
                endActivity(matcher.group(1), true);
            } else {
                FileLog.d("tsupportTrello", "Request not found: " + html);
                FileLog.d("tsupportTrello", "Request trying unicode");
                matcher = tokenUnicodePattern.matcher(html);
                if (matcher.find()) {
                    FileLog.d("tsupportTrello", "Request found unicode: " + matcher.group(1));
                    endActivity(matcher.group(1), true);
                }
            }
        }

    }

    private static final String trelloAuthURL = "https://trello.com/1/authorize?key=@myapikey@&name=TsupportAndroid&expiration=never&response_type=token";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final WebView webview= new WebView(this);
        WebSettings settings = webview.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDefaultTextEncodingName("utf-8");
        webview.addJavascriptInterface(new MyJavaScriptInterface(this), "HtmlViewer");

        WebViewClient webViewClient = new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if(Uri.parse(url).getPath().endsWith("approve")) {
                    loading = true;
                }
                return super.shouldOverrideUrlLoading(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url)
            {
                FileLog.d("tsupportTrello", "Request result: " + url);
                Matcher matcher = approvedPattern.matcher(url);
                if (url.toString().contains("approve") || matcher.find() || loading) {
                    if (matcher.find()) {
                        FileLog.d("tsupportTrello", "Matcher");
                    }
                    if (loading) {
                        FileLog.d("tsupportTrello", "Loading");
                    }
                    if (url.toString().contains("approve")) {
                        FileLog.d("tsupportTrello", "Contains");
                    }

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                        view.evaluateJavascript("document.getElementsByTagName('html')[0].innerHTML", new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String s) {
                                showHTML(s);
                            }
                        });
                    } else {
                        webview.loadUrl("javascript:window.HtmlViewer.showHTML" +
                                "('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');");
                    }
                } else if (url.compareTo("") == 0){
                    endActivity("", false);
                    FileLog.d("tsupportTrello", "Request not approved: " + url);
                } else {
                    FileLog.d("tsupportTrello", "Not found approve con string: " + url.toString().contains("approve"));
                    FileLog.d("tsupportTrello", "Not found approve: " + url.contains("approve"));
                }
            }
        };

        webview.setWebViewClient(webViewClient);
        FileLog.d("tsupportTrello", "Request: " + trelloAuthURL.replace("@myapikey@", BuildVars.TRELLO_API_KEY));
        webview.loadUrl(trelloAuthURL.replace("@myapikey@", BuildVars.TRELLO_API_KEY));


        setContentView(webview);
    }

    public void showHTML(String html) {
        html = Normalizer.normalize(html, Normalizer.Form.NFKD);
        Matcher matcher = tokenPattern.matcher(html);
        if (matcher.find()) {
            FileLog.d("tsupportTrello", "Request found: " + matcher.group(1));
            endActivity(matcher.group(1), true);
        } else {
            FileLog.d("tsupportTrello", "Request not found: " + html);
            FileLog.d("tsupportTrello", "Request trying unicode");
            matcher = tokenUnicodePattern.matcher(html);
            if (matcher.find()) {
                FileLog.d("tsupportTrello", "Request found unicode: " + matcher.group(1));
                endActivity(matcher.group(1), true);
            }
        }
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
