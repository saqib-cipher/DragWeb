package sketchweb.gl;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

public class PreviewBottomdialogFragmentActivity extends BottomSheetDialogFragment {

	private LinearLayout linear1;
	private LinearLayout linear3;
	private WebView webview1;
	private TextView textview1;
	private MaterialButtonToggleGroup linear2;
	private Button button1;
	private Button button2;
	private Button button3;
	private ChipGroup chipGroupResponsive;
	private Chip chipMobile, chipTablet, chipDesktop;
	private String htmlCode;
	private int currentWidth = 375;

	@NonNull
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.preview_bottomdialog_fragment, container, false);
		initialize(savedInstanceState, view);
		initializeLogic();
		return view;
	}

	private void initialize(Bundle savedInstanceState, View view) {
		linear1 = view.findViewById(R.id.linear1);
		linear3 = view.findViewById(R.id.linear3);
		webview1 = view.findViewById(R.id.webview1);
		textview1 = view.findViewById(R.id.textview1);
		linear2 = view.findViewById(R.id.linear2);
		button1 = view.findViewById(R.id.button1);
		button2 = view.findViewById(R.id.button2);
		button3 = view.findViewById(R.id.button3);
		chipGroupResponsive = view.findViewById(R.id.chipGroupResponsive);
		chipMobile = view.findViewById(R.id.chipMobile);
		chipTablet = view.findViewById(R.id.chipTablet);
		chipDesktop = view.findViewById(R.id.chipDesktop);

		htmlCode = getArguments() != null ? getArguments().getString("finalCode") : "";

		WebSettings settings = webview1.getSettings();
		settings.setJavaScriptEnabled(true);
		settings.setSupportZoom(true);
		settings.setBuiltInZoomControls(true);
		settings.setDisplayZoomControls(false);
		settings.setUseWideViewPort(true);
		settings.setLoadWithOverviewMode(true);

		webview1.setWebViewClient(new WebViewClient() {
			@Override
			public void onPageStarted(WebView view, String url, Bitmap favicon) {
				super.onPageStarted(view, url, favicon);
			}

			@Override
			public void onPageFinished(WebView view, String url) {
				super.onPageFinished(view, url);
			}
		});

		// Save / Export button
		button1.setOnClickListener(v -> {
			Toast.makeText(getContext(), "Use Export from the main editor", Toast.LENGTH_SHORT).show();
		});

		// Copy to clipboard button
		button2.setOnClickListener(v -> {
			ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(getContext().CLIPBOARD_SERVICE);
			clipboard.setPrimaryClip(ClipData.newPlainText("html", htmlCode));
			Toast.makeText(getContext(), "HTML copied to clipboard", Toast.LENGTH_SHORT).show();
		});

		// Close button
		button3.setOnClickListener(v -> dismiss());

		// Responsive device toggle
		setupResponsiveToggle();
	}

	private void setupResponsiveToggle() {
		if (chipGroupResponsive == null) return;

		chipGroupResponsive.setOnCheckedStateChangeListener((group, checkedIds) -> {
			if (checkedIds.isEmpty()) return;
			int checkedId = checkedIds.get(0);
			if (checkedId == R.id.chipMobile) {
				currentWidth = 375;
				textview1.setText("Preview - Mobile");
			} else if (checkedId == R.id.chipTablet) {
				currentWidth = 768;
				textview1.setText("Preview - Tablet");
			} else if (checkedId == R.id.chipDesktop) {
				currentWidth = 1024;
				textview1.setText("Preview - Desktop");
			}
			loadResponsivePreview();
		});
	}

	private void loadResponsivePreview() {
		if (htmlCode == null || htmlCode.isEmpty()) return;

		// Inject viewport meta tag with specific width
		String viewportMeta = "<meta name=\"viewport\" content=\"width=" + currentWidth + ", initial-scale=1.0\">";
		String modifiedHtml = htmlCode;
		if (modifiedHtml.contains("<meta name=\"viewport\"")) {
			modifiedHtml = modifiedHtml.replaceAll(
				"<meta\\s+name=\"viewport\"[^>]*>",
				viewportMeta
			);
		} else if (modifiedHtml.contains("<head>")) {
			modifiedHtml = modifiedHtml.replace("<head>", "<head>" + viewportMeta);
		}

		webview1.loadDataWithBaseURL(null, modifiedHtml, "text/html", "utf-8", null);
	}

	private void initializeLogic() {
		loadResponsivePreview();
	}

	@Override
	public void onStart() {
		super.onStart();
		Dialog dialog = getDialog();
		if (dialog != null && dialog.getWindow() != null) {
			dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

			WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
			params.width = WindowManager.LayoutParams.MATCH_PARENT;
			params.height = WindowManager.LayoutParams.WRAP_CONTENT;
			params.gravity = Gravity.BOTTOM;
			dialog.getWindow().setAttributes(params);
		}
	}
}
