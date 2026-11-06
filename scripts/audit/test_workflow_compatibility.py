#!/usr/bin/env python3
"""
Workflow Compatibility and Output Envelope Regression Suite.
Proves end-to-end compatibility of CLI output contracts with external automation,
CI pipelines, and AI agent integration tools.
"""

from __future__ import annotations

import json
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
CLI_BAT = REPO_ROOT / "build" / "install" / "vericore" / "bin" / "vericore.bat"
CLI_SH = REPO_ROOT / "build" / "install" / "vericore" / "bin" / "vericore"
CLI_BIN = CLI_BAT if os.name == "nt" else CLI_SH

def run_cli(args: list[str], cwd: str | None = None, input_text: str | None = None) -> subprocess.CompletedProcess:
    if os.name == "nt":
        cmd = ["cmd", "/c", str(CLI_BIN)] + args
    else:
        cmd = [str(CLI_BIN)] + args
    return subprocess.run(
        cmd,
        cwd=cwd or str(REPO_ROOT),
        input=input_text,
        text=True,
        capture_output=True,
        encoding="utf-8",
        errors="replace"
    )

class WorkflowCompatibilityTests(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.mkdtemp(prefix="veri-compat-test-")
        # Initialize a basic git repo
        subprocess.run(["git", "init", "-q"], cwd=self.temp_dir, check=True)
        subprocess.run(["git", "config", "user.name", "Compatibility Tester"], cwd=self.temp_dir, check=True)
        subprocess.run(["git", "config", "user.email", "compat@vericore.io"], cwd=self.temp_dir, check=True)
        src = pathlib.Path(self.temp_dir) / "src" / "main" / "java" / "example"
        src.mkdir(parents=True, exist_ok=True)
        (src / "App.java").write_text("package example; public class App {}", encoding="utf-8")
        subprocess.run(["git", "add", "."], cwd=self.temp_dir, check=True)
        subprocess.run(["git", "commit", "-m", "initial", "-q"], cwd=self.temp_dir, check=True)

    def tearDown(self):
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def test_universal_envelope_structure_on_stdout(self):
        """Proves CLI stdout in --json mode emits the standardized 1.0 envelope."""
        res = run_cli(["doctor", "--path", self.temp_dir, "--json"])
        self.assertEqual(res.returncode, 0, res.stderr)
        self.assertFalse(res.stdout.strip().startswith("=="), "Stdout must not contain ASCII banner in JSON mode")
        
        envelope = json.loads(res.stdout)
        self.assertEqual(envelope["schemaVersion"], "1.0")
        self.assertEqual(envelope["command"], "doctor")
        self.assertIn(envelope["status"], ["PASS", "WARNING", "FAIL"])
        self.assertIsInstance(envelope["summary"], str)
        self.assertIn("execution", envelope)
        self.assertEqual(envelope["execution"]["exitCode"], 0)
        self.assertIn("data", envelope)
        self.assertTrue(isinstance(envelope["data"], (dict, list)), "data must be a dict or list payload")

    def test_human_output_mode_remains_functional_without_json(self):
        """Proves default execution produces human-readable output, not JSON."""
        res = run_cli(["doctor", "--path", self.temp_dir])
        self.assertEqual(res.returncode, 0)
        self.assertFalse(res.stdout.strip().startswith("{"), "Human output must not start with JSON brace")
        self.assertIn("Vericore doctor", res.stdout)

    def test_format_validation_rejection(self):
        """Proves unsupported and conflicting format flags are rejected with exit code 2."""
        res_conflict = run_cli(["doctor", "--format", "human", "--json"])
        self.assertEqual(res_conflict.returncode, 2, "Conflicting format options must exit with code 2")
        
        res_invalid = run_cli(["doctor", "--format", "yaml"])
        self.assertEqual(res_invalid.returncode, 2, "Unsupported format option must exit with code 2")

    def test_mcp_config_emits_raw_client_json_not_envelope(self):
        """Proves mcp-config outputs editor-ready JSON snippet without CommandEnvelope wrapper."""
        res = run_cli(["mcp-config"])
        self.assertEqual(res.returncode, 0)
        config = json.loads(res.stdout)
        self.assertIn("mcpServers", config, "mcp-config must have mcpServers root key")
        self.assertNotIn("schemaVersion", config, "mcp-config must not be wrapped in CommandEnvelope")
        self.assertNotIn("command", config)

    def test_mcp_stdio_jsonrpc_protocol_not_wrapped_in_cli_envelope(self):
        """Proves mcp stdio protocol emits JSON-RPC 2.0 messages, distinct from CLI envelope."""
        init_req = json.dumps({
            "jsonrpc": "2.0",
            "id": 1,
            "method": "initialize",
            "params": {
                "protocolVersion": "2024-11-05",
                "capabilities": {},
                "clientInfo": {"name": "compat-test", "version": "1.0"}
            }
        }) + "\n"
        res = run_cli(["mcp"], input_text=init_req)
        # Find jsonrpc response
        found_rpc = False
        for line in res.stdout.splitlines():
            if not line.strip():
                continue
            try:
                msg = json.loads(line)
                if msg.get("jsonrpc") == "2.0" and msg.get("id") == 1:
                    found_rpc = True
                    self.assertIn("result", msg)
                    self.assertNotIn("schemaVersion", msg)
                    break
            except Exception:
                continue
        self.assertTrue(found_rpc, f"Did not find JSON-RPC 2.0 message in mcp stdout: {res.stdout}")

    def test_context_diff_unboxing_raw_and_wrapped_snapshots(self):
        """Proves context-diff unboxes both raw domain snapshots and envelope-wrapped snapshots."""
        raw_snap = pathlib.Path(self.temp_dir) / "raw.json"
        wrapped_snap = pathlib.Path(self.temp_dir) / "wrapped.json"
        
        raw_data = {
            "schemaVersion": "1.0",
            "repositoryCommit": "11223344",
            "files": [],
            "totalFiles": 0,
            "totalBytes": 0,
            "languages": [],
            "dirty": False,
            "changedPaths": [],
            "snapshotDigest": "digest_a"
        }
        raw_snap.write_text(json.dumps(raw_data), encoding="utf-8")
        
        wrapped_data = {
            "schemaVersion": "1.0",
            "command": "context-snapshot",
            "status": "PASS",
            "summary": "Snapshot",
            "data": raw_data,
            "execution": {"exitCode": 0}
        }
        wrapped_snap.write_text(json.dumps(wrapped_data), encoding="utf-8")
        
        res = run_cli(["context-diff", str(raw_snap), str(wrapped_snap), "--json"])
        self.assertEqual(res.returncode, 0, res.stderr)
        diff_envelope = json.loads(res.stdout)
        self.assertEqual(diff_envelope["command"], "context-diff")
        self.assertEqual(diff_envelope["status"], "PASS")

    def test_domain_artifact_written_to_disk_is_valid_domain_model(self):
        """Proves domain artifact files written to disk are valid domain JSON."""
        res = run_cli(["context-snapshot", self.temp_dir, "--json"])
        self.assertEqual(res.returncode, 0)
        
        artifact_path = pathlib.Path(self.temp_dir) / "output" / "engineering-context.json"
        self.assertTrue(artifact_path.is_file(), f"Expected artifact at {artifact_path}")
        
        domain_json = json.loads(artifact_path.read_text(encoding="utf-8"))
        self.assertEqual(domain_json["schemaVersion"], "1.0")
        self.assertIn("snapshotDigest", domain_json)
        self.assertIn("totalFiles", domain_json)

if __name__ == "__main__":
    unittest.main()

