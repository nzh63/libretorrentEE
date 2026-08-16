/*
 * Copyright (C) 2016-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of LibreTorrent.
 *
 * LibreTorrent is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibreTorrent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LibreTorrent.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.proninyaroslav.libretorrent;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.CursorWindow;
import android.util.Log;

import android.app.Application;

import org.acra.ACRA;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.data.StringFormat;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.core.utils.LocaleHelper;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.ui.TorrentNotifier;
import org.proninyaroslav.libretorrent.ui.errorreport.ErrorReportActivity;

import io.reactivex.rxjava3.exceptions.CompositeException;
import io.reactivex.rxjava3.exceptions.UndeliverableException;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;

public class MainApplication extends Application {
    public static final String TAG = MainApplication.class.getSimpleName();

    @Override
    protected void attachBaseContext(Context base) {
        SettingsRepository pref = RepositoryHelper.getSettingsRepository(base);
        String locale = pref.locale();
        Context context = LocaleHelper.wrapContext(base, locale);
        super.attachBaseContext(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        /*
         * RxJava 3 routes errors that cannot be delivered to a disposed
         * subscriber to a global handler. When a Room/DB query inside a
         * Flowable is interrupted (e.g. the subscriber unsubscribes mid-query),
         * the resulting InterruptedException surfaces as an UndeliverableException
         * and, without a handler, reaches the uncaught-exception handler / ACRA
         * as a spurious crash. Ignore it; these are expected during teardown.
         */
        RxJavaPlugins.setErrorHandler(throwable -> {
            Throwable t = throwable;
            if (t instanceof UndeliverableException)
                t = t.getCause();
            if (t instanceof InterruptedException)
                return; // expected when a stream is disposed mid-query
            if (t instanceof CompositeException) {
                for (Throwable inner : ((CompositeException) t).getExceptions()) {
                    if (inner instanceof InterruptedException)
                        return;
                }
            }
            Log.e(TAG, "Unhandled RxJava error: " + Log.getStackTraceString(throwable));
        });

        CoreConfigurationBuilder builder = new CoreConfigurationBuilder();
        builder
                .withBuildConfigClass(BuildConfig.class)
                .withReportFormat(StringFormat.JSON);
        builder.withPluginConfigurations(new MailSenderConfigurationBuilder()
                .withMailTo("proninyaroslav@mail.ru")
                .build());
        builder.withPluginConfigurations(new DialogConfigurationBuilder()
                .withEnabled(true)
                .withReportDialogClass(ErrorReportActivity.class)
                .build());
        // Set stub handler
        if (Thread.getDefaultUncaughtExceptionHandler() == null) {
            Thread.setDefaultUncaughtExceptionHandler((t, e) ->
                    Log.e(TAG, "Uncaught exception in " + t + ": " + Log.getStackTraceString(e))
            );
        }
        ACRA.init(this, builder);

        increaseCursorWindowSize();

        TorrentNotifier.getInstance(this).makeNotifyChans();

        SettingsRepository pref = RepositoryHelper.getSettingsRepository(this);
        LocaleHelper.setLocale(pref.locale());

        Utils.applyNightMode(this);
    }

    /** @noinspection JavaReflectionMemberAccess*/
    @SuppressLint("DiscouragedPrivateApi")
    private void increaseCursorWindowSize() {
        try {
            var field = CursorWindow.class.getDeclaredField("sCursorWindowSize");
            field.setAccessible(true);
            field.set(null, 100 * 1024 * 1024); // 100MB
        } catch (Exception e) {
            Log.e(TAG, "Unable to increase CursorWindow size", e);
        }
    }
}