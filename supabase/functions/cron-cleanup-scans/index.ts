import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req) => {
  try {
    // 1. Initialize Supabase Admin Client using Service Role Key to bypass RLS
    const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? '';
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '';

    if (!supabaseUrl || !supabaseServiceKey) {
      throw new Error('Missing Supabase environment variables');
    }

    const supabase = createClient(supabaseUrl, supabaseServiceKey);

    console.log("Starting Scheduled Cleanup Job for Free Users...");

    // 2. Identify Free Users
    const { data: freeUsers, error: usersError } = await supabase
      .from('user_quotas')
      .select('user_id')
      .eq('is_premium', false);

    if (usersError) throw new Error(`Error fetching free users: ${usersError.message}`);

    if (!freeUsers || freeUsers.length === 0) {
      console.log("No free users found. Exiting gracefully.");
      return new Response(JSON.stringify({ message: "No free users found. Nothing to delete." }), { status: 200 });
    }

    // Extract raw array of user IDs
    const freeUserIds = freeUsers.map((u) => u.user_id);

    // 3. Find Old Scans (older than 3 days)
    const threeDaysAgo = new Date();
    threeDaysAgo.setDate(threeDaysAgo.getDate() - 3);

    const { data: oldScans, error: scansError } = await supabase
      .from('scan_history')
      .select('id, image_url')
      .in('user_id', freeUserIds)
      .lt('created_at', threeDaysAgo.toISOString());

    if (scansError) throw new Error(`Error fetching old scans: ${scansError.message}`);

    if (!oldScans || oldScans.length === 0) {
      console.log("No old scans (>3 days) found for free users. Exiting gracefully.");
      return new Response(JSON.stringify({ message: "No old scans found. Nothing to delete." }), { status: 200 });
    }

    console.log(`Found ${oldScans.length} old scan(s) to process.`);

    // 4. Delete Process (CRITICAL ORDER)
    let deletedCount = 0;
    let errorCount = 0;

    for (const scan of oldScans) {
      const scanId = scan.id;
      const imageUrl = scan.image_url;
      let storageSuccess = false;

      try {
        if (imageUrl) {
          // Extract the exact file path from the image URL saved in the database
          const bucketStr = '/plant_images/';
          let filePath = imageUrl;
          const bucketIndex = imageUrl.indexOf(bucketStr);

          // If it's a full public URL, we slice it to get only the sub-path
          if (bucketIndex !== -1) {
            // e.g. "https://xyz.supabase.co/storage/v1/object/public/plant_images/user_123/scan.jpg" 
            // -> "user_123/scan.jpg"
            filePath = decodeURIComponent(imageUrl.substring(bucketIndex + bucketStr.length));
          } else {
             // In case just the path is stored, assume we can decode it directly
             filePath = decodeURIComponent(filePath);
          }

          // FIRST: Remove the physical file from the Supabase Storage Bucket
          const { data: storageData, error: storageError } = await supabase
            .storage
            .from('plant_images')
            .remove([filePath]);

          // `remove` method returns error if something goes wrong, but an empty array on success if file doesn't exist
          if (storageError) {
            console.error(`Storage Error: Failed to delete file ${filePath} for scan ${scanId}:`, storageError);
            errorCount++;
          } else {
            // Storage deletion was successfully acknowledged by the API
            storageSuccess = true;
          }
        } else {
          // Edge case: if there's no url in db, proceed so the row can finally be purged
          storageSuccess = true;
        }

        // SECOND: If storage file is gone (or missing/not provided), proceed to delete DB record
        if (storageSuccess) {
          const { error: dbError } = await supabase
            .from('scan_history')
            .delete()
            .eq('id', scanId);

          if (dbError) {
            console.error(`DB Error: Failed to delete row for scan ${scanId}:`, dbError);
            errorCount++;
          } else {
            deletedCount++;
          }
        }
      } catch (err) {
        console.error(`Unexpected Exception processing scan ${scanId}:`, err);
        errorCount++;
      }
    }

    // 5. Execution Logging
    console.log(`Cleanup Job Finished. Deleted ${deletedCount} records. Errors encountered: ${errorCount}`);

    return new Response(JSON.stringify({ 
      success: true, 
      scansProcessed: oldScans.length,
      deletedCount: deletedCount,
      errorCount: errorCount
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });

  } catch (error) {
    console.error('Fatal Edge Function Error:', error);
    // Return 500 so pg_cron/health-checks know it failed completely
    return new Response(JSON.stringify({ error: error instanceof Error ? error.message : 'Unknown error' }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' }
    });
  }
});
