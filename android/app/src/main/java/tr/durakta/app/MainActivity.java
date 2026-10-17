package tr.durakta.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Durakta Android uygulaması.
 * GitHub Pages'teki siteyi açar; böylece her gece güncellenen saatler
 * uygulamaya da kendiliğinden gelir. İnternet yokken son açılan sayfalar
 * önbellekten gösterilir.
 */
public class MainActivity extends Activity {

    private static final String SITE = "https://enskygr234.github.io/durakta/";
    private static final String SITE_HOST = "enskygr234.github.io";

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#13233A"));
        w.setNavigationBarColor(Color.parseColor("#13233A"));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#13233A"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // favoriler için localStorage
        s.setCacheMode(cevrimici() ? WebSettings.LOAD_DEFAULT : WebSettings.LOAD_CACHE_ELSE_NETWORK);
        s.setUserAgentString(s.getUserAgentString() + " DuraktaApp/1");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (SITE_HOST.equals(u.getHost()) && u.getPath() != null && u.getPath().startsWith("/durakta")) {
                    return false; // kendi sitemiz: uygulamada aç
                }
                // EGO, OpenStreetMap vb. dış bağlantılar tarayıcıda açılsın
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (ActivityNotFoundException ignored) { }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) {
                    view.loadDataWithBaseURL(null, cevrimdisiSayfa(), "text/html", "utf-8", null);
                }
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            Uri gelen = getIntent().getData();
            web.loadUrl(gelen != null && SITE_HOST.equals(gelen.getHost()) ? gelen.toString() : SITE);
        }
    }

    private boolean cevrimici() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm == null) return true;
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private String cevrimdisiSayfa() {
        return "<!doctype html><html lang='tr'><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<style>body{margin:0;min-height:100vh;display:flex;flex-direction:column;justify-content:center;"
            + "padding:32px;box-sizing:border-box;background:#13233A;color:#fff;font:17px/1.5 sans-serif}"
            + "h1{font-size:26px;margin:0 0 8px}p{color:#B9C6D8;margin:0 0 24px}"
            + "a{display:inline-block;background:#F2B705;color:#1D1600;padding:14px 22px;border-radius:10px;"
            + "text-decoration:none;font-weight:600}</style></head><body>"
            + "<h1>İnternet bağlantısı yok</h1>"
            + "<p>Saatleri yüklemek için bağlantı gerekiyor. Daha önce açtığın hatlar bağlantı gelince yeniden görünür.</p>"
            + "<div><a href='" + SITE + "'>Yeniden dene</a></div></body></html>";
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        web.getSettings().setCacheMode(cevrimici() ? WebSettings.LOAD_DEFAULT : WebSettings.LOAD_CACHE_ELSE_NETWORK);
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }
}
