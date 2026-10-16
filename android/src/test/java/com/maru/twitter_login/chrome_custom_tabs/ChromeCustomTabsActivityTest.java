package com.maru.twitter_login.chrome_custom_tabs;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

import com.maru.twitter_login.TwitterLoginPlugin;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.util.ReflectionHelpers;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.StandardMethodCodec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ChromeCustomTabsActivityTest {
    private RecordingMessenger messenger;
    private ChromeSafariBrowserManager manager;
    private ActivityController<ChromeCustomTabsActivity> controller;

    @Before
    public void setUp() {
        messenger = new RecordingMessenger();
        TwitterLoginPlugin plugin = new TwitterLoginPlugin();
        plugin.setMessenger(messenger);
        manager = new ChromeSafariBrowserManager(plugin);
    }

    @After
    public void tearDown() {
        if (controller != null) {
            controller.destroy();
        }
        manager.dispose();
        ChromeSafariBrowserManager.shared.clear();
    }

    @Test
    public void missingExtrasFinishesWithoutCrashing() {
        ChromeCustomTabsActivity activity = createActivity(new Intent());

        assertTrue(activity.isFinishing());
        activity.onStart();
        activity.onStop();
        activity.close();
        activity.dispose();
        assertTrue(messenger.calls.isEmpty());
    }

    @Test
    public void missingManagerIdFinishesWithoutCrashing() {
        Intent intent = validIntent();
        intent.removeExtra("managerId");

        assertTrue(createActivity(intent).isFinishing());
    }

    @Test
    public void lostManagerAfterProcessDeathFinishesWithoutCrashing() {
        Intent intent = validIntent();
        ChromeSafariBrowserManager.shared.clear();

        assertTrue(createActivity(intent).isFinishing());
    }

    @Test
    public void detachedPluginFinishesWithoutCrashing() {
        manager.plugin = null;

        assertTrue(createActivity(validIntent()).isFinishing());
    }

    @Test
    public void missingMessengerFinishesWithoutCrashing() {
        manager.plugin.setMessenger(null);

        assertTrue(createActivity(validIntent()).isFinishing());
    }

    @Test
    public void missingBrowserIdFinishesWithoutRegisteringChannel() {
        Intent intent = validIntent();
        intent.removeExtra("id");

        assertTrue(createActivity(intent).isFinishing());
        assertEquals(1, messenger.handlers.size());
    }

    @Test
    public void missingUrlNotifiesDartAndFinishes() {
        Intent intent = validIntent();
        intent.removeExtra("url");

        assertTrue(createActivity(intent).isFinishing());
        assertEquals("twitter_login/auth_browser_browser:onClose", messenger.calls.get(0));
        assertFalse(messenger.handlers.containsKey("twitter_login/auth_browser_browser"));
    }

    @Test
    public void emptyUrlNotifiesDartAndFinishes() {
        Intent intent = validIntent().putExtra("url", "");

        assertTrue(createActivity(intent).isFinishing());
        assertEquals(1, messenger.calls.size());
    }

    @Test
    public void validLaunchInitializesBrowser() {
        ChromeCustomTabsActivity activity = createActivity(validIntent());

        assertFalse(activity.isFinishing());
        assertNotNull(activity.helper);
        assertTrue(messenger.handlers.containsKey("twitter_login/auth_browser_browser"));
    }

    @Test
    public void repeatedServiceConnectionsLaunchOnlyOneBrowser() {
        ChromeCustomTabsActivity activity = createActivity(validIntent());
        CustomTabActivityHelper.ConnectionCallback callback = connectionCallback(activity);

        callback.onCustomTabsConnected();
        callback.onCustomTabsConnected();

        assertBrowserLaunched(activity);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());
        assertTrue(messenger.calls.isEmpty());
    }

    @Test
    public void returningBeforeBrowserResultDoesNotReopenOrCancelLogin() {
        ChromeCustomTabsActivity activity = createActivity(validIntent());
        activity.onStart();
        connectionCallback(activity).onCustomTabsConnected();
        assertBrowserLaunched(activity);

        activity.onStop();
        activity.onStart();
        connectionCallback(activity).onCustomTabsConnected();

        assertNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());
        assertFalse(activity.isFinishing());
        assertTrue(messenger.calls.isEmpty());
        assertTrue(messenger.handlers.containsKey("twitter_login/auth_browser_browser"));

        activity.onActivityResult(0, Activity.RESULT_CANCELED, null);

        assertTrue(activity.isFinishing());
        assertEquals(1, messenger.calls.size());
        assertEquals("twitter_login/auth_browser_browser:onClose", messenger.calls.get(0));
    }

    @Test
    public void recreationWhileBrowserIsOpenDoesNotLaunchAnotherBrowser() {
        ChromeCustomTabsActivity activity = createActivity(validIntent());
        connectionCallback(activity).onCustomTabsConnected();
        assertBrowserLaunched(activity);

        Bundle state = new Bundle();
        controller.saveInstanceState(state).destroy();
        controller = Robolectric.buildActivity(ChromeCustomTabsActivity.class, validIntent())
                .create(state);
        ChromeCustomTabsActivity restored = controller.get();
        restored.onStart();
        connectionCallback(restored).onCustomTabsConnected();

        assertNull(Shadows.shadowOf(restored).getNextStartedActivityForResult());
        assertFalse(restored.isFinishing());
        assertTrue(messenger.calls.isEmpty());

        restored.onActivityResult(0, Activity.RESULT_CANCELED, null);

        assertEquals(1, messenger.calls.size());
        assertFalse(messenger.handlers.containsKey("twitter_login/auth_browser_browser"));
    }

    @Test
    public void recreationBeforeServiceConnectionStillLaunchesBrowser() {
        createActivity(validIntent());
        Bundle state = new Bundle();
        controller.saveInstanceState(state).destroy();
        controller = Robolectric.buildActivity(ChromeCustomTabsActivity.class, validIntent())
                .create(state);
        ChromeCustomTabsActivity restored = controller.get();

        connectionCallback(restored).onCustomTabsConnected();

        assertBrowserLaunched(restored);
        assertTrue(messenger.calls.isEmpty());
    }

    @Test
    public void newLoginAfterCancellationCanLaunchBrowser() {
        ChromeCustomTabsActivity activity = createActivity(validIntent());
        connectionCallback(activity).onCustomTabsConnected();
        assertBrowserLaunched(activity);
        activity.onActivityResult(0, Activity.RESULT_CANCELED, null);
        controller.destroy();

        ChromeCustomTabsActivity retry = createActivity(validIntent());
        connectionCallback(retry).onCustomTabsConnected();

        assertBrowserLaunched(retry);
        assertFalse(retry.isFinishing());
        assertEquals(1, messenger.calls.size());
    }

    @Test
    public void lateServiceConnectionAfterDestructionDoesNotLaunchBrowser() {
        ChromeCustomTabsActivity activity = createActivity(validIntent());
        CustomTabActivityHelper.ConnectionCallback callback = connectionCallback(activity);
        controller.destroy();
        controller = null;

        callback.onCustomTabsConnected();

        assertNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());
        assertTrue(messenger.calls.isEmpty());
    }

    @Test
    public void browserCancellationNotifiesDartAndReleasesChannel() {
        ChromeCustomTabsActivity activity = createActivity(validIntent());

        activity.onActivityResult(0, Activity.RESULT_CANCELED, null);

        assertTrue(activity.isFinishing());
        assertEquals(1, messenger.calls.size());
        assertEquals("twitter_login/auth_browser_browser:onClose", messenger.calls.get(0));
        assertFalse(messenger.handlers.containsKey("twitter_login/auth_browser_browser"));
        assertNull(activity.manager);
        activity.onActivityResult(0, Activity.RESULT_CANCELED, null);
        assertEquals(1, messenger.calls.size());
    }

    @Test
    public void destructionReleasesChannel() {
        ChromeCustomTabsActivity activity = createActivity(validIntent());

        controller.destroy();
        controller = null;

        assertFalse(messenger.handlers.containsKey("twitter_login/auth_browser_browser"));
        assertNull(activity.manager);
    }

    @Test
    public void activityRemainsInHistoryToReceiveBrowserResult() throws Exception {
        ActivityInfo info = RuntimeEnvironment.getApplication().getPackageManager()
                .getActivityInfo(new ComponentName(RuntimeEnvironment.getApplication(),
                        ChromeCustomTabsActivity.class), 0);

        assertEquals(0, info.flags & ActivityInfo.FLAG_NO_HISTORY);
    }

    private ChromeCustomTabsActivity createActivity(Intent intent) {
        controller = Robolectric.buildActivity(ChromeCustomTabsActivity.class, intent).create();
        return controller.get();
    }

    private CustomTabActivityHelper.ConnectionCallback connectionCallback(
            ChromeCustomTabsActivity activity) {
        return ReflectionHelpers.getField(activity.helper, "mConnectionCallback");
    }

    private void assertBrowserLaunched(ChromeCustomTabsActivity activity) {
        ShadowActivity.IntentForResult launched =
                Shadows.shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(launched);
        assertEquals(0, launched.requestCode);
        assertEquals(validIntent().getStringExtra("url"), launched.intent.getDataString());
    }

    private Intent validIntent() {
        return new Intent()
                .putExtra("managerId", manager.id)
                .putExtra("id", "browser")
                .putExtra("url", "https://api.twitter.com/oauth/authorize?oauth_token=test");
    }

    private static class RecordingMessenger implements BinaryMessenger {
        final Map<String, BinaryMessageHandler> handlers = new HashMap<>();
        final List<String> calls = new ArrayList<>();

        @Override
        public void send(String channel, ByteBuffer message) {
            send(channel, message, null);
        }

        @Override
        public void send(String channel, ByteBuffer message, BinaryReply callback) {
            ByteBuffer encoded = message.duplicate();
            encoded.flip();
            calls.add(channel + ":" + StandardMethodCodec.INSTANCE.decodeMethodCall(encoded).method);
        }

        @Override
        public void setMessageHandler(String channel, BinaryMessageHandler handler) {
            if (handler == null) {
                handlers.remove(channel);
            } else {
                handlers.put(channel, handler);
            }
        }
    }
}
