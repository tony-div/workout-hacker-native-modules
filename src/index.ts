import { getHostComponent, NitroModules } from 'react-native-nitro-modules'
import type { PoseLandmarks as PoseLandmarksSpec } from './specs/pose-landmarks.nitro'
import type {
  PoseLandmarksViewProps,
  PoseLandmarksViewMethods,
} from './specs/pose-landmarks-view.nitro'
import PoseLandmarksViewConfig from '../nitrogen/generated/shared/json/PoseLandmarksViewConfig.json'

export const PoseLandmarks =
  NitroModules.createHybridObject<PoseLandmarksSpec>('PoseLandmarks')

export const PoseLandmarksView = getHostComponent<
  PoseLandmarksViewProps,
  PoseLandmarksViewMethods
>('PoseLandmarksView', () => PoseLandmarksViewConfig)