package com.medicine.app.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.medicine.app.R;
import com.medicine.app.fragments.AIFragment;
import com.medicine.app.fragments.HistoryFragment;
import com.medicine.app.fragments.HomeFragment;
import com.medicine.app.fragments.MyMedicineFragment;
import com.medicine.app.fragments.ProfileFragment;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private Fragment currentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);

        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment(), false);
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                fragment = new HomeFragment();
            } else if (id == R.id.nav_medicine) {
                fragment = new MyMedicineFragment();
            } else if (id == R.id.nav_history) {
                fragment = new HistoryFragment();
            } else if (id == R.id.nav_ai) {
                fragment = new AIFragment();
            } else if (id == R.id.nav_profile) {
                fragment = new ProfileFragment();
            }

            if (fragment != null) {
                loadFragment(fragment, true);
                return true;
            }
            return false;
        });
    }

    private void loadFragment(Fragment fragment, boolean animate) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (animate) {
            transaction.setCustomAnimations(R.anim.fade_in, R.anim.fade_out);
        }
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
        currentFragment = fragment;
    }

    public void navigateToTab(int tabId) {
        bottomNav.setSelectedItemId(tabId);
    }
}
