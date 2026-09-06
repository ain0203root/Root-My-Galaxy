# RMG forensic build

The forensic branch adds a diagnostic-only build path. The regular APK source and release behavior remain the reference implementation; the forensic workflow applies the instrumentation at build time.

The resulting trace has two layers:

1. Application layer: target selection, repository resolution, artifact metadata, command/transport metadata, raw output deltas, exit codes, timeouts, and observed decision lines.
2. Native layer: source line information plus compiler callbacks for control-flow edges, comparisons with operands, switch dispatch, division, pointer-index operations, indirect calls, and function entry/exit.

This is designed to answer, after a single run, where execution went, what values were compared, which candidates were observed, and where timeouts/rejections occurred. It intentionally does not claim to reconstruct every CPU instruction or hidden kernel state.
