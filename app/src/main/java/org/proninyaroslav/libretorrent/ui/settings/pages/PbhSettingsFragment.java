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

import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.InputFilterRange;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.ui.settings.CustomPreferenceFragment;
import org.proninyaroslav.libretorrent.ui.settings.customprefs.SwitchBarPreference;

import java.util.Locale;

/*
 * PeerBanHelper-compatible anti-leech options. Hosted as a child fragment
 * inside the Peer blacklist settings page.
 */
public class PbhSettingsFragment extends CustomPreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private SettingsRepository pref;

    private final InputFilter[] unsignedIntFilter = new InputFilter[]{InputFilterRange.UNSIGNED_INT};
    /*
     * Allows a non-negative decimal number, e.g. "0.5", "1.2". Used for the
     * PCB double-typed settings (thresholds, differences, percentages).
     * ExpressionFilters reject whole-String pastes correctly: any pasted text
     * that isn't a valid non-negative decimal is discarded.
     */
    private static final InputFilter[] decimalFilter = new InputFilter[]{
            (source, start, end, dest, dstart, dend) -> {
                StringBuilder sb = new StringBuilder(dest);
                sb.replace(dstart, dend, source.subSequence(start, end).toString());
                String s = sb.toString().trim();
                if (s.isEmpty())
                    return source;
                try {
                    double v = Double.parseDouble(s);
                    if (v >= 0)
                        return source;
                } catch (NumberFormatException e) {
                    /* fall through */
                }
                return "";
            }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pref = RepositoryHelper.getSettingsRepository(requireContext().getApplicationContext());

        bindSwitch(R.string.pref_key_pbh_enabled, pref.pbhEnabled());
        bindSwitch(R.string.pref_key_pbh_anti_vampire_enabled, pref.pbhAntiVampireEnabled());
        bindSwitch(R.string.pref_key_pbh_pcb_enabled, pref.pbhPcbEnabled());
        bindSwitch(R.string.pref_key_pbh_pcb_block_excessive_clients, pref.pbhPcbBlockExcessiveClients());

        bindLong(R.string.pref_key_pbh_ban_duration, pref.pbhBanDuration());
        bindLong(R.string.pref_key_pbh_anti_vampire_upload_threshold, pref.pbhAntiVampireUploadThreshold());
        bindLong(R.string.pref_key_pbh_anti_vampire_min_progress_ppm, pref.pbhAntiVampireMinProgressPpm());
        bindLong(R.string.pref_key_pbh_pcb_torrent_minimum_size, pref.pbhPcbTorrentMinimumSize());
        bindLong(R.string.pref_key_pbh_pcb_ban_delay_duration, pref.pbhPcbBanDelayDuration());
        bindLong(R.string.pref_key_pbh_pcb_fast_pcb_test_blocking_duration, pref.pbhPcbFastPcbTestBlockingDuration());

        bindInt(R.string.pref_key_pbh_check_interval, pref.pbhCheckInterval());
        bindInt(R.string.pref_key_pbh_pcb_ipv4_prefix_length, pref.pbhPcbIpv4PrefixLength());
        bindInt(R.string.pref_key_pbh_pcb_ipv6_prefix_length, pref.pbhPcbIpv6PrefixLength());

        bindDouble(R.string.pref_key_pbh_pcb_excessive_threshold, pref.pbhPcbExcessiveThreshold());
        bindDouble(R.string.pref_key_pbh_pcb_maximum_difference, pref.pbhPcbMaximumDifference());
        bindDouble(R.string.pref_key_pbh_pcb_rewind_maximum_difference, pref.pbhPcbRewindMaximumDifference());
        bindDouble(R.string.pref_key_pbh_pcb_fast_pcb_test_percentage, pref.pbhPcbFastPcbTestPercentage());

        // BTN settings
        bindSwitch(R.string.pref_key_btn_enabled, pref.btnEnabled());
        bindSwitch(R.string.pref_key_btn_submit_bans, pref.btnSubmitBansEnabled());
        bindSwitch(R.string.pref_key_btn_submit_swarm, pref.btnSubmitSwarmEnabled());
        bindText(R.string.pref_key_btn_config_url, pref.btnConfigUrl());
        bindText(R.string.pref_key_btn_app_id, pref.btnAppId());
        bindText(R.string.pref_key_btn_app_secret, pref.btnAppSecret());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Hosted inside the Peer blacklist page tab: hide the nested app bar
        // so we don't get two stacked toolbars.
        binding.appBarLayout.setVisibility(View.GONE);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_pbh_settings, rootKey);
    }

    private void bindSwitch(int keyRes, boolean value) {
        SwitchBarPreference p = findPreference(getString(keyRes));
        if (p != null) {
            p.setChecked(value);
            p.setOnPreferenceChangeListener(this);
        }
    }

    private void bindInt(int keyRes, int value) {
        EditTextPreference p = findPreference(getString(keyRes));
        if (p != null) {
            p.setOnBindEditTextListener((editText) -> editText.setFilters(unsignedIntFilter));
            p.setText(Integer.toString(value));
            p.setSummary(Integer.toString(value));
            p.setOnPreferenceChangeListener(this);
        }
    }

    private void bindLong(int keyRes, long value) {
        EditTextPreference p = findPreference(getString(keyRes));
        if (p != null) {
            p.setOnBindEditTextListener((editText) -> editText.setFilters(unsignedIntFilter));
            p.setText(Long.toString(value));
            p.setSummary(Long.toString(value));
            p.setOnPreferenceChangeListener(this);
        }
    }

    private void bindDouble(int keyRes, double value) {
        EditTextPreference p = findPreference(getString(keyRes));
        if (p != null) {
            p.setOnBindEditTextListener((editText) -> editText.setFilters(decimalFilter));
            p.setText(String.format(Locale.ROOT, "%.2f", value));
            p.setSummary(String.format(Locale.ROOT, "%.2f", value));
            p.setOnPreferenceChangeListener(this);
        }
    }

    private void bindText(int keyRes, String value) {
        EditTextPreference p = findPreference(getString(keyRes));
        if (p != null) {
            p.setText(value);
            p.setSummary(value == null || value.isEmpty() ? getString(R.string.not_set) : value);
            p.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();

        if (key.equals(getString(R.string.pref_key_pbh_enabled))) {
            pref.pbhEnabled((boolean) newValue);
        } else if (key.equals(getString(R.string.pref_key_pbh_anti_vampire_enabled))) {
            pref.pbhAntiVampireEnabled((boolean) newValue);
        } else if (key.equals(getString(R.string.pref_key_pbh_pcb_enabled))) {
            pref.pbhPcbEnabled((boolean) newValue);
        } else if (key.equals(getString(R.string.pref_key_pbh_pcb_block_excessive_clients))) {
            pref.pbhPcbBlockExcessiveClients((boolean) newValue);
        } else if (key.equals(getString(R.string.pref_key_pbh_check_interval))) {
            int v = parseInt(newValue, pref.pbhCheckInterval());
            pref.pbhCheckInterval(v);
            preference.setSummary(Integer.toString(v));
        } else if (key.equals(getString(R.string.pref_key_pbh_ban_duration))) {
            long v = parseLong(newValue, pref.pbhBanDuration());
            pref.pbhBanDuration(v);
            preference.setSummary(Long.toString(v));
        } else if (key.equals(getString(R.string.pref_key_pbh_anti_vampire_upload_threshold))) {
            long v = parseLong(newValue, pref.pbhAntiVampireUploadThreshold());
            pref.pbhAntiVampireUploadThreshold(v);
            preference.setSummary(Long.toString(v));
        } else if (key.equals(getString(R.string.pref_key_pbh_anti_vampire_min_progress_ppm))) {
            long v = parseLong(newValue, pref.pbhAntiVampireMinProgressPpm());
            pref.pbhAntiVampireMinProgressPpm(v);
            preference.setSummary(Long.toString(v));
        } else if (key.equals(getString(R.string.pref_key_pbh_pcb_torrent_minimum_size))) {
            long v = parseLong(newValue, pref.pbhPcbTorrentMinimumSize());
            pref.pbhPcbTorrentMinimumSize(v);
            preference.setSummary(Long.toString(v));
        } else if (key.equals(getString(R.string.pref_key_pbh_pcb_ban_delay_duration))) {
            long v = parseLong(newValue, pref.pbhPcbBanDelayDuration());
            pref.pbhPcbBanDelayDuration(v);
            preference.setSummary(Long.toString(v));
        } else if (key.equals(getString(R.string.pref_key_pbh_pcb_fast_pcb_test_blocking_duration))) {
            long v = parseLong(newValue, pref.pbhPcbFastPcbTestBlockingDuration());
            pref.pbhPcbFastPcbTestBlockingDuration(v);
            preference.setSummary(Long.toString(v));
        } else if (key.equals(getString(R.string.pref_key_pbh_pcb_ipv4_prefix_length))) {
            int v = parseInt(newValue, pref.pbhPcbIpv4PrefixLength());
            pref.pbhPcbIpv4PrefixLength(v);
            preference.setSummary(Integer.toString(v));
        } else if (key.equals(getString(R.string.pref_key_pbh_pcb_ipv6_prefix_length))) {
            int v = parseInt(newValue, pref.pbhPcbIpv6PrefixLength());
            pref.pbhPcbIpv6PrefixLength(v);
            preference.setSummary(Integer.toString(v));
        } else if (key.equals(getString(R.string.pref_key_pbh_pcb_excessive_threshold))) {
            double v = parseDouble(newValue, pref.pbhPcbExcessiveThreshold());
            pref.pbhPcbExcessiveThreshold(v);
            preference.setSummary(String.format(Locale.ROOT, "%.2f", v));
        } else if (key.equals(getString(R.string.pref_key_pbh_pcb_maximum_difference))) {
            double v = parseDouble(newValue, pref.pbhPcbMaximumDifference());
            pref.pbhPcbMaximumDifference(v);
            preference.setSummary(String.format(Locale.ROOT, "%.2f", v));
        } else if (key.equals(getString(R.string.pref_key_pbh_pcb_rewind_maximum_difference))) {
            double v = parseDouble(newValue, pref.pbhPcbRewindMaximumDifference());
            pref.pbhPcbRewindMaximumDifference(v);
            preference.setSummary(String.format(Locale.ROOT, "%.2f", v));
        } else if (key.equals(getString(R.string.pref_key_pbh_pcb_fast_pcb_test_percentage))) {
            double v = parseDouble(newValue, pref.pbhPcbFastPcbTestPercentage());
            pref.pbhPcbFastPcbTestPercentage(v);
            preference.setSummary(String.format(Locale.ROOT, "%.2f", v));
        } else if (key.equals(getString(R.string.pref_key_btn_enabled))) {
            pref.btnEnabled((boolean) newValue);
        } else if (key.equals(getString(R.string.pref_key_btn_submit_bans))) {
            pref.btnSubmitBansEnabled((boolean) newValue);
        } else if (key.equals(getString(R.string.pref_key_btn_submit_swarm))) {
            pref.btnSubmitSwarmEnabled((boolean) newValue);
        } else if (key.equals(getString(R.string.pref_key_btn_config_url))) {
            String v = String.valueOf(newValue);
            pref.btnConfigUrl(v);
            preference.setSummary(v.isEmpty() ? getString(R.string.not_set) : v);
        } else if (key.equals(getString(R.string.pref_key_btn_app_id))) {
            pref.btnAppId(String.valueOf(newValue));
        } else if (key.equals(getString(R.string.pref_key_btn_app_secret))) {
            pref.btnAppSecret(String.valueOf(newValue));
        }

        return true;
    }

    private static int parseInt(Object value, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(Object value, long fallback) {
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(Object value, double fallback) {
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}