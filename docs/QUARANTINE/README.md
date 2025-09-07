Quarantine folder for potentially unused or legacy files

Purpose:
- Collect source files that appear to have no references/imports in the codebase so they are excluded from compilation and can be reviewed safely.

Current items:
- ConstantsObject.kt: Deprecated legacy constants holder replaced by top-level constants in Constants.kt. No code references were found; only documentation mentions it. Moved here on 2025-09-06 for inspection.

How to restore:
- If a quarantined file is determined to be needed, move it back to an appropriate source set (e.g., src/commonMain/kotlin/...) and adjust packages as necessary.

Policy:
- Prefer removing dead code entirely; quarantine is a temporary holding area during refactors.
