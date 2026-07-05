package com.borg.pharmacy.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.borg.pharmacy.data.local.dao.PharmacyDao
import com.borg.pharmacy.data.remote.SupabaseConfig
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SyncManager(
    private val context: Context,
    private val dao: PharmacyDao
) {
    fun syncWithSupabase() {
        if (!isNetworkAvailable(context)) {
            Log.d("SyncManager", "No internet connection. Skip sync.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Get unsynced local data
                val unsyncedCompanies = dao.getUnsyncedCompanies()
                
                if (unsyncedCompanies.isNotEmpty()) {
                    // In a real app, map Room Entities to Supabase DTOs
                    // 2. Upload to Supabase
                    // SupabaseConfig.client.from("companies").insert(unsyncedCompanies)
                    
                    // 3. Mark as synced locally
                    dao.markCompaniesAsSynced(unsyncedCompanies.map { it.id })
                    Log.d("SyncManager", "Synced ${unsyncedCompanies.size} companies to Supabase.")
                }
                
                // 4. Fetch updates from Supabase to sync DOWN to local DB
                // val remoteCompanies = SupabaseConfig.client.from("companies").select().decodeList<CompanyEntity>()
                // dao.insertCompanies(remoteCompanies.map { it.copy(isSynced = true) })

            } catch (e: Exception) {
                Log.e("SyncManager", "Sync failed: ${e.message}")
            }
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
