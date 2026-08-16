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
import androidx.recyclerview.widget.RecyclerView;

import org.proninyaroslav.libretorrent.databinding.ItemBlacklistEntryBinding;

import java.util.ArrayList;
import java.util.List;

public class AntiLeechAdapter extends RecyclerView.Adapter<AntiLeechAdapter.ViewHolder> {
    private final List<String> entries = new ArrayList<>();
    private final OnRemoveClickListener listener;

    public interface OnRemoveClickListener {
        void onRemoveClick(@NonNull String entry);
    }

    public AntiLeechAdapter(OnRemoveClickListener listener) {
        this.listener = listener;
    }

    public void submitList(@NonNull List<String> newEntries) {
        entries.clear();
        entries.addAll(newEntries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        var binding = ItemBlacklistEntryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);

        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(entries.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemBlacklistEntryBinding binding;

        ViewHolder(ItemBlacklistEntryBinding binding) {
            super(binding.getRoot());

            this.binding = binding;
        }

        void bind(String entry, OnRemoveClickListener listener) {
            binding.entryText.setText(entry);
            binding.removeButton.setOnClickListener((v) -> {
                if (listener != null) {
                    listener.onRemoveClick(entry);
                }
            });
        }
    }
}
