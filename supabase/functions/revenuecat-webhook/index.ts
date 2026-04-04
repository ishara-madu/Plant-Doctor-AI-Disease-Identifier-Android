import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// CORS headers to accept cross-origin requests
const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

serve(async (req) => {
  // Handle CORS preflight requests
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    // Webhooks should be POST requests
    if (req.method !== 'POST') {
      return new Response(JSON.stringify({ error: 'Method Not Allowed' }), {
        status: 405,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const payload = await req.json();

    // The payload wrapper is typically { "event": { ... } } or just the structure containing event details depending on RevenueCat config
    // We safely parse it based on standard RevenueCat webhook format
    const event = payload?.event;

    if (!event || !event.type || !event.app_user_id) {
      return new Response(JSON.stringify({ error: 'Invalid or missing Event payload' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const { type, app_user_id } = event;

    // Initialize Supabase Client with service role key to bypass RLS
    const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? '';
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '';

    if (!supabaseUrl || !supabaseServiceKey) {
      throw new Error('Supabase configuration environment variables are missing');
    }

    const supabase = createClient(supabaseUrl, supabaseServiceKey);

    // Determine the is_premium status based on the event type
    let isPremium: boolean | null = null;

    if (['INITIAL_PURCHASE', 'RENEWAL', 'NON_RENEWING_PURCHASE'].includes(type)) {
      isPremium = true;
    } else if (['EXPIRATION', 'BILLING_ISSUE'].includes(type)) {
      isPremium = false;
    }

    // Attempt database update only if we have a defined status change
    if (isPremium !== null) {
      const { error } = await supabase
        .from('user_quotas')
        .update({ is_premium: isPremium })
        .eq('user_id', app_user_id);

      if (error) {
        console.error(`Supabase DB Update Error for user ${app_user_id}:`, error);
        // Throwing will trigger the outer 500 response below, so RevenueCat can retry the delivery
        throw new Error('Failed to update user quota');
      }

      console.log(`Successfully updated user_id ${app_user_id} to is_premium: ${isPremium}. TriggerEvent: ${type}`);
    } else {
      console.log(`Ignoring event type ${type} for user_id ${app_user_id}`);
    }

    // Return a 200 OK so RevenueCat records successful webhook delivery
    return new Response(JSON.stringify({ success: true, message: 'Processed successfully' }), {
      status: 200,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });

  } catch (error) {
    console.error('Webhook processing error:', error);
    // Return 500 on critical errors to allow RevenueCat to perform exponential backoff retries
    return new Response(JSON.stringify({ error: error instanceof Error ? error.message : 'Unknown error' }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});
