# Host-only alternative-model probe

`compare-tinyclap.py` reproduces the fixed, exploratory zero-shot configuration
recorded in `docs/acceptance/data/tinyclap-zero-shot-2026-10-31.json`. It has no
Android/device, microphone, network or hearing-aid API. It writes scalar results
only. No model or audio from this probe is packaged with either app.

Use an isolated environment with the report's pinned versions and FFmpeg. Prepare
a model cache containing the files named in its `provenance` object, fetched from
their listed primary-source URLs. The tool verifies their SHA-256 values before
loading any network definition or checkpoint. Student loading uses
`torch.load(..., weights_only=True)`. Source definitions are selected from the
reviewed, pinned upstream files; training and framework download code is omitted.

The text cache holds only the 204 `caption_encoder.*` tensors from the official
2022 Microsoft CLAP checkpoint, plus `manifest.json`. On a host with sufficient
storage/RAM, prepare it using this code and the checkpoint URL in the report:

```python
state = torch.load(checkpoint, map_location="cpu", weights_only=True)["model"]
manifest = {}
for index, (key, tensor) in enumerate((k, v) for k, v in state.items()
                                    if k.startswith("caption_encoder.")):
    path = text_cache / f"{index:03}.npy"
    np.save(path, tensor.detach().cpu().numpy())
    manifest[key] = {"file": path.name,
                     "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                     "shape": list(tensor.shape)}
(text_cache / "manifest.json").write_text(json.dumps(manifest))
```

The aggregate tensor digest must match the recorded run. The original constrained
host fetched selected ZIP storage ranges instead of retaining the 2.3 GB teacher
checkpoint; only its text tensors were used. This does not establish an independent
authenticity audit of the upstream checkpoint.

```
python tools/environment/compare-tinyclap.py --model-cache /tmp/heysig-tinyclap --text-cache /tmp/heysig-clap-text --audio-cache /tmp/heysig-environment-corpus --output shared/build/environment-corpus/tinyclap.json
```

Primary sources: [tinyCLAP](https://github.com/fpaissan/tinyCLAP),
[student weights](https://huggingface.co/fpaissan/tinyCLAP),
[Microsoft CLAP](https://huggingface.co/microsoft/msclap).
tinyCLAP/MicroMind sources and student weights use Apache-2.0; upstream explicitly
identifies the Microsoft teacher/text weights as MS-PL. Keep their supplied
licenses with the developer cache. Do not assume all derived components are
Apache-2.0 or redistribute the teacher/student under a new blanket license.

This run uses one fixed set of six text descriptions, not prompt optimization,
training or calibrated probabilities. Cosine top-1 results force a choice and
cannot be compared directly with the app's abstaining decisions. Seven recordings
and correlated duration/gain variants cannot qualify six-class generalization.
The result is a rejected configuration, not a claim that every tinyCLAP setup or
other scene model must fail. Watch/runtime/battery acceptance was not performed.
