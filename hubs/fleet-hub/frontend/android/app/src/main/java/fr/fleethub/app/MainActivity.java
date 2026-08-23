package fr.fleethub.app;

import android.os.Bundle;
import android.webkit.WebSettings;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Debug uniquement : autorise les XHR du WebView (https://localhost) vers le
        // backend de dev en HTTP (http://10.0.2.2:8090). Sans cela, le navigateur bloque
        // les requêtes en "mixed content". La release reste en HTTPS seul.
        if (BuildConfig.DEBUG && getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
    }
}
