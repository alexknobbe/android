/**
 * Nextcloud Android client application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2016 Andy Scherzinger
 * Copyright (C) 2016 Nextcloud
 * Copyright (C) 2016 ownCloud Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU AFFERO GENERAL PUBLIC LICENSE
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU AFFERO GENERAL PUBLIC LICENSE for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.owncloud.android.ui.activity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.owncloud.android.MainApp;
import com.owncloud.android.R;
import com.owncloud.android.authentication.AccountUtils;
import com.owncloud.android.datamodel.ExternalLinksProvider;
import com.owncloud.android.datamodel.OCFile;
import com.owncloud.android.lib.common.ExternalLink;
import com.owncloud.android.lib.common.ExternalLinkType;
import com.owncloud.android.lib.common.OwnCloudAccount;
import com.owncloud.android.lib.common.Quota;
import com.owncloud.android.lib.common.UserInfo;
import com.owncloud.android.lib.common.accounts.ExternalLinksOperation;
import com.owncloud.android.lib.common.operations.RemoteOperation;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.lib.resources.files.SearchOperation;
import com.owncloud.android.lib.resources.users.GetRemoteUserInfoOperation;
import com.owncloud.android.operations.GetCapabilitiesOperarion;
import com.owncloud.android.ui.TextDrawable;
import com.owncloud.android.ui.events.ChangeMenuEvent;
import com.owncloud.android.ui.events.DummyDrawerEvent;
import com.owncloud.android.ui.events.MenuItemClickEvent;
import com.owncloud.android.ui.events.SearchEvent;
import com.owncloud.android.utils.DisplayUtils;
import com.owncloud.android.utils.svg.MenuSimpleTarget;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;

/**
 * Base class to handle setup of the drawer implementation including user switching and avatar fetching and fallback
 * generation.
 */
public abstract class DrawerActivity extends ToolbarActivity implements DisplayUtils.AvatarGenerationListener {
    private static final String TAG = DrawerActivity.class.getSimpleName();
    private static final String KEY_IS_ACCOUNT_CHOOSER_ACTIVE = "IS_ACCOUNT_CHOOSER_ACTIVE";
    private static final String KEY_CHECKED_MENU_ITEM = "CHECKED_MENU_ITEM";
    private static final String EXTERNAL_LINKS_COUNT = "EXTERNAL_LINKS_COUNT";
    private static final int ACTION_MANAGE_ACCOUNTS = 101;
    private static final int MENU_ORDER_ACCOUNT = 1;
    private static final int MENU_ORDER_ACCOUNT_FUNCTION = 2;
    private static final int MENU_ORDER_EXTERNAL_LINKS = 3;
    private static final int MENU_ITEM_EXTERNAL_LINK = 111;
    /**
     * menu account avatar radius.
     */
    private float mMenuAccountAvatarRadiusDimension;

    /**
     * current account avatar radius.
     */
    private float mCurrentAccountAvatarRadiusDimension;

    /**
     * other accounts avatar radius.
     */
    private float mOtherAccountAvatarRadiusDimension;

    /**
     * Reference to the drawer layout.
     */
    protected DrawerLayout mDrawerLayout;

    /**
     * Reference to the drawer toggle.
     */
    protected ActionBarDrawerToggle mDrawerToggle;

    /**
     * Reference to the navigation view.
     */
    private NavigationView mNavigationView;

    /**
     * Reference to the account chooser toggle.
     */
    private ImageView mAccountChooserToggle;

    /**
     * Reference to the middle account avatar.
     */
    private ImageView mAccountMiddleAccountAvatar;

    /**
     * Reference to the end account avatar.
     */
    private ImageView mAccountEndAccountAvatar;

    /**
     * Flag to signal if the account chooser is active.
     */
    private boolean mIsAccountChooserActive;

    /**
     * Id of the checked menu item.
     */
    private int mCheckedMenuItem = Menu.NONE;

    /**
     * accounts for the (max) three displayed accounts in the drawer header.
     */
    private Account[] mAvatars = new Account[3];

    /**
     * container layout of the quota view.
     */
    private LinearLayout mQuotaView;

    /**
     * progress bar of the quota view.
     */
    private ProgressBar mQuotaProgressBar;

    /**
     * text view of the quota view.
     */
    private TextView mQuotaTextPercentage;
    private TextView mQuotaTextLink;

    /**
     * runnable that will be executed after the drawer has been closed.
     */
    private Runnable pendingRunnable;

    private ExternalLinksProvider externalLinksProvider;
    private SharedPreferences sharedPreferences;

    /**
     * Initializes the drawer, its content and highlights the menu item with the given id.
     * This method needs to be called after the content view has been set.
     *
     * @param menuItemId the menu item to be checked/highlighted
     */
    protected void setupDrawer(int menuItemId) {
        setupDrawer();
        setDrawerMenuItemChecked(menuItemId);
    }

