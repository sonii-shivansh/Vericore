#!/usr/bin/env bash
set -euo pipefail
: "${VERICORE_COMMAND:=analyze}"
: "${VERICORE_PATH:=.}"
: "${VERICORE_ARGS:=}"
: "${VERICORE_CHANGE_SUMMARY:=}"
: "${VERICORE_AGENT_PROVIDER:=}"
: "${VERICORE_AGENT_ID:=}"
: "${VERICORE_AGENT_SESSION_ID:=}"
: "${VERICORE_AGENT_EVENT_ID:=}"
case "$VERICORE_COMMAND" in
  analyze|prepare|verify|pr-intelligence|architecture-contract) ;;
  *) echo "::error::Unsupported Vericore command '$VERICORE_COMMAND'."; exit 1 ;;
esac
read -r -a extra_args <<< "$VERICORE_ARGS"
case "$VERICORE_COMMAND" in
  prepare)
    if [[ -z "$VERICORE_CHANGE_SUMMARY" ]]; then
      echo "::error::The change-summary input is required when command is prepare."
      exit 2
    fi
    prepare_args=(prepare "$VERICORE_CHANGE_SUMMARY" --path "$VERICORE_PATH")
    if [[ -n "$VERICORE_AGENT_PROVIDER" ]]; then
      prepare_args+=(--agent-provider "$VERICORE_AGENT_PROVIDER")
      [[ -z "$VERICORE_AGENT_ID" ]] || prepare_args+=(--agent-id "$VERICORE_AGENT_ID")
      [[ -z "$VERICORE_AGENT_SESSION_ID" ]] || prepare_args+=(--agent-session-id "$VERICORE_AGENT_SESSION_ID")
      [[ -z "$VERICORE_AGENT_EVENT_ID" ]] || prepare_args+=(--agent-event-id "$VERICORE_AGENT_EVENT_ID")
    elif [[ -n "$VERICORE_AGENT_ID" || -n "$VERICORE_AGENT_SESSION_ID" || -n "$VERICORE_AGENT_EVENT_ID" ]]; then
      echo "::error::agent-provider is required when any agent ID input is supplied."
      exit 2
    fi
    vericore "${prepare_args[@]}" "${extra_args[@]}"
    ;;
  verify)
    report_path="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/vericore-verification-${GITHUB_RUN_ID:-local}.json"
    has_output=false
    expect_output_value=false
    for arg in "${extra_args[@]}"; do
      if [[ "$expect_output_value" == true ]]; then
        report_path="$arg"
        expect_output_value=false
        continue
      fi
      case "$arg" in
        --output) has_output=true; expect_output_value=true ;;
        --output=*) report_path="${arg#--output=}"; has_output=true ;;
      esac
    done
    if [[ "$has_output" != true ]]; then extra_args+=(--output "$report_path"); fi
    set +e
    vericore verify --path "$VERICORE_PATH" "${extra_args[@]}"
    verify_status=$?
    set -e
    if command -v python3 >/dev/null 2>&1; then
      python3 "$GITHUB_ACTION_PATH/scripts/action_verification_summary.py" "$report_path" "$VERICORE_PATH" ||
        echo "::warning::Could not write Vericore verification step summary."
    else
      echo "::warning::Python 3 is unavailable; Vericore verification ran without a step summary."
    fi
    exit "$verify_status"
    ;;
  analyze|pr-intelligence|architecture-contract)
    vericore "$VERICORE_COMMAND" "$VERICORE_PATH" "${extra_args[@]}"
    ;;
esac
