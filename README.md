# react-native-pose-landmarks

react-native-pose-landmarks is a react native package built with Nitro

[![Version](https://img.shields.io/npm/v/react-native-pose-landmarks.svg)](https://www.npmjs.com/package/react-native-pose-landmarks)
[![Downloads](https://img.shields.io/npm/dm/react-native-pose-landmarks.svg)](https://www.npmjs.com/package/react-native-pose-landmarks)
[![License](https://img.shields.io/npm/l/react-native-pose-landmarks.svg)](https://github.com/patrickkabwe/react-native-pose-landmarks/LICENSE)

## Requirements

- React Native v0.85.1 or higher
- Node v20.19.4 or higher
- `react-native-nitro-modules` v0.35.4 or higher

> [!IMPORTANT]
> Nitro Views require React Native v0.78.0 or higher.

## Installation

```bash
npm install react-native-pose-landmarks react-native-nitro-modules
```

## Compatibility notes

- This package is generated with Nitrogen v0.35.4.
- Android includes an adapter class at `com.poselandmarks.HybridPoseLandmarks` for Nitro object registration.
- iOS autolinking sets `SWIFT_INSTALL_OBJC_HEADER=NO` to avoid static-linking header issues on newer Xcode versions.

## Credits

Bootstrapped with [create-nitro-module](https://github.com/patrickkabwe/create-nitro-module).

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

## Release

This package is released from GitHub Actions when you push a version tag.

- Create and push a tag in the format `v*` (for example `v1.0.1`).
- The release workflow creates a GitHub release automatically from that tag.
