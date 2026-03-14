import { NitroModules } from 'react-native-nitro-modules'
import type { PoseLandmarks as PoseLandmarksSpec } from './specs/pose-landmarks.nitro'

export const PoseLandmarks =
  NitroModules.createHybridObject<PoseLandmarksSpec>('PoseLandmarks')