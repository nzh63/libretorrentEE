/*
 * Copyright (C) 2019-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.model;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;

import org.apache.commons.io.filefilter.FileFilterUtils;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.TorrentFileObserver;
import org.proninyaroslav.libretorrent.core.exception.DecodeException;
import org.proninyaroslav.libretorrent.core.exception.FreeSpaceException;
import org.proninyaroslav.libretorrent.core.exception.TorrentAlreadyExistsException;
import org.proninyaroslav.libretorrent.core.exception.UnknownUriException;
import org.proninyaroslav.libretorrent.core.logger.LogEntry;
import org.proninyaroslav.libretorrent.core.logger.Logger;
import org.proninyaroslav.libretorrent.core.model.data.AdvancedTorrentInfo;
import org.proninyaroslav.libretorrent.core.model.data.MagnetInfo;
import org.proninyaroslav.libretorrent.core.model.data.PeerInfo;
import org.proninyaroslav.libretorrent.core.model.data.Priority;
import org.proninyaroslav.libretorrent.core.model.data.TorrentInfo;
import org.proninyaroslav.libretorrent.core.model.data.TrackerInfo;
import org.proninyaroslav.libretorrent.core.model.data.entity.TagInfo;
import org.proninyaroslav.libretorrent.core.model.data.entity.Torrent;
import org.proninyaroslav.libretorrent.core.model.data.metainfo.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.model.session.AdvancedPeerInfo;
import org.proninyaroslav.libretorrent.core.model.session.TorrentDownload;
import org.proninyaroslav.libretorrent.core.model.session.TorrentSession;
import org.proninyaroslav.libretorrent.core.model.session.TorrentSessionImpl;
import org.proninyaroslav.libretorrent.core.model.session.SessionLogger;
import org.proninyaroslav.libretorrent.core.model.stream.TorrentInputStream;
import org.proninyaroslav.libretorrent.core.model.stream.TorrentStream;
import org.proninyaroslav.libretorrent.core.model.stream.TorrentStreamServer;
import org.proninyaroslav.libretorrent.core.pbh.BanRecord;
import org.proninyaroslav.libretorrent.core.pbh.BanRecordStore;
import org.proninyaroslav.libretorrent.core.pbh.BanResult;
import org.proninyaroslav.libretorrent.core.pbh.ClientNameMatcher;
import org.proninyaroslav.libretorrent.core.pbh.DisconnectBans;
import org.proninyaroslav.libretorrent.core.pbh.IpUtils;
import org.proninyaroslav.libretorrent.core.pbh.PeerBanHelperEngine;
import org.proninyaroslav.libretorrent.core.pbh.PeerSnapshot;
import org.proninyaroslav.libretorrent.core.pbh.PbhSettings;
import org.proninyaroslav.libretorrent.core.pbh.TorrentSnapshot;
import org.proninyaroslav.libretorrent.core.btn.BtnClient;
import org.proninyaroslav.libretorrent.core.btn.BtnIpQueryResult;
import org.proninyaroslav.libretorrent.core.btn.BtnManager;
import org.proninyaroslav.libretorrent.core.btn.BtnPayload;
import org.proninyaroslav.libretorrent.core.btn.BtnRuleSet;
import org.proninyaroslav.libretorrent.core.btn.BtnRuleStore;
import org.proninyaroslav.libretorrent.core.btn.BtnSettings;
import org.proninyaroslav.libretorrent.core.btn.TorrentIdentifier;
import org.proninyaroslav.libretorrent.core.settings.SessionSettings;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.core.storage.TagRepository;
import org.proninyaroslav.libretorrent.core.storage.TorrentRepository;
import org.proninyaroslav.libretorrent.core.system.FileDescriptorWrapper;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.receiver.ConnectionReceiver;
import org.proninyaroslav.libretorrent.receiver.PowerReceiver;
import org.proninyaroslav.libretorrent.service.TorrentService;
import org.proninyaroslav.libretorrent.ui.TorrentNotifier;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class TorrentEngine {
    private static final String TAG = TorrentEngine.class.getSimpleName();

    /* Minimum interval between BTN cloud rule refreshes */
    private static final long BTN_MIN_REFRESH_INTERVAL_MS = 60_000L; /* 1 min */
    /* Minimum interval between BTN ban submissions */
    private static final long BTN_MIN_BAN_SUBMIT_INTERVAL_MS = 60_000L; /* 1 min */
    /* Interval between BTN swarm snapshots */
    private static final long BTN_SWARM_SUBMIT_INTERVAL_MS = 15 * 60_000L; /* 15 min */
    /* BTN-Spec: at most 1000 entries per request */
    private static final int BTN_MAX_ENTRIES_PER_REQUEST = 1000;

    private final Context appContext;
    private final TorrentSession session;
    private TorrentStreamServer torrentStreamServer;
    private final TorrentRepository repo;
    private final TagRepository tagRepo;
    private final SettingsRepository pref;
    private final TorrentNotifier notifier;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private TorrentFileObserver fileObserver;
    private final PowerReceiver powerReceiver = new PowerReceiver();
    private final ConnectionReceiver connectionReceiver = new ConnectionReceiver();
    private final FileSystemFacade fs;
    private final DownloadsCompletedListener downloadsCompleted;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final SessionErrorFilter errorFilter = new SessionErrorFilter();
    /* PeerBanHelper-compatible anti-leech engine */
    private final PeerBanHelperEngine pbhEngine = new PeerBanHelperEngine();
    /* BTN (BitTorrent Threat Network) rule management */
    private final BtnManager btnManager;
    /*
     * Serializes the anti-leech scans (interval ticks and manual triggers)
     * so detection state is never accessed concurrently. The scan itself
     * performs no network I/O.
     */
    private final ExecutorService pbhScanExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PBHScan");
        t.setDaemon(true);
        return t;
    });
    /*
     * Runs all BTN network work (rule refresh, ban/swarm submission) off the
     * scan path, so a slow BTN server cannot stall peer detection. Single
     * thread keeps the tasks ordered and their rate-limit state single-
     * threaded.
     */
    private final ExecutorService btnExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BtnNetwork");
        t.setDaemon(true);
        return t;
    });
    /* Rate-limit timestamps, only accessed on btnExec */
    private long lastBtnRefreshMs;
    private long lastBanSubmitMs;
    private long lastSwarmSubmitMs;
    private long lastHistorySubmitMs;
    /* Short-lived fast-PCB disconnect probes, kept out of the blacklist */
    private final DisconnectBans disconnectBans = new DisconnectBans();
    /*
     * Automatic (engine-issued) bans, stored separately from the user's
     * manual blacklist and carrying the metadata shown in the ban-list UI.
     */
    private final BanRecordStore banRecordStore;
    /* PCB state TTL: evict tracking entries not seen for this long */
    private static final long PBH_STATE_TTL_MS = 24 * 3600_000L; /* 24 h */
    private long lastPbhStateEvictMs;
    /* Periodic ban scan; replaced when the check interval is changed */
    private Disposable pbhScanDisposable;
    /* Guards access to pbhScanDisposable from the settings observer */
    private final Object pbhScanLock = new Object();

    private static volatile TorrentEngine INSTANCE;

    public static TorrentEngine getInstance(@NonNull Context appContext) {
        if (INSTANCE == null) {
            synchronized (TorrentEngine.class) {
                if (INSTANCE == null)
                    INSTANCE = new TorrentEngine(appContext);
            }
        }

        return INSTANCE;
    }

    private TorrentEngine(@NonNull Context appContext) {
        this.appContext = appContext;
        repo = RepositoryHelper.getTorrentRepository(appContext);
        tagRepo = RepositoryHelper.getTagRepository(appContext);
        fs = SystemFacadeHelper.getFileSystemFacade(appContext);
        pref = RepositoryHelper.getSettingsRepository(appContext);
        notifier = TorrentNotifier.getInstance(appContext);
        downloadsCompleted = new DownloadsCompletedListener(this);
        session = new TorrentSessionImpl(repo,
                fs,
                SystemFacadeHelper.getSystemFacade(appContext));
        session.setSettings(pref.readSessionSettings(), false);
        session.addListener(engineListener);

        btnManager = new BtnManager(
                new BtnClient(),
                new BtnRuleStore(appContext));
        // Load the last persisted BTN rules into the engine so they are
        // effective immediately on startup (offline-first). This is a pure
        // in-memory/SharedPreferences load with NO network access, so it is
        // safe to run here on the main thread. Network refreshes happen later
        // from refreshBtnRules() on the IO scheduler.
        pbhEngine.updateBtnRules(btnManager.load());
        banRecordStore = new BanRecordStore(appContext);

        ensureBtnInstallationId();
        migrateLegacyAutoBans();
    }

    /*
     * One-time migration from the pre-split storage: older versions merged
     * automatic bans into the manual blacklist (with "ip=expiry" bookkeeping
     * in pbhAutoBannedIps). Moves them into the BanRecordStore so manual and
     * automatic bans are cleanly separated. Cheap (one empty-set check per
     * startup), safe to run on the main thread.
     */
    private void migrateLegacyAutoBans() {
        Set<String> legacy = pref.pbhAutoBannedIps();
        if (legacy.isEmpty())
            return;

        long now = System.currentTimeMillis();
        Set<String> manual = new HashSet<>(pref.peerIpBlacklist());
        for (String entry : legacy) {
            String ip = getAutoBanIp(entry);
            if (ip.isEmpty() || banRecordStore.get(ip) != null)
                continue;
            banRecordStore.record(new BanRecord(ip, "legacy",
                    "auto ban (migrated from a previous version)", "",
                    now, getAutoBanExpiry(entry)));
            manual.remove(ip);
        }
        pref.peerIpBlacklist(manual);
        pref.pbhAutoBannedIps(new HashSet<>());
        Log.i(TAG, "[PBH] migrated " + legacy.size()
                + " legacy auto-ban(s) into the ban record store");
    }

    /*
     * BTN identifies anonymous clients by a random "installation ID" that is
     * persisted once per app install. Cheap (one SharedPreferences read +
     * one write on first launch), safe to call on the main thread.
     */
    private void ensureBtnInstallationId() {
        if (pref.btnInstallationId() != null && !pref.btnInstallationId().isEmpty())
            return;

        String id = UUID.randomUUID().toString();
        pref.btnInstallationId(id);
        Log.i(TAG, "[PBH] generated BTN installation ID: " + id);
    }

    private void handleAutoStop() {
        if (pref.shutdownDownloadsComplete())
            forceStop();
    }

    public void start() {
        if (isRunning())
            return;

        Utils.startServiceBackground(appContext, new Intent(appContext, TorrentService.class));
    }

    public void restartForegroundNotification() {
        Intent i = new Intent(appContext, TorrentService.class);
        i.setAction(TorrentService.ACTION_RESTART_FOREGROUND_NOTIFICATION);
        Utils.startServiceBackground(appContext, i);
    }

    public Flowable<Boolean> observeNeedStartEngine() {
        return Flowable.create((emitter) -> {
            if (emitter.isCancelled())
                return;

            Runnable emitLoop = () -> {
                while (!Thread.interrupted()) {
                    try {
                        Thread.sleep(1000);

                    } catch (InterruptedException e) {
                        return;
                    }

                    if (emitter.isCancelled() || isRunning())
                        return;

                    emitter.onNext(true);
                }
            };

            Disposable d = observeEngineRunning()
                    .subscribeOn(Schedulers.io())
                    .subscribe((isRunning) -> {
                        if (emitter.isCancelled())
                            return;

                        if (!isRunning) {
                            emitter.onNext(true);
                            exec.submit(emitLoop);
                        }
                    });

            if (!emitter.isCancelled()) {
                emitter.onNext(!isRunning());
                emitter.setDisposable(d);
            }

        }, BackpressureStrategy.LATEST);
    }

    public Flowable<Boolean> observeEngineRunning() {
        return Flowable.create((emitter) -> {
            if (emitter.isCancelled())
                return;

            TorrentEngineListener listener = new TorrentEngineListener() {
                @Override
                public void onSessionStarted() {
                    if (!emitter.isCancelled())
                        emitter.onNext(true);
                }

                @Override
                public void onSessionStopped() {
                    if (!emitter.isCancelled())
                        emitter.onNext(false);
                }
            };

            if (!emitter.isCancelled()) {
                emitter.onNext(isRunning());
                addListener(listener);
                emitter.setDisposable(Disposable.fromAction(() -> removeListener(listener)));
            }

        }, BackpressureStrategy.LATEST);
    }

    /*
     * Only calls from TorrentService
     */

    public void doStart() {
        if (isRunning())
            return;

        switchConnectionReceiver();
        switchPowerReceiver();
        disposables.add(pref.observeSettingsChanged()
                .subscribe(this::handleSettingsChanged));

        logPbh("anti-leech engine " + (pref.pbhEnabled() ? "enabled" : "disabled") +
                ", check interval " + Math.max(1, pref.pbhCheckInterval()) + "s, BTN " +
                (pref.btnEnabled() ? "enabled" : "disabled"));

        startPbhScan();

        disposables.add(downloadsCompleted.listen()
                .subscribe(
                        this::handleAutoStop,
                        (err) -> {
                            Log.e(TAG, "Auto stop error: " +
                                    Log.getStackTraceString(err));
                            handleAutoStop();
                        }
                ));

        disposables.add(session.getLogger().observeDataSetChanged()
                .subscribe((change) -> {
                    if (change.reason() == Logger.DataSetChange.Reason.NEW_ENTRIES && change.entries() != null)
                        printSessionLog(change.entries());
                }));

        session.start();
    }

    /*
     * Starts (or restarts with a new interval) the periodic anti-leech scan.
     * Uses a raw interval Observable so the delay resets to the new value
     * immediately when the user changes the check interval setting.
     *
     * The tick only enqueues the scan; the actual work runs on the dedicated
     * single-thread pbhScanExec (never on the Rx computation scheduler that
     * interval emits on), so concurrent triggers cannot overlap.
     */
    private void startPbhScan() {
        long interval = Math.max(1, pref.pbhCheckInterval());

        synchronized (pbhScanLock) {
            if (pbhScanDisposable != null && !pbhScanDisposable.isDisposed())
                pbhScanDisposable.dispose();
            pbhScanDisposable = Observable.interval(interval, TimeUnit.SECONDS)
                    .observeOn(Schedulers.io())
                    .subscribe((__) -> submitPbhScan());
        }
    }

    private void submitPbhScan() {
        pbhScanExec.execute(() -> {
            try {
                checkAndBanBadPeers();
            } catch (Exception e) {
                Log.e(TAG, "[PBH] scan failed: " + Log.getStackTraceString(e));
            }
        });
    }

    private void printSessionLog(List<LogEntry> entries) {
        for (LogEntry entry : entries) {
            if (entry == null)
                continue;

            Log.i(TAG, entry.toString());
        }
    }

    /*
     * Leaves the right to the engine to decide whether to shutdown or not
     */

    public void requestStop() {
        if (pref.keepAlive())
            return;

        forceStop();
    }

    public void forceStop() {
        Intent i = new Intent(appContext, TorrentService.class);
        i.setAction(TorrentService.ACTION_SHUTDOWN);
        Utils.startServiceBackground(appContext, i);
    }

    /*
     * Only calls from TorrentService
     */

    public void doStop() {
        if (!isRunning())
            return;

        disposables.clear();
        synchronized (pbhScanLock) {
            if (pbhScanDisposable != null && !pbhScanDisposable.isDisposed())
                pbhScanDisposable.dispose();
            pbhScanDisposable = null;
        }
        stopWatchDir();
        stopStreamingServer();
        session.requestStop();
        cleanTemp();
    }

    public boolean isRunning() {
        return session.isRunning();
    }

    public void addListener(TorrentEngineListener listener) {
        session.addListener(listener);
    }

    public void removeListener(TorrentEngineListener listener) {
        session.removeListener(listener);
    }

    public void rescheduleTorrents() {
        disposables.add(Completable.fromRunnable(() -> {
                    if (!isRunning())
                        return;

                    if (checkPauseTorrents())
                        session.pauseAll();
                    else
                        session.resumeAll();

                }).subscribeOn(Schedulers.io())
                .subscribe());
    }

    public void addTorrents(@NonNull List<AddTorrentParams> paramsList,
                            boolean removeFile) {
        if (!isRunning())
            return;

        disposables.add(Observable.fromIterable(paramsList)
                .subscribeOn(Schedulers.io())
                .subscribe((params) -> {
                    try {
                        session.addTorrent(params, removeFile);

                    } catch (Exception e) {
                        handleAddTorrentError(params.name, e);
                    }
                }));
    }

    public void addTorrent(@NonNull Uri file) {
        addTorrent(file, null);
    }

    public void addTorrent(@NonNull Uri file, @Nullable Uri savePath) {
        disposables.add(addTorrentCompletable(file, savePath)
                .subscribeOn(Schedulers.io())
                .subscribe()
        );
    }

    public Completable addTorrentCompletable(@NonNull Uri file) {
        return addTorrentCompletable(file, null);
    }

    public Completable addTorrentCompletable(@NonNull Uri file, @Nullable Uri savePath) {
        return Completable.fromRunnable(() -> {
            if (!isRunning())
                return;

            TorrentMetaInfo info = null;
            try (FileDescriptorWrapper w = fs.getFD(file)) {
                FileDescriptor outFd = w.open("r");

                try (FileInputStream is = new FileInputStream(outFd)) {
                    info = new TorrentMetaInfo(is);

                } catch (Exception e) {
                    throw new DecodeException(e);
                }
                addTorrentSync(file, info, savePath);

            } catch (Exception e) {
                handleAddTorrentError((info == null ? file.getPath() : info.torrentName), e);
            }
        });
    }

    /*
     * Do not run in the UI thread
     */

    public Torrent addTorrentSync(
            @NonNull AddTorrentParams params,
            boolean removeFile
    ) throws
            IOException,
            TorrentAlreadyExistsException,
            DecodeException,
            UnknownUriException {
        if (!isRunning())
            return null;

        return session.addTorrent(params, removeFile);
    }

    public Pair<MagnetInfo, Single<TorrentMetaInfo>> fetchMagnet(@NonNull String uri) throws Exception {
        if (!isRunning())
            return null;

        MagnetInfo info = session.fetchMagnet(uri);
        if (info == null)
            return null;
        Single<TorrentMetaInfo> res = createFetchMagnetSingle(info.getSha1hash());

        return Pair.create(info, res);
    }

    public MagnetInfo parseMagnet(@NonNull String uri) {
        return session.parseMagnet(uri);
    }

    private Single<TorrentMetaInfo> createFetchMagnetSingle(String targetHash) {
        return Single.create((emitter) -> {
            TorrentEngineListener listener = new TorrentEngineListener() {
                @Override
                public void onMagnetLoaded(@NonNull String hash, byte[] bencode) {
                    if (!targetHash.equals(hash))
                        return;

                    if (!emitter.isDisposed()) {
                        if (bencode == null)
                            emitter.onError(new IOException(new NullPointerException("bencode is null")));
                        else
                            sendInfoToEmitter(emitter, bencode);
                    }
                }
            };
            if (!emitter.isDisposed()) {
                /* Check if metadata is already loaded */
                byte[] bencode = session.getLoadedMagnet(targetHash);
                if (bencode == null) {
                    session.addListener(listener);
                    emitter.setDisposable(Disposable.fromAction(() ->
                            session.removeListener(listener)));
                } else {
                    sendInfoToEmitter(emitter, bencode);
                }
            }
        });
    }

    private void sendInfoToEmitter(SingleEmitter<TorrentMetaInfo> emitter, byte[] bencode) {
        TorrentMetaInfo info;
        try {
            info = new TorrentMetaInfo(bencode);

        } catch (DecodeException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            if (!emitter.isDisposed())
                emitter.onError(e);
            return;
        }

        if (!emitter.isDisposed())
            emitter.onSuccess(info);
    }

    /*
     * Used only for magnets from the magnetList (non added magnets)
     */

    public void cancelFetchMagnet(@NonNull String infoHash) {
        if (!isRunning())
            return;

        session.cancelFetchMagnet(infoHash);
    }

    public void pauseResumeTorrent(@NonNull String id) {
        disposables.add(Completable.fromRunnable(() -> {
                    TorrentDownload task = session.getTask(id);
                    if (task == null)
                        return;
                    try {
                        if (task.isPaused())
                            task.resumeManually();
                        else
                            task.pauseManually();

                    } catch (Exception e) {
                        /* Ignore */
                    }

                }).subscribeOn(Schedulers.io())
                .subscribe());
    }

    public void forceRecheckTorrents(@NonNull List<String> ids) {
        disposables.add(Observable.fromIterable(ids)
                .filter(Objects::nonNull)
                .subscribe((id) -> {
                    if (!isRunning())
                        return;

                    TorrentDownload task = session.getTask(id);
                    if (task != null)
                        task.forceRecheck();
                }));
    }

    public void forceAnnounceTorrents(@NonNull List<String> ids) {
        disposables.add(Observable.fromIterable(ids)
                .filter(Objects::nonNull)
                .subscribe((id) -> {
                    if (!isRunning())
                        return;

                    TorrentDownload task = session.getTask(id);
                    if (task != null)
                        task.requestTrackerAnnounce();
                }));
    }

    public void deleteTorrents(@NonNull List<String> ids, boolean withFiles) {
        disposables.add(Observable.fromIterable(ids)
                .observeOn(Schedulers.io())
                .subscribe((id) -> {
                    if (!isRunning())
                        return;
                    session.deleteTorrent(id, withFiles);
                    // Drop anti-leech tracking state of the deleted torrent
                    pbhEngine.evictTorrentState(id);
                    swarmTracks.keySet().removeIf(k -> k.startsWith(id + "|"));
                }));
    }

    public void deleteTrackers(@NonNull String id, @NonNull List<String> urls) {
        if (!isRunning())
            return;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return;

        Set<String> trackers = task.getTrackersUrl();
        urls.forEach(trackers::remove);

        task.replaceTrackers(trackers);
    }

    public void replaceTrackers(@NonNull String id, @NonNull List<String> urls) {
        if (!isRunning())
            return;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return;

        task.replaceTrackers(new HashSet<>(urls));
    }

    public void addTrackers(@NonNull String id, @NonNull List<String> urls) {
        if (!isRunning())
            return;

        TorrentDownload task = session.getTask(id);
        if (task != null)
            task.addTrackers(new HashSet<>(urls));
    }

    public String makeMagnet(@NonNull String id, boolean includePriorities) {
        if (!isRunning())
            return null;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return null;

        return task.makeMagnet(includePriorities);
    }

    public Flowable<TorrentMetaInfo> observeTorrentMetaInfo(@NonNull String id) {
        return Flowable.create((emitter) -> {
            TorrentEngineListener listener = new TorrentEngineListener() {
                @Override
                public void onTorrentMetadataLoaded(@NonNull String torrentId, Exception err) {
                    if (!id.equals(torrentId) || emitter.isCancelled())
                        return;

                    if (err == null) {
                        TorrentMetaInfo info = getTorrentMetaInfo(id);
                        if (info == null)
                            emitter.onError(new NullPointerException());
                        else
                            emitter.onNext(info);
                    } else {
                        emitter.onError(err);
                    }
                }
            };
            if (!emitter.isCancelled()) {
                TorrentMetaInfo info = getTorrentMetaInfo(id);
                if (info == null)
                    emitter.onError(new NullPointerException());
                else
                    emitter.onNext(info);

                session.addListener(listener);
                emitter.setDisposable(Disposable.fromAction(() ->
                        session.removeListener(listener)));
            }
        }, BackpressureStrategy.LATEST);
    }

    public TorrentMetaInfo getTorrentMetaInfo(@NonNull String id) {
        if (!isRunning())
            return null;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return null;

        TorrentMetaInfo info = null;
        try {
            info = task.getTorrentMetaInfo();

        } catch (DecodeException e) {
            Log.e(TAG, "Can't decode torrent info: ");
            Log.e(TAG, Log.getStackTraceString(e));
        }

        return info;
    }

    public boolean[] getPieces(@NonNull String id) {
        if (!isRunning())
            return new boolean[0];

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return new boolean[0];

        return task.pieces();
    }

    public void pauseAll() {
        disposables.add(Completable.fromRunnable(() -> {
                    if (isRunning())
                        session.pauseAllManually();

                }).subscribeOn(Schedulers.io())
                .subscribe());
    }

    public void resumeAll() {
        disposables.add(Completable.fromRunnable(() -> {
                    if (isRunning())
                        session.resumeAllManually();

                }).subscribeOn(Schedulers.io())
                .subscribe());
    }

    public void setTorrentName(@NonNull String id, @NonNull String name) {
        disposables.add(Completable.fromRunnable(() -> {
                    if (!isRunning())
                        return;

                    TorrentDownload task = session.getTask(id);
                    if (task != null)
                        task.setTorrentName(name);

                }).subscribeOn(Schedulers.io())
                .subscribe());
    }

    public void setDownloadPath(@NonNull String id, @NonNull Uri path) {
        disposables.add(Completable.fromRunnable(() -> {
                    if (!isRunning())
                        return;

                    TorrentDownload task = session.getTask(id);
                    if (task != null)
                        task.setDownloadPath(path);

                }).subscribeOn(Schedulers.io())
                .subscribe());
    }

    public void setSequentialDownload(@NonNull String id, boolean sequential) {
        disposables.add(Completable.fromRunnable(() -> {
                    if (!isRunning())
                        return;

                    TorrentDownload task = session.getTask(id);
                    if (task != null)
                        task.setSequentialDownload(sequential);

                }).subscribeOn(Schedulers.io())
                .subscribe());
    }

    public void setFirstLastPiecePriority(@NonNull String id, boolean enabled) {
        disposables.add(Completable.fromRunnable(() -> {
            if (!isRunning()) {
                return;
            }
            var task = session.getTask(id);
            if (task != null) {
                task.setFirstLastPiecePriority(enabled);
            }
        }).subscribeOn(Schedulers.io()).subscribe());
    }

    public boolean isFirstLastPiecePriority(@NonNull String id) {
        if (!isRunning()) {
            return false;
        }

        var task = session.getTask(id);
        if (task == null) {
            return false;
        }

        return task.isFirstLastPiecePriority();
    }

    public void prioritizeFiles(@NonNull String id, @NonNull Priority[] priorities) {
        disposables.add(Completable.fromRunnable(() -> {
                    if (!isRunning())
                        return;

                    TorrentDownload task = session.getTask(id);
                    if (task != null)
                        task.prioritizeFiles(priorities);

                }).subscribeOn(Schedulers.io())
                .subscribe());
    }

    public TorrentStream getStream(@NonNull String id, int fileIndex) {
        if (!isRunning())
            return null;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return null;

        return task.getStream(fileIndex);
    }

    public TorrentInputStream getTorrentInputStream(@NonNull TorrentStream stream) {
        return new TorrentInputStream(session, stream);
    }

    /*
     * Do not run in the UI thread
     */

    public TorrentInfo makeInfoSync(@NonNull String id) {
        Torrent torrent = repo.getTorrentById(id);
        if (torrent == null) {
            return null;
        }
        List<TagInfo> tags = tagRepo.getByTorrentId(id);

        return makeInfo(torrent, tags);
    }

    private TorrentInfo makeInfo(Torrent torrent, List<TagInfo> tags) {
        TorrentDownload task = session.getTask(torrent.id);
        if (task == null || !task.isValid() || task.isStopped()) {
            return new TorrentInfo(
                    torrent.id,
                    torrent.name,
                    torrent.dateAdded,
                    torrent.error,
                    tags
            );
        } else {
            return new TorrentInfo(
                    torrent.id,
                    torrent.name,
                    task.getStateCode(),
                    task.getProgress(),
                    task.getReceivedBytes(),
                    task.getTotalSentBytes(),
                    task.getTotalWanted(),
                    task.getDownloadSpeed(),
                    task.getUploadSpeed(),
                    task.getETA(),
                    torrent.dateAdded,
                    task.getTotalPeers(),
                    task.getConnectedPeers(),
                    torrent.error,
                    task.isSequentialDownload(),
                    task.getFilePriorities(),
                    tags,
                    task.isFirstLastPiecePriority()
            );
        }
    }

    /*
     * Do not run in the UI thread
     */

    public List<TorrentInfo> makeInfoListSync() {
        ArrayList<TorrentInfo> stateList = new ArrayList<>();

        for (Torrent torrent : repo.getAllTorrents()) {
            if (torrent == null) {
                continue;
            }
            List<TagInfo> tags = tagRepo.getByTorrentId(torrent.id);
            stateList.add(makeInfo(torrent, tags));
        }

        return stateList;
    }

    /*
     * Do not run in the UI thread
     */

    public AdvancedTorrentInfo makeAdvancedInfoSync(@NonNull String id) {
        if (!isRunning())
            return null;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return null;

        Torrent torrent = repo.getTorrentById(id);
        if (torrent == null)
            return null;

        int[] piecesAvail = task.getPiecesAvailability();

        return new AdvancedTorrentInfo(
                torrent.id,
                task.getFilesReceivedBytes(),
                task.getTotalSeeds(),
                task.getConnectedSeeds(),
                task.getNumDownloadedPieces(),
                task.getShareRatio(),
                task.getActiveTime(),
                task.getSeedingTime(),
                task.getAvailability(piecesAvail),
                task.getFilesAvailability(piecesAvail),
                task.getConnectedLeechers(),
                task.getTotalLeechers());
    }

    public List<TrackerInfo> makeTrackerInfoList(@NonNull String id) {
        if (!isRunning())
            return new ArrayList<>();

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return new ArrayList<>();

        return task.getTrackerInfoList();
    }

    public List<PeerInfo> makePeerInfoList(@NonNull String id) {
        if (!isRunning())
            return new ArrayList<>();

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return new ArrayList<>();

        return task.getPeerInfoList();
    }

    /*
     * Adds the peer's IP to the blacklist and immediately bans it at the
     * session level (disconnects the peer and blocks reconnections).
     */

    public void banPeerIp(@NonNull String ip) {
        Set<String> bannedIps = new HashSet<>(pref.peerIpBlacklist());
        if (ip.isEmpty() || !bannedIps.add(ip))
            return;

        setPeerIpBlacklist(bannedIps);
    }

    /*
     * Replaces the whole IP blacklist (e.g. after editing it in the settings)
     * and re-applies it to the session.
     */

    public void setPeerIpBlacklist(@NonNull Set<String> bannedIps) {
        pref.peerIpBlacklist(bannedIps);

        if (isRunning())
            applyEffectiveBannedIps();
    }

    /*
     * Replaces the whole user agent blacklist (e.g. after editing it in the
     * settings) and re-scans connected peers.
     */

    public void setPeerUserAgentBlacklist(@NonNull Set<String> bannedUserAgents) {
        pref.peerUserAgentBlacklist(bannedUserAgents);
        submitPbhScan();
    }

    /*
     * Adds the peer's user agent to the blacklist and bans all currently
     * connected peers with a matching user agent.
     */

    public void banPeerUserAgent(@NonNull String userAgent) {
        Set<String> bannedUserAgents = new HashSet<>(pref.peerUserAgentBlacklist());
        if (userAgent.isEmpty() || !bannedUserAgents.add(userAgent))
            return;

        setPeerUserAgentBlacklist(bannedUserAgents);
    }

    /*
     * Scans connected peers of all running torrents and bans:
     *  - peers flagged by the PeerBanHelper-compatible anti-leech engine
     *    (AntiVampire, progress-cheat, IP/CIDR rules, BTN cloud rules);
     *  - peers whose user agent matches the user-agent blacklist
     *    (equivalent of PBH's ClientNameBlacklist; enforced regardless of
     *    the anti-leech master switch since it is a manual list).
     *
     * Fast-PCB probes (BAN_FOR_DISCONNECT) are NOT real bans: the peer is
     * disconnected and blocked for pcbFastPcbTestBlockingDuration only, and
     * is not persisted to the blacklist nor submitted to BTN.
     *
     * Runs on pbhScanExec; performs no network I/O (BTN work is enqueued
     * onto btnExec).
     */

    private void checkAndBanBadPeers() {
        if (!isRunning())
            return;

        long nowMs = System.currentTimeMillis();
        evictPbhState(nowMs);

        PbhSettings settings = buildPbhSettings();
        unbanExpiredAutoBans(nowMs);
        expireDisconnectBans(nowMs);

        refreshBtnRulesAsync();

        List<TorrentSnapshot> snapshots = buildTorrentSnapshots();
        Map<String, TorrentSnapshot> torrentById = new HashMap<>();
        for (TorrentSnapshot torrent : snapshots)
            torrentById.put(torrent.id, torrent);

        /*
         * The ban universe this scan starts from: the user's manual blacklist
         * plus the still-active automatic bans. Peers already covered by it
         * are skipped by every detector, and the set also feeds AutoRangeBan
         * ("connectivity by association" with already banned addresses).
         */
        Set<String> alreadyBanned = new HashSet<>(pref.peerIpBlacklist());
        alreadyBanned.addAll(banRecordStore.activeIps(nowMs));
        pbhEngine.updateRangeBanAddresses(alreadyBanned, settings);

        // Collect bans with the peer/torrent context needed for BTN submission.
        List<BanContext> newBans = new ArrayList<>();
        List<String> disconnectProbes = new ArrayList<>();

        // Run the PeerBanHelper-compatible detection engine if enabled.
        if (settings.enabled) {
            for (BanResult ban : pbhEngine.evaluate(snapshots, settings)) {
                if (ban == null || ban.peerIp == null)
                    continue;
                if (ban.action == BanResult.Action.BAN_FOR_DISCONNECT) {
                    // Temporary probe: disconnect only, never a real ban.
                    disconnectProbes.add(ban.peerIp);
                    logPbh("disconnect probe by " + ban.module + ": " + ban.peerIp +
                            (ban.reason == null ? "" : " (" + ban.reason + ")"));
                    continue;
                }
                if (alreadyBanned.contains(ban.peerIp))
                    continue; // already banned (manual or earlier auto ban)
                String msg = "ban by " + ban.module + ": " + ban.peerIp +
                        (ban.reason == null ? "" : " (" + ban.reason + ")");
                logPbh(msg);
                TorrentSnapshot torrent = ban.torrentId != null
                        ? torrentById.get(ban.torrentId) : null;
                BanContext ctx = new BanContext(ban,
                        torrent != null ? findPeerIn(torrent, ban.peerIp) : null,
                        torrent);
                if (newBans.add(ctx)) {
                    alreadyBanned.add(ban.peerIp); // one entry per IP this scan
                }
            }
        }

        // User-agent blacklist (manual list, independent of the master switch).
        Set<String> bannedUserAgents = pref.peerUserAgentBlacklist();
        if (!bannedUserAgents.isEmpty()) {
            for (TorrentSnapshot torrent : snapshots) {
                for (PeerSnapshot peer : torrent.peers) {
                    if (peer.ip == null || peer.ip.isEmpty() || alreadyBanned.contains(peer.ip))
                        continue;
                    if (ClientNameMatcher.matches(peer.client, bannedUserAgents)) {
                        String msg = "ban by user-agent blacklist: " + peer.ip +
                                " (client=" + peer.client + ")";
                        logPbh(msg);
                        newBans.add(new BanContext(
                                BanResult.ban("user-agent-blacklist", peer.ip, "banned user agent")
                                        .withTorrentId(torrent.id),
                                peer, torrent));
                        alreadyBanned.add(peer.ip);
                    }
                }
            }
        }

        // Apply the fast-PCB disconnect probes (short block, auto-expires).
        if (!disconnectProbes.isEmpty()) {
            for (String ip : disconnectProbes)
                disconnectBans.add(ip, settings.pcbFastPcbTestBlockingDurationMs, nowMs);
            session.banIps(new HashSet<>(disconnectProbes));
            logPbh("fast-PCB probe: disconnected " + disconnectProbes.size() +
                    " peer(s) for " + settings.pcbFastPcbTestBlockingDurationMs + " ms");
        }

        /*
         * Record and apply the real bans. Auto bans live in their own store
         * (with reason/expiry metadata); the manual blacklist is untouched.
         */
        if (!newBans.isEmpty()) {
            Set<String> freshIps = new LinkedHashSet<>();
            for (BanContext ctx : newBans)
                freshIps.add(ctx.ban.peerIp);
            session.banIps(freshIps);
            recordAutoBans(newBans, settings);
            submitBansAsync(newBans);
        }
        submitSwarmSnapshotAsync(snapshots);
        submitHistoryAsync(snapshots);
    }

    /*
     * Periodically drops anti-leech tracking state that has not been touched
     * recently, so it cannot grow without bound. Runs at most once per hour.
     */
    private void evictPbhState(long nowMs) {
        if (nowMs - lastPbhStateEvictMs < 3600_000L)
            return;
        lastPbhStateEvictMs = nowMs;
        pbhEngine.evictStaleState(PBH_STATE_TTL_MS, nowMs);
        evictStaleSwarmTracks(nowMs);
    }

    /*
     * Removes expired fast-PCB disconnect probes and lifts the block. Called
     * from the scan before detection so probes never outlive their duration.
     */
    private void expireDisconnectBans(long nowMs) {
        if (!disconnectBans.hasExpired(nowMs))
            return;

        Set<String> expired = disconnectBans.removeExpired(nowMs);
        if (expired.isEmpty())
            return;

        if (isRunning())
            applyEffectiveBannedIps();
        logPbh("fast-PCB probe expired, unblocked: " + String.join(", ", expired));
    }

    /*
     * Stores the metadata (module, reason, torrent, expiry) of the newly
     * detected auto-bans. Range bans use their own (typically shorter)
     * duration; permanent bans (duration 0) never expire by themselves.
     * Re-detection of a still-recorded IP does not extend its expiry.
     */
    private void recordAutoBans(List<BanContext> bans, PbhSettings settings) {
        long now = System.currentTimeMillis();
        for (BanContext ctx : bans) {
            long durationMs = ctx.ban.module.equals("auto-range-ban")
                    ? settings.rangeBanDurationMs
                    : settings.banDurationMs;
            long expireAt = durationMs > 0 ? now + durationMs : 0;
            banRecordStore.record(new BanRecord(
                    ctx.ban.peerIp,
                    ctx.ban.module,
                    ctx.ban.reason == null ? "" : ctx.ban.reason,
                    ctx.torrent != null ? ctx.torrent.name : "",
                    now,
                    expireAt));
        }

        logPbh("auto-ban recorded for " + bans.size() + " peer(s)"
                + " (duration " + settings.banDurationMs + " ms)");
    }

    /*
     * The automatic ban records for the ban-list UI (reason, module,
     * torrent, expiry). Manual blacklist entries are not included.
     */
    @NonNull
    public List<BanRecord> getAutoBanRecords() {
        return banRecordStore.all();
    }

    /*
     * Lifts a single automatic ban (e.g. from the ban-list UI) and updates
     * the session IP filter right away.
     */
    public void unbanAutoRecord(@NonNull String ip) {
        if (banRecordStore.remove(ip) != null) {
            logPbh("auto-ban lifted manually: " + ip);
            if (isRunning())
                applyEffectiveBannedIps();
        }
    }

    /*
     * Queries the BTN network's aggregated information about one IP
     * (BTN ip_query ability) off the UI thread. The callback is invoked on
     * the BTN network executor with null result on failure.
     */
    public void queryBtnIp(@NonNull String ip,
                           @NonNull java.util.function.BiConsumer<BtnIpQueryResult, Exception> callback) {
        btnExec.execute(() -> {
            try {
                callback.accept(btnManager.queryIp(buildBtnSettings(), ip), null);
            } catch (Exception e) {
                Log.e(TAG, "[PBH] BTN IP query error: " + Log.getStackTraceString(e));
                callback.accept(null, e);
            }
        });
    }

    /*
     * Removes auto-ban records whose expiry time has passed and lifts the
     * block. Permanent records (expiry 0) are never removed automatically.
     */
    private void unbanExpiredAutoBans(long now) {
        List<BanRecord> expired = banRecordStore.removeExpired(now);
        if (expired.isEmpty())
            return;

        if (isRunning())
            applyEffectiveBannedIps();

        List<String> ips = new ArrayList<>();
        for (BanRecord r : expired)
            ips.add(r.ip);
        logPbh(ips.size() + " auto-ban(s) expired, unbanned: "
                + String.join(", ", ips));
    }

    /* Extracts the IP from an "ip=expiryMs" auto-ban entry. */
    private static String getAutoBanIp(String entry) {
        int idx = entry.lastIndexOf('=');
        return (idx < 0 ? entry : entry.substring(0, idx));
    }

    /* Extracts the expiry time from an "ip=expiryMs" entry, 0 if invalid. */
    private static long getAutoBanExpiry(String entry) {
        int idx = entry.lastIndexOf('=');
        if (idx < 0)
            return 0;
        try {
            return Long.parseLong(entry.substring(idx + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /* Finds the stored auto-ban entry for the given IP, or null. */
    @Nullable
    private static String findAutoBan(Set<String> autoBans, String ip) {
        for (String entry : autoBans) {
            if (getAutoBanIp(entry).equals(ip))
                return entry;
        }
        return null;
    }

    /* Locates the peer snapshot with the given IP within one torrent. */
    @Nullable
    private static PeerSnapshot findPeerIn(TorrentSnapshot torrent, String ip) {
        for (PeerSnapshot peer : torrent.peers) {
            if (peer.ip != null && peer.ip.equals(ip))
                return peer;
        }
        return null;
    }

    /*
     * The effective set of IPs to block in the session: the manual blacklist,
     * the still-active automatic bans and the fast-PCB disconnect probes.
     * Replaces the session-level ban set wholesale.
     */
    private void applyEffectiveBannedIps() {
        long now = System.currentTimeMillis();
        Set<String> effective = new HashSet<>(pref.peerIpBlacklist());
        effective.addAll(banRecordStore.activeIps(now));
        effective.addAll(disconnectBans.active(now));
        session.setBannedIps(effective);
    }

    /*
     * Enqueues a BTN ban submission onto btnExec, so the scan is never
     * blocked on network I/O. The captured context is immutable.
     */
    private void submitBansAsync(List<BanContext> bans) {
        List<BanContext> snapshot = new ArrayList<>(bans);
        btnExec.execute(() -> {
            try {
                doSubmitBans(snapshot);
            } catch (Exception e) {
                Log.e(TAG, "[PBH] BTN ban submission error: " + Log.getStackTraceString(e));
            }
        });
    }

    /*
     * Submits the collected bans to BTN (split into requests of at most 1000
     * entries, deduplicated per IP). Rate-limited by
     * BTN_MIN_BAN_SUBMIT_INTERVAL_MS. Runs on btnExec.
     */
    private void doSubmitBans(List<BanContext> bans) {
        if (bans.isEmpty())
            return;
        BtnSettings btnSettings = buildBtnSettings();
        if (!btnSettings.enabled || !btnSettings.submitBansEnabled)
            return;

        long now = System.currentTimeMillis();
        if (now - lastBanSubmitMs < BTN_MIN_BAN_SUBMIT_INTERVAL_MS)
            return;
        lastBanSubmitMs = now;

        List<BtnPayload.BanEntry> entries = new ArrayList<>();
        Set<String> seenIps = new HashSet<>();
        for (BanContext ctx : bans) {
            if (!seenIps.add(ctx.ban.peerIp))
                continue; // one entry per IP, even if several modules hit it
            BtnPayload.BanEntry e = new BtnPayload.BanEntry();
            e.banAtMs = now;
            e.peerIp = ctx.ban.peerIp;
            e.module = ctx.ban.module;
            e.rule = ctx.ban.module;
            e.description = ctx.ban.reason == null ? ctx.ban.module : ctx.ban.reason;
            if (ctx.peer != null) {
                e.peerPort = ctx.peer.port;
                e.peerId = ctx.peer.peerId;
                e.peerClientName = ctx.peer.client;
                e.peerProgress = ctx.peer.progressPpm / 1_000_000.0;
                e.fromPeerTraffic = ctx.peer.totalDownload;
                e.toPeerTraffic = ctx.peer.totalUpload;
            }
            if (ctx.torrent != null) {
                e.torrentIdentifier = TorrentIdentifier.getHashedIdentifier(ctx.torrent.id);
                e.torrentIsPrivate = ctx.torrent.privateTorrent;
                e.torrentSize = ctx.torrent.totalSize;
                e.downloaderProgress = ctx.torrent.totalSize == 0 ? 0.0
                        : (double) ctx.torrent.completedSize / ctx.torrent.totalSize;
            }
            entries.add(e);
        }

        int submitted = 0;
        for (int i = 0; i < entries.size(); i += BTN_MAX_ENTRIES_PER_REQUEST) {
            List<BtnPayload.BanEntry> batch = entries.subList(i,
                    Math.min(i + BTN_MAX_ENTRIES_PER_REQUEST, entries.size()));
            boolean ok;
            try {
                ok = btnManager.submitBans(btnSettings, batch);
            } catch (Exception e) {
                ok = false;
                Log.e(TAG, "[PBH] BTN ban submission error: " + e.getMessage());
            }
            String msg = ok
                    ? "BTN bans submitted: " + batch.size() + " entries"
                    : "BTN ban submission failed: " + batch.size() + " entries";
            logPbh(msg);
            if (ok)
                submitted += batch.size();
        }
        if (submitted == 0)
            return;

        // Log the per-ban details for the activity log.
        for (BanContext ctx : bans) {
            logPbh("BTN submit ban: " + ctx.ban.peerIp +
                    " by " + ctx.ban.module);
        }
    }

    /*
     * Enqueues a BTN swarm submission onto btnExec.
     */
    private void submitSwarmSnapshotAsync(List<TorrentSnapshot> snapshots) {
        List<TorrentSnapshot> snapshot = new ArrayList<>(snapshots);
        btnExec.execute(() -> {
            try {
                doSubmitSwarmSnapshot(snapshot);
            } catch (Exception e) {
                Log.e(TAG, "[PBH] BTN swarm submission error: " + Log.getStackTraceString(e));
            }
        });
    }

    /*
     * Submits the current swarm snapshot (all connected peers of all
     * torrents) to BTN every BTN_SWARM_SUBMIT_INTERVAL_MS. Runs on btnExec.
     */
    private void doSubmitSwarmSnapshot(List<TorrentSnapshot> snapshots) {
        BtnSettings btnSettings = buildBtnSettings();
        if (!btnSettings.enabled || !btnSettings.submitSwarmEnabled)
            return;

        long now = System.currentTimeMillis();
        if (now - lastSwarmSubmitMs < BTN_SWARM_SUBMIT_INTERVAL_MS)
            return;
        lastSwarmSubmitMs = now;

        List<BtnPayload.SwarmEntry> entries = new ArrayList<>();
        for (TorrentSnapshot torrent : snapshots) {
            if (torrent.peers.isEmpty())
                continue;
            String torrentId = TorrentIdentifier.getHashedIdentifier(torrent.id);
            for (PeerSnapshot peer : torrent.peers) {
                BtnPayload.SwarmEntry e = new BtnPayload.SwarmEntry();
                e.torrentIdentifier = torrentId;
                e.torrentIsPrivate = torrent.privateTorrent;
                e.torrentSize = torrent.totalSize;
                e.downloader = btnSettings.installationId == null || btnSettings.installationId.isEmpty()
                        ? "LibreTorrent" : btnSettings.installationId;
                e.downloaderProgress = torrent.totalSize == 0 ? 0.0
                        : (double) torrent.completedSize / torrent.totalSize;
                e.peerIp = peer.ip;
                e.peerPort = peer.port;
                e.peerId = peer.peerId;
                e.peerClientName = peer.client;
                e.peerProgress = peer.progressPpm / 1_000_000.0;
                SwarmTrack track = swarmTracks.get(swarmKey(torrent.id, peer.ip));
                if (track != null) {
                    /*
                     * Traffic offsets reconstruct the true cumulative traffic
                     * across reconnects: the server adds the offset to the
                     * connection-local counters reported above.
                     */
                    e.toPeerTraffic = peer.totalUpload;
                    e.toPeerTrafficOffset = track.uploadedOffset;
                    e.fromPeerTraffic = peer.totalDownload;
                    e.fromPeerTrafficOffset = track.downloadedOffset;
                    e.firstTimeSeenMs = track.firstSeenMs;
                    e.lastTimeSeenMs = track.lastSeenMs;
                    e.uploadSpeedMax = track.upSpeedMax;
                    e.downloadSpeedMax = track.downSpeedMax;
                } else {
                    e.toPeerTraffic = peer.totalUpload;
                    e.toPeerTrafficOffset = 0;
                    e.fromPeerTraffic = peer.totalDownload;
                    e.fromPeerTrafficOffset = 0;
                    e.firstTimeSeenMs = now;
                    e.lastTimeSeenMs = now;
                }
                e.uploadSpeed = peer.upSpeed;
                e.downloadSpeed = peer.downSpeed;
                entries.add(e);
            }
        }

        if (entries.isEmpty())
            return;

        boolean ok = false;
        try {
            ok = btnManager.submitSwarm(btnSettings, entries);
        } catch (Exception e) {
            Log.e(TAG, "[PBH] BTN swarm submission error: " + e.getMessage());
        }
        String msg = ok
                ? "BTN swarm submitted: " + entries.size() + " peers"
                : "BTN swarm submission failed: " + entries.size() + " peers";
        logPbh(msg);
    }

    /*
     * Enqueues a BTN peer-history submission onto btnExec.
     */
    private void submitHistoryAsync(List<TorrentSnapshot> snapshots) {
        List<TorrentSnapshot> snapshot = new ArrayList<>(snapshots);
        btnExec.execute(() -> {
            try {
                doSubmitHistory(snapshot);
            } catch (Exception e) {
                Log.e(TAG, "[PBH] BTN history submission error: " + Log.getStackTraceString(e));
            }
        });
    }

    /*
     * Reports the peers seen since the last successful submission to the
     * legacy BTN submit_histories ability (rate-limited internally by the
     * interval from the server config). Runs on btnExec.
     */
    private void doSubmitHistory(List<TorrentSnapshot> snapshots) {
        BtnSettings btnSettings = buildBtnSettings();
        if (!btnSettings.enabled || !btnSettings.submitHistoryEnabled)
            return;

        List<BtnPayload.PeerHistoryEntry> entries = new ArrayList<>();
        for (TorrentSnapshot torrent : snapshots) {
            String torrentId = TorrentIdentifier.getHashedIdentifier(torrent.id);
            for (PeerSnapshot peer : torrent.peers) {
                SwarmTrack track = swarmTracks.get(swarmKey(torrent.id, peer.ip));
                if (track == null || track.lastSeenMs <= lastHistorySubmitMs)
                    continue; // unchanged since the last submission
                BtnPayload.PeerHistoryEntry e = new BtnPayload.PeerHistoryEntry();
                e.ipAddress = peer.ip;
                e.port = track.port;
                e.peerId = track.peerId;
                e.clientName = track.clientName;
                e.torrentIdentifier = torrentId;
                e.torrentIsPrivate = torrent.privateTorrent;
                e.torrentSize = torrent.totalSize;
                e.downloaded = peer.totalDownload;
                e.downloadedOffset = track.downloadedOffset;
                e.uploaded = peer.totalUpload;
                e.uploadedOffset = track.uploadedOffset;
                e.firstTimeSeenMs = track.firstSeenMs;
                e.lastTimeSeenMs = track.lastSeenMs;
                e.peerFlag = "";
                entries.add(e);
            }
        }
        if (entries.isEmpty())
            return;

        boolean ok = false;
        try {
            ok = btnManager.submitHistory(btnSettings, entries);
        } catch (Exception e) {
            Log.e(TAG, "[PBH] BTN history submission error: " + e.getMessage());
        }
        if (ok) {
            lastHistorySubmitMs = System.currentTimeMillis();
            logPbh("BTN history submitted: " + entries.size() + " peer record(s)");
        }
    }

    /* A ban together with the peer/torrent context it came from. */
    private static final class BanContext {
        final BanResult ban;
        @Nullable final PeerSnapshot peer;
        @Nullable final TorrentSnapshot torrent;

        BanContext(BanResult ban, @Nullable PeerSnapshot peer, @Nullable TorrentSnapshot torrent) {
            this.ban = ban;
            this.peer = peer;
            this.torrent = torrent;
        }
    }

    /*
     * Per (torrent, peer) swarm observations used for BTN submissions: real
     * first/last-seen times and traffic offsets that survive reconnects.
     */
    private static final class SwarmTrack {
        long firstSeenMs;
        volatile long lastSeenMs;
        /* Latest connection metadata, captured for BTN history submissions */
        volatile int port;
        volatile String peerId = "";
        volatile String clientName = "";
        /* Traffic totals of previous connections, added on counter resets */
        volatile long uploadedOffset;
        volatile long downloadedOffset;
        volatile long lastUploaded;
        volatile long lastDownloaded;
        volatile long upSpeedMax;
        volatile long downSpeedMax;
    }

    private final ConcurrentHashMap<String, SwarmTrack> swarmTracks = new ConcurrentHashMap<>();

    private static String swarmKey(String torrentId, String ip) {
        return torrentId + "|" + ip;
    }

    /*
     * Updates the swarm tracking state for every peer of the given snapshot.
     * Called on every scan so speed maxima and last-seen times stay fresh.
     */
    private void updateSwarmTracks(List<TorrentSnapshot> snapshots, long nowMs) {
        Set<String> liveKeys = new HashSet<>();
        for (TorrentSnapshot torrent : snapshots) {
            for (PeerSnapshot peer : torrent.peers) {
                if (peer.ip == null || peer.ip.isEmpty())
                    continue;
                String key = swarmKey(torrent.id, peer.ip);
                liveKeys.add(key);
                SwarmTrack track = swarmTracks.computeIfAbsent(key, k -> {
                    SwarmTrack t = new SwarmTrack();
                    t.firstSeenMs = nowMs;
                    return t;
                });
                track.lastSeenMs = nowMs;
                track.port = peer.port;
                track.peerId = peer.peerId;
                track.clientName = peer.client;
                if (peer.totalUpload < track.lastUploaded)
                    track.uploadedOffset += track.lastUploaded; // counter reset
                track.lastUploaded = Math.max(peer.totalUpload, track.lastUploaded);
                if (peer.totalDownload < track.lastDownloaded)
                    track.downloadedOffset += track.lastDownloaded;
                track.lastDownloaded = Math.max(peer.totalDownload, track.lastDownloaded);
                track.upSpeedMax = Math.max(track.upSpeedMax, peer.upSpeed);
                track.downSpeedMax = Math.max(track.downSpeedMax, peer.downSpeed);
            }
        }
        /* Drop entries of peers that are no longer connected */
        swarmTracks.keySet().retainAll(liveKeys);
    }

    /* Drops swarm tracking state that has not been refreshed recently. */
    private void evictStaleSwarmTracks(long nowMs) {
        swarmTracks.entrySet().removeIf(e ->
                nowMs - e.getValue().lastSeenMs > PBH_STATE_TTL_MS);
    }

    /*
     * Builds the immutable settings snapshot consumed by the anti-leech engine
     * from the current user preferences.
     */

    private PbhSettings buildPbhSettings() {
        return PbhSettings.builder()
                .enabled(pref.pbhEnabled())
                .checkIntervalSec(pref.pbhCheckInterval())
                .banDurationMs(pref.pbhBanDuration())
                .antiVampireEnabled(pref.pbhAntiVampireEnabled())
                .antiVampireUploadThreshold(pref.pbhAntiVampireUploadThreshold())
                .antiVampireMinProgressPpm(pref.pbhAntiVampireMinProgressPpm())
                .ipCidrBlacklist(pref.peerIpBlacklist())
                .peerIdBlacklist(pref.peerIdBlacklist())
                .rangeBanEnabled(pref.pbhRangeBanEnabled())
                .rangeBanIpv4PrefixLength(pref.pbhRangeBanIpv4PrefixLength())
                .rangeBanIpv6PrefixLength(pref.pbhRangeBanIpv6PrefixLength())
                .rangeBanDurationMs(pref.pbhRangeBanDuration())
                .pcbEnabled(pref.pbhPcbEnabled())
                .pcbTorrentMinimumSize(pref.pbhPcbTorrentMinimumSize())
                .pcbBlockExcessiveClients(pref.pbhPcbBlockExcessiveClients())
                .pcbExcessiveThreshold(pref.pbhPcbExcessiveThreshold())
                .pcbMaximumDifference(pref.pbhPcbMaximumDifference())
                .pcbRewindMaximumDifference(pref.pbhPcbRewindMaximumDifference())
                .pcbBanDelayDurationMs(pref.pbhPcbBanDelayDuration())
                .pcbIpv4PrefixLength(pref.pbhPcbIpv4PrefixLength())
                .pcbIpv6PrefixLength(pref.pbhPcbIpv6PrefixLength())
                .pcbFastPcbTestPercentage(pref.pbhPcbFastPcbTestPercentage())
                .pcbFastPcbTestBlockingDurationMs(pref.pbhPcbFastPcbTestBlockingDuration())
                .build();
    }

    /*
     * Builds the BTN settings snapshot from the current user preferences.
     */

    private BtnSettings buildBtnSettings() {
        return BtnSettings.builder()
                .enabled(pref.btnEnabled())
                .configUrl(pref.btnConfigUrl())
                .appId(pref.btnAppId())
                .appSecret(pref.btnAppSecret())
                .installationId(pref.btnInstallationId())
                .submitBansEnabled(pref.btnSubmitBansEnabled())
                .submitSwarmEnabled(pref.btnSubmitSwarmEnabled())
                .submitHistoryEnabled(pref.btnSubmitHistoryEnabled())
                .build();
    }

    /*
     * Enqueues a BTN rule refresh onto btnExec so the scan is never blocked
     * on network I/O. Refreshed rules apply from the next scan on.
     */
    private void refreshBtnRulesAsync() {
        if (!pref.btnEnabled())
            return;

        btnExec.execute(() -> {
            try {
                doRefreshBtnRules();
            } catch (Exception e) {
                Log.e(TAG, "[PBH] " + Log.getStackTraceString(e));
            }
        });
    }

    /*
     * Refreshes the BTN cloud rules at most once per minute (the intervals
     * from the server config are much longer). Updates the engine's BTN module
     * with the latest rules. Runs on btnExec.
     */

    private void doRefreshBtnRules() {
        if (!pref.btnEnabled())
            return;

        long now = System.currentTimeMillis();
        if (now - lastBtnRefreshMs < BTN_MIN_REFRESH_INTERVAL_MS)
            return;
        lastBtnRefreshMs = now;

        try {
            BtnRuleSet rules = btnManager.refresh(buildBtnSettings());
            pbhEngine.updateBtnRules(rules);
            String msg = "BTN rules refreshed: " +
                    rules.ipDenylist.size() + " deny IPs, " +
                    rules.ipAllowlist.size() + " allow IPs, " +
                    rules.clientNameRules.size() + " client-name rules, " +
                    rules.peerIdRules.size() + " peer-id rules";
            logPbh(msg);

            // Keep-alive; the manager rate-limits by the server interval.
            String externalIp = btnManager.heartbeat(buildBtnSettings(), now);
            if (externalIp != null)
                logPbh("BTN heartbeat sent, external IP: " + externalIp);
        } catch (Exception e) {
            logPbh("Unable to refresh BTN rules: " + e.getMessage());
            Log.e(TAG, "[PBH] " + Log.getStackTraceString(e));
        }
    }

    /*
     * Builds a snapshot of all running torrents and their connected peers for
     * the anti-leech engine, and refreshes the BTN swarm tracking state.
     */

    private List<TorrentSnapshot> buildTorrentSnapshots() {
        List<TorrentSnapshot> snapshots = new ArrayList<>();
        for (Torrent torrent : repo.getAllTorrents()) {
            if (torrent == null)
                continue;

            TorrentDownload task = session.getTask(torrent.id);
            if (task == null)
                continue;

            List<AdvancedPeerInfo> peerInfoList = task.getAdvancedPeerInfoList();
            List<PeerSnapshot> peers = new ArrayList<>(peerInfoList.size());
            for (AdvancedPeerInfo peer : peerInfoList) {
                if (peer == null || peer.ip() == null || peer.ip().isEmpty())
                    continue;
                /*
                 * libtorrent exposes peer endpoints as "tcp://1.2.3.4:6881"
                 * and may report IPv4 as IPv4-mapped IPv6 ("::ffff:1.2.3.4");
                 * normalise to the bare, canonical address so the ban engine
                 * and IP filter see one identity per peer.
                 */
                String ip = IpUtils.normalizeIp(peer.ip());
                if (ip.isEmpty())
                    continue;
                peers.add(new PeerSnapshot(
                        ip,
                        peer.port(),
                        peer.client(),
                        peer.peerId(),
                        peer.totalUpload(),
                        peer.totalDownload(),
                        peer.progressPpm(),
                        peer.upSpeed(),
                        peer.downSpeed()));
            }

            snapshots.add(new TorrentSnapshot(
                    torrent.id,
                    torrent.name,
                    task.getSize(),
                    task.getReceivedBytes(),
                    task.isPrivate(),
                    peers));
        }

        updateSwarmTracks(snapshots, System.currentTimeMillis());

        return snapshots;
    }

    public int getUploadSpeedLimit(@NonNull String id) {
        if (!isRunning())
            return -1;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return -1;

        return task.getUploadSpeedLimit();
    }

    public int getDownloadSpeedLimit(@NonNull String id) {
        if (!isRunning())
            return -1;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return -1;

        return task.getDownloadSpeedLimit();
    }

    public void setDownloadSpeedLimit(@NonNull String id, int limit) {
        if (!isRunning())
            return;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return;

        task.setDownloadSpeedLimit(limit);
    }

    public void setUploadSpeedLimit(@NonNull String id, int limit) {
        if (!isRunning())
            return;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return;

        task.setUploadSpeedLimit(limit);
    }

    public byte[] getBencode(@NonNull String id) {
        if (!isRunning())
            return null;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return null;

        return task.getBencode();
    }

    public boolean isSequentialDownload(@NonNull String id) {
        if (!isRunning())
            return false;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return false;

        return task.isSequentialDownload();
    }

    public int[] getPieceSizeList() {
        return session.getPieceSizeList();
    }

    public int[] getTorrentVersionList() {
        return session.getTorrentVersionList();
    }

    public Logger getSessionLogger() {
        return session.getLogger();
    }

    /*
     * Applies the journal filter prefs to the session logger immediately.
     * Used by the log page's filter dialog so toggles take effect at once.
     */
    public void applyLogFilters(boolean logSession, boolean logPbh,
                                boolean logDht, boolean logPeer,
                                boolean logPortmap, boolean logTorrent) {
        if (session.getLogger() instanceof SessionLogger sessionLogger) {
            sessionLogger.applyFilterParams(new SessionLogger.SessionFilterParams(
                    logSession, logPbh, logDht, logPeer, logPortmap, logTorrent));
        }
    }

    /*
     * Logs a PeerBanHelper anti-leech message to the session logger (log page
     * in the main screen). Cheap to call on every detection.
     */
    private void logPbh(@NonNull String msg) {
        Log.i(TAG, "[PBH] " + msg);
        if (session.getLogger() instanceof SessionLogger sessionLogger) {
            sessionLogger.logPbh(msg);
        }
    }

    private void saveTorrentFileIn(@NonNull Torrent torrent,
                                   @NonNull Uri saveDir) {
        String torrentFileName = torrent.name + ".torrent";
        try {
            if (!saveTorrentFile(torrent.id, saveDir, torrentFileName))
                Log.w(TAG, "Could not save torrent file + " + torrentFileName);

        } catch (Exception e) {
            Log.w(TAG, "Could not save torrent file + " + torrentFileName + ": ", e);
        }
    }

    private boolean saveTorrentFile(String id, Uri destDir, String fileName) throws IOException, UnknownUriException {
        byte[] bencode = getBencode(id);
        if (bencode == null)
            return false;

        String name = (fileName != null ? fileName : id);

        Uri path = fs.createFile(destDir, name, true);
        if (path == null)
            return false;

        fs.write(bencode, path);

        return true;
    }

    private void switchPowerReceiver() {
        boolean batteryControl = pref.batteryControl();
        boolean customBatteryControl = pref.customBatteryControl();
        boolean onlyCharging = pref.onlyCharging();

        try {
            appContext.unregisterReceiver(powerReceiver);

        } catch (IllegalArgumentException e) {
            /* Ignore non-registered receiver */
        }
        if (customBatteryControl) {
            ContextCompat.registerReceiver(appContext, powerReceiver, PowerReceiver.getCustomFilter(), ContextCompat.RECEIVER_NOT_EXPORTED);
            /* Custom receiver doesn't send sticky intent, reschedule manually */
            rescheduleTorrents();
        } else if (batteryControl || onlyCharging) {
            ContextCompat.registerReceiver(appContext, powerReceiver, PowerReceiver.getFilter(), ContextCompat.RECEIVER_NOT_EXPORTED);
        }
    }

    private void switchConnectionReceiver() {
        boolean unmeteredOnly = pref.unmeteredConnectionsOnly();
        boolean roaming = pref.enableRoaming();

        try {
            appContext.unregisterReceiver(connectionReceiver);

        } catch (IllegalArgumentException e) {
            /* Ignore non-registered receiver */
        }
        if (unmeteredOnly || roaming) {
            ContextCompat.registerReceiver(appContext, connectionReceiver, ConnectionReceiver.getFilter(), ContextCompat.RECEIVER_NOT_EXPORTED);
        }
    }

    private boolean checkPauseTorrents() {
        boolean batteryControl = pref.batteryControl();
        boolean customBatteryControl = pref.customBatteryControl();
        int customBatteryControlValue = pref.customBatteryControlValue();
        boolean onlyCharging = pref.onlyCharging();
        boolean unmeteredOnly = pref.unmeteredConnectionsOnly();
        boolean roaming = pref.enableRoaming();

        boolean stop = false;
        if (roaming)
            stop = Utils.isRoaming(appContext);
        if (unmeteredOnly)
            stop = Utils.isMetered(appContext);
        if (onlyCharging)
            stop |= !Utils.isBatteryCharging(appContext);
        if (customBatteryControl)
            stop |= Utils.isBatteryBelowThreshold(appContext, customBatteryControlValue);
        else if (batteryControl)
            stop |= Utils.isBatteryLow(appContext);

        return stop;
    }

    private void handleOnSessionStarted() {
        applyEffectiveBannedIps();

        if (pref.enableIpFiltering()) {
            String path = pref.ipFilteringFile();
            if (path != null)
                session.enableIpFilter(Uri.parse(path));
        }

        if (pref.watchDir())
            startWatchDir();

        boolean enableStreaming = pref.enableStreaming();
        if (enableStreaming)
            startStreamingServer();

        loadTorrents();
    }

    private void startStreamingServer() {
        stopStreamingServer();

        String hostname = pref.streamingHostname();
        int port = pref.streamingPort();

        torrentStreamServer = new TorrentStreamServer(hostname, port);
        try {
            torrentStreamServer.start(appContext);

        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            notifier.makeErrorNotify(appContext.getString(R.string.pref_streaming_error));
        }
    }

    private void stopStreamingServer() {
        if (torrentStreamServer != null)
            torrentStreamServer.stop();
        torrentStreamServer = null;
    }

    private void loadTorrents() {
        disposables.add(Completable.fromRunnable(() -> {
                    if (isRunning())
                        session.restoreTorrents();

                }).subscribeOn(Schedulers.io())
                .subscribe());
    }

    private void setProxy() {
        SessionSettings s = session.getSettings();

        s.proxyType = SessionSettings.ProxyType.fromValue(pref.proxyType());
        s.proxyAddress = pref.proxyAddress();
        s.proxyPort = pref.proxyPort();
        s.proxyPeersToo = pref.proxyPeersToo();
        s.proxyRequireAllConnections = pref.proxyRequireAllConnections();
        s.proxyRequiresAuth = pref.proxyRequiresAuth();
        s.proxyLogin = pref.proxyLogin();
        s.proxyPassword = pref.proxyPassword();

        session.setSettings(s);
    }

    private SessionSettings.EncryptMode getEncryptInConnectionsMode() {
        return SessionSettings.EncryptMode.fromValue(pref.encryptInConnectionsMode());
    }

    private SessionSettings.EncryptMode getEncryptOutConnectionsMode() {
        return SessionSettings.EncryptMode.fromValue(pref.encryptOutConnectionsMode());
    }

    private void startWatchDir() {
        String dir = pref.dirToWatch();
        Uri uri = Uri.parse(dir);
        if (!Utils.isFileSystemPath(uri))
            throw new IllegalArgumentException("SAF is not supported:" + uri);
        dir = uri.getPath();

        scanTorrentsInDir(dir);
        fileObserver = makeTorrentFileObserver(dir);
        fileObserver.startWatching();
    }

    private void stopWatchDir() {
        if (fileObserver == null)
            return;

        fileObserver.stopWatching();
        fileObserver = null;
    }

    private TorrentFileObserver makeTorrentFileObserver(String pathToDir) {
        return new TorrentFileObserver(pathToDir) {
            @Override
            public void onEvent(int event, @Nullable String name) {
                if (name == null)
                    return;

                File f = new File(pathToDir, name);
                if (!f.exists())
                    return;
                if (f.isDirectory() || !f.getName().endsWith(".torrent"))
                    return;
                Uri uri = Uri.fromFile(f);
                disposables.add(addTorrentCompletable(uri)
                        .subscribeOn(Schedulers.io())
                        .subscribe(() -> {
                            if (pref.watchDirDeleteFile()) {
                                try {
                                    fs.deleteFile(uri);
                                } catch (IOException | UnknownUriException e) {
                                    Log.w(TAG, "[Watch] Unable to delete file: "
                                            + Log.getStackTraceString(e));
                                }
                            }
                        })
                );
            }
        };
    }

    private void scanTorrentsInDir(String pathToDir) {
        File dir = new File(pathToDir);
        if (!dir.exists())
            return;
        for (File file : org.apache.commons.io.FileUtils.listFiles(dir, FileFilterUtils.suffixFileFilter(".torrent"), null)) {
            if (!file.exists())
                continue;
            addTorrent(Uri.fromFile(file));
        }
    }

    private Torrent addTorrentSync(Uri file, TorrentMetaInfo info, Uri savePath)
            throws IOException,
            FreeSpaceException,
            TorrentAlreadyExistsException,
            DecodeException,
            UnknownUriException {
        Priority[] priorities = new Priority[info.fileCount];
        Arrays.fill(priorities, Priority.DEFAULT);
        Uri downloadPath = (savePath == null ? Uri.parse(pref.saveTorrentsIn()) : savePath);

        AddTorrentParams params = new AddTorrentParams(
                file.toString(),
                false,
                info.sha1Hash,
                info.torrentName,
                priorities,
                downloadPath,
                false,
                false,
                new ArrayList<>(),
                false
        );

        if (fs.getDirAvailableBytes(downloadPath) < info.torrentSize) {
            throw new FreeSpaceException();
        }

        return addTorrentSync(params, false);
    }

    private void handleAddTorrentError(String name, Throwable e) {
        if (e instanceof TorrentAlreadyExistsException) {
            notifier.makeTorrentInfoNotify(name, appContext.getString(R.string.torrent_exist));
            return;
        }
        Log.e(TAG, Log.getStackTraceString(e));
        String message;
        if (e instanceof FileNotFoundException)
            message = appContext.getString(R.string.error_file_not_found_add_torrent);
        else if (e instanceof IOException)
            message = appContext.getString(R.string.error_io_add_torrent);
        else
            message = appContext.getString(R.string.error_add_torrent);
        notifier.makeTorrentErrorNotify(name, message);
    }

    private void cleanTemp() {
        try {
            fs.cleanTempDir();

        } catch (Exception e) {
            Log.e(TAG, "Error during setup of temp directory: ", e);
        }
    }

    private void setRandomPortRange(boolean useRandomPort) {
        SessionSettings settings = session.getSettings();
        settings.useRandomPort = useRandomPort;
        if (!useRandomPort) {
            int first = pref.portRangeFirst();
            int second = pref.portRangeSecond();
            if (first != -1 && second != -1) {
                settings.portRangeFirst = first;
                settings.portRangeSecond = second;
            }
        }
        session.setSettings(settings, false);
    }

    private void setPortRange(int first, int second) {
        if (first == -1 || second == -1)
            return;

        SessionSettings settings = session.getSettings();
        settings.portRangeFirst = first;
        settings.portRangeSecond = second;
        session.setSettings(settings, false);
    }

    /*
     * Disable notifications for torrent
     */

    private void markAsHiddenSync(Torrent torrent) {
        torrent.visibility = Torrent.VISIBILITY_HIDDEN;
        repo.updateTorrent(torrent);
    }

    private final TorrentEngineListener engineListener = new TorrentEngineListener() {
        @Override
        public void onSessionStarted() {
            handleOnSessionStarted();
        }

        @Override
        public void onTorrentAdded(@NonNull String id) {
            if (pref.saveTorrentFiles())
                saveTorrentFileIn(repo.getTorrentById(id),
                        Uri.parse(pref.saveTorrentFilesIn()));

            if (checkPauseTorrents()) {
                disposables.add(Completable.fromRunnable(() -> {
                            if (!isRunning())
                                return;
                            TorrentDownload task = session.getTask(id);
                            if (task != null)
                                task.pause();

                        }).subscribeOn(Schedulers.io())
                        .subscribe());
            }
        }

        @Override
        public void onTorrentLoaded(@NonNull String id) {
            if (checkPauseTorrents()) {
                disposables.add(Completable.fromRunnable(() -> {
                            if (!isRunning())
                                return;
                            TorrentDownload task = session.getTask(id);
                            if (task != null)
                                task.pause();

                        }).subscribeOn(Schedulers.io())
                        .subscribe());
            }
        }

        @Override
        public void onTorrentFinished(@NonNull String id) {
            disposables.add(repo.getTorrentByIdSingle(id)
                    .subscribeOn(Schedulers.io())
                    .filter(Objects::nonNull)
                    .subscribe((torrent) -> {
                                notifier.makeTorrentFinishedNotify(torrent);
                                if (torrent.visibility != Torrent.VISIBILITY_HIDDEN)
                                    markAsHiddenSync(torrent);

                                if (pref.moveAfterDownload()) {
                                    String curPath = torrent.downloadPath.toString();
                                    String newPath = pref.moveAfterDownloadIn();

                                    if (!curPath.equals(newPath))
                                        setDownloadPath(id, Uri.parse(newPath));
                                }
                            },
                            (Throwable t) -> Log.e(TAG, "Getting torrent " + id + " error: " +
                                    Log.getStackTraceString(t)))
            );
        }

        @Override
        public void onTorrentMoving(@NonNull String id) {
            disposables.add(repo.getTorrentByIdSingle(id)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((torrent) -> {
                                String name;
                                if (torrent == null)
                                    name = id;
                                else
                                    name = torrent.name;

                                notifier.makeMovingTorrentNotify(name);
                            },
                            (Throwable t) -> Log.e(TAG, "Getting torrent " + id + " error: " +
                                    Log.getStackTraceString(t)))
            );
        }

        @Override
        public void onTorrentMoved(@NonNull String id, boolean success) {
            disposables.add(repo.getTorrentByIdSingle(id)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((torrent) -> {
                                String name;
                                if (torrent == null)
                                    name = id;
                                else
                                    name = torrent.name;

                                if (success)
                                    notifier.makeTorrentInfoNotify(name,
                                            appContext.getString(R.string.torrent_move_success));
                                else
                                    notifier.makeTorrentErrorNotify(name,
                                            appContext.getString(R.string.torrent_move_fail));
                            },
                            (Throwable t) -> Log.e(TAG, "Getting torrent " + id + " error: " +
                                    Log.getStackTraceString(t)))
            );
        }

        @Override
        public void onIpFilterParsed(int ruleCount) {
            disposables.add(Completable.fromRunnable(() -> Toast.makeText(appContext,
                                    (ruleCount > 0 ?
                                            appContext.getString(R.string.ip_filter_add_success) :
                                            appContext.getString(R.string.ip_filter_add_error, ruleCount)),
                                    Toast.LENGTH_LONG)
                            .show())
                    .subscribeOn(AndroidSchedulers.mainThread())
                    .subscribe()
            );
        }

        @Override
        public void onSessionError(@NonNull String errorMsg) {
            if (errorFilter.skip(errorMsg)) {
                return;
            }
            notifier.makeSessionErrorNotify(errorMsg);
        }

        @Override
        public void onNatError(@NonNull String errorMsg) {
            Log.e(TAG, "NAT error: " + errorMsg);
            if (pref.showNatErrors())
                notifier.makeNatErrorNotify(errorMsg);
        }

        @Override
        public void onRestoreSessionError(@NonNull String id) {
            disposables.add(repo.getTorrentByIdSingle(id)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((torrent) -> {
                                String name;
                                if (torrent == null)
                                    name = id;
                                else
                                    name = torrent.name;

                                notifier.makeTorrentErrorNotify(name,
                                        appContext.getString(R.string.restore_torrent_error));
                            },
                            (Throwable t) -> Log.e(TAG, "Getting torrent " + id + " error: " +
                                    Log.getStackTraceString(t)))
            );
        }

        @Override
        public void onTorrentMetadataLoaded(@NonNull String id, Exception err) {
            if (err != null) {
                Log.e(TAG, "Load metadata error: ");
                Log.e(TAG, Log.getStackTraceString(err));
            }

            disposables.add(repo.getTorrentByIdSingle(id)
                    .subscribeOn(Schedulers.io())
                    .filter(Objects::nonNull)
                    .subscribe((torrent) -> {
                                if (err == null) {
                                    if (pref.saveTorrentFiles())
                                        saveTorrentFileIn(torrent, Uri.parse(pref.saveTorrentFilesIn()));

                                } else if (err instanceof FreeSpaceException) {
                                    notifier.makeTorrentErrorNotify(torrent.name, appContext.getString(R.string.error_free_space));
                                }
                            },
                            (Throwable t) -> Log.e(TAG, "Getting torrent " + id + " error: " +
                                    Log.getStackTraceString(t)))
            );

            if (checkPauseTorrents()) {
                disposables.add(Completable.fromRunnable(() -> {
                            if (!isRunning())
                                return;
                            TorrentDownload task = session.getTask(id);
                            if (task != null)
                                task.pause();

                        }).subscribeOn(Schedulers.io())
                        .subscribe());
            }
        }
    };

    private void handleSettingsChanged(String key) {
        boolean reschedule = false;

        if (key.equals(appContext.getString(R.string.pref_key_unmetered_connections_only)) ||
                key.equals(appContext.getString(R.string.pref_key_enable_roaming))) {
            reschedule = true;
            switchConnectionReceiver();

        } else if (key.equals(appContext.getString(R.string.pref_key_download_and_upload_only_when_charging)) ||
                key.equals(appContext.getString(R.string.pref_key_battery_control))) {
            reschedule = true;
            switchPowerReceiver();

        } else if (key.equals(appContext.getString(R.string.pref_key_custom_battery_control)) ||
                key.equals(appContext.getString(R.string.pref_key_custom_battery_control_value))) {
            switchPowerReceiver();

        } else if (key.equals(appContext.getString(R.string.pref_key_max_download_speed))) {
            SessionSettings s = session.getSettings();
            s.downloadRateLimit = pref.maxDownloadSpeedLimit();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_max_upload_speed))) {
            SessionSettings s = session.getSettings();
            s.uploadRateLimit = pref.maxUploadSpeedLimit();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_max_connections))) {
            SessionSettings s = session.getSettings();
            s.connectionsLimit = pref.maxConnections();
            s.maxPeerListSize = s.connectionsLimit;
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_max_connections_per_torrent))) {
            session.setMaxConnectionsPerTorrent(pref.maxConnectionsPerTorrent());

        } else if (key.equals(appContext.getString(R.string.pref_key_max_uploads_per_torrent))) {
            session.setMaxUploadsPerTorrent(pref.maxUploadsPerTorrent());

        } else if (key.equals(appContext.getString(R.string.pref_key_max_active_downloads))) {
            SessionSettings s = session.getSettings();
            s.activeDownloads = pref.maxActiveDownloads();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_max_active_uploads))) {
            SessionSettings s = session.getSettings();
            s.activeSeeds = pref.maxActiveUploads();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_max_active_torrents))) {
            SessionSettings s = session.getSettings();
            s.activeLimit = pref.maxActiveTorrents();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enable_dht))) {
            SessionSettings s = session.getSettings();
            s.dhtEnabled = pref.enableDht();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enable_lsd))) {
            SessionSettings s = session.getSettings();
            s.lsdEnabled = pref.enableLsd();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enable_utp))) {
            SessionSettings s = session.getSettings();
            s.utpEnabled = pref.enableUtp();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enable_upnp))) {
            SessionSettings s = session.getSettings();
            s.upnpEnabled = pref.enableUpnp();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enable_natpmp))) {
            SessionSettings s = session.getSettings();
            s.natPmpEnabled = pref.enableNatPmp();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enc_in_connections_mode))) {
            SessionSettings s = session.getSettings();
            s.encryptModeIncoming = getEncryptInConnectionsMode();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enc_out_connections_mode))) {
            SessionSettings s = session.getSettings();
            s.encryptModeOutcoming = getEncryptOutConnectionsMode();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_use_random_port))) {
            setRandomPortRange(pref.useRandomPort());

        } else if (key.equals(appContext.getString(R.string.pref_key_port_range_first)) ||
                key.equals(appContext.getString(R.string.pref_key_port_range_second))) {
            int portFirst = pref.portRangeFirst();
            int portSecond = pref.portRangeSecond();
            setPortRange(portFirst, portSecond);

        } else if (key.equals(appContext.getString(R.string.pref_key_enable_ip_filtering))) {
            if (pref.enableIpFiltering()) {
                String path = pref.ipFilteringFile();
                if (path != null)
                    session.enableIpFilter(Uri.parse(path));
            } else {
                session.disableIpFilter();
            }

        } else if (key.equals(appContext.getString(R.string.pref_key_ip_filtering_file))) {
            String path = pref.ipFilteringFile();
            if (path != null)
                session.enableIpFilter(Uri.parse(path));

        } else if (key.equals(appContext.getString(R.string.pref_key_apply_proxy))) {
            if (pref.applyProxy()) {
                pref.applyProxy(false);
                setProxy();
                Toast.makeText(appContext,
                                R.string.proxy_settings_applied,
                                Toast.LENGTH_SHORT)
                        .show();
            }

        } else if (key.equals(appContext.getString(R.string.pref_key_auto_manage))) {
            session.setAutoManaged(pref.autoManage());

        } else if (key.equals(appContext.getString(R.string.pref_key_watch_dir))) {
            if (pref.watchDir())
                startWatchDir();
            else
                stopWatchDir();

        } else if (key.equals(appContext.getString(R.string.pref_key_dir_to_watch))) {
            if (pref.watchDir()) {
                stopWatchDir();
                startWatchDir();
            }
        } else if (key.equals(appContext.getString(R.string.pref_key_streaming_enable))) {
            if (pref.enableStreaming())
                startStreamingServer();
            else
                stopStreamingServer();

        } else if (key.equals(appContext.getString(R.string.pref_key_streaming_port)) ||
                key.equals(appContext.getString(R.string.pref_key_streaming_hostname))) {
            startStreamingServer();

        } else if (key.equals(appContext.getString(R.string.pref_key_anonymous_mode))) {
            SessionSettings s = session.getSettings();
            s.anonymousMode = pref.anonymousMode();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_seeding_outgoing_connections))) {
            SessionSettings s = session.getSettings();
            s.seedingOutgoingConnections = pref.seedingOutgoingConnections();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_enable_logging))) {
            SessionSettings s = session.getSettings();
            s.logging = pref.logging();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_log_session_filter))) {
            SessionSettings s = session.getSettings();
            s.logSessionFilter = pref.logSessionFilter();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_log_pbh_filter))) {
            SessionSettings s = session.getSettings();
            s.logPbhFilter = pref.logPbhFilter();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_log_dht_filter))) {
            SessionSettings s = session.getSettings();
            s.logDhtFilter = pref.logDhtFilter();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_log_peer_filter))) {
            SessionSettings s = session.getSettings();
            s.logPeerFilter = pref.logPeerFilter();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_log_portmap_filter))) {
            SessionSettings s = session.getSettings();
            s.logPortmapFilter = pref.logPortmapFilter();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_log_torrent_filter))) {
            SessionSettings s = session.getSettings();
            s.logTorrentFilter = pref.logTorrentFilter();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_max_log_size))) {
            SessionSettings s = session.getSettings();
            s.maxLogSize = pref.maxLogSize();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_default_trackers_list))) {
            session.setDefaultTrackersList(pref.defaultTrackersList().split("\n"));

        } else if (key.equals(appContext.getString(R.string.pref_key_validate_https_trackers))) {
            SessionSettings s = session.getSettings();
            s.validateHttpsTrackers = pref.validateHttpsTrackers();
            session.setSettings(s);

        } else if (key.equals(appContext.getString(R.string.pref_key_pbh_check_interval))) {
            // Restart the periodic scan with the new interval.
            startPbhScan();

        } else if (key.equals(appContext.getString(R.string.pref_key_pbh_ban_duration))) {
            /*
             * When the ban duration is changed to 0 (permanent), existing
             * auto-ban records keep their original expiry; new bans are
             * recorded as permanent. Nothing to clean up anymore - expiry is
             * stored per record in the BanRecordStore.
             */
        }

        if (reschedule)
            rescheduleTorrents();
    }
}
