package org.tsupport.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.tsupport.messenger.BuildVars;
import org.tsupport.messenger.FileLog;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by ruben on 19/10/14.
 */
public class TrelloTokenActivity extends Activity {

    private static final Pattern tokenPattern = Pattern.compile("<html>\\s*\\n([^\\n<]+)\\s*<\\/html>");
    private static final Pattern tokenUnicodePattern = Pattern.compile("html(\\\\u003E|>)\\s*([^\\sn\\\\]+).*");
    private static final Pattern token64Chars = Pattern.compile("(\\w{64})");

    private class MyJavaScriptInterface {

        private Context ctx;

        MyJavaScriptInterface(Context ctx) {
            this.ctx = ctx;
        }

        public void showHTML(String html) {
            // TODO Simplify this, last method may work for everyone, NEED TO CHECK
            html = Normalizer.normalize(html, Normalizer.Form.NFKD);
            FileLog.d("tsupportTrello", "HTML code: " + html);
            try {
                String temp = html.replace("<html>", "");
                temp = temp.replace("</html>", "");
                temp = temp.replaceAll("\\s+", "");
                temp = temp.replace("\n", "");
                temp = temp.trim();
                if (temp.length() == 64) {
                    FileLog.d("tsupportTrello", "Request found: " + temp);
                    endActivity(temp, true);
                    return;
                }
            } catch (Exception e) {
                FileLog.e("tsupportTrello","Exception");
            }
            FileLog.d("tsupportTrello", "Request not found");
            FileLog.d("tsupportTrello", "Request trying tokenPattern");
            Matcher matcher = tokenPattern.matcher(html);
            if (matcher.find()) {
                if (matcher.groupCount() == 1 && matcher.group(1).length() == 64) {
                    FileLog.d("tsupportTrello", "Request found: " + matcher.group(1));
                    endActivity(matcher.group(1), true);
                    return;
                }
            }
            FileLog.d("tsupportTrello", "Request not found");
            FileLog.d("tsupportTrello", "Request trying unicode");
            matcher = tokenUnicodePattern.matcher(html);
            if (matcher.find()) {
                if (matcher.group(2).length() == 64) {
                    FileLog.d("tsupportTrello", "Request found: " + matcher.group(2));
                    endActivity(matcher.group(2), true);
                    return;
                }
            }
            FileLog.d("tsupportTrello", "Request not found");
            FileLog.d("tsupportTrello", "Request trying 64chars");
            matcher = token64Chars.matcher(html);
            if (matcher.find()) {
                if (matcher.group(1).length() == 64) {
                    endActivity(matcher.group(2), true);
                    return;
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
            public void onPageFinished(WebView view, String url)
            {
                FileLog.d("tsupportTrello", "Request result: " + url);
                if (url.toString().contains("approve")) {
                   if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                        view.evaluateJavascript("document.getElementsByTagName('pre')[0].innerHTML", new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String s) {
                                showHTML(s);
                            }
                        });
                    } else {
                        webview.loadUrl("javascript:window.HtmlViewer.showHTML" +
                                "('<html>'+document.getElementsByTagName('pre')[0].innerHTML+'</html>');");
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
        // TODO Simplify this, last method may work for everyone, NEED TO CHECK
        html = Normalizer.normalize(html, Normalizer.Form.NFKD);
        FileLog.d("tsupportTrello", "HTML code: " + html);
        try {
            String temp = html.replace("<html>", "");
            temp = temp.replace("</html>", "");
            temp = temp.replaceAll("\\s+", "");
            temp = temp.replace("\n", "");
            temp = temp.trim();
            if (temp.length() == 64) {
                FileLog.d("tsupportTrello", "Request found: " + temp);
                endActivity(temp, true);
                return;
            }
        } catch (Exception e) {
            FileLog.e("tsupportTrello","Exception");
        }
        FileLog.d("tsupportTrello", "Request not found");
        FileLog.d("tsupportTrello", "Request trying tokenPattern");
        Matcher matcher = tokenPattern.matcher(html);
        if (matcher.find()) {
            if (matcher.groupCount() == 1 && matcher.group(1).length() == 64) {
                FileLog.d("tsupportTrello", "Request found: " + matcher.group(1));
                endActivity(matcher.group(1), true);
                return;
            }
        }
        FileLog.d("tsupportTrello", "Request not found");
        FileLog.d("tsupportTrello", "Request trying unicode");
        matcher = tokenUnicodePattern.matcher(html);
        if (matcher.find()) {
            if (matcher.group(2).length() == 64) {
                FileLog.d("tsupportTrello", "Request found: " + matcher.group(2));
                endActivity(matcher.group(2), true);
                return;
            }
        }
        FileLog.d("tsupportTrello", "Request not found");
        FileLog.d("tsupportTrello", "Request trying 64chars");
        matcher = token64Chars.matcher(html);
        if (matcher.find()) {
            if (matcher.group(1).length() == 64) {
                endActivity(matcher.group(2), true);
                return;
            }
        }
    }

    public void endActivity(String token, boolean success) {
        Intent returnIntent = new Intent();
        if (success && token.length() == 64) {
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
