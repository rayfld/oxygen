# Live Voice Mode — Full-Duplex Simulation Plan

A complete plan for a hands-free, continuous voice conversation in Oxygen that
**feels** full-duplex: the microphone never closes, the user can interrupt
mid-word, the assistant reacts while being spoken over, and turn-taking feels
like a phone call rather than a walkie-talkie.

True full duplex (listening and speaking inside one audio model) exists only in
GPT-Live, served by OpenAI (`v1/live/sessions`, WebRTC/WebSocket, `gpt-live-1`).
OpenRouter has **no** realtime API and its audio endpoints are request/response:

| Stage | Endpoint | Streaming? | Latency profile |
| --- | --- | --- | --- |
| Speech → text | `POST /api/v1/audio/transcriptions` | no | 300–900 ms |
| Assistant | `POST /api/v1/chat/completions` | yes (SSE) | TTFT 300–1500 ms |
| Text → speech | `POST /api/v1/audio/speech` | no (raw PCM/MP3 bytes) | 300–800 ms first sentence |

Everything below exists to hide those latencies and overlaps so the seams
disappear. This document is the engineering spec: components, state machine,
algorithms, tunables, latency budget, failure ladder, telemetry, and milestones.

---

## 1. Experience specification

What "very very realistic" means, concretely and measurably:

| Behavior | Target |
| --- | --- |
| Mic is always hot, including while the assistant speaks | barge-in audible within **≤ 250 ms** of the user starting |
| Perceived turn-around (user stops → assistant audio starts) | **p50 ≤ 900 ms**, p95 ≤ 1.6 s |
| Assistant stops within ~1 clause of an interruption | stop command → silence in **≤ 80 ms** after confirmation |
| False interruptions (cough, door, own echo) | **< 5%** of turns |
| Assistant never talks over a user who is still thinking | "wait" state + semantic endpointing |
| Long silences mid-thought don't get cut off | patience extends on incomplete clauses |
| The model's context matches **what the user actually heard** | spoken-text truncation |
| It reacts while you're still talking | backchannels ("mhm", "yeah") on micro-pauses |
| It doesn't go silent while doing work | fillers while reasoning/tooling ("one sec, checking") |
| No dead air | first sound within filler budget, always |

Non-goals: not changing models on the fly, not translating (maybe later), not
video.

---

## 2. Architecture

```
┌─────────────────────────┐        ┌────────────────────────────────────────┐
│  Mic (AudioRecord 16k)  │        │            OpenRouterClient            │
│  + AEC/NS (platform)    │        │  transcribe()  POST /audio/transcriptions │
│  ring buffer, pre-roll  │        │  speech()      POST /audio/speech       │
└───────────┬─────────────┘        └───────────────┬────────────────────────┘
            │ 10 ms PCM frames                     │
            ▼                                      │
┌─────────────────────────┐                        │
│  VadDetector            │  speech/onset/offset   │
│  energy + noise floor   │──────────┐             │
│  + echo floor + semantic│          │             │
└─────────────────────────┘          ▼             │
                          ┌──────────────────────────┴─────────────┐
                          │        VoiceSessionController          │
                          │  state machine, speculation, barge-in  │
                          │  turn assembly, truncation, budgeting  │
                          └───┬───────────────┬───────────────┬────┘
                              │               │               │
                     STT client│        Chat (GenerationManager)│  TTS client
                              ▼               ▼               ▼
                     transcript store   Room (existing)   sentence queue
                                      │               │
                                      │               ▼
                                      │     ┌───────────────────┐
                                      │     │  PcmPlayer        │
                                      │     │  AudioTrack 24k   │
                                      │     │  head-position    │
                                      │     │  flush() <20 ms   │
                                      │     └───────────────────┘
                                      ▼
                            ┌───────────────────────┐
                            │ Backchannel/Filler    │  pre-synthesized,
                            │ library (disk cache)  │  zero-latency playback
                            └───────────────────────┘
```

New modules:

```
core/audio/AudioCapture.kt      AudioRecord + AEC + ring buffer + frame taps
core/audio/VadDetector.kt       adaptive energy VAD, echo floor, semantic hints
core/audio/PcmPlayer.kt         AudioTrack streaming, precise flushing, level
core/audio/WavWriter.kt         PCM16 → WAV for STT upload
core/voice/SentenceChunker.kt   streaming text → speakable sentences
core/voice/TranscriptStitcher.kt  longest-stable-prefix across speculative STTs
core/voice/VoicePhrases.kt      backchannel/filler library + synthesis cache
data/VoiceSessionController.kt  the state machine (all of the above)
features/voice/VoiceScreen.kt   overlay: orb, captions, controls
features/voice/VoiceViewModel.kt
data/VoiceSettings.kt           DataStore: models, voice, tunables
```

