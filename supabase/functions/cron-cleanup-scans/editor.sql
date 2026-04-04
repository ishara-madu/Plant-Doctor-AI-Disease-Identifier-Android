select
  cron.schedule(
    'invoke-scan-cleanup-job',
    '0 0 * * *',
    $$
    select
      net.http_post(
          url:='your_endpoint_URL',
          headers:='{"Content-Type": "application/json"}'::jsonb
      ) as request_id;
    $$
  );