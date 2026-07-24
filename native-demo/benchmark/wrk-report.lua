done = function(summary, latency, requests)
  local errors = summary.errors.connect
      + summary.errors.read
      + summary.errors.write
      + summary.errors.status
      + summary.errors.timeout

  if errors > 0 then
    io.stderr:write(string.format("wrk recorded %d errors\n", errors))
    os.exit(1)
  end

  io.write(string.format(
      "WRK_RESULT\t%.2f\t%.3f\t%.3f\t%.3f\t%.3f\n",
      summary.requests / (summary.duration / 1000000),
      latency.mean / 1000,
      latency:percentile(50) / 1000,
      latency:percentile(95) / 1000,
      latency:percentile(99) / 1000
  ))
end
