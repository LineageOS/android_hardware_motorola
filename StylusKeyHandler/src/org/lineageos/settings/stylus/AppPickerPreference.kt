/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.stylus

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.DialogPreference
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.android.settingslib.widget.preference.app.R as settingslib_R
import org.lineageos.settings.resources.R as devicesettings_R

class AppPickerPreference(context: Context, attrs: AttributeSet) :
    DialogPreference(context, attrs) {

    private var appEntries: List<AppInfo> = emptyList()
    private var selectedPackageName: String? = null
    private var dialog: AlertDialog? = null

    init {
        setLayoutResource(settingslib_R.layout.preference_app)
        loadAppEntries()
    }

    data class AppInfo(
        val label: String,
        val packageName: String,
        val icon: Drawable?
    )

    private fun loadAppEntries() {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = pm.queryIntentActivities(intent, 0)
            .map {
                AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm))
            }
            .sortedBy { it.label }
            .toMutableList()

        apps.add(0, AppInfo(context.getString(devicesettings_R.string.none), "", null))
        appEntries = apps
    }

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager) {
        super.onAttachedToHierarchy(preferenceManager)
        selectedPackageName = getPersistedString(null)
        updateSummaryAndIcon()
    }

    private fun updateSummaryAndIcon() {
        val selectedApp = appEntries.find { it.packageName == selectedPackageName }
        summary = selectedApp?.label ?: context.getString(devicesettings_R.string.none)
        icon = selectedApp?.icon
    }

    override fun onClick() {
        val recyclerView = LayoutInflater.from(context)
            .inflate(R.layout.app_picker_dialog, null) as RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = AppListAdapter(appEntries) { appInfo ->
            persistString(appInfo.packageName)
            selectedPackageName = appInfo.packageName
            updateSummaryAndIcon()
            dialog?.dismiss()
        }

        dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(recyclerView)
            .create()
        dialog?.show()
    }

    private class AppListAdapter(
        private val apps: List<AppInfo>,
        private val onItemClick: (AppInfo) -> Unit
    ) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.app_list_item, parent, false)
            return AppViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            holder.bind(apps[position], onItemClick)
        }

        override fun getItemCount(): Int = apps.size

        class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
            private val appName: TextView = itemView.findViewById(R.id.app_name)

            fun bind(app: AppInfo, onItemClick: (AppInfo) -> Unit) {
                if (app.icon != null) {
                    appIcon.setImageDrawable(app.icon)
                    appIcon.visibility = View.VISIBLE
                } else {
                    appIcon.visibility = View.GONE
                }
                appName.text = app.label
                itemView.setOnClickListener { onItemClick(app) }
            }
        }
    }
}
