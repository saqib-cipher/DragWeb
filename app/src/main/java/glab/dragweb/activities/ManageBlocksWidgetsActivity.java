package glab.dragweb.activities;

import glab.dragweb.R;
import glab.dragweb.activities.*;
import glab.dragweb.fragments.*;
import glab.dragweb.engine.*;
import glab.dragweb.ui.*;
import glab.dragweb.logic.*;
import glab.dragweb.codegen.*;
import glab.dragweb.data.*;
import glab.dragweb.adapters.*;
import glab.dragweb.models.*;
import glab.dragweb.util.*;
import glab.dragweb.colorpicker.*;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

public class ManageBlocksWidgetsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_dashboard);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle("Manage Blocks & Widgets");
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        MaterialCardView cardBlocks = findViewById(R.id.card_manage_blocks);
        if (cardBlocks != null) {
            cardBlocks.setOnClickListener(v -> {
                startActivity(new Intent(this, ManageBlocksActivity.class));
            });
        }

        MaterialCardView cardCategories = findViewById(R.id.card_manage_categories);
        if (cardCategories != null) {
            cardCategories.setOnClickListener(v -> {
                startActivity(new Intent(this, ManageCategoriesActivity.class));
            });
        }

        MaterialCardView cardWidgets = findViewById(R.id.card_manage_widgets);
        if (cardWidgets != null) {
            cardWidgets.setOnClickListener(v -> {
                startActivity(new Intent(this, ManageWidgetsActivity.class));
            });
        }
    }
}
