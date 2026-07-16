# Qodana

This pack adds:
- Qodana Gradle plugin to the build script
- `qodana.yml` profile configuration with IntelliJ plugin development inspections
- Qodana Code Inspection job in GitHub Actions build workflow (when the GitHub pack is also enabled)

Note that module.ksl.yaml is placed in the `main` directory.
Otherwise, the modules merging logic doesn't include the Qodana Gradle plugin in the modular build script.