#!/usr/bin/env python3
"""Generates complete command inventory for Vericore CLI."""

import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLI_BAT = ROOT / "build" / "install" / "vericore" / "bin" / "vericore.bat"
OUTPUT_DIR = ROOT / "output" / "cli-e2e-audit"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

COMMAND_METADATA = [
    {
        "name": "init",
        "source": "src/main/kotlin/com/vericore/cli/InitCommand.kt",
        "description": "Initialize Vericore for a repository, creating .vericore.json configuration",
        "requiredArgs": [],
        "optionalArgs": ["[PATH] repository path (default: current directory)"],
        "availableFlags": ["--format <human|json>", "--json", "-f, --force"],
        "defaultValues": {"path": ".", "format": "human", "force": False},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Creates .vericore.json with standard scanning and threshold defaults; emits envelope in JSON mode or table in human mode",
        "expectedFailure": "Exit code 1 if config exists without --force; exit code 2 on invalid flags",
        "prerequisites": "Writable directory",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "scan",
        "source": "src/main/kotlin/com/vericore/cli/ScanCommand.kt",
        "description": "Understand a repository and generate engineering intelligence",
        "requiredArgs": [],
        "optionalArgs": ["[PATH] repository path (default: .)"],
        "availableFlags": ["--format <human|json>", "--json", "--skip-cache", "--clear-cache"],
        "defaultValues": {"path": ".", "format": "human", "skipCache": False, "clearCache": False},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Discovers repository files, languages, and builds initial scan summary",
        "expectedFailure": "Exit code 3 if path is not a directory or invalid repository",
        "prerequisites": "Valid repository or codebase directory",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "analyze",
        "source": "src/main/kotlin/com/vericore/cli/ImprovedAnalyzeCommand.kt",
        "description": "Analyze a repository and generate engineering intelligence",
        "requiredArgs": [],
        "optionalArgs": ["[PATH] repository path (default: .)"],
        "availableFlags": ["--format <human|json>", "--json", "--output <FILE>", "--skip-cache", "--clear-cache", "--include-tests"],
        "defaultValues": {"path": ".", "format": "human", "output": "output/analysis-snapshot.json"},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Generates analysis-snapshot.json AST graph and engineering metrics",
        "expectedFailure": "Exit code 3 if repository path does not exist",
        "prerequisites": "Valid source code files (Java, Kotlin, etc.)",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "impact",
        "source": "src/main/kotlin/com/vericore/cli/ImpactCommand.kt",
        "description": "Analyze the dependency impact of changed files",
        "requiredArgs": ["<CHANGED_FILES...> paths of changed files"],
        "optionalArgs": [],
        "availableFlags": ["--format <human|json>", "--json", "--path <PATH>", "--output <FILE>", "--depth <INT>"],
        "defaultValues": {"path": ".", "format": "human", "depth": 3, "output": "output/change-impact.json"},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Computes blast radius, impacted nodes, and recommended test candidates",
        "expectedFailure": "Exit code 2 if no changed files provided; exit code 3 if repository missing",
        "prerequisites": "Existing repository and analysis data",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "inspect",
        "source": "src/main/kotlin/com/vericore/cli/InspectCommand.kt",
        "description": "Inspect repository architecture, risk, hotspots, and next actions",
        "requiredArgs": [],
        "optionalArgs": ["[PATH] repository path (default: .)"],
        "availableFlags": ["--format <human|json>", "--json", "--output <FILE>", "--clear-cache"],
        "defaultValues": {"path": ".", "format": "human", "output": "output/inspection.json"},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Evaluates architectural hotspots, risks, and next engineering actions",
        "expectedFailure": "Exit code 3 on missing directory",
        "prerequisites": "Repository source code",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "architecture",
        "source": "src/main/kotlin/com/vericore/cli/ArchitectureCommand.kt",
        "description": "Analyze repository architecture and detect structural violations",
        "requiredArgs": [],
        "optionalArgs": ["[PATH] repository path (default: .)"],
        "availableFlags": ["--format <human|json>", "--json", "--output <FILE>", "--clear-cache"],
        "defaultValues": {"path": ".", "format": "human", "output": "output/architecture.json"},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Emits architectural package dependencies, circular dependency checks, and layer analysis",
        "expectedFailure": "Exit code 3 on invalid repository",
        "prerequisites": "Repository source code",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "architecture-drift",
        "source": "src/main/kotlin/com/vericore/cli/ArchitectureDriftCommand.kt",
        "description": "Compare current architecture intelligence with a baseline snapshot",
        "requiredArgs": [],
        "optionalArgs": ["[PATH] repository path (default: .)"],
        "availableFlags": ["--format <human|json>", "--json", "--baseline <FILE>", "--output <FILE>"],
        "defaultValues": {"path": ".", "format": "human", "output": "output/architecture-drift.json"},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Detects newly introduced dependency edges, circularities, and package boundary violations",
        "expectedFailure": "Exit code 1 on architectural violation; exit code 3 if baseline unreadable",
        "prerequisites": "Baseline snapshot or previous analysis",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "architecture-contract",
        "source": "src/main/kotlin/com/vericore/cli/ArchitectureContractCommand.kt",
        "description": "Evaluate repository architecture against a deterministic contract",
        "requiredArgs": [],
        "optionalArgs": ["[PATH] repository path (default: .)"],
        "availableFlags": ["--format <human|json>", "--json", "--contract <FILE>", "--output <FILE>"],
        "defaultValues": {"path": ".", "format": "human", "output": "output/architecture-contract.json"},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Validates allowed/forbidden dependency boundaries against declared contract rules",
        "expectedFailure": "Exit code 1 if contract rules violated; exit code 2 if contract unparseable",
        "prerequisites": "Architecture contract definition file",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "context-snapshot",
        "source": "src/main/kotlin/com/vericore/cli/EngineeringContextCommand.kt",
        "description": "Create a deterministic repository engineering-context snapshot",
        "requiredArgs": [],
        "optionalArgs": ["[PATH] repository path (default: .)"],
        "availableFlags": ["--format <human|json>", "--json", "--output <FILE>", "--clear-cache"],
        "defaultValues": {"path": ".", "format": "human", "output": "output/engineering-context.json"},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Emits deterministic hash-pinned snapshot of files, symbols, Git HEAD, and candidate state",
        "expectedFailure": "Exit code 3 if repository path is invalid",
        "prerequisites": "Git repository",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "context-diff",
        "source": "src/main/kotlin/com/vericore/cli/EngineeringContextCommand.kt",
        "description": "Compare two engineering-context snapshots",
        "requiredArgs": ["<SNAPSHOT_A> first snapshot file", "<SNAPSHOT_B> second snapshot file"],
        "optionalArgs": [],
        "availableFlags": ["--format <human|json>", "--json", "--output <FILE>"],
        "defaultValues": {"format": "human", "output": "output/engineering-context-diff.json"},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Identifies added, modified, removed files, symbol deltas, and state divergence",
        "expectedFailure": "Exit code 2 on missing snapshot arguments; exit code 3 on unreadable files",
        "prerequisites": "Two context snapshot JSON files",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "evidence-graph",
        "source": "src/main/kotlin/com/vericore/cli/EvidenceGraphCommand.kt",
        "description": "Build a deterministic semantic evidence graph from the current analysis snapshot",
        "requiredArgs": [],
        "optionalArgs": ["[PATH] repository path (default: .)"],
        "availableFlags": ["--format <human|json>", "--json", "--output <FILE>"],
        "defaultValues": {"path": ".", "format": "human", "output": "output/semantic-evidence-graph.json"},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Constructs grounded semantic graph nodes linking files, classes, methods, and evidence",
        "expectedFailure": "Exit code 3 on missing repository",
        "prerequisites": "Analyzed repository",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "reality",
        "source": "src/main/kotlin/com/vericore/cli/RealityCommand.kt",
        "description": "Build a deterministic engineering-reality snapshot from the current repository state",
        "requiredArgs": [],
        "optionalArgs": ["[PATH] repository path (default: .)"],
        "availableFlags": ["--format <human|json>", "--json", "--output <FILE>"],
        "defaultValues": {"path": ".", "format": "human", "output": "output/engineering-reality.json"},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Combines AST analysis, Git status, and reality invariants into an authoritative snapshot",
        "expectedFailure": "Exit code 3 on invalid repository",
        "prerequisites": "Git repository",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "pr-intelligence",
        "source": "src/main/kotlin/com/vericore/cli/PRIntelligenceCommand.kt",
        "description": "Analyze a Git change set for impact, risk, architecture, and test signals",
        "requiredArgs": [],
        "optionalArgs": ["[PATH] repository path (default: .)"],
        "availableFlags": ["--format <human|json>", "--json", "--base <REF>", "--head <REF>", "--output <FILE>"],
        "defaultValues": {"path": ".", "format": "human", "base": "main", "head": "HEAD", "output": "output/pr-intelligence.json"},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Emits PR risk scoring, architectural diffs, and required verification commands",
        "expectedFailure": "Exit code 3 if base or head Git refs do not exist",
        "prerequisites": "Git repository with commit history",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "repo-qa",
        "source": "src/main/kotlin/com/vericore/cli/RepositoryQACommand.kt",
        "description": "Retrieve grounded repository evidence for a developer question",
        "requiredArgs": ["<QUESTION> developer query string"],
        "optionalArgs": ["[PATH] repository path (default: .)"],
        "availableFlags": ["--format <human|json>", "--json", "--limit <INT>", "--output <FILE>"],
        "defaultValues": {"path": ".", "format": "human", "limit": 5, "output": "output/grounded-evidence.json"},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Returns ranked, grounded code evidence citations without LLM hallucination",
        "expectedFailure": "Exit code 2 if question is blank; exit code 3 on invalid repository",
        "prerequisites": "Indexed repository or analyzed codebase",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "report",
        "source": "src/main/kotlin/com/vericore/cli/ReportCommand.kt",
        "description": "Generate a durable HTML engineering report from the latest Vericore session",
        "requiredArgs": [],
        "optionalArgs": ["[PATH] repository path (default: .)"],
        "availableFlags": ["--format <human|json>", "--json", "--output <FILE>"],
        "defaultValues": {"path": ".", "format": "human", "output": "output/engineering-report.html"},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Builds interactive HTML report with charts, risk tables, and evidence sections",
        "expectedFailure": "Exit code 3 if no prior analysis or session data exists",
        "prerequisites": "Completed session or analysis in .vericore/sessions/",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "review",
        "source": "src/main/kotlin/com/vericore/cli/ReviewCommand.kt",
        "description": "Review a Git change with deterministic impact, risk, architecture, and test signals",
        "requiredArgs": [],
        "optionalArgs": ["[PATH] repository path (default: .)"],
        "availableFlags": ["--format <human|json>", "--json", "--base <REF>", "--output <FILE>"],
        "defaultValues": {"path": ".", "format": "human", "base": "HEAD~1", "output": "output/review.json"},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Generates complete pre-merge code review signals and test candidates",
        "expectedFailure": "Exit code 3 on missing git history",
        "prerequisites": "Git repository",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "recommendations",
        "source": "src/main/kotlin/com/vericore/cli/EngineeringRecommendationsCommand.kt",
        "description": "Generate deterministic engineering recommendations from an analysis snapshot",
        "requiredArgs": [],
        "optionalArgs": ["[PATH] repository path (default: .)"],
        "availableFlags": ["--format <human|json>", "--json", "--output <FILE>"],
        "defaultValues": {"path": ".", "format": "human", "output": "output/recommendations.json"},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Produces prioritized engineering recommendations (refactor, test, cycle break)",
        "expectedFailure": "Exit code 3 on missing repository",
        "prerequisites": "Repository code",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "plan",
        "source": "src/main/kotlin/com/vericore/cli/EngineeringPlanCommand.kt",
        "description": "Generate an evidence-backed engineering plan for a proposed change",
        "requiredArgs": ["<INTENT> description of intended changes"],
        "optionalArgs": ["[PATH] repository path (default: .)"],
        "availableFlags": ["--format <human|json>", "--json", "--output <FILE>"],
        "defaultValues": {"path": ".", "format": "human", "output": "output/engineering-plan.json"},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Drafts step-by-step engineering plan with touched files and safety guidelines",
        "expectedFailure": "Exit code 2 if intent is empty",
        "prerequisites": "Repository code",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "prepare",
        "source": "src/main/kotlin/com/vericore/cli/PrepareCommand.kt",
        "description": "Prepare an evidence-backed change plan before coding",
        "requiredArgs": ["<TASK> task description"],
        "optionalArgs": [],
        "availableFlags": ["--format <human|json>", "--json", "--path <PATH>", "--output <FILE>"],
        "defaultValues": {"path": ".", "format": "human", "output": "output/engineering-preparation.json"},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Freezes candidate state, generates agent-change-contract.json and engineering-plan.json",
        "expectedFailure": "Exit code 2 if task is blank; exit code 3 if repo unreadable",
        "prerequisites": "Git repository",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "verify",
        "source": "src/main/kotlin/com/vericore/cli/VerifyCommand.kt",
        "description": "Verify the current change against an engineering plan and immutable change contract",
        "requiredArgs": [],
        "optionalArgs": [],
        "availableFlags": ["--format <human|json>", "--json", "--path <PATH>", "--contract <FILE>", "--output <FILE>"],
        "defaultValues": {"path": ".", "format": "human", "contract": "output/agent-change-contract.json", "output": "output/verification-result.json"},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Exits 0 if changes match planned scope and criteria; exits 1 if out-of-scope or violated",
        "expectedFailure": "Exit code 1 on contract violation; exit code 3 if contract file missing",
        "prerequisites": "Prepared agent-change-contract.json",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "ask",
        "source": "src/main/kotlin/com/vericore/cli/AIAssistantCommand.kt",
        "description": "Ask questions about your codebase using AI",
        "requiredArgs": ["<PROMPT> question or prompt"],
        "optionalArgs": ["[PATH] repository path (default: .)"],
        "availableFlags": ["--format <human|json>", "--json", "--model <NAME>", "--context-budget <INT>"],
        "defaultValues": {"path": ".", "format": "human", "contextBudget": 8000},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Retrieves codebase context and synthesizes AI answer (or fallback message if no API key)",
        "expectedFailure": "Exit code 2 on empty prompt; exit code 0/1 with actionable advisory if no API key",
        "prerequisites": "AI provider credentials configured in setup",
        "modifiesState": False,
        "requiresExternal": True
    },
    {
        "name": "evolution",
        "source": "src/main/kotlin/com/vericore/cli/EvolutionCommand.kt",
        "description": "Analyze codebase evolution over time",
        "requiredArgs": [],
        "optionalArgs": ["[PATH] repository path (default: .)"],
        "availableFlags": ["--format <human|json>", "--json", "--limit <INT>", "--output <FILE>"],
        "defaultValues": {"path": ".", "format": "human", "limit": 30, "output": "output/evolution.json"},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Analyzes Git commit trajectory, complexity growth, and hotspot velocity",
        "expectedFailure": "Exit code 3 if path is not a git repository",
        "prerequisites": "Git repository with commit history",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "server",
        "source": "src/main/kotlin/com/vericore/cli/ServerCommand.kt",
        "description": "Start the Vericore HTTP API (loopback by default)",
        "requiredArgs": [],
        "optionalArgs": [],
        "availableFlags": ["--port <INT>", "--host <HOST>", "--dev"],
        "defaultValues": {"port": 8080, "host": "127.0.0.1", "dev": False},
        "supportedFormats": ["REST JSON (HTTP)"],
        "expectedSuccess": "Launches Ktor embedded HTTP server on specified loopback interface and port",
        "expectedFailure": "Exit code 2 on invalid port; exit code 3 if port bound",
        "prerequisites": "Available local TCP port",
        "modifiesState": False,
        "requiresExternal": False
    },
    {
        "name": "mcp",
        "source": "src/main/kotlin/com/vericore/cli/McpServerCommand.kt",
        "description": "Start the Vericore MCP server over stdio",
        "requiredArgs": [],
        "optionalArgs": ["[PATH] repository path (default: .)"],
        "availableFlags": ["--debug", "--transport <stdio>"],
        "defaultValues": {"path": ".", "transport": "stdio", "debug": False},
        "supportedFormats": ["JSON-RPC 2.0 (stdio)"],
        "expectedSuccess": "Listens for JSON-RPC 2.0 requests over stdio; never emits CLI envelope on stdout",
        "expectedFailure": "Exit code 3 on fatal stdio I/O error",
        "prerequisites": "LSP/MCP client sending JSON-RPC 2.0 requests",
        "modifiesState": False,
        "requiresExternal": False
    },
    {
        "name": "mcp-config",
        "source": "src/main/kotlin/com/vericore/cli/McpConfigCommand.kt",
        "description": "Print or write a client-ready MCP stdio server configuration",
        "requiredArgs": [],
        "optionalArgs": [],
        "availableFlags": ["--ide <cursor|antigravity|claude|vscode>", "--write <FILE>"],
        "defaultValues": {"ide": "cursor"},
        "supportedFormats": ["Raw Editor JSON"],
        "expectedSuccess": "Emits direct editor configuration JSON object with mcpServers root key",
        "expectedFailure": "Exit code 2 on unsupported IDE name",
        "prerequisites": "None",
        "modifiesState": False,
        "requiresExternal": False
    },
    {
        "name": "setup",
        "source": "src/main/kotlin/com/vericore/cli/SetupCommand.kt",
        "description": "Set up Vericore AI credentials securely on this machine",
        "requiredArgs": [],
        "optionalArgs": [],
        "availableFlags": ["--provider <NAME>", "--key <KEY>", "--clear", "--status"],
        "defaultValues": {"provider": "gemini"},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Stores or clears credentials in secure user-level config with encrypted or masked representation",
        "expectedFailure": "Exit code 2 on invalid provider name",
        "prerequisites": "User home directory",
        "modifiesState": True,
        "requiresExternal": False
    },
    {
        "name": "doctor",
        "source": "src/main/kotlin/com/vericore/cli/DoctorCommand.kt",
        "description": "Check the local Vericore installation and configuration",
        "requiredArgs": [],
        "optionalArgs": [],
        "availableFlags": ["--format <human|json>", "--json", "--path <PATH>", "--verbose"],
        "defaultValues": {"path": ".", "format": "human", "verbose": False},
        "supportedFormats": ["human", "json"],
        "expectedSuccess": "Evaluates Java 25 runtime, Git binary, repo health, and AI credentials",
        "expectedFailure": "Exit code 1 if fatal prerequisites missing",
        "prerequisites": "Operating environment",
        "modifiesState": False,
        "requiresExternal": False
    }
]