---

## 3. Components in detail

### 3.1 Audio capture (`AudioCapture`)

- `AudioRecord` with `MediaRecorder.AudioSource.VOICE_COMMUNICATION`
  (this source engages the platform echo canceller path), 16 kHz, mono, PCM16,
  min buffer × 2. Read on a dedicated thread into a lock-free ring buffer.
- Attach, when available (`isAvailable`):
  - `AcousticEchoCanceler` — cancels speaker output from the mic (the core
    duplex enabler).
  - `NoiseSuppressor` — improves the noise floor estimate.
  - `AutomaticGainControl` — **off by default** (AGC pumps the noise floor and
    hurts both VAD and STT; expose as a setting).
- Audio mode & routing: `AudioManager.mode = MODE_IN_COMMUNICATION`, route to
  speaker with `setCommunicationDevice(SPEAKER)` on API 31+ (legacy:
  `isSpeakerphoneOn = true`). `requestAudioFocus` with `AUDIOFOCUS_GAIN_TRANSIENT`
  and duck/pause other apps.
- **Pre-roll**: keep the last 400 ms of audio always; when speech onset is
  confirmed, the utterance starts 400 ms *before* the trigger so the first
  syllable is never clipped.
- Expose: `frame` callback (10 ms RMS + ZCR + peak), `utteranceBuffer`,
  `start/stop`, `sessionId`, headset/Bluetooth state.
- Headphones/Bluetooth: when a wired/BT headset is active, echo is negligible —
  lower the echo floor logic and tighten thresholds for snappier barge-in.

### 3.2 VAD & endpointing (`VadDetector`)

Two layers:

**Layer 1 — acoustic.**
- Frame features at 10 ms: RMS (dBFS), zero-crossing rate, peak.
- Noise floor: 10th percentile of frame energy over a 5 s sliding window.
- Speech frame if `rms > noiseFloor + marginDb` (margin 8 dB default,
  "sensitivity" setting 4–14 dB) **and** ZCR is speech-plausible.
- Onset: ≥ 120 ms of speech frames (debounces clicks).
- Offset: ≥ `silenceMs` of non-speech frames. `silenceMs` is **adaptive** (below).
- Hysteresis + hangover to avoid flutter.

**Layer 2 — echo/double-talk (see 3.3).** During playback the threshold is
`max(noiseFloor, echoFloor) + bargeInMarginDb`.

**Layer 3 — semantic endpointing** (the cheap version of GPT-Live's turn
detector; requires a speculative transcript, so it engages after ~1.2 s):
- Base silence window `endpointBaseMs = 500`.
- If the stable transcript **ends incomplete** — trailing token in
  {and, but, so, or, because, if, when, then, that, with, to, for, of, the, a,
  an, like, um, uh} or no terminal punctuation and < 6 words → extend to
  `endpointPatientMs = 1100`.
- If it **ends complete** — terminal `?`/`.`/`!`, or ≥ 6 words with a finished
  clause → shorten to `endpointEagerMs = 380`.
- If the user says *"wait" / "hold on" / "one sec" / "give me a second"* →
  **WAIT** state: no response and no timeout until they say *"okay" / "go on" /
  "continue"* or press the orb. (Barge-in stays armed.)
- Silences > 6 s mid-thought: stay patient, optionally breathe a soft "take your
  time" only if the setting is on (default off — silence is more natural).

### 3.3 Echo suppression & double-talk detection

The platform AEC is good but not perfect; we add a software guard:

- Track `playbackLevelDb` from the PCM we are writing and the exact playback
  delay (AudioTrack latency + device path, estimated from
  `getPlaybackHeadPosition` vs. write position).
- On playback start, learn `echoFloorDb` for 300 ms: `echoFloor = micLevel +
  6 dB` while we know only the assistant is audible. Re-learn opportunistically
  during long assistant turns.
- Confirm a barge-in only when mic level exceeds
  `max(noiseFloor, echoFloor) + bargeInMarginDb` (default 5 dB) continuously for
  `bargeInConfirmMs` (default 200 ms, range 120–300).
