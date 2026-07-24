#!/usr/bin/env bash

set -euo pipefail

benchmark_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "$benchmark_dir/.." && pwd)"
output_dir="${1:-/tmp/graalvm-native-benchmark}"

startup_rounds="${STARTUP_ROUNDS:-5}"
benchmark_trials="${BENCHMARK_TRIALS:-3}"
warmup_duration="${WARMUP_DURATION:-5s}"
measured_duration="${MEASURED_DURATION:-10s}"
threads="${THREADS:-4}"
concurrency="${CONCURRENCY:-32}"

jvm_port=18080
native_port=18081
jvm_jar="$project_dir/build/libs/native-demo-0.0.1-SNAPSHOT.jar"
native_binary="$project_dir/build/native/nativeCompile/native-demo"
wrk_report="$benchmark_dir/wrk-report.lua"
wrk_bin="${WRK_BIN:-$(command -v wrk || true)}"

mkdir -p "$output_dir"

if [[ -z "$wrk_bin" || ! -x "$wrk_bin" ]]; then
  echo "wrk is required. Install it or set WRK_BIN=/path/to/wrk." >&2
  exit 1
fi

for required_file in "$jvm_jar" "$native_binary" "$wrk_report"; do
  if [[ ! -f "$required_file" ]]; then
    echo "Missing required file: $required_file" >&2
    exit 1
  fi
done

active_pid=""
startup_ms_result=""

stop_server() {
  if [[ -n "$active_pid" ]] && kill -0 "$active_pid" 2>/dev/null; then
    kill -TERM "$active_pid"
    wait "$active_pid" 2>/dev/null || true
  fi
  active_pid=""
}

trap stop_server EXIT INT TERM

start_server() {
  local runtime="$1"
  local port="$2"
  local log_file="$3"

  local started_ns
  local ready_ns
  started_ns="$(date +%s%N)"

  if [[ "$runtime" == "jvm" ]]; then
    java -jar "$jvm_jar" \
      --server.port="$port" \
      --spring.main.banner-mode=off >"$log_file" 2>&1 &
  else
    "$native_binary" \
      --server.port="$port" \
      --spring.main.banner-mode=off >"$log_file" 2>&1 &
  fi
  active_pid=$!

  until curl --silent --fail --max-time 0.2 \
      "http://127.0.0.1:$port/hello" >/dev/null; do
    if ! kill -0 "$active_pid" 2>/dev/null; then
      echo "$runtime process exited before becoming ready" >&2
      sed -n '1,160p' "$log_file" >&2
      exit 1
    fi
    sleep 0.01
  done

  ready_ns="$(date +%s%N)"
  startup_ms_result="$(awk -v start="$started_ns" -v ready="$ready_ns" \
    'BEGIN { printf "%.3f", (ready - start) / 1000000 }')"
}

rss_kib() {
  ps -o rss= -p "$active_pid" | tr -d ' '
}

printf "runtime\tround\tstartup_to_http_ms\trss_at_ready_kib\n" \
  >"$output_dir/startup.tsv"

for ((round = 1; round <= startup_rounds; round++)); do
  for runtime in jvm native; do
    if [[ "$runtime" == "jvm" ]]; then
      port="$jvm_port"
    else
      port="$native_port"
    fi

    start_server "$runtime" "$port" \
      "$output_dir/${runtime}-startup-${round}.log"
    ready_rss="$(rss_kib)"
    printf "%s\t%s\t%s\t%s\n" \
      "$runtime" "$round" "$startup_ms_result" "$ready_rss" \
      >>"$output_dir/startup.tsv"
    stop_server
  done
done

printf "runtime\ttrial\trequests_per_second\tmean_ms\tp50_ms\tp95_ms\tp99_ms\trss_kib\n" \
  >"$output_dir/throughput.tsv"

for runtime in jvm native; do
  if [[ "$runtime" == "jvm" ]]; then
    port="$jvm_port"
  else
    port="$native_port"
  fi

  start_server "$runtime" "$port" \
    "$output_dir/${runtime}-throughput.log" >/dev/null

  "$wrk_bin" -t"$threads" -c"$concurrency" -d"$warmup_duration" \
    "http://127.0.0.1:$port/hello" >/dev/null

  for ((trial = 1; trial <= benchmark_trials; trial++)); do
    wrk_output="$("$wrk_bin" -t"$threads" -c"$concurrency" \
      -d"$measured_duration" --latency -s "$wrk_report" \
      "http://127.0.0.1:$port/hello")"
    printf "%s\n" "$wrk_output" \
      >"$output_dir/${runtime}-throughput-${trial}.txt"
    result="$(printf "%s\n" "$wrk_output" |
      awk -F '\t' '$1 == "WRK_RESULT" {
        print $2 "\t" $3 "\t" $4 "\t" $5 "\t" $6
      }')"
    if [[ -z "$result" ]]; then
      echo "Unable to parse wrk output for $runtime trial $trial" >&2
      exit 1
    fi
    current_rss="$(rss_kib)"
    printf "%s\t%s\t%s\t%s\n" \
      "$runtime" "$trial" "$result" "$current_rss" \
      >>"$output_dir/throughput.tsv"
  done

  stop_server
done

{
  printf "artifact\tbytes\n"
  printf "boot_jar\t%s\n" "$(stat -c %s "$jvm_jar")"
  printf "native_executable\t%s\n" "$(stat -c %s "$native_binary")"
} >"$output_dir/artifacts.tsv"

echo "Benchmark results written to $output_dir"
