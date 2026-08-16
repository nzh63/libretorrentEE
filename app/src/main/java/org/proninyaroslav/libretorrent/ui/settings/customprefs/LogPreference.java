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

package org.proninyaroslav.libretorrent.ui.settings.customprefs;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import org.proninyaroslav.libretorrent.R;

/*
 * A clickable Preference that renders its summary as a multi-line log area.
 * The default preference layout constrains the summary to a few lines; this
 * subclass removes that limit so the PBH activity log can show many entries.
 */
public class LogPreference extends Preference {

    public LogPreference(Context context) {
        this(context, null);
    }

    public LogPreference(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.preference.R.attr.preferenceStyle);
    }

    public LogPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, defStyleAttr);
    }

    public LogPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        setLayoutResource(R.layout.pref_widget_log);
        setIconSpaceReserved(false);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        TextView title = (TextView) holder.findViewById(android.R.id.title);
        TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        if (title != null) {
            title.setMaxLines(Integer.MAX_VALUE);
            title.setSingleLine(false);
        }
        if (summary != null) {
            summary.setMaxLines(Integer.MAX_VALUE);
            summary.setEllipsize(null);
            summary.setSingleLine(false);
        }
    }
}