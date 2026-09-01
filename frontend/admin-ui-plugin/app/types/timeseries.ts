export interface DashboardTimeseries {
  windowStart: string
  windowEnd: string
  bucketMinutes: number
  buckets: TimeseriesBucketRow[]
}

export interface TimeseriesBucketRow {
  bucketStart: string
  statusCounts: Record<string, number>
}
