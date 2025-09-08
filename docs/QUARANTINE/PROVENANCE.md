# Provenance (prov-*) tooling removed

The repository previously included a debug-only provenance feature and scripts (e.g., `prov_pigz.sh`) that invoked a `prov-zlib` CLI command. This feature has been removed.

- The CLI does not expose `prov-zlib`.
- `scripts/prov_pigz.sh` has been converted to a deprecation stub which exits non-zero and points to CLI logging controls.

## Logging via CLI
Use the CLI to control logging at runtime:

- Enable logging: `zlib-cli log-on`
- Disable logging: `zlib-cli log-off`

You can further configure verbosity via environment variables recognized by the logger:
- `ZLIB_LOG_ENABLE=1`
- `ZLIB_LOG_DEBUG=1`
- `ZLIB_LOG_BITWISE=1`
- `ZLIB_LOG_PATH=/path/to/zlib.log`

This keeps the production code simple and the diagnostics explicitly opt-in via CLI.
