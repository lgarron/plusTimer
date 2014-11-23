package com.pluscubed.plustimer.ui;

import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.crashlytics.android.Crashlytics;
import com.pluscubed.plustimer.BuildConfig;
import com.pluscubed.plustimer.R;
import com.pluscubed.plustimer.model.PuzzleType;
import com.pluscubed.plustimer.ui.widget.SlidingTabLayout;

import io.fabric.sdk.android.Fabric;

/**
 * Current Session Activity
 */
public class CurrentSessionActivity extends DrawerActivity implements
        SolveDialogFragment.OnDialogDismissedListener,
        CreateDialogCallback,
        CurrentSessionTimerFragment.ScrambleImageActionEnableCallback {

    public static final String DIALOG_SOLVE_TAG = "SOLVE_DIALOG";

    private static final String STATE_MENU_ITEMS_ENABLE_BOOLEAN =
            "menu_items_enable_boolean";

    private boolean mScrambleImageActionEnable;

    private int mSelectedPage;

    private boolean mInvalidateActionBarOnDrawerClosed;

    public static String makeFragmentName(int viewId, int index) {
        return "android:switcher:" + viewId + ":" + index;
    }

    @Override
    protected int getSelfNavDrawerItem() {
        return NAVDRAWER_ITEM_CURRENT_SESSION;
    }

    @Override
    public void onDialogDismissed() {
        if (getCurrentSessionTimerFragment() != null) {
            getCurrentSessionTimerFragment().onSessionSolvesChanged();
        }
        if (getSolveListFragment() != null) {
            getSolveListFragment().onSessionSolvesChanged();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_current_session);

        PuzzleType.initialize(this);

        if (BuildConfig.USE_CRASHLYTICS) {
            Fabric.with(this, new Crashlytics());
        }

        if (savedInstanceState != null) {
            mScrambleImageActionEnable = savedInstanceState.getBoolean
                    (STATE_MENU_ITEMS_ENABLE_BOOLEAN);
        }

        //Set up ViewPager with CurrentSessionAdapter
        ViewPager viewPager = (ViewPager) findViewById(R.id
                .activity_current_session_viewpager);
        viewPager.setAdapter(new CurrentSessionPagerAdapter
                (getFragmentManager(),
                        getResources().getStringArray(R.array
                                .current_session_page_titles)));

        //Set up SlidingTabLayout
        SlidingTabLayout slidingTabLayout = (SlidingTabLayout) findViewById(R
                .id.activity_current_session_slidingtablayout);
        int[] attrs = {R.attr.colorAccent};
        slidingTabLayout.setSelectedIndicatorColors(obtainStyledAttributes
                (attrs).getColor(0, Color.BLACK));
        slidingTabLayout.setDistributeEvenly(true);
        slidingTabLayout.setCustomTabView(R.layout.sliding_tab_textview,
                android.R.id.text1);
        slidingTabLayout.setViewPager(viewPager);
        slidingTabLayout.setOnPageChangeListener(new ViewPager
                .OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset,
                                       int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                mSelectedPage = position;
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager.SCROLL_STATE_DRAGGING || state ==
                        ViewPager.SCROLL_STATE_SETTLING) {
                    getCurrentSessionTimerFragment().onSessionSolvesChanged();
                    getCurrentSessionTimerFragment().stopHoldTimer();
                    getSolveListFragment().onSessionSolvesChanged();
                    getSolveListFragment().finishActionMode();
                }
            }
        });
        viewPager.setCurrentItem(0);

        getSupportActionBar().setElevation(0);

        overridePendingTransition(0, 0);
    }

    private CurrentSessionTimerFragment getCurrentSessionTimerFragment() {
        return (CurrentSessionTimerFragment) getFragmentManager()
                .findFragmentByTag(makeFragmentName(R.id
                        .activity_current_session_viewpager, 0));
    }

    private SolveListFragment getSolveListFragment() {
        return (SolveListFragment) getFragmentManager()
                .findFragmentByTag(makeFragmentName(R.id
                        .activity_current_session_viewpager, 1));
    }

    @Override
    public void enableMenuItems(boolean enable) {
        mScrambleImageActionEnable = enable;
        if (!isNavDrawerOpen()) {
            invalidateOptionsMenu();
        } else {
            // Workaround for weird bug where calling invalidateOptionsMenu()
            // while nav drawer is open doesn't call onCreateOptionsMenu()
            mInvalidateActionBarOnDrawerClosed = true;
        }
    }

    @Override
    protected void onNavDrawerClosed() {
        if (mInvalidateActionBarOnDrawerClosed) {
            invalidateOptionsMenu();
            mInvalidateActionBarOnDrawerClosed = false;
        }
    }

    @Override
    protected void onNavDrawerSlide(float offset) {
        getSolveListFragment().finishActionMode();
        getCurrentSessionTimerFragment().stopHoldTimer();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_MENU_ITEMS_ENABLE_BOOLEAN,
                mScrambleImageActionEnable);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (isNavDrawerOpen()) {
            return true;
        }
        getMenuInflater().inflate(R.menu.menu_current_session, menu);

        Spinner menuPuzzleSpinner = (Spinner) MenuItemCompat.getActionView
                (menu.findItem(R.id
                        .menu_activity_current_session_puzzletype_spinner));
        ArrayAdapter<PuzzleType> puzzleTypeSpinnerAdapter = new
                SpinnerPuzzleTypeAdapter(getLayoutInflater(),
                getSupportActionBar().getThemedContext());
        menuPuzzleSpinner.setAdapter(puzzleTypeSpinnerAdapter);
        menuPuzzleSpinner.setSelection(puzzleTypeSpinnerAdapter.getPosition
                (PuzzleType.getCurrent()), true);
        menuPuzzleSpinner.setOnItemSelectedListener(new AdapterView
                .OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                PuzzleType.setCurrent((PuzzleType) parent.getItemAtPosition
                        (position), CurrentSessionActivity.this);
                ((CurrentSessionTimerFragment) getFragmentManager()
                        .findFragmentByTag(makeFragmentName(R.id
                                        .activity_current_session_viewpager,
                                0))).onSessionChanged();
                ((SolveListFragment) getFragmentManager().findFragmentByTag
                        (makeFragmentName(R.id
                                        .activity_current_session_viewpager,
                                1))).onSessionChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        MenuItem displayScrambleImage = menu.findItem(
                R.id.menu_activity_current_session_scramble_image_menuitem);
        MenuItem share = menu.findItem(R.id.menu_solvelist_share_menuitem);
        MenuItem addSolve = menu.findItem(R.id.menu_solvelist_add_menuitem);

        if (displayScrambleImage != null) {
            displayScrambleImage.setEnabled(mScrambleImageActionEnable);
            displayScrambleImage.getIcon()
                    .setAlpha(mScrambleImageActionEnable ? 255 : 96);
        }

        if (share != null && displayScrambleImage != null && addSolve != null) {
            if (mSelectedPage == 0) {
                share.setVisible(false);
                displayScrambleImage.setVisible(true);
                addSolve.setVisible(false);
            } else {
                displayScrambleImage.setVisible(false);
                share.setVisible(true);
                /*TODO: Set visible to true once dialog editing is fully
                implemented*/
                addSolve.setVisible(false);
            }
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void createSolveDialog(String displayName, int sessionIndex,
                                  int solveIndex) {
        DialogFragment dialog = (DialogFragment) getFragmentManager()
                .findFragmentByTag(DIALOG_SOLVE_TAG);
        if (dialog == null) {
            SolveDialogFragment d = SolveDialogFragment.newInstance
                    (PuzzleType.getCurrent().name(),
                            PuzzleType.CURRENT_SESSION, solveIndex);
            d.show(getFragmentManager(), DIALOG_SOLVE_TAG);
        }
    }

    public class CurrentSessionPagerAdapter extends FragmentPagerAdapter {

        private final String[] mPageTitles;

        public CurrentSessionPagerAdapter(FragmentManager fm,
                                          String[] pageTitles) {
            super(fm);
            mPageTitles = pageTitles;
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return new CurrentSessionTimerFragment();
                case 1:
                    return SolveListFragment.newInstance(true,
                            PuzzleType.getCurrent().name(),
                            PuzzleType.CURRENT_SESSION);
            }
            return null;
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (mPageTitles.length == 2) {
                return mPageTitles[position];
            }
            return null;
        }
    }
}