    /**
     * Initializes the drawer and its content.
     * This method needs to be called after the content view has been set.
     */
    protected void setupDrawer() {
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        mNavigationView = (NavigationView) findViewById(R.id.nav_view);
        if (mNavigationView != null) {
            setupDrawerHeader();

            setupDrawerMenu(mNavigationView);

            setupQuotaElement();

            // show folder sync menu item only for Android 6+
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M &&
                    mNavigationView.getMenu().findItem(R.id.nav_folder_sync) != null) {
                mNavigationView.getMenu().removeItem(R.id.nav_folder_sync);
            }
        }

        setupDrawerToggle();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    /**
     * initializes and sets up the drawer toggle.
     */
    private void setupDrawerToggle() {
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                // standard behavior of drawer is to switch to the standard menu on closing
                if (mIsAccountChooserActive) {
                    toggleAccountList();
                }
                invalidateOptionsMenu();
                mDrawerToggle.setDrawerIndicatorEnabled(isDrawerIndicatorAvailable());

                if (pendingRunnable != null) {
                    new Handler().post(pendingRunnable);
                    pendingRunnable = null;
                }
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                mDrawerToggle.setDrawerIndicatorEnabled(true);
                invalidateOptionsMenu();
            }
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerToggle.setDrawerIndicatorEnabled(true);
    }

    /**
     * initializes and sets up the drawer header.
     */
    private void setupDrawerHeader() {
        mAccountChooserToggle = (ImageView) findNavigationViewChildById(R.id.drawer_account_chooser_toogle);
        mAccountChooserToggle.setImageResource(R.drawable.ic_down);
        mIsAccountChooserActive = false;
        mAccountMiddleAccountAvatar = (ImageView) findNavigationViewChildById(R.id.drawer_account_middle);
        mAccountEndAccountAvatar = (ImageView) findNavigationViewChildById(R.id.drawer_account_end);

        findNavigationViewChildById(R.id.drawer_active_user)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleAccountList();
                    }
                });
    }

    /**
     * setup quota elements of the drawer.
     */
    private void setupQuotaElement() {
        mQuotaView = (LinearLayout) findViewById(R.id.drawer_quota);
        mQuotaProgressBar = (ProgressBar) findViewById(R.id.drawer_quota_ProgressBar);
        mQuotaTextPercentage = (TextView) findViewById(R.id.drawer_quota_percentage);
        mQuotaTextLink = (TextView) findViewById(R.id.drawer_quota_link);
        DisplayUtils.colorPreLollipopHorizontalProgressBar(mQuotaProgressBar);
    }

    /**
     * setup drawer content, basically setting the item selected listener.
     *
     * @param navigationView the drawers navigation view
     */
    protected void setupDrawerMenu(NavigationView navigationView) {
        // on pre lollipop the light theme adds a black tint to icons with white coloring
        // ruining the generic avatars, so tinting for icons is deactivated pre lollipop
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            navigationView.setItemIconTintList(null);
        }

        // setup actions for drawer menu items
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(final MenuItem menuItem) {
                        mDrawerLayout.closeDrawers();
                        // pending runnable will be executed after the drawer has been closed
                        pendingRunnable = new Runnable() {
                            @Override
                            public void run() {
                                selectNavigationItem(menuItem);
                            }
                        };
                        return true;
                    }
                });

        // handle correct state
        if (mIsAccountChooserActive) {
            navigationView.getMenu().setGroupVisible(R.id.drawer_menu_accounts, true);
        } else {
            navigationView.getMenu().setGroupVisible(R.id.drawer_menu_accounts, false);
        }

        Account account = AccountUtils.
                getCurrentOwnCloudAccount(MainApp.getAppContext());
        boolean searchSupported = AccountUtils.hasSearchSupport(account);

        if ((getResources().getBoolean(R.bool.bottom_toolbar_enabled) || (!searchSupported)) &&
                (account != null)){
            navigationView.getMenu().removeItem(R.id.nav_photos);
            if (getResources().getBoolean(R.bool.bottom_toolbar_enabled)) {
                navigationView.getMenu().removeItem(R.id.nav_all_files);
                navigationView.getMenu().removeItem(R.id.nav_settings);
                navigationView.getMenu().removeItem(R.id.nav_favorites);
            }
            if (!searchSupported) {
                navigationView.getMenu().removeItem(R.id.nav_videos);
            }
        }

        if (getResources().getBoolean(R.bool.use_home) && navigationView.getMenu().findItem(R.id.nav_all_files) !=
                null) {
            navigationView.getMenu().findItem(R.id.nav_all_files).setTitle(getResources().
                    getString(R.string.drawer_item_home));
            navigationView.getMenu().findItem(R.id.nav_all_files).setIcon(R.drawable.ic_home);
        }

        if (!getResources().getBoolean(R.bool.participate_enabled)) {
            navigationView.getMenu().removeItem(R.id.nav_participate);
        }

        if (!getResources().getBoolean(R.bool.shared_enabled)) {
            navigationView.getMenu().removeItem(R.id.nav_shared);
        }

        if (AccountUtils.hasSearchSupport(account)) {
            if (!getResources().getBoolean(R.bool.recently_added_enabled)) {
                navigationView.getMenu().removeItem(R.id.nav_recently_added);
            }

            if (!getResources().getBoolean(R.bool.recently_modified_enabled)) {
                navigationView.getMenu().removeItem(R.id.nav_recently_modified);
            }

            if (!getResources().getBoolean(R.bool.videos_enabled)) {
                navigationView.getMenu().removeItem(R.id.nav_videos);
            }
        } else if (account != null) {
            navigationView.getMenu().removeItem(R.id.nav_recently_added);
            navigationView.getMenu().removeItem(R.id.nav_recently_modified);
            navigationView.getMenu().removeItem(R.id.nav_videos);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MenuItemClickEvent event) {
        unsetAllDrawerMenuItems();

        switch (event.menuItem.getItemId()) {
            case R.id.nav_bar_files:
                showFiles(false);
                break;
            case R.id.nav_bar_settings:
                Intent settingsIntent = new Intent(getApplicationContext(), Preferences.class);
                startActivity(settingsIntent);
                break;
            default:
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(DummyDrawerEvent event) {
        unsetAllDrawerMenuItems();
    }


    private void selectNavigationItem(final MenuItem menuItem) {

        switch (menuItem.getItemId()) {
            case R.id.nav_all_files:
                menuItem.setChecked(true);
                mCheckedMenuItem = menuItem.getItemId();
                showFiles(false);
                EventBus.getDefault().post(new ChangeMenuEvent());
                break;
            case R.id.nav_favorites:
                menuItem.setChecked(true);
                mCheckedMenuItem = menuItem.getItemId();
                EventBus.getDefault().post(new SearchEvent("", SearchOperation.SearchType.FAVORITE_SEARCH,
                        SearchEvent.UnsetType.NO_UNSET));
                break;
            case R.id.nav_photos:
                EventBus.getDefault().post(new SearchEvent("image/%",
                        SearchOperation.SearchType.CONTENT_TYPE_SEARCH, SearchEvent.UnsetType.NO_UNSET));
                break;
            case R.id.nav_on_device:
                menuItem.setChecked(true);
                mCheckedMenuItem = menuItem.getItemId();
                showFiles(true);
                break;
            case R.id.nav_uploads:
                Intent uploadListIntent = new Intent(getApplicationContext(),
                        UploadListActivity.class);
                uploadListIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(uploadListIntent);
                break;
            case R.id.nav_activity:
                Intent activityIntent = new Intent(getApplicationContext(), ActivitiesListActivity.class);
                activityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(activityIntent);
                break;
            case R.id.nav_folder_sync:
                Intent folderSyncIntent = new Intent(getApplicationContext(), FolderSyncActivity.class);
                startActivity(folderSyncIntent);
                break;
            case R.id.nav_settings:
                Intent settingsIntent = new Intent(getApplicationContext(), Preferences.class);
                startActivity(settingsIntent);
                break;
            case R.id.nav_participate:
                Intent participateIntent = new Intent(getApplicationContext(),
                        ParticipateActivity.class);
                startActivity(participateIntent);
                break;
            case R.id.drawer_menu_account_add:
                createAccount(false);
                break;
            case R.id.drawer_menu_account_manage:
                Intent manageAccountsIntent = new Intent(getApplicationContext(),
                        ManageAccountsActivity.class);
                startActivityForResult(manageAccountsIntent, ACTION_MANAGE_ACCOUNTS);
                break;
            case R.id.nav_recently_added:
                menuItem.setChecked(true);
                mCheckedMenuItem = menuItem.getItemId();
                EventBus.getDefault().post(new SearchEvent("%", SearchOperation.SearchType.CONTENT_TYPE_SEARCH,
                        SearchEvent.UnsetType.UNSET_BOTTOM_NAV_BAR));
                break;
            case R.id.nav_recently_modified:
                menuItem.setChecked(true);
                mCheckedMenuItem = menuItem.getItemId();
                EventBus.getDefault().post(new SearchEvent("", SearchOperation.SearchType.RECENTLY_MODIFIED_SEARCH,
                        SearchEvent.UnsetType.UNSET_BOTTOM_NAV_BAR));
                break;
            case R.id.nav_shared:
                menuItem.setChecked(true);
                mCheckedMenuItem = menuItem.getItemId();
                EventBus.getDefault().post(new SearchEvent("", SearchOperation.SearchType.SHARED_SEARCH,
                        SearchEvent.UnsetType.UNSET_BOTTOM_NAV_BAR));
                break;
            case R.id.nav_videos:
                menuItem.setChecked(true);
                mCheckedMenuItem = menuItem.getItemId();
                EventBus.getDefault().post(new SearchEvent("video/%", SearchOperation.SearchType.CONTENT_TYPE_SEARCH,
                        SearchEvent.UnsetType.UNSET_BOTTOM_NAV_BAR));
                break;
            case MENU_ITEM_EXTERNAL_LINK:
                // external link clicked
                externalLinkClicked(menuItem);
                break;
            case Menu.NONE:
                // account clicked
                accountClicked(menuItem.getTitle().toString());
            default:
                Log_OC.i(TAG, "Unknown drawer menu item clicked: " + menuItem.getTitle());
        }
    }

    /**
     * show the file list to the user.
     *
     * @param onDeviceOnly flag to decide if all files or only the ones on the device should be shown
     */
    public abstract void showFiles(boolean onDeviceOnly);


    /**
     * sets the new/current account and restarts. In case the given account equals the actual/current account the
     * call will be ignored.
     *
     * @param accountName The account name to be set
     */
    private void accountClicked(String accountName) {
        if (!AccountUtils.getCurrentOwnCloudAccount(getApplicationContext()).name.equals(accountName)) {
            AccountUtils.setCurrentOwnCloudAccount(getApplicationContext(), accountName);

            fetchExternalLinks(true);

            restart();
        }
    }

    private void externalLinkClicked(MenuItem menuItem){
        for (ExternalLink link : externalLinksProvider.getExternalLink(ExternalLinkType.LINK)) {
            if (menuItem.getTitle().toString().equalsIgnoreCase(link.name)) {
                Intent externalWebViewIntent = new Intent(getApplicationContext(),
                        ExternalSiteWebView.class);
                externalWebViewIntent.putExtra(ExternalSiteWebView.EXTRA_TITLE, link.name);
                externalWebViewIntent.putExtra(ExternalSiteWebView.EXTRA_URL, link.url);
                externalWebViewIntent.putExtra(ExternalSiteWebView.EXTRA_SHOW_SIDEBAR, true);
                externalWebViewIntent.putExtra(ExternalSiteWebView.EXTRA_MENU_ITEM_ID, menuItem.getItemId());
                startActivity(externalWebViewIntent);
            }
        }
    }

    /**
     * click method for mini avatars in drawer header.
     *
     * @param view the clicked ImageView
     */
    public void onAccountDrawerClick(View view) {
        accountClicked(view.getContentDescription().toString());
    }

    /**
     * checks if the drawer exists and is opened.
     *
     * @return <code>true</code> if the drawer is open, else <code>false</code>
     */
    public boolean isDrawerOpen() {
        return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START);
    }

    /**
     * closes the drawer.
     */
    public void closeDrawer() {
        if (mDrawerLayout != null) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    /**
     * opens the drawer.
     */
    public void openDrawer() {
        if (mDrawerLayout != null) {
            mDrawerLayout.openDrawer(GravityCompat.START);

            updateExternalLinksInDrawer();
            updateQuotaLink();
        }
    }

    /**
     * Enable or disable interaction with all drawers.
     *
     * @param lockMode The new lock mode for the given drawer. One of {@link DrawerLayout#LOCK_MODE_UNLOCKED},
     *                 {@link DrawerLayout#LOCK_MODE_LOCKED_CLOSED} or {@link DrawerLayout#LOCK_MODE_LOCKED_OPEN}.
     */
    public void setDrawerLockMode(int lockMode) {
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerLockMode(lockMode);
        }
    }

    /**
     * Enable or disable the drawer indicator.
     *
     * @param enable <code>true</code> to enable, <code>false</code> to disable
     */
    public void setDrawerIndicatorEnabled(boolean enable) {
        if (mDrawerToggle != null) {
            mDrawerToggle.setDrawerIndicatorEnabled(enable);
        }
    }

    /**
     * updates the account list in the drawer.
     */
    public void updateAccountList() {
        Account[] accounts = AccountManager.get(this).getAccountsByType(MainApp.getAccountType());
        if (mNavigationView != null && mDrawerLayout != null) {
            if (accounts.length > 0) {
                repopulateAccountList(accounts);
                setAccountInDrawer(AccountUtils.getCurrentOwnCloudAccount(this));
                populateDrawerOwnCloudAccounts();

                // activate second/end account avatar
                if (mAvatars[1] != null) {
                    DisplayUtils.setAvatar(mAvatars[1], this,
                            mOtherAccountAvatarRadiusDimension, getResources(), getStorageManager(),
                            findNavigationViewChildById(R.id.drawer_account_end));
                    mAccountEndAccountAvatar.setVisibility(View.VISIBLE);
                } else {
                    mAccountEndAccountAvatar.setVisibility(View.GONE);
                }

                // activate third/middle account avatar
                if (mAvatars[2] != null) {
                    DisplayUtils.setAvatar(mAvatars[2], this,
                            mOtherAccountAvatarRadiusDimension, getResources(), getStorageManager(),
                            findNavigationViewChildById(R.id.drawer_account_middle));
                    mAccountMiddleAccountAvatar.setVisibility(View.VISIBLE);
                } else {
                    mAccountMiddleAccountAvatar.setVisibility(View.GONE);
                }
            } else {
                mAccountEndAccountAvatar.setVisibility(View.GONE);
                mAccountMiddleAccountAvatar.setVisibility(View.GONE);
            }
        }
    }

    /**
     * re-populates the account list.
     *
     * @param accounts list of accounts
     */
    private void repopulateAccountList(Account[] accounts) {
        // remove all accounts from list
        mNavigationView.getMenu().removeGroup(R.id.drawer_menu_accounts);

        // add all accounts to list
        for (int i = 0; i < accounts.length; i++) {
            try {
                // show all accounts except the currently active one
                if (!getAccount().name.equals(accounts[i].name)) {
                    MenuItem accountMenuItem = mNavigationView.getMenu().add(
                            R.id.drawer_menu_accounts,
                            Menu.NONE,
                            MENU_ORDER_ACCOUNT,
                            accounts[i].name)
                            .setIcon(TextDrawable.createAvatar(
                                    accounts[i].name,
                                    mMenuAccountAvatarRadiusDimension)
                            );
                    DisplayUtils.setAvatar(accounts[i], this, mMenuAccountAvatarRadiusDimension, getResources(), getStorageManager(), accountMenuItem);
                }
            } catch (Exception e) {
                Log_OC.e(TAG, "Error calculating RGB value for account menu item.", e);
                mNavigationView.getMenu().add(
                        R.id.drawer_menu_accounts,
                        Menu.NONE,
                        MENU_ORDER_ACCOUNT,
                        accounts[i].name)
                        .setIcon(R.drawable.ic_user);
            }
        }

        // re-add add-account and manage-accounts
        mNavigationView.getMenu().add(R.id.drawer_menu_accounts, R.id.drawer_menu_account_add,
                MENU_ORDER_ACCOUNT_FUNCTION,
                getResources().getString(R.string.prefs_add_account)).setIcon(R.drawable.ic_account_plus);
        mNavigationView.getMenu().add(R.id.drawer_menu_accounts, R.id.drawer_menu_account_manage,
                MENU_ORDER_ACCOUNT_FUNCTION,
                getResources().getString(R.string.drawer_manage_accounts)).setIcon(R.drawable.ic_settings);

        // adding sets menu group back to visible, so safety check and setting invisible
        showMenu();
    }

    /**
     * Updates title bar and home buttons (state and icon).
     * <p/>
     * Assumes that navigation drawer is NOT visible.
     */
    protected void updateActionBarTitleAndHomeButton(OCFile chosenFile) {
        super.updateActionBarTitleAndHomeButton(chosenFile);

        /// set home button properties
        if (mDrawerToggle != null && chosenFile != null) {
            mDrawerToggle.setDrawerIndicatorEnabled(isRoot(chosenFile));
        } else if (mDrawerToggle != null){
            mDrawerToggle.setDrawerIndicatorEnabled(false);
        }
    }

    /**
     * sets the given account name in the drawer in case the drawer is available. The account name is shortened
     * beginning from the @-sign in the username.
     *
     * @param account the account to be set in the drawer
     */
    protected void setAccountInDrawer(Account account) {
        if (mDrawerLayout != null && account != null) {
            TextView username = (TextView) findNavigationViewChildById(R.id.drawer_username);
            TextView usernameFull = (TextView) findNavigationViewChildById(R.id.drawer_username_full);
            usernameFull.setText(account.name);
            try {
                OwnCloudAccount oca = new OwnCloudAccount(account, this);
                username.setText(oca.getDisplayName());
            } catch (com.owncloud.android.lib.common.accounts.AccountUtils.AccountNotFoundException e) {
                Log_OC.w(TAG, "Couldn't read display name of account fallback to account name");
                username.setText(AccountUtils.getAccountUsername(account.name));
            }

            DisplayUtils.setAvatar(account, this,
                    mCurrentAccountAvatarRadiusDimension, getResources(), getStorageManager(),
                    findNavigationViewChildById(R.id.drawer_current_account));

            // check and show quota info if available
            getAndDisplayUserQuota();
        }
    }

    /**
     * Toggle between standard menu and account list including saving the state.
     */
    private void toggleAccountList() {
        mIsAccountChooserActive = !mIsAccountChooserActive;
        showMenu();
    }

    /**
     * depending on the #mIsAccountChooserActive flag shows the account chooser or the standard menu.
     */
    private void showMenu() {
        if (mNavigationView != null) {
            if (mIsAccountChooserActive) {
                mAccountChooserToggle.setImageResource(R.drawable.ic_up);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_accounts, true);

                if (!getResources().getBoolean(R.bool.multiaccount_support) &&
                        mNavigationView.getMenu().findItem(R.id.drawer_menu_account_add) != null) {
                    mNavigationView.getMenu().removeItem(R.id.drawer_menu_account_add);
                }

                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_standard, false);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_external_links, false);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_bottom, false);
            } else {
                mAccountChooserToggle.setImageResource(R.drawable.ic_down);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_accounts, false);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_standard, true);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_external_links, true);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_bottom, true);
            }
        }
    }

    /**
     * shows or hides the quota UI elements.
     *
     * @param showQuota show/hide quota information
     */
    private void showQuota(boolean showQuota) {
        if (showQuota) {
            mQuotaView.setVisibility(View.VISIBLE);
        } else {
            mQuotaView.setVisibility(View.GONE);
        }
    }

    /**
     * configured the quota to be displayed.
     *
     * @param usedSpace  the used space
     * @param totalSpace the total space
     * @param relative   the percentage of space already used
     */
    private void setQuotaInformation(long usedSpace, long totalSpace, int relative) {
        mQuotaProgressBar.setProgress(relative);
        DisplayUtils.colorHorizontalProgressBar(mQuotaProgressBar, DisplayUtils.getRelativeInfoColor(this, relative));

        updateQuotaLink();

        mQuotaTextPercentage.setText(String.format(
                getString(R.string.drawer_quota),
                DisplayUtils.bytesToHumanReadable(usedSpace),
                DisplayUtils.bytesToHumanReadable(totalSpace)));

        showQuota(true);
    }

    protected void unsetAllDrawerMenuItems() {
        if (mNavigationView != null && mNavigationView.getMenu() != null) {
            Menu menu = mNavigationView.getMenu();
            for (int i = 0; i < menu.size(); i++) {
                menu.getItem(i).setChecked(false);
            }
        }

        mCheckedMenuItem = Menu.NONE;
    }

    private void updateQuotaLink() {
        if (getBaseContext().getResources().getBoolean(R.bool.show_external_links)) {
            ArrayList<ExternalLink> quotas = externalLinksProvider.getExternalLink(ExternalLinkType.QUOTA);

            float density = getResources().getDisplayMetrics().density;
            final int size = Math.round(24 * density);

            if (quotas.size() > 0) {
                final ExternalLink firstQuota = quotas.get(0);
                mQuotaTextLink.setText(firstQuota.name);
                mQuotaTextLink.setClickable(true);
                mQuotaTextLink.setVisibility(View.VISIBLE);
                mQuotaTextLink.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent externalWebViewIntent = new Intent(getApplicationContext(), ExternalSiteWebView.class);
                        externalWebViewIntent.putExtra(ExternalSiteWebView.EXTRA_TITLE, firstQuota.name);
                        externalWebViewIntent.putExtra(ExternalSiteWebView.EXTRA_URL, firstQuota.url);
                        externalWebViewIntent.putExtra(ExternalSiteWebView.EXTRA_SHOW_SIDEBAR, true);
                        externalWebViewIntent.putExtra(ExternalSiteWebView.EXTRA_MENU_ITEM_ID, -1);
                        startActivity(externalWebViewIntent);
                    }
                });


                SimpleTarget target = new SimpleTarget<Drawable>() {
                    @Override
                    public void onResourceReady(Drawable resource, GlideAnimation glideAnimation) {
                        Drawable test = resource.getCurrent();
                        test.setBounds(0, 0, size, size);
                        mQuotaTextLink.setCompoundDrawablesWithIntrinsicBounds(test, null, null, null);
                    }

                    @Override
                    public void onLoadFailed(Exception e, Drawable errorDrawable) {
                        super.onLoadFailed(e, errorDrawable);

                        Drawable test = errorDrawable.getCurrent();
                        test.setBounds(0, 0, size, size);

                        mQuotaTextLink.setCompoundDrawablesWithIntrinsicBounds(test, null, null, null);
                    }
                };

                externalLinksProvider.downloadIcon(this, firstQuota.iconUrl, target, R.drawable.ic_link_grey, size, size);

            } else {
                mQuotaTextLink.setVisibility(View.INVISIBLE);
            }
        } else {
            mQuotaTextLink.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * checks/highlights the provided menu item if the drawer has been initialized and the menu item exists.
     *
     * @param menuItemId the menu item to be highlighted
     */
    protected void setDrawerMenuItemChecked(int menuItemId) {
        if (mNavigationView != null && mNavigationView.getMenu() != null && mNavigationView.getMenu().findItem
                (menuItemId) != null) {
            mNavigationView.getMenu().findItem(menuItemId).setChecked(true);
            mCheckedMenuItem = menuItemId;
        } else {
            Log_OC.w(TAG, "setDrawerMenuItemChecked has been called with invalid menu-item-ID");
        }
    }

    /**
     * Retrieves and shows the user quota if available
     */
    private void getAndDisplayUserQuota() {
        // set user space information
        Thread t = new Thread(new Runnable() {
            public void run() {

                RemoteOperation getQuotaInfoOperation = new GetRemoteUserInfoOperation();
                RemoteOperationResult result = getQuotaInfoOperation.execute(
                        AccountUtils.getCurrentOwnCloudAccount(DrawerActivity.this), DrawerActivity.this);

                if (result.isSuccess() && result.getData() != null) {
                    final UserInfo userInfo = (UserInfo) result.getData().get(0);
                    final Quota quota = userInfo.getQuota();

                    if (quota != null) {
                        final long used = quota.getUsed();
                        final long total = quota.getTotal();
                        final int relative = (int) Math.ceil(quota.getRelative());
                        final long quotaValue = quota.getQuota();

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (quotaValue > 0
                                        || quotaValue == GetRemoteUserInfoOperation.QUOTA_LIMIT_INFO_NOT_AVAILABLE) {
                                    /**
                                     * show quota in case
                                     * it is available and calculated (> 0) or
                                     * in case of legacy servers (==QUOTA_LIMIT_INFO_NOT_AVAILABLE)
                                     */
                                    setQuotaInformation(used, total, relative);
                                } else {
                                    /**
                                     * quotaValue < 0 means special cases like
                                     * {@link RemoteGetUserQuotaOperation.SPACE_NOT_COMPUTED},
                                     * {@link RemoteGetUserQuotaOperation.SPACE_UNKNOWN} or
                                     * {@link RemoteGetUserQuotaOperation.SPACE_UNLIMITED}
                                     * thus don't display any quota information.
                                     */
                                    showQuota(false);
                                }
                            }
                        });
                    }
                }
            }
        });

        t.start();
    }

    public void updateExternalLinksInDrawer() {
        if (mNavigationView != null && getBaseContext().getResources().getBoolean(R.bool.show_external_links)) {
            mNavigationView.getMenu().removeGroup(R.id.drawer_menu_external_links);

            float density = getResources().getDisplayMetrics().density;
            final int size = Math.round(24 * density);

            for (final ExternalLink link : externalLinksProvider.getExternalLink(ExternalLinkType.LINK)) {

                int id=mNavigationView.getMenu().add(R.id.drawer_menu_external_links, MENU_ITEM_EXTERNAL_LINK,
                        MENU_ORDER_EXTERNAL_LINKS, link.name).setCheckable(true).getItemId();

                MenuSimpleTarget target = new MenuSimpleTarget<Drawable>(id) {
                    @Override
                    public void onResourceReady(Drawable resource, GlideAnimation glideAnimation) {
                        mNavigationView.getMenu().findItem(getIdMenuItem()).setIcon(resource);
                    }

                    @Override
                    public void onLoadFailed(Exception e, Drawable errorDrawable) {
                        super.onLoadFailed(e, errorDrawable);
                        mNavigationView.getMenu().findItem(getIdMenuItem()).setIcon(errorDrawable.getCurrent());
                    }


                };

                externalLinksProvider.downloadIcon(this, link.iconUrl, target, R.drawable.ic_link_grey, size, size);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mIsAccountChooserActive = savedInstanceState.getBoolean(KEY_IS_ACCOUNT_CHOOSER_ACTIVE, false);
            mCheckedMenuItem = savedInstanceState.getInt(KEY_CHECKED_MENU_ITEM, Menu.NONE);
        }

        mCurrentAccountAvatarRadiusDimension = getResources()
                .getDimension(R.dimen.nav_drawer_header_avatar_radius);
        mOtherAccountAvatarRadiusDimension = getResources()
                .getDimension(R.dimen.nav_drawer_header_avatar_other_accounts_radius);
        mMenuAccountAvatarRadiusDimension = getResources()
                .getDimension(R.dimen.nav_drawer_menu_avatar_radius);

        externalLinksProvider = new ExternalLinksProvider(MainApp.getAppContext().getContentResolver());

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(KEY_IS_ACCOUNT_CHOOSER_ACTIVE, mIsAccountChooserActive);
        outState.putInt(KEY_CHECKED_MENU_ITEM, mCheckedMenuItem);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mIsAccountChooserActive = savedInstanceState.getBoolean(KEY_IS_ACCOUNT_CHOOSER_ACTIVE, false);
        mCheckedMenuItem = savedInstanceState.getInt(KEY_CHECKED_MENU_ITEM, Menu.NONE);

        // (re-)setup drawer state
        showMenu();

        // check/highlight the menu item if present
        if (mCheckedMenuItem > Menu.NONE || mCheckedMenuItem < Menu.NONE) {
            setDrawerMenuItemChecked(mCheckedMenuItem);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (mDrawerToggle != null) {
            mDrawerToggle.syncState();
            if (isDrawerOpen()) {
                mDrawerToggle.setDrawerIndicatorEnabled(true);
            }
        }
        updateAccountList();
        updateExternalLinksInDrawer();
        updateQuotaLink();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void onBackPressed() {
        if (isDrawerOpen()) {
            closeDrawer();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setDrawerMenuItemChecked(mCheckedMenuItem);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // update Account list and active account if Manage Account activity replies with
        // - ACCOUNT_LIST_CHANGED = true
        // - RESULT_OK
        if (requestCode == ACTION_MANAGE_ACCOUNTS
                && resultCode == RESULT_OK
                && data.getBooleanExtra(ManageAccountsActivity.KEY_ACCOUNT_LIST_CHANGED, false)) {

            // current account has changed
            if (data.getBooleanExtra(ManageAccountsActivity.KEY_CURRENT_ACCOUNT_CHANGED, false)) {
                setAccount(AccountUtils.getCurrentOwnCloudAccount(this));
                restart();
            } else {
                updateAccountList();
            }
        }
    }

    /**
     * Finds a view that was identified by the id attribute from the drawer header.
     *
     * @param id the view's id
     * @return The view if found or <code>null</code> otherwise.
     */
    private View findNavigationViewChildById(int id) {
        return ((NavigationView) findViewById(R.id.nav_view)).getHeaderView(0).findViewById(id);
    }

    /**
     * restart helper method which is called after a changing the current account.
     */
    protected abstract void restart();

    @Override
    protected void onAccountCreationSuccessful(AccountManagerFuture<Bundle> future) {
        super.onAccountCreationSuccessful(future);
        updateAccountList();
        restart();
    }

    /**
     * populates the avatar drawer array with the first three ownCloud {@link Account}s while the first element is
     * always the current account.
     */
    private void populateDrawerOwnCloudAccounts() {
        mAvatars = new Account[3];
        Account[] accountsAll = AccountManager.get(this).getAccountsByType
                (MainApp.getAccountType());
        Account currentAccount = AccountUtils.getCurrentOwnCloudAccount(this);

        mAvatars[0] = currentAccount;
        int j = 0;
        for (int i = 1; i <= 2 && i < accountsAll.length && j < accountsAll.length; j++) {
            if (!currentAccount.equals(accountsAll[j])) {
                mAvatars[i] = accountsAll[j];
                i++;
            }
        }
    }

    @Override
    public void avatarGenerated(Drawable avatarDrawable, Object callContext) {
        if (callContext instanceof MenuItem) {
            MenuItem mi = (MenuItem) callContext;
            mi.setIcon(avatarDrawable);
        } else if (callContext instanceof ImageView) {
            ImageView iv = (ImageView) callContext;
            iv.setImageDrawable(avatarDrawable);
        }
    }

    @Override
    public boolean shouldCallGeneratedCallback(String tag, Object callContext) {
        if (callContext instanceof MenuItem) {
            MenuItem mi = (MenuItem) callContext;
            return String.valueOf(mi.getTitle()).equals(tag);
        } else if (callContext instanceof ImageView) {
            ImageView iv = (ImageView) callContext;
            return String.valueOf(iv.getTag()).equals(tag);
        }
        return false;
    }

    /**
     * Adds other listeners to react on changes of the drawer layout.
     *
     * @param listener Object interested in changes of the drawer layout.
     */
    public void addDrawerListener(DrawerLayout.DrawerListener listener) {
        if (mDrawerLayout != null) {
            mDrawerLayout.addDrawerListener(listener);
        } else {
            Log_OC.e(TAG, "Drawer layout not ready to add drawer listener");
        }
    }

    public boolean isDrawerIndicatorAvailable() {
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    /**
     * Retrieves external links via api from 'external' app
     */
    public void fetchExternalLinks(final boolean force) {
        if (getBaseContext().getResources().getBoolean(R.bool.show_external_links)) {
            Thread t = new Thread(new Runnable() {
                public void run() {
                    // fetch capabilities as early as possible
                    if ((getCapabilities() == null || getCapabilities().getAccountName().isEmpty())
                            && getStorageManager() != null) {
                        GetCapabilitiesOperarion getCapabilities = new GetCapabilitiesOperarion();
                        getCapabilities.execute(getStorageManager(), getBaseContext());
                    }

                    Account account = AccountUtils.getCurrentOwnCloudAccount(DrawerActivity.this);

                    if (account != null && getStorageManager().getCapability(account.name) != null &&
                            getStorageManager().getCapability(account.name).getExternalLinks().isTrue()) {

                        int count = sharedPreferences.getInt(EXTERNAL_LINKS_COUNT, -1);
                        if (count > 10 || count == -1 || force) {
                            if (force) {
                                Log_OC.d("ExternalLinks", "force update");
                            }

                            sharedPreferences.edit().putInt(EXTERNAL_LINKS_COUNT, 0).apply();

                            Log_OC.d("ExternalLinks", "update via api");
                            ExternalLinksProvider externalLinksProvider = new ExternalLinksProvider(getContentResolver());

                            RemoteOperation getExternalLinksOperation = new ExternalLinksOperation();
                            RemoteOperationResult result = getExternalLinksOperation.execute(account, DrawerActivity.this);

                            if (result.isSuccess() && result.getData() != null) {
                                externalLinksProvider.deleteAllExternalLinks();

                                ArrayList<ExternalLink> externalLinks = (ArrayList<ExternalLink>) (Object) result.getData();

                                for (ExternalLink link : externalLinks) {
                                    externalLinksProvider.storeExternalLink(link);
                                }
                            }
                        } else {
                            sharedPreferences.edit().putInt(EXTERNAL_LINKS_COUNT, count + 1).apply();
                        }
                    } else {
                        Log_OC.d("ExternalLinks", "links disabled");
                    }
                }
            });

            t.start();
        }
    }
}
