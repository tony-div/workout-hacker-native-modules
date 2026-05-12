import { type HybridObject } from 'react-native-nitro-modules'

export interface PoseLandmarks extends HybridObject<{ ios: 'swift', android: 'kotlin' }> {
  initPoseLandmarker(
    minVisibilityConfidence?: number,
    inferenceSampleRateHz?: number,
    rigidBodyWindowFrames?: number,
    modelSelection?: number,
    delegateSelection?: number,
    enableVisibilityRecovery?: boolean,
    enableRigidBodyConstraint?: boolean,
    enableOneEuroFilter?: boolean,
    enableMotionPrediction?: boolean,
    oneEuroMinCutoff?: number,
    oneEuroBeta?: number
  ): boolean;
  closePoseLandmarker(): boolean;
  getLandmarksBuffer(): Array<number>;
  getLastInferenceTimeMs(): number;
}
