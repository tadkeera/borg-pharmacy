// Supabase Edge Function: admin-create-user
// Creates an Auth user and user_profiles row inside the caller admin's tenant.
// Deploy with: supabase functions deploy admin-create-user
// Required secrets: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

type CreateUserPayload = {
  email: string;
  password: string;
  displayName: string;
  role: "ADMIN" | "PHARMACIST";
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const authHeader = req.headers.get("Authorization") ?? "";
    const jwt = authHeader.replace("Bearer ", "");

    if (!jwt) {
      return json({ error: "Missing bearer token" }, 401);
    }

    const admin = createClient(supabaseUrl, serviceRole, {
      auth: { autoRefreshToken: false, persistSession: false },
    });

    const { data: callerData, error: callerError } = await admin.auth.getUser(jwt);
    if (callerError || !callerData.user) {
      return json({ error: "Invalid caller token" }, 401);
    }

    const { data: callerProfile, error: profileError } = await admin
      .from("user_profiles")
      .select("tenant_id, role, active")
      .eq("user_id", callerData.user.id)
      .single();

    if (profileError || !callerProfile?.active || callerProfile.role !== "ADMIN") {
      return json({ error: "Only active ADMIN can create users" }, 403);
    }

    const payload = (await req.json()) as CreateUserPayload;
    const email = (payload.email || "").trim().toLowerCase();
    const password = payload.password || "";
    const displayName = (payload.displayName || email).trim();
    const role = payload.role === "ADMIN" ? "ADMIN" : "PHARMACIST";

    if (!email || password.length < 6) {
      return json({ error: "Email and password >= 6 chars are required" }, 400);
    }

    const { data: created, error: createError } = await admin.auth.admin.createUser({
      email,
      password,
      email_confirm: true,
      user_metadata: {
        tenant_id: callerProfile.tenant_id,
        display_name: displayName,
        role,
      },
    });

    if (createError || !created.user) {
      return json({ error: createError?.message ?? "Unable to create Auth user" }, 400);
    }

    const { error: upsertProfileError } = await admin.from("user_profiles").upsert({
      user_id: created.user.id,
      tenant_id: callerProfile.tenant_id,
      display_name: displayName,
      role,
      active: true,
      must_change_password: false,
      updated_at: new Date().toISOString(),
    });

    if (upsertProfileError) {
      return json({ error: upsertProfileError.message }, 500);
    }

    return json({
      id: created.user.id,
      email,
      displayName,
      role,
      tenantId: callerProfile.tenant_id,
    });
  } catch (error) {
    return json({ error: String(error?.message ?? error) }, 500);
  }
});

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}
