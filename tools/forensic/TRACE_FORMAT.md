# Trace format

Human-readable events use wall-clock and monotonic timestamps plus PID/TID. JSONL contains the same event stream for machine processing.

Native events use these prefixes:

- `FT-PC`: executed instrumented control-flow location.
- `FT-CMP`: observed comparison operands.
- `FT-SWITCH`: switch value and available cases.
- `FT-DIV`: division input.
- `FT-GEP`: pointer/index calculation input.
- `FT-INDIRECT`: indirect-call target.
- `FT-FUNC+` / `FT-FUNC-`: function entry/exit.
- `FT-DECISION`: explicit application-side semantic decision.

A post-run analysis should correlate native PCs with the exact forensic ELF and then join those records with surrounding normal payload messages. This preserves the observed values and context needed to understand a failed run rather than reducing it to a final error string.
