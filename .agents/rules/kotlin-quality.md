---
trigger: model_decision
description: Apply when planning or changing Kotlin/JVM sources, Gradle configuration, parsers, scanner or graph logic.
---
# Kotlin and JVM engineering
- Reuse established Kotlin packages, structured outputs, Clikt/Ktor contracts and existing deterministic core; do not rewrite the architecture for style.
- Inspect the checked-in `build.gradle.kts` and `gradle/wrapper/gradle-wrapper.properties` for actual versions. Do not assume docs are current.
- Keep deterministic ordering and stable identifiers where artifact compatibility depends on them.
- Use the repository's established tests and fixtures, and add focused regressions for any fixed behavior.
- Record benchmark/runtime changes rather than claiming performance improvements without measurements.
- Document precise parser limitations; regex-based Kotlin parsing is not a complete compiler AST.