- Because confirmation happens **before** stopping audio, a cough costs 200 ms
  of overlap instead of a false interruption; a real interruption costs 200 ms
  of latency. This trade is tunable and is the single most important knob.

### 3.4 Playback (`PcmPlayer`)

- `AudioTrack.Builder`: 24 kHz mono PCM16 stream
  (`PERFORMANCE_MODE_LOW_LATENCY`), attributes
  `USAGE_VOICE_COMMUNICATION` + `CONTENT_TYPE_SPEECH`.
- The cloud returns raw PCM at a fixed documented rate (24 kHz for ElevenLabs
  "pcm", and OpenAI `pcm`); make the rate a per-model setting with a runtime
  sanity check (play a 1 kHz calibration? no — simply expose "audio rate" in
  voice settings, default 24000, and note the alternative 16000 for some
  providers).
- **Chunk queue**: TTS arrives per sentence; player queues PCM buffers and
  writes them in order. Sentence N plays while sentence N+1 is synthesizing.
- **Flush**: `pause(); flush();` — measured stop latency < 20 ms. Optional 60 ms
  volume fade (`setVolume`) to avoid a click.
- **Precise heard-position**: `getPlaybackHeadPosition()` (frames played) plus
  a frame→text map (each sentence's frame range) yields exactly what was heard —
  this drives truncation (3.10).
- Playback level meter feeds the orb and the echo logic.

### 3.5 STT client + speculative transcription

Request (JSON, avoids multipart/25 MB issues):
`{ model, input_audio: { data: <base64 WAV>, format: "wav" }, language?, temperature? }`
→ `{ text, usage }`.

- WAV: PCM16 mono 16 kHz with a 44-byte header (`WavWriter`).
- Chunking: OpenRouter STT is non-streaming, so *we* stream it:
  - After 1.2 s of user speech, send the audio-so-far every ~1.5 s
    (`speculativeStt = on` setting, default on when on Wi-Fi).
  - Speculative requests use the fast model (e.g. `openai/gpt-4o-mini-transcribe`);
    the committed turn uses the quality model (e.g. `elevenlabs/scribe-v2`).
  - `TranscriptStitcher` keeps the longest common, ≥ 2-token-stable prefix
    across consecutive results; that prefix is what semantic endpointing and
    speculative chat consume.
  - When the utterance ends, if the last speculative transcript is < 300 ms old
    and covers ≥ 95% of the audio, skip the final request.
- Language: auto-detect by default, or pinned in settings.
- Failure: retry once with the fallback model; on total failure, insert a
  soft-error caption and stay listening.

### 3.6 Chat integration + voice persona

- Reuse `GenerationManager` so the conversation is persisted as normal text and
  the existing streaming/trace/tools machinery applies.
- **Voice persona**: in voice mode, inject a system addendum:
  "You are in a live spoken conversation. Reply in short, direct sentences.
  No markdown, lists, or code unless asked. Front-load the answer, then add at
  most one sentence of detail. Never mention formatting. If a request will take
  longer than a few seconds (search, tools, long reasoning), say a one-line
  acknowledgment first, then continue."
  This alone removes most of the "reading a document aloud" feel.
- **Code/long output**: if the answer contains a code block or a long list, the
  voice layer speaks a one-sentence summary and points to the text bubble
  ("I've put the code in the chat"). Prevents unlistenably long speech.
- **Tools**: hook `GenerationManager`'s tool steps; when a tool/step is active
  and speech has nothing queued, play a pending filler (3.8).

### 3.7 TTS client + pipelining + pre-warming

Request: `{ model, input, voice, response_format: "pcm", instructions? }` →
raw PCM bytes + `X-Generation-Id`.

- **Sentence chunker** on the chat stream: flush a speakable chunk at
  `. ! ? …` plus a length guard (first chunk small: ≥ 12 chars or 400 ms since
  last flush; later chunks up to ~300 chars). First-chunk-small is the single
  biggest first-audio win.
- **Pipeline**: synthesis of chunk N+1 overlaps playback of chunk N; queue depth
  2–3 chunks.
- **Pre-warm**: keep the TLS connection hot (HTTP/2, periodic no-op request
  every ~20 s); optionally synthesize the first filler library at session start.
- **Cost visibility**: use `X-Generation-Id` + `GET /generation?id=` to pull the
  real cost for STT/TTS and add it to the session ledger (fallback: estimate
  from chars/seconds).
- Long-text limits: per-model max chars (4k OpenAI, 10k ElevenLabs). The chunker
  stays far below; hard-split at 1000 chars if a model rejects.

### 3.8 Backchannels & fillers (`VoicePhrases`) — the "aliveness" layer

Two pre-generated, on-disk–cached audio libraries, synthesized once per
(model, voice):

- **Backchannels** — `mhm`, `yeah`, `okay`, `right`, `got it`, `uh-huh`, `sure`.
  Played while the user is speaking, on micro-pauses (120–250 ms inside the
  utterance), at most one per 4 s and at most one per utterance, at −6 dB, with
  a 60 ms fade-in. Randomized variant to avoid repetition.
- **Fillers** — `hmm`, `let me think`, `one sec`, `let me check on that`,
  `good question` . Played when THINKING exceeds `fillerAfterMs` (default
  900 ms) with no first token, or while a tool step is running.

Rules: never play a filler while any assistant audio is queued; never play a
backchannel during the final 300 ms of an utterance (it would collide with the
real answer); kill switch in settings.

There is no network hop for these — that's the whole point.

### 3.9 Turn controller (`VoiceSessionController`) — state machine

States:

```
IDLE → LISTENING → ENDPOINTING → TRANSCRIBING → THINKING → SPEAKING → LISTENING
             ▲                                                      │
             └────────────── INTERRUPTED ◄──── barge-in ────────────┘
             ▲
             └── WAIT (user asked to pause) / MUTED / ERROR / BUDGET_STOP
```

Transition table (abridged):

| From | Event | To | Actions |
| --- | --- | --- | --- |
| LISTENING | onset confirmed | LISTENING (recording) | start utterance, arm speculation |
| LISTENING | offset confirmed | ENDPOINTING | freeze buffer, take stable prefix |
| ENDPOINTING | final transcript | TRANSCRIBING→THINKING | commit user message, start chat |
| THINKING | first sentence ready | SPEAKING | enqueue TTS, play |
| THINKING | filler timeout | THINKING | play filler |
| THINKING | barge-in confirmed & stream young | LISTENING | cancel chat, merge into new utterance |
| SPEAKING | barge-in confirmed | INTERRUPTED→LISTENING | flush player, truncate message, cancel or background the stream |
| SPEAKING | queue drained | LISTENING | resume listening |
| any | "wait" detected | WAIT | stop speech, disable timeout |
| any | mute | MUTED | stop capture + playback |
| any | budget exceeded | BUDGET_STOP | banner + optional spoken notice |

Pseudocode (core loop):

```kotlin
onFrame(frame) {
    vad.push(frame)
    if (state == SPEAKING && vad.bargeInConfirmed()) {
        val heard = player.stopAndGetHeardFrames()      // <20 ms
        val spoken = chunker.textHeardUpTo(heard)
        controller.interrupt(spoken)                    // truncate + cancel/background
        state = LISTENING; vad.beginUtterance(preRoll)
    }
    if (vad.onset()) capture.beginUtterance()
    if (vad.offset()) {
        val partial = stitcher.stablePrefix()
        val final = if (speculativeFresh(partial)) partial else stt.transcribe(capture.utterance())
        commitUserMessage(final)
        state = THINKING
        chat.send(final).collect { delta ->
            chunker.feed(delta) { sentence -> tts.enqueue(sentence) }
        }
    }
}
```

Threading: capture/VAD on a dedicated high-priority thread; network on
`Dispatchers.IO`; a single mutex/actor owns the state machine so transitions are
serialized. No blocking calls on the audio thread — ever.

### 3.10 Conversation-state fidelity (what the model thinks it said)

When interrupted, GPT-Live keeps the conversation coherent: the model "knows"
only what was heard. We reproduce this:

- `SentenceChunker` records, for each sentence: text range and the PCM frame
  range it was synthesized into.
- `PcmPlayer` reports frames actually played. Mapping frames → text gives
  `spokenText`.
- Truncate rule: include all fully-heard sentences; include the partial sentence
  only if ≥ 80% of its frames played (round to the nearest word boundary).
- Persist `spokenUpTo: Int?` (char offset) on the assistant message:
  - UI: the unspoken remainder is shown dimmed/("not spoken").
  - Chain rebuild: the assistant turn sent to the model is the **truncated**
    text, so the model's self-history matches the user's experience.
  - If "finish in background" is on, the stream continues and the full text is
    stored, but only `spokenText` is sent as history.
- Interruption during THINKING (no speech yet): if the chat stream has produced
  < ~24 tokens, cancel it and treat the new utterance as one merged turn; else
  let it complete silently, mark it unspoken, and respond to the new input.

### 3.11 UI (`VoiceScreen`)

- Full-screen overlay from the composer (long-press mic, or a dedicated button).
- **Orb**: driven by real levels — outer ring = user level, inner pulse =
  assistant level; color/state = listening / thinking / speaking / waiting.
  Both rings can be active at once — that's the visual story of duplex.
- **Live captions**: user partial transcript in italics as it is speculated,
  solidifying on commit; assistant text streams word-by-word, with the sentence
  currently being spoken highlighted.
- Controls: mute, pause-listening, "wait", end session, model/voice quick-pick.
- Latency optics: a subtle three-dot "breath" instead of spinners; no progress
  bars.
- The session writes normal chat messages, so nothing is lost; a chip in the
  chat marks turns that were spoken vs. typed.

---

## 4. Latency budget

| Stage | Naive | With this plan | Technique |
| --- | --- | --- | --- |
| Endpoint (silence) | 700–1000 ms | **380–500 ms** | semantic endpointing + speculation |
| STT (final) | 600–900 ms | **0–300 ms** | speculative STT, fresh-result reuse |
| Chat TTFT | 400–1500 ms | **300–900 ms** | pre-warmed conn, voice persona, low reasoning, speculative chat |
| TTS first sentence | 400–800 ms | **250–600 ms** | small first chunk, pipelining, pre-warm |
| **First audio after user stops** | **~2.1–4.2 s** | **p50 ≤ 900 ms** | all of the above |
| Plus filler coverage | — | **0 dead air** | pre-synthesized fillers |

Barge-in: confirm 200 ms + stop <20 ms → assistant silent ≤ 250 ms after the
user starts, including human reaction time not counted.

---

## 5. Failure modes & degradation ladder

| Failure | Detection | Response |
| --- | --- | --- |
| No hardware AEC | `isAvailable == false` | half-duplex fallback: mute mic during playback, show "headphones recommended"; keep barge-in for headset use |
| Echo mis-detected as speech | echo floor exceeded but transcript empty/tiny | raise echo floor, shorten confirmation; log false barge-in metric |
| STT timeout / 5xx | request error | retry once on fallback model, then caption + keep listening |
| Chat 402/429 | API error | stop session, banner, optional spoken "I've run out of credits" |
| TTS failure | error / no bytes | fall back to platform TTS for that sentence (voice changes — flag it) or show text-only |
| Network flap | repeated IOExceptions | pause session, keep transcript, offer "resume" |
| Device thermal/battery | sustained CPU + mic | lower speculation (off), increase endpoint patience |
| Background | app leaves foreground | stop capture immediately (privacy); optional foreground service for audio-only sessions later |
| Cost runaway | ledger delta per turn | per-session budget with warning at 80%, hard stop at 100% |

Privacy: persistent mic indicator in the overlay, one-tap mute, capture stops on
session end and app background, transcripts stored locally in Room only, no
audio retained after transcription (buffers zeroed).

---

## 6. Settings & tunables

| Setting | Default | Range / notes |
| --- | --- | --- |
| STT model | `openai/gpt-4o-transcribe` | any `/models?output_modalities=transcription` |
| Fast STT (speculation) | `openai/gpt-4o-mini-transcribe` | or off |
| TTS model | `openai/gpt-4o-mini-tts` | or `elevenlabs/eleven-v4-turbo` |
| Voice | model default | per-model voice list |
| Output PCM rate | 24000 | 16000 for providers that differ |
| Endpoint base / eager / patient | 500 / 380 / 1100 ms | 250–2000 |
| VAD sensitivity | 8 dB | 4–14 dB above noise floor |
| Barge-in confirm | 200 ms | 120–300 |
| Barge-in margin | 5 dB | 3–10 above echo floor |
| Backchannels | on | off / on |
| Fillers | on | 900 ms trigger |
| Speculative STT | on (Wi-Fi) | off / always |
| Speculative chat | on | off / always |
| Finish in background | off | off / on |
| Voice persona prompt | on | editable |
| Session budget | $1.00 | 0 = unlimited |
| Half-duplex fallback | auto | force on/off |

---

## 7. Telemetry & test plan

**Per turn, logged (SQLite table + CSV export from a debug screen):
`endpointDelayMs`, `sttMs`, `sttSpeculative`, `chatTtftMs`, `ttsFirstAudioMs`,
`playbackStartMs`, `perceivedMs`, `bargeIn`, `falseBargeIn`, `fillerPlayed`,
`sttCost`, `ttsCost`, `chatCost`.

**Unit tests** (pure JVM, no device):
- `VadDetector` with synthetic PCM: onset/offset, adaptive floor, echo floor,
  barge-in confirmation, false-positive on clicks.
- `SentenceChunker`: streaming boundaries, abbreviations ("Dr.", "e.g."),
  numbers, code-fence suppression, char limits.
- `TranscriptStitcher`: longest stable prefix, corrections, empty results.
- `TruncationMapper`: frame→text mapping, 80% partial rule, word snapping.
- Semantic endpointing: continuation-word extension, eager cases.

**Instrumented/manual matrix**:
- speaker vs. wired headset vs. Bluetooth; quiet vs. café/TV noise; device AEC
  on/off; interrupting on the first word, mid-word, and during a silent
  "thinking" gap; user says "wait"/"go on"; false starts ("uh— no, I mean…").
- Loopback rig: play a WAV from the speaker while recording, assert the echo
  floor suppresses barge-in.

**Instrumentation targets** (acceptance): p50 perceived ≤ 900 ms; p95 ≤ 1.6 s;
barge-in stop ≤ 250 ms from speech onset; false barge-in < 5%; zero dead-air
turns.

---

## 8. Milestones

- **M0 — spike & measure.** Real latency measurements of STT/TTS/chat from the
  target network; calibrate the budget table. `OpenRouterClient.transcribe()` /
  `speech()`, `WavWriter`, `PcmPlayer` playing a canned PCM buffer.
- **M1 — half-duplex baseline.** Capture → WAV → STT → chat → TTS → playback,
  push-to-talk, no duplex. User message + spoken reply land in the chat.
- **M2 — duplex core.** AEC, echo floor, barge-in with confirmation, player
  flush, spoken-text truncation, `spokenUpTo` persistence.
- **M3 — realism.** Semantic endpointing, WAIT state, backchannels, fillers,
  voice persona, sentence pipelining, the orb + live captions.
- **M4 — speculation.** Speculative STT + stitcher, speculative chat with
  shadow stream, connection pre-warm, filler-before-filler-time tuning.
- **M5 — hardening.** Failure ladder, budgets, telemetry screen, device/noise
  matrix, settings polish.
- **M6 — optional real duplex.** GPT-Live over WebRTC (`v1/live/sessions`) with
  an OpenRouter model as the delegated backend (client delegation), behind the
  same `VoiceSessionController` interface. Requires a separate OpenAI key and a
  WebRTC stack; the overlay, captions, truncation, and persistence layers are
  reused unchanged.

---

## 9. Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Platform AEC quality varies wildly by device | echo triggers barge-in | echo-floor learning, confirm window, half-duplex fallback, recommend headphones |
| Speculative STT/chat cost | money | off by default on cellular, cheap speculation model, cancel-on-resume, session budget |
| Semantic endpointing errors | clipped speech | patient default, WAIT state, "continue" quick action, easy re-record |
| Long answers read aloud | unusable | persona prompt, summary-for-code rule, hard 400-char chunk cap |
| Thermal/battery during long sessions | throttling | speculation off after N minutes, mic rest when muted, session length reminder at 30 min |
| Race conditions in the state machine | stuck sessions | single-actor controller, watchdog that returns to LISTENING on inconsistent state, full state logged |
| Privacy concerns | trust | mic indicator, instant mute, no audio retention, capture stops on background |

---

## 10. Open decisions

1. Default STT pair (suggest `gpt-4o-transcribe` + `gpt-4o-mini-transcribe` for
   speculation) vs. ElevenLabs Scribe v2.
2. Default TTS voice/model (suggest `openai/gpt-4o-mini-tts`, "marin"/"cedar";
   or `elevenlabs/eleven-v4-turbo`).
3. Spoken-text truncation on barge-in: truncate model history to what was heard
   (recommended, GPT-Live-like) vs. keep full text.
4. Live sessions append to the current chat (recommended) vs. a dedicated
   scratch chat.
5. Backchannels on by default (recommended) — some users find them uncanny.