def main():
    print(f"Discovered {len(COMMAND_METADATA)} commands.")
    inv_json = OUTPUT_DIR / "command-inventory.json"
    with open(inv_json, "w", encoding="utf-8") as f:
        json.dump({"totalCommands": len(COMMAND_METADATA), "commands": COMMAND_METADATA}, f, indent=2)
    print(f"Saved: {inv_json}")

    inv_md = OUTPUT_DIR / "command-inventory.md"
    with open(inv_md, "w", encoding="utf-8") as f:
        f.write("# Vericore CLI Command Inventory\n\n")
        f.write(f"Total user-facing commands discovered: **{len(COMMAND_METADATA)}**\n\n")
        f.write("| # | Command | Description | Supported Formats | State Modifying? | External Dep? |\n")
        f.write("|---|---------|-------------|-------------------|:----------------:|:-------------:|\n")
        for i, cmd in enumerate(COMMAND_METADATA, 1):
            fmts = ", ".join(f"`{fmt}`" for fmt in cmd["supportedFormats"])
            mod = "Yes" if cmd["modifiesState"] else "No"
            ext = "Yes" if cmd["requiresExternal"] else "No"
            f.write(f"| {i} | [`{cmd['name']}`](#{cmd['name']}) | {cmd['description']} | {fmts} | {mod} | {ext} |\n")
        
        f.write("\n---\n\n## Detailed Command Specifications\n\n")
        for cmd in COMMAND_METADATA:
            f.write(f"### `{cmd['name']}`\n\n")
            f.write(f"- **Implementation**: [`{cmd['source']}`]({cmd['source']})\n")
            f.write(f"- **Description**: {cmd['description']}\n")
            f.write(f"- **Required Arguments**: {', '.join(f'`{a}`' for a in cmd['requiredArgs']) if cmd['requiredArgs'] else 'None'}\n")
            f.write(f"- **Optional Arguments**: {', '.join(f'`{a}`' for a in cmd['optionalArgs']) if cmd['optionalArgs'] else 'None'}\n")
            f.write(f"- **Available Flags**: {', '.join(f'`{f}`' for f in cmd['availableFlags'])}\n")
            f.write(f"- **Default Values**: `{json.dumps(cmd['defaultValues'])}`\n")
            f.write(f"- **Supported Output Formats**: {', '.join(cmd['supportedFormats'])}\n")
            f.write(f"- **Expected Success**: {cmd['expectedSuccess']}\n")
            f.write(f"- **Expected Failure**: {cmd['expectedFailure']}\n")
            f.write(f"- **Prerequisites**: {cmd['prerequisites']}\n")
            f.write(f"- **Modifies State**: {'Yes' if cmd['modifiesState'] else 'No'}\n")
            f.write(f"- **External Dependency**: {'Yes' if cmd['requiresExternal'] else 'No'}\n\n")
    print(f"Saved: {inv_md}")

if __name__ == "__main__":
    main()

