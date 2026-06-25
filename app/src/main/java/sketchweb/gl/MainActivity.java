package sketchweb.gl;

import android.os.Bundle;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.appbar.MaterialToolbar;
import java.io.File;

public class MainActivity extends AppCompatActivity {

	private ViewPager2 viewPager;
	private TabLayout tabLayout;
	private String projectId = "";
	private String projectName = "Untitled";
	private MainEditorFragment mainEditorFragment;
	private EventsFragment eventsFragment;
	private AssetsFragment assetsFragment;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		EdgeToEdge.enable(this);
		setContentView(R.layout.main);

		// Get project info from intent
		if (getIntent().hasExtra("project_id")) {
			projectId = getIntent().getStringExtra("project_id");
		}
		if (getIntent().hasExtra("project_name")) {
			projectName = getIntent().getStringExtra("project_name");
		}
		if (projectId.isEmpty() && !projectName.isEmpty()) {
			projectId = projectName;
		}

		// Initialize Toolbar
		MaterialToolbar toolbar = findViewById(R.id.toolbar);
		if (toolbar != null) {
			toolbar.setTitle(projectName);
			toolbar.setSubtitle("ID: " + projectId);
			toolbar.setNavigationOnClickListener(v -> {
				if (mainEditorFragment != null) {
					mainEditorFragment.saveProject();
				}
				finish();
			});
		}

		// Handle system back navigation to auto-save
		getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				if (mainEditorFragment != null) {
					mainEditorFragment.saveProject();
				}
				finish();
			}
		});

		tabLayout = findViewById(R.id.tabLayout);
		setupTabLayout();

		// Apply top window inset padding to the toolbar so its contents align correctly below status bar
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			if (toolbar != null) {
				toolbar.setPadding(toolbar.getPaddingLeft(), systemBars.top, toolbar.getPaddingRight(), toolbar.getPaddingBottom());
			}
			return insets;
		});
	}

	private void setupTabLayout() {
		viewPager = findViewById(R.id.view_pager);
		viewPager.setOffscreenPageLimit(2);
		viewPager.setAdapter(new FragmentStateAdapter(this) {
			@NonNull
			@Override
			public Fragment createFragment(int position) {
				if (position == 0) {
					mainEditorFragment = MainEditorFragment.newInstance(projectId, projectName);
					return mainEditorFragment;
				} else if (position == 1) {
					String currentPage = "index";
					if (mainEditorFragment != null && mainEditorFragment.getPageManager() != null) {
						currentPage = mainEditorFragment.getPageManager().getCurrentPage();
					}
					eventsFragment = EventsFragment.newInstance(projectId, currentPage);
					return eventsFragment;
				} else {
					assetsFragment = AssetsFragment.newInstance(projectId);
					return assetsFragment;
				}
			}

			@Override
			public int getItemCount() {
				return 3;
			}
		});

		new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
			if (position == 0) {
				tab.setText("View");
			} else if (position == 1) {
				tab.setText("Event");
			} else {
				tab.setText("Assets");
			}
		}).attach();

		viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
			@Override
			public void onPageSelected(int position) {
				if (position == 1 && eventsFragment != null && mainEditorFragment != null && mainEditorFragment.getPageManager() != null) {
					eventsFragment.refreshLogicList(mainEditorFragment.getPageManager().getCurrentPage());
				} else if (position == 2 && assetsFragment != null) {
					assetsFragment.refresh();
				}
			}
		});
	}

	public MainEditorFragment getMainEditorFragment() {
		return mainEditorFragment;
	}

	public void useFileAsImageSource(File file) {
		if (mainEditorFragment != null) {
			mainEditorFragment.useFileAsImageSource(file);
		}
	}
}
