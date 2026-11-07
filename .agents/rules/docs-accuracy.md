---
trigger: model_decision
description: Apply for changes to README, docs, support matrix, API/CLI/MCP guides, examples, release claims, diagrams or roadmap descriptions.
---
# Documentation accuracy
- Verify release facts against actual tagged releases and exact commit; verify toolchain versions against build and wrapper files.
- Cross-check CLI syntax against registered commands and help; API routes and MCP tools against source; JSON examples against serializers and schemas.
- Distinguish CURRENT, HISTORICAL and PROPOSED text. Preserve correct historical details.
- Record source path/line and exact SHA for contested claims; mark unverified claims instead of guessing.
- Check links, markdown and example executability when practical. A passing command-name parity check is not a full docs audit.
