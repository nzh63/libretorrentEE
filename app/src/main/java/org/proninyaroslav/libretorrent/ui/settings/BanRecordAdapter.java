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

package org.proninyaroslav.libretorrent.ui.settings;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.proninyaroslav.libretorrent.core.pbh.BanRecord;
import org.proninyaroslav.libretorrent.databinding.ItemBanRecordBinding;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/*
 * List of automatic ban records with their metadata (module, reason, torrent,
 * ban/expiry time) for the anti-leech "ban records" tab.
 */
public class BanRecordAdapter extends RecyclerView.Adapter<BanRecordAdapter.ViewHolder> {
    private final List<BanRecord> records = new ArrayList<>();
    @Nullable
    private final Listener listener;

    public interface Listener {
        void onRemoveClick(@NonNull BanRecord record);

        void onItemClick(@NonNull BanRecord record);
    }

    public BanRecordAdapter(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void submitList(@NonNull List<BanRecord> newRecords) {
        records.clear();
        records.addAll(newRecords);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        var binding = ItemBanRecordBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(records.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemBanRecordBinding binding;

        ViewHolder(ItemBanRecordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(BanRecord record, Listener listener) {
            binding.banRecordIp.setText(record.ip);
            String reason = record.module;
            if (!record.reason.isEmpty())
                reason += " (" + record.reason + ")";
            binding.banRecordReason.setText(reason);

            DateFormat format = DateFormat.getDateTimeInstance(
                    DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault());
            String time = binding.getRoot().getContext().getString(
                    org.proninyaroslav.libretorrent.R.string.ban_record_time,
                    format.format(new Date(record.bannedAtMs)),
                    record.expireAtMs > 0
                            ? format.format(new Date(record.expireAtMs))
                            : binding.getRoot().getContext().getString(
                            org.proninyaroslav.libretorrent.R.string.ban_record_permanent));
            binding.banRecordTime.setText(time);

            binding.removeButton.setOnClickListener((v) -> {
                if (listener != null)
                    listener.onRemoveClick(record);
            });
            binding.getRoot().setOnClickListener((v) -> {
                if (listener != null)
                    listener.onItemClick(record);
            });
        }
    }
}
