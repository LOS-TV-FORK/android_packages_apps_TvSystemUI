/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.tv.statusbar;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.CoreStartable;
import com.android.systemui.assist.AssistManager;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.KeyboardShortcuts;
import com.android.systemui.utils.windowmanager.WindowManagerProvider;

import dagger.Lazy;

import javax.inject.Inject;

/**
 * Status bar implementation for "large screen" products that mostly present no on-screen nav.
 * Serves as a collection of UI components, rather than showing its own UI.
 */
@SysUISingleton
public class TvStatusBar implements CoreStartable, CommandQueue.Callbacks {

    private static final String ACTION_SHOW_PIP_MENU =
            "com.android.wm.shell.pip.tv.notification.action.SHOW_PIP_MENU";
    private static final String SYSTEMUI_PERMISSION = "com.android.systemui.permission.SELF";

    private static final String TAG = "TvStatusBar";

    // Recents is drawn by the Catapult launcher, which is the home app on this product. Keep in
    // sync with MainActivity in packages/apps/Catapult.
    private static final String RECENTS_PACKAGE = "org.lineageos.tv.launcher";
    // Starting the activity is the request itself: the launcher toggles its
    // recents overlay on launch and back off when it is already up.
    private static final String RECENTS_ACTIVITY = "org.lineageos.tv.launcher.RecentsActivity";

    private final Context mContext;
    private final CommandQueue mCommandQueue;
    private final Lazy<AssistManager> mAssistManagerLazy;
    private final WindowManagerProvider mWindowManagerProvider;

    @Inject
    public TvStatusBar(Context context, CommandQueue commandQueue,
            Lazy<AssistManager> assistManagerLazy, WindowManagerProvider windowManagerProvider) {
        mContext = context;
        mCommandQueue = commandQueue;
        mAssistManagerLazy = assistManagerLazy;
        mWindowManagerProvider = windowManagerProvider;
    }

    @Override
    public void start() {
        final IStatusBarService barService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));
        mCommandQueue.addCallback(this);
        try {
            barService.registerStatusBar(mCommandQueue);
        } catch (RemoteException ex) {
            // If the system process isn't there we're doomed anyway.
        }
    }

    @Override
    public void startAssist(Bundle args) {
        mAssistManagerLazy.get().startAssist(args);
    }

    @Override
    public void showPictureInPictureMenu() {
        mContext.sendBroadcast(
                new Intent(ACTION_SHOW_PIP_MENU).setPackage(mContext.getPackageName()),
                SYSTEMUI_PERMISSION);
    }

    @Override
    public void toggleKeyboardShortcutsMenu(int deviceId) {
        KeyboardShortcuts.show(mContext, deviceId, mWindowManagerProvider);
    }

    /**
     * Reached from PhoneWindowManager.performKeyAction(Action.APP_SWITCH), which is the action
     * config_doubleTapOnHomeBehavior selects for a double tap on HOME.
     *
     * Without this override the default in CommandQueue.Callbacks does nothing, and the stock
     * path that would follow is LauncherProxyService -> the package named by
     * config_recentsComponentName. That package is absent here, and LauncherProxyService is
     * only ever built for the navigation bar, so on this TV build the double tap was swallowed
     * without showing anything.
     *
     * The double tap itself is detected by PhoneWindowManager, which sees both presses in the
     * input stream, so only the sink was missing. Recents is drawn by the Catapult launcher:
     * The recents overlay is drawn by the launcher's translucent RecentsActivity, so the
     * app the menu was called from stays visible and unpaused underneath. Launching it is
     * the request: the menu is shown, or toggled back off when it is already up.
     */
    @Override
    public void toggleRecentApps() {
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(new ComponentName(RECENTS_PACKAGE, RECENTS_ACTIVITY));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            mContext.startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "Failed to start TV recents", e);
        }
    }
}
