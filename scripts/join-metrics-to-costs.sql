-- This query generates a lookup table of Cloud Storage prices per GiB per month,
-- normalizing the region and storage class names to facilitate joining with the
-- Cloud Montoring metrics export data.
-- This takes the Cloud Pricing Export table and extracts the latest storage pricing and creates a
-- simple prices table with region and storage classs keys that match what we can get from Monitoring:
-- | join_region_key | join_class_key | usd_per_gibibyte_per_month |
-- Then joines against the bucket snapshots to get cost timeseries at a granularity of a day.
-- LIMITATIONS AND KNOWN ISSUES:
--  It does not match historical bucket sizes against historical costs, it joins historical observations against the latest costs.
--  Cloud Metrics reports Dual Region storage as Multi-Region so dual region storage is cost estmated as Multi-Region.
--  Cloud Metrics does not provide a way to identify the storage class (standard, coldline, etc) of Multi-Region storage
--  so this cost estimate uses the most expensive pricing of any storage class for that Multi-Region, to err on the high side.


WITH
    LatestExport AS (
        -- Find the latest 'export_time' for pricing data.
        SELECT
            MAX(export_time) AS max_export_time
        FROM
            `gcp_billing_export.cloud_pricing_export`
    ),
    FilteredSKUs AS (
        -- Select relevant monthly storage SKUs from the latest snapshot.
        SELECT
            pe.sku.description AS sku_description,
            pe.geo_taxonomy.regions AS regions,
            -- Extract price from the first entry in tiered_rates
            pe.list_price.tiered_rates[OFFSET(0)].usd_amount AS usd_unit_price
        FROM
            `gcp_billing_export.cloud_pricing_export` AS pe
                CROSS JOIN
            LatestExport AS le
        WHERE
            pe.export_time = le.max_export_time
          AND pe.service.description = 'Cloud Storage'
          -- Filter strictly for monthly GiB storage SKUs based on SKU description.
          AND pe.sku.description LIKE '%Storage%'
          AND pe.sku.description NOT LIKE '%Operations%'
          AND pe.sku.description NOT LIKE '%Network%'
          AND pe.sku.description NOT LIKE '%Egress%'
          AND pe.sku.description NOT LIKE '%Retrieval%'
          AND pe.sku.description NOT LIKE '%Replication%'
          AND pe.sku.description NOT LIKE '%Autoclass%'
          AND pe.pricing_unit = 'GIBIBYTE_MONTH'
    ),
    UnnestedSKUs AS (
        -- Unnest the regions array to process each location associated with an SKU.
        SELECT
            FilteredSKUs.sku_description,
            FilteredSKUs.usd_unit_price,
            region AS RawLocation
        FROM
            FilteredSKUs
                CROSS JOIN
            UNNEST(FilteredSKUs.regions) AS region
    ),
    NormalizedPricing AS (
        -- Create normalized region and storage class keys that match the region and storage class values from
        -- the CloudMonitoring metric storage.googleapis.com/storage/v2/total_bytes
        -- so we can join the two data sources
        SELECT
            UnnestedSKUs.usd_unit_price,
            UnnestedSKUs.RawLocation,
            UnnestedSKUs.sku_description,

            -- Create the region key (e.g., 'us-west1' or 'us' for multi-region)
            CASE
                -- For Multi-Regional SKUs, capture the region prefix (us, asia, europe) which precedes 'Multi-region'.
                WHEN UnnestedSKUs.sku_description LIKE '%Multi-region%'
                    THEN
                    LOWER(
                            REGEXP_EXTRACT(
                                    UnnestedSKUs.sku_description, r'(\w+)\s+Multi-region'))

                -- Dual-Region SKUs: The pricing data has pricing for Dual-Region storage, but the Cloud Metric data doesn't distinguish it
                WHEN UnnestedSKUs.sku_description LIKE '%Dual-region%'
                    THEN CONCAT('Dual Region Not used - ', UnnestedSKUs.sku_description)
                -- For Regional SKUs, use the specific raw region name.
                ELSE UnnestedSKUs.RawLocation
                END AS join_region_key,

            -- Create the storage class key (e.g., 'REGIONAL', 'MULTI_REGIONAL', etc.)
            -- Cloud Monitoring seems to define the following preference order:   first MULTI-REGION, then ARCHIVE or COLDLINE or NEARLINE,  then REGIONAL
            -- Cloud Monitoring does not provide any way to identify Dual-Region storage.
            -- Cloud Monitoring does not provide any way to identify the storage class (Archive, Coldine, etc) of Multi-Region storage.
            CASE
                -- Cloud Monitoring metric storage class for Multi-Region storage is 'MULTI_REGIONAL'
                WHEN UnnestedSKUs.sku_description LIKE '%Multi-region%'
                    THEN 'MULTI_REGIONAL'
                -- -- Cloud Monitoring does not indicate Dual-Region storage as a region or storage class
                WHEN UnnestedSKUs.sku_description LIKE '%Dual-region%'
                    THEN 'DUAL_REGION_UNUSED'
                WHEN UnnestedSKUs.sku_description LIKE '%Nearline%' THEN 'NEARLINE'
                WHEN UnnestedSKUs.sku_description LIKE '%Coldline%' THEN 'COLDLINE'
                WHEN UnnestedSKUs.sku_description LIKE '%Archive%' THEN 'ARCHIVE'
                -- Cloud Metrics appears to mark single-region standard storage as storage class REGIONAL
                WHEN UnnestedSKUs.sku_description LIKE '%Standard%' THEN 'REGIONAL'
                -- Otherwise, not useful for this exercise
                ELSE 'UNKNOWN'
                END AS join_class_key
        FROM
            UnnestedSKUs
        -- Exclude 'global' as the region key, as it's generally covered by the specific Multi-Regional keys (e.g., 'US', 'EUROPE').
        WHERE
            UnnestedSKUs.RawLocation != 'global'
    ),

    -- 5. Deduplicate the pricing lookup table.
    DedupedPrices AS (
SELECT
    join_region_key AS region,
    join_class_key AS storage_class,

    -- Use MAX as the least-bad way to deal with the mismatch that Cloud Metrics uses MULTI-REGIONAL as the storage class
    -- The UnnestedSKUs have nearline multi-region, archive multi-region but the Cloud Metrics storage class does not.
    -- So err on the side of overestimating cost and pick the most expensive multi-region storage class for the global region from the pricing data.
    MAX(usd_unit_price) AS usd_per_gibibyte_per_month
FROM
    NormalizedPricing
WHERE join_class_key != "DUAL_REGION_UNUSED"
-- Group by the final join keys to ensure a 1:1 match for each price.
GROUP BY join_region_key, join_class_key
    )


SELECT
    DATE(bucket_snapshots.observed_at) AS observation_date,
    bucket_snapshots.gcp_project,
    bucket_snapshots.gcp_project,
    MAX(bucket_snapshots.total_bytes / POW(1024, 3)) AS gibibytes,
    MAX(
    bucket_snapshots.total_bytes
    / POW(1024, 3)
    * prices.usd_per_gibibyte_per_month) AS monthly_cost_usd,
    bucket_snapshots.region,
    bucket_snapshots.storage_class
FROM `gcs_storage_costs.bucket_snapshots` AS bucket_snapshots
    LEFT JOIN DedupedPrices AS prices
ON (
    (prices.region = bucket_snapshots.region)
    AND (prices.storage_class = bucket_snapshots.storage_class))
GROUP BY
    observation_date, bucket_snapshots.gcp_project, bucket_snapshots.gcp_project,
    bucket_snapshots.region, bucket_snapshots.storage_class
ORDER BY observation_date ASC, monthly_cost_usd DESC
