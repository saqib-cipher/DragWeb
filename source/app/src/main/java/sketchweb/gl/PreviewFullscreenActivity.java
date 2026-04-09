package sketchweb.gl;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;

public class PreviewFullscreenActivity extends AppCompatActivity {

    private WebView webviewPreview;
    private MaterialToolbar toolbarPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview_fullscreen);

        toolbarPreview = findViewById(R.id.toolbarPreview);
        webviewPreview = findViewById(R.id.webviewPreview);

        toolbarPreview.setNavigationOnClickListener(v -> finish());

        String htmlCode = getIntent().getStringExtra("finalCode");
        if (htmlCode == null) htmlCode = "<html><body>Error loading preview</body></html>";

        WebSettings settings = webviewPreview.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        webviewPreview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
            }
        });

        webviewPreview.loadDataWithBaseURL(null, htmlCode, "text/html", "UTF-8", null);
    }
}
