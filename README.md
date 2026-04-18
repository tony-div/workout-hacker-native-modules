# react-native-pose-landmarks

`react-native-pose-landmarks` is a React Native Nitro module that exposes pose landmark data from native code.

[![Version](https://img.shields.io/npm/v/react-native-pose-landmarks.svg)](https://www.npmjs.com/package/react-native-pose-landmarks)
[![Downloads](https://img.shields.io/npm/dm/react-native-pose-landmarks.svg)](https://www.npmjs.com/package/react-native-pose-landmarks)
[![License](https://img.shields.io/npm/l/react-native-pose-landmarks.svg)](https://github.com/tony-div/react-native-pose-landmarks/LICENSE)

## Requirements

- React Native `0.85.1` or newer
- Node.js `20.19.4` or newer
- `react-native-nitro-modules` `0.35.4` or newer

> [!IMPORTANT]
> Nitro Views require React Native `0.78.0` or newer.

## Installation

```bash
npm install react-native-pose-landmarks react-native-nitro-modules
```

For iOS, install pods after adding dependencies:

```bash
npx pod-install
```

## Usage

Import the module and initialize it once (for example, when a screen mounts):

```ts
import { useEffect, useState } from 'react'
import { PoseLandmarks } from 'react-native-pose-landmarks'

const LANDMARK_COUNT = 33
const VALUES_PER_LANDMARK = 4

export function usePoseLandmarks() {
  const [landmarks, setLandmarks] = useState<number[]>([])
  const [inferenceMs, setInferenceMs] = useState<number>(-1)

  useEffect(() => {
    const initialized = PoseLandmarks.initPoseLandmarker()
    if (!initialized) return

    const interval = setInterval(() => {
      const buffer = PoseLandmarks.getLandmarksBuffer()
      if (buffer.length === LANDMARK_COUNT * VALUES_PER_LANDMARK) {
        setLandmarks(buffer)
      }
      setInferenceMs(PoseLandmarks.getLastInferenceTimeMs())
    }, 33)

    return () => {
      clearInterval(interval)
      PoseLandmarks.closePoseLandmarker()
    }
  }, [])

  return { landmarks, inferenceMs }
}
```

Each landmark uses this layout in the flattened array:

- index `i * 4 + 0`: `x`
- index `i * 4 + 1`: `y`
- index `i * 4 + 2`: `z`
- index `i * 4 + 3`: `visibility`

Total values in one frame: `33 * 4 = 132`.

## API

The module exports a single hybrid object from `src/index.ts`:

```ts
export const PoseLandmarks = NitroModules.createHybridObject<PoseLandmarksSpec>('PoseLandmarks')
```

### `PoseLandmarks.initPoseLandmarker(): boolean`

Initializes the native pose landmarker runtime.

- Returns `true` when initialization succeeds.
- Returns `false` when initialization fails.

Call this before reading any landmarks.

### `PoseLandmarks.closePoseLandmarker(): boolean`

Releases native pose landmarker resources.

- Returns `true` when resources are closed.
- Returns `false` when close fails.

Call this during cleanup (for example, in `useEffect` teardown).

### `PoseLandmarks.getLandmarksBuffer(): number[]`

Returns the latest flattened landmarks buffer.

- Expected full frame length is `132` values.
- Returns an empty array when no frame is available yet.

### `PoseLandmarks.getLastInferenceTimeMs(): number`

Returns the most recent inference duration in milliseconds.

- Returns a non-negative number after successful inference.
- May return `-1` before first inference.

## Contributing

Contributions are welcome.

1. Fork and clone the repository.
2. Install dependencies with `npm install`.
3. Make your changes.
4. Run checks locally:

```bash
npm run typecheck
npm run build
```

5. If you update Nitro specs or native glue, regenerate bindings:

```bash
npm run codegen
```

6. Open a pull request with a clear description of the change and why it is needed.

For larger changes, open an issue first so design/API decisions can be aligned early.

## Compatibility Notes

- Generated with Nitrogen `0.35.4`.
- Android includes `com.poselandmarks.HybridPoseLandmarks` as an adapter class for Nitro object registration.
- iOS autolinking sets `SWIFT_INSTALL_OBJC_HEADER=NO` to avoid static-linking header issues on newer Xcode versions.

## Release

Releases are published from GitHub Actions when pushing a version tag.

- Create and push a tag in the format `v*` (for example `v1.0.1`).
- The release workflow creates a GitHub release from that tag.

## Credits

Bootstrapped with [create-nitro-module](https://github.com/patrickkabwe/create-nitro-module).
