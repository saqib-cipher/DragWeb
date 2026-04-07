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

public class PreviewBottomdialogFragmentActivity extends BottomSheetDialogFragment {

	private LinearLayout linear1;
	private LinearLayout linear3;
	private WebView webview1;
	private TextView textview1;
	private MaterialButtonToggleGroup linear2;
	private Button button1;
	private Button button2;
	private Button button3;

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
			String code = getArguments().getString("finalCode");
			ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(getContext().CLIPBOARD_SERVICE);
			clipboard.setPrimaryClip(ClipData.newPlainText("html", code));
			Toast.makeText(getContext(), "HTML copied to clipboard", Toast.LENGTH_SHORT).show();
		});

		// Close button
		button3.setOnClickListener(v -> dismiss());
	}

	private void initializeLogic() {
		String code = getArguments().getString("finalCode");
		webview1.loadDataWithBaseURL(null, code, "text/html", "utf-8", null);
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
