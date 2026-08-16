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
import org.proninyaroslav.libretorrent.core.model.TorrentEngine;
import org.proninyaroslav.libretorrent.core.pbh.IpUtils;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.FragmentAntiLeechBinding;
import org.proninyaroslav.libretorrent.ui.settings.AntiLeechAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * Settings screen for the anti-leech protections: the PBH/BTN engine tab and
 * the peer blacklists (banned IPs and banned user agents).
 */

public class AntiLeechSettingsFragment extends Fragment
        implements AntiLeechAdapter.OnRemoveClickListener {
    private static final String KEY_CURRENT_TAB = "current_tab";
    private static final int TAB_PBH = 0;
    private static final int TAB_IPS = 1;
    private static final int TAB_USER_AGENTS = 2;

    private AppCompatActivity activity;
    private FragmentAntiLeechBinding binding;
    private SettingsRepository pref;
    private TorrentEngine engine;
    private AntiLeechAdapter adapter;
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
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.peer_blacklist_user_agent_tab));
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
        binding.blacklistList.setLayoutManager(new LinearLayoutManager(activity));
        binding.blacklistList.setAdapter(adapter);

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
            binding.fab.show();
            reloadList();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(KEY_CURRENT_TAB, currentTab);

        super.onSaveInstanceState(outState);
    }

    private void reloadList() {
        Set<String> data = (currentTab == TAB_IPS ?
                pref.peerIpBlacklist() :
                pref.peerUserAgentBlacklist());

        List<String> entries = new ArrayList<>(data);
        Collections.sort(entries);
        adapter.submitList(entries);

        boolean empty = entries.isEmpty();
        binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.emptyView.setText(currentTab == TAB_IPS ?
                R.string.empty_blacklist_ip :
                R.string.empty_blacklist_user_agent);
    }

    private void showAddDialog() {
        var input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setSingleLine(true);
        input.setHint(currentTab == TAB_IPS ?
                R.string.blacklist_ip_hint :
                R.string.blacklist_user_agent_hint);

        var dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(currentTab == TAB_IPS ?
                        R.string.add_blacklist_ip_title :
                        R.string.add_blacklist_user_agent_title)
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

        if (currentTab == TAB_IPS) {
            if (!isValidIp(value)) {
                Toast.makeText(activity, R.string.invalid_ip, Toast.LENGTH_SHORT).show();

                return false;
            }

            Set<String> ips = new HashSet<>(pref.peerIpBlacklist());
            if (ips.add(value)) {
                engine.setPeerIpBlacklist(ips);
            }

        } else {
            Set<String> userAgents = new HashSet<>(pref.peerUserAgentBlacklist());
            if (userAgents.add(value)) {
                engine.setPeerUserAgentBlacklist(userAgents);
            }
        }

        reloadList();

        return true;
    }

    @Override
    public void onRemoveClick(@NonNull String entry) {
        if (currentTab == TAB_IPS) {
            Set<String> ips = new HashSet<>(pref.peerIpBlacklist());
            if (ips.remove(entry)) {
                engine.setPeerIpBlacklist(ips);
            }

        } else {
            Set<String> userAgents = new HashSet<>(pref.peerUserAgentBlacklist());
            if (userAgents.remove(entry)) {
                engine.setPeerUserAgentBlacklist(userAgents);
            }
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
}
