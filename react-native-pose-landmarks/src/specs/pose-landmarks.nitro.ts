import { type HybridObject } from 'react-native-nitro-modules'

export interface PoseLandmarks extends HybridObject<{ ios: 'swift', android: 'kotlin' }> {
  initPoseLandmarker(): boolean;
  closePoseLandmarker(): boolean;
  getLandmarksBuffer(): Array<number>;
  getLastInferenceTimeMs(): number;
}