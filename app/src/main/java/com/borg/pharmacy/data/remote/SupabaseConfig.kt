package com.borg.pharmacy.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseConfig {
    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = "https://dtkldxmfkhipdgiltzjl.supabase.co",
        supabaseKey = "YOUR_SUPABASE_ANON_KEY" // Needs to be provided via env or secure storage
    ) {
        install(Postgrest)
        install(Realtime)
    }
}
