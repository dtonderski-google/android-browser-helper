// Copyright 2026 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.androidbrowserhelper.trusted;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;

import androidx.browser.trusted.Token;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.internal.DoNotInstrument;
import org.robolectric.shadows.ShadowPackageManager;

@RunWith(RobolectricTestRunner.class)
@DoNotInstrument
@Config(sdk = {Build.VERSION_CODES.O_MR1})
public class LauncherActivityMetadataTest {
    private Context mContext;
    private ShadowPackageManager mShadowPackageManager;

    private static final String DEFAULT_URL = "https://www.example.com/twa/home";

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mShadowPackageManager = shadowOf(mContext.getPackageManager());
    }

    private void registerLauncherActivity(Bundle metaData) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = mContext.getPackageName();
        packageInfo.signatures = new Signature[]{new Signature("1234567890abcdef")};

        ActivityInfo dummyLauncherActivity = new ActivityInfo();
        dummyLauncherActivity.packageName = mContext.getPackageName();
        dummyLauncherActivity.name = LauncherActivity.class.getName();
        dummyLauncherActivity.metaData = metaData;

        packageInfo.activities = new ActivityInfo[]{dummyLauncherActivity};
        mShadowPackageManager.addPackage(packageInfo);
    }

    @Test
    public void parsesDefaultMetadataWithoutBrowserTargeting() {
        Bundle bundle = new Bundle();
        bundle.putString("android.support.customtabs.trusted.DEFAULT_URL", DEFAULT_URL);
        registerLauncherActivity(bundle);

        LauncherActivityMetadata metadata = LauncherActivityMetadata.parse(mContext);
        assertNull(metadata.launchingBrowser);
        assertNull(metadata.launchingBrowserName);
        assertNull(metadata.launchingBrowserToken);
    }

    @Test
    public void parsesLaunchingBrowserAndName() {
        Bundle bundle = new Bundle();
        bundle.putString("android.support.customtabs.trusted.DEFAULT_URL", DEFAULT_URL);
        bundle.putString("android.support.customtabs.trusted.LAUNCHING_BROWSER", "com.android.chrome");
        bundle.putString("android.support.customtabs.trusted.LAUNCHING_BROWSER_NAME", "Google Chrome");
        registerLauncherActivity(bundle);

        LauncherActivityMetadata metadata = LauncherActivityMetadata.parse(mContext);
        assertEquals("com.android.chrome", metadata.launchingBrowser);
        assertEquals("Google Chrome", metadata.launchingBrowserName);
        assertNull(metadata.launchingBrowserToken);
    }

    @Test
    public void parsesLaunchingBrowserTokenAndInfersBrowserPackage() {
        PackageInfo browserInfo = new PackageInfo();
        browserInfo.packageName = "com.test.browser";
        browserInfo.signatures = new Signature[]{new Signature("abcdef1234567890")};
        mShadowPackageManager.addPackage(browserInfo);

        Token token = Token.create("com.test.browser", mContext.getPackageManager());
        assertNotNull(token);
        String base64Token = Base64.encodeToString(token.serialize(), Base64.DEFAULT);

        Bundle bundle = new Bundle();
        bundle.putString("android.support.customtabs.trusted.DEFAULT_URL", DEFAULT_URL);
        bundle.putString("android.support.customtabs.trusted.LAUNCHING_BROWSER_TOKEN", base64Token);
        registerLauncherActivity(bundle);

        LauncherActivityMetadata metadata = LauncherActivityMetadata.parse(mContext);
        assertNotNull(metadata.launchingBrowserToken);
        assertEquals("com.test.browser", metadata.launchingBrowser);
    }

    @Test
    public void handlesMalformedLaunchingBrowserTokenGracefully() {
        Bundle bundle = new Bundle();
        bundle.putString("android.support.customtabs.trusted.DEFAULT_URL", DEFAULT_URL);
        bundle.putString("android.support.customtabs.trusted.LAUNCHING_BROWSER_TOKEN", "not_a_valid_token");
        registerLauncherActivity(bundle);

        LauncherActivityMetadata metadata = LauncherActivityMetadata.parse(mContext);
        assertNull(metadata.launchingBrowserToken);
    }
}
