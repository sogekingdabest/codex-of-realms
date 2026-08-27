# Local model evaluation results

This directory receives machine-specific JSON and Markdown reports produced by
`scripts/evaluate-local-models.ps1`. Generated reports are intentionally ignored by Git: they can contain
model output, hardware details, timings, and results that are not reproducible without the same local setup.

To promote a result into project evidence, review it for sensitive data, copy the relevant aggregate table into
the model-evaluation documentation, and record the model tag, Ollama version, Git commit, hardware, and run count.
