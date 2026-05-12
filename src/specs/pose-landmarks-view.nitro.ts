import {
  type HybridView,
  type HybridViewMethods,
  type HybridViewProps,
} from 'react-native-nitro-modules'

export interface PoseLandmarksViewProps extends HybridViewProps {
  isActive: boolean
  enableSkeleton: boolean
  skeletonColor: string
  skeletonBoneThickness: number
  landmarkColor: string
  minVisibilityConfidence: number
  modelSelection: number
  delegateSelection: number
  inferenceSampleRateHz: number
  enableVisibilityRecovery: boolean
  enableOneEuroFilter: boolean
  enableMotionPrediction: boolean
  oneEuroMinCutoff: number
  oneEuroBeta: number
  width: number
  height: number
}

export interface PoseLandmarksViewMethods extends HybridViewMethods {
  getLandmarksBuffer(): Array<number>
  getLastInferenceTimeMs(): number
}

export type PoseLandmarksView = HybridView<
  PoseLandmarksViewProps,
  PoseLandmarksViewMethods,
  { android: 'kotlin' }
>
