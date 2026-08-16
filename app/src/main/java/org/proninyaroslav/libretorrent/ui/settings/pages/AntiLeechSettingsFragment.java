/*
 * Copyright (C) 2026
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

package org.proninyaroslav.libretorrent.ui.settings.pages;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import org.libtorrent4j.swig.address;
import org.libtorrent4j.swig.error_code;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.btn.BtnIpQueryResult;
import org.proninyaroslav.libretorrent.core.model.TorrentEngine;
import org.proninyaroslav.libretorrent.core.pbh.BanRecord;
import org.proninyaroslav.libretorrent.core.pbh.IpUtils;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.FragmentAntiLeechBinding;
import org.proninyaroslav.libretorrent.ui.settings.AntiLeechAdapter;
import org.proninyaroslav.libretorrent.ui.settings.BanRecordAdapter;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/*
 * Settings screen for the anti-leech protections: the PBH/BTN engine tab,
 * the manual peer blacklists (banned IPs, peer ids and banned user agents)
 * and the automatic ban records issued by the engine (with reason/expiry
 * metadata and a BTN IP query).
 */

public class AntiLeechSettingsFragment extends Fragment
        implements AntiLeechAdapter.OnRemoveClickListener, BanRecordAdapter.Listener {
    private static final String KEY_CURRENT_TAB = "current_tab";
    private static final int TAB_PBH = 0;
    private static final int TAB_IPS = 1;
    private static final int TAB_PEER_IDS = 2;
    private static final int TAB_USER_AGENTS = 3;
    private static final int TAB_BAN_RECORDS = 4;

    private AppCompatActivity activity;
    private FragmentAntiLeechBinding binding;
    private SettingsRepository pref;
    private TorrentEngine engine;
    private AntiLeechAdapter adapter;
    private BanRecordAdapter banRecordAdapter;
    private int currentTab = TAB_PBH;
    private PbhSettingsFragment pbhFragment;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity a) {
            activity = a;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAntiLeechBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) requireActivity();
        }
        if (savedInstanceState != null) {
            currentTab = savedInstanceState.getInt(KEY_CURRENT_TAB, TAB_IPS);
        }

        pref = RepositoryHelper.getSettingsRepository(activity.getApplicationContext());
        engine = TorrentEngine.getInstance(activity.getApplicationContext());

        binding.appBar.setTitle(R.string.pref_header_anti_leech);
        binding.appBar.setNavigationOnClickListener((v) ->
                activity.getOnBackPressedDispatcher().onBackPressed());

        if (Utils.isTwoPane(activity) && getParentFragmentManager().getBackStackEntryCount() < 2) {
            binding.appBar.setNavigationIcon(null);
        }

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.anti_leech_pbh_tab));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.peer_blacklist_ip_tab));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.peer_blacklist_peer_id_tab));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.peer_blacklist_user_agent_tab));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.ban_records_tab));
        binding.tabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);
        var savedTab = binding.tabLayout.getTabAt(currentTab);
        if (savedTab != null) {
            binding.tabLayout.selectTab(savedTab);
        }
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                updateTabContent();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        adapter = new AntiLeechAdapter(this);
        banRecordAdapter = new BanRecordAdapter(this);
        binding.blacklistList.setLayoutManager(new LinearLayoutManager(activity));

        binding.fab.setOnClickListener((v) -> showAddDialog());

        updateTabContent();
    }

    private void updateTabContent() {
        if (currentTab == TAB_PBH) {
            binding.blacklistList.setVisibility(View.GONE);
            binding.emptyView.setVisibility(View.GONE);
            binding.fab.hide();
            if (pbhFragment == null) {
                pbhFragment = new PbhSettingsFragment();
                getChildFragmentManager()
                        .beginTransaction()
                        .replace(R.id.pbh_container, pbhFragment)
                        .commitNow();
            }
            binding.pbhContainer.setVisibility(View.VISIBLE);
        } else {
            binding.pbhContainer.setVisibility(View.GONE);
            if (pbhFragment != null) {
                getChildFragmentManager()
                        .beginTransaction()
                        .remove(pbhFragment)
                        .commitNow();
                pbhFragment = null;
            }
            binding.blacklistList.setVisibility(View.VISIBLE);
            binding.blacklistList.setAdapter(
                    currentTab == TAB_BAN_RECORDS ? banRecordAdapter : adapter);
            if (currentTab == TAB_BAN_RECORDS) {
                binding.fab.hide();
            } else {
                binding.fab.show();
            }
            reloadList();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(KEY_CURRENT_TAB, currentTab);

        super.onSaveInstanceState(outState);
    }

    private void reloadList() {
        if (currentTab == TAB_BAN_RECORDS) {
            List<BanRecord> records = new ArrayList<>(engine.getAutoBanRecords());
            Collections.reverse(records); // newest first
            banRecordAdapter.submitList(records);

            boolean empty = records.isEmpty();
            binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.emptyView.setText(R.string.empty_ban_records);
            return;
        }

        Set<String> data = stringTabData();
        List<String> entries = new ArrayList<>(data);
        Collections.sort(entries);
        adapter.submitList(entries);

        boolean empty = entries.isEmpty();
        binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.emptyView.setText(currentTab == TAB_IPS ?
                R.string.empty_blacklist_ip :
                currentTab == TAB_PEER_IDS ?
                        R.string.empty_blacklist_peer_id :
                        R.string.empty_blacklist_user_agent);
    }

    private Set<String> stringTabData() {
        return switch (currentTab) {
            case TAB_IPS -> pref.peerIpBlacklist();
            case TAB_PEER_IDS -> pref.peerIdBlacklist();
            default -> pref.peerUserAgentBlacklist();
        };
    }

    private void setStringTabData(Set<String> data) {
        switch (currentTab) {
            case TAB_IPS -> engine.setPeerIpBlacklist(data);
            case TAB_PEER_IDS -> pref.peerIdBlacklist(data); // applied on the next scan
            default -> engine.setPeerUserAgentBlacklist(data);
        }
    }

    private void showAddDialog() {
        var input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setSingleLine(true);
        int hintRes = currentTab == TAB_IPS ?
                R.string.blacklist_ip_hint :
                currentTab == TAB_PEER_IDS ?
                        R.string.blacklist_peer_id_hint :
                        R.string.blacklist_user_agent_hint;
        input.setHint(hintRes);

        int titleRes = currentTab == TAB_IPS ?
                R.string.add_blacklist_ip_title :
                currentTab == TAB_PEER_IDS ?
                        R.string.add_blacklist_peer_id_title :
                        R.string.add_blacklist_user_agent_title;
        var dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(titleRes)
                .setView(input)
                .setPositiveButton(R.string.add, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener((d) -> {
            var positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            positiveButton.setOnClickListener((v) -> {
                String value = input.getText().toString().trim();
                if (addEntry(value)) {
                    dialog.dismiss();
                }
            });
        });
        dialog.show();
    }

    private boolean addEntry(String value) {
        if (value.isEmpty()) {
            return false;
        }

        if (currentTab == TAB_IPS && !isValidIp(value)) {
            Toast.makeText(activity, R.string.invalid_ip, Toast.LENGTH_SHORT).show();
            return false;
        }

        Set<String> data = new HashSet<>(stringTabData());
        if (data.add(value)) {
            setStringTabData(data);
        }

        reloadList();

        return true;
    }

    @Override
    public void onRemoveClick(@NonNull String entry) {
        Set<String> data = new HashSet<>(stringTabData());
        if (data.remove(entry)) {
            setStringTabData(data);
        }

        reloadList();
    }

    /*
     * Accepts both bare IPs and CIDR blocks ("1.2.3.0/24"): the engine and
     * the session IP filter support both forms.
     */
    private boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty())
            return false;
        if (ip.contains("/"))
            return IpUtils.matchesCidrSyntax(ip);
        error_code ec = new error_code();
        address.from_string(ip, ec);

        return ec.value() == 0;
    }

    /* ---- automatic ban records tab ---- */

    @Override
    public void onRemoveClick(@NonNull BanRecord record) {
        engine.unbanAutoRecord(record.ip);
        reloadList();
    }

    @Override
    public void onItemClick(@NonNull BanRecord record) {
        showBanRecordDialog(record);
    }

    private void showBanRecordDialog(@NonNull BanRecord record) {
        DateFormat format = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault());
        StringBuilder msg = new StringBuilder();
        msg.append(getString(R.string.ban_record_detail_module, record.module)).append('\n');
        if (!record.reason.isEmpty())
            msg.append(getString(R.string.ban_record_detail_reason, record.reason)).append('\n');
        if (!record.torrentName.isEmpty())
            msg.append(getString(R.string.ban_record_detail_torrent, record.torrentName)).append('\n');
        msg.append(getString(R.string.ban_record_detail_banned_at,
                format.format(new Date(record.bannedAtMs)))).append('\n');
        msg.append(record.expireAtMs > 0
                ? getString(R.string.ban_record_detail_expires_at,
                format.format(new Date(record.expireAtMs)))
                : getString(R.string.ban_record_permanent));

        TextView text = new TextView(activity);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding / 2, padding, 0);
        text.setTextIsSelectable(true);
        text.setText(msg);

        var dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.ban_record_detail_title)
                .setView(text)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.btn_ip_query, null)
                .create();
        dialog.setOnShowListener((d) -> {
            var queryButton = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            queryButton.setEnabled(pref.btnEnabled());
            queryButton.setOnClickListener((v) -> {
                queryButton.setEnabled(false);
                text.append("\n\n" + getString(R.string.btn_ip_query_running));
                engine.queryBtnIp(record.ip, (result, error) ->
                        activity.runOnUiThread(() -> {
                            if (!dialog.isShowing())
                                return;
                            text.append("\n" + formatIpQueryResult(result, error));
                        }));
            });
        });
        dialog.show();
    }

    private String formatIpQueryResult(@Nullable BtnIpQueryResult result,
                                       @Nullable Exception error) {
        if (error != null || result == null)
            return getString(R.string.btn_ip_query_failed);
        String labels = result.labels.isEmpty() ? "-" : String.join(", ", result.labels);
        return getString(R.string.btn_ip_query_result,
                result.color, labels,
                Math.max(result.totalBans, 0),
                Math.max(result.concurrentDownloads, 0),
                Math.max(result.concurrentSeeds, 0));
    }
}
