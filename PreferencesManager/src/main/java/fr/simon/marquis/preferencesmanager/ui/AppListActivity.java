/*
 * Copyright (C) 2013 Simon Marquis (http://www.simon-marquis.fr)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package fr.simon.marquis.preferencesmanager.ui;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.SearchView.OnQueryTextListener;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

import java.util.ArrayList;

import fr.simon.marquis.preferencesmanager.R;
import fr.simon.marquis.preferencesmanager.model.AppEntry;
import fr.simon.marquis.preferencesmanager.util.Ui;
import fr.simon.marquis.preferencesmanager.util.Utils;
import se.emilsjolander.stickylistheaders.StickyListHeadersListView;

public class AppListActivity extends ActionBarActivity {

    private static final int REQUEST_CODE_PREFERENCES_ACTIVITY = 123;

    private StickyListHeadersListView listView;
    private AppAdapter mAdapter;
    private View loadingView;
    private View emptyView;
    private SearchView mSearchView;
    private GetApplicationsTask task;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_list);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setTitle(Ui.applyCustomTypeFace(getString(R.string.app_name), this));
        }

        loadingView = findViewById(R.id.loadingView);
        emptyView = findViewById(R.id.emptyView);
        listView = (StickyListHeadersListView) findViewById(R.id.listView);
        listView.setDrawingListUnderStickyHeader(false);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                startPreferencesActivity((AppEntry) mAdapter.getItem(arg2));
            }
        });

        if (savedInstanceState == null || Utils.getPreviousApps() == null) {
            startTask();
        } else {
            updateListView(Utils.getPreviousApps());
        }

        if (savedInstanceState == null) {
            checkIfIsRoot();
        }

    }

    private void checkIfIsRoot() {
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                if (!App.getRoot().connected()) {
                    Utils.displayNoRoot(getFragmentManager());
                }
            }
        });
    }

    private void checkIfIsRootForce() {
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                if (!App.getRootForce().connected()) {
                    Utils.displayNoRoot(getFragmentManager());
                }
            }
        });
    }

    /**
     * Start the PreferencesActivity with supplied AppEntry
     *
     * @param app to browse
     */
    private void startPreferencesActivity(AppEntry app) {
        if (!App.getRoot().connected()) {
            checkIfIsRootForce();
        } else {
            Intent i = new Intent(AppListActivity.this, PreferencesActivity.class);
            i.putExtra(PreferencesActivity.EXTRA_TITLE, app.getLabel());
            i.putExtra(PreferencesActivity.EXTRA_PACKAGE_NAME, app.getApplicationInfo().packageName);
            startActivityForResult(i, REQUEST_CODE_PREFERENCES_ACTIVITY);
        }
    }

    /**
     * @return true if a new task is started
     */
    private boolean startTask() {
        if (task == null || task.isCancelled()) {
            task = new GetApplicationsTask(this);
            if (Utils.hasHONEYCOMB()) {
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
            } else {
                task.execute();
            }
            return true;
        }
        return false;
    }

    /**
     * Update ListView with provided apps
     *
     * @param apps List of applications
     */
    private void updateListView(ArrayList<AppEntry> apps) {
        mAdapter = new AppAdapter(this, apps, emptyView);
        listView.setAdapter(mAdapter);
        setListState(false);
    }

    /**
     * Switch ListView to loading/loaded state
     *
     * @param loading state to apply
     */
    private void setListState(boolean loading) {
        Ui.animateView(this, loadingView, loading, loading);
        Ui.animateView(this, listView, !loading, !loading);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PREFERENCES_ACTIVITY) {
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.app_list_activity, menu);

        MenuItem searchItem = menu.findItem(R.id.menu_search);
        mSearchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        mSearchView.setQueryHint(getString(R.string.action_search));
        mSearchView.setOnQueryTextListener(new OnQueryTextListener() {

            @Override
            public boolean onQueryTextSubmit(String s) {
                Ui.hideSoftKeyboard(getApplicationContext(), mSearchView);
                mSearchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                return updateFilter(s);
            }
        });

        MenuItemCompat.setOnActionExpandListener(searchItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                return updateFilter(null);
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean show = Utils.isShowSystemApps(this);
        MenuItem item = menu.findItem(R.id.show_system_apps);
        if (item != null) {
            item.setTitle(show ? R.string.hide_system_apps : R.string.show_system_apps);
            item.setIcon(show ? R.drawable.ic_action_show : R.drawable.ic_action_hide);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.show_popup:
                AboutDialog.show(getFragmentManager(), false);
                break;
            case R.id.show_system_apps:
                Utils.setShowSystemApps(this, !Utils.isShowSystemApps(this));
                if (!startTask()) {
                    Utils.setShowSystemApps(this, !Utils.isShowSystemApps(this));
                }
                invalidateOptionsMenu();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean updateFilter(String s) {
        String filter = !TextUtils.isEmpty(s) ? s.trim() : null;
        if (mAdapter == null) {
            return false;
        }

        mAdapter.setFilter(filter);
        mAdapter.getFilter().filter(filter);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (AboutDialog.alreadyDisplayed(this)) {
            super.onBackPressed();
        } else {
            AboutDialog.show(getFragmentManager(), true);
        }
    }

    public class GetApplicationsTask extends AsyncTask<Void, Void, ArrayList<AppEntry>> {
        private final Context mContext;

        public GetApplicationsTask(Context ctx) {
            this.mContext = ctx;
        }

        @Override
        protected void onPreExecute() {
            setListState(true);
            super.onPreExecute();
        }

        @Override
        protected ArrayList<AppEntry> doInBackground(Void... params) {
            return Utils.getApplications(mContext);
        }

        @Override
        protected void onPostExecute(ArrayList<AppEntry> result) {
            super.onPostExecute(result);
            updateListView(result);
            finishTask();
        }

        private void finishTask() {
            task = null;
        }

        @Override
        protected void onCancelled() {
            finishTask();
            super.onCancelled();
        }

    }

}
