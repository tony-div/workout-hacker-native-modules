import React, { useEffect, useMemo, useState } from 'react'
import {
  Dimensions,
  LayoutChangeEvent,
  Pressable,
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
} from 'react-native'
import { PoseLandmarks } from 'react-native-pose-landmarks'

const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get('window')

const LANDMARK_COUNT = 33
const VALUES_PER_LANDMARK = 4
const DOT_SIZE = 8

const MODEL_LITE = 1
const MODEL_FULL = 0

type ModelKey = 'lite' | 'full'

type ProcessingConfig = {
  model: ModelKey
  minVisibility: number
  sampleRate: number
  enableVisibilityRecovery: boolean
  enableOneEuroFilter: boolean
  enableMotionPrediction: boolean
  oneEuroMinCutoff: number
  oneEuroBeta: number
}

function App(): React.JSX.Element {
  const [initialized, setInitialized] = useState<boolean | null>(null)
  const [landmarks, setLandmarks] = useState<number[]>([])
  const [latencyMs, setLatencyMs] = useState<number>(-1)

const [model, setModel] = useState<ModelKey>('lite')
  const [minVisibilityInput, setMinVisibilityInput] = useState('0.95')
  const [sampleRateInput, setSampleRateInput] = useState('30')
  const [configError, setConfigError] = useState<string | null>(null)
  const [enableVisibilityRecovery, setEnableVisibilityRecovery] = useState(true)
  const [enableOneEuroFilter, setEnableOneEuroFilter] = useState(true)
  const [enableMotionPrediction, setEnableMotionPrediction] = useState(false)
  const [oneEuroMinCutoffInput, setOneEuroMinCutoffInput] = useState('1.0')
  const [oneEuroBetaInput, setOneEuroBetaInput] = useState('0.009')
  const [canvasSize, setCanvasSize] = useState({
    width: SCREEN_WIDTH - 24,
    height: SCREEN_HEIGHT * 0.5,
  })

  const [activeMinVisibility, setActiveMinVisibility] = useState(0.95)
  const [activeSampleRate, setActiveSampleRate] = useState(30)
  const [activeModel, setActiveModel] = useState<ModelKey>('lite')
  const [activeEnableVisibilityRecovery, setActiveEnableVisibilityRecovery] = useState(true)
  const [activeEnableOneEuroFilter, setActiveEnableOneEuroFilter] = useState(true)
  const [activeEnableMotionPrediction, setActiveEnableMotionPrediction] = useState(false)
  const [activeOneEuroMinCutoff, setActiveOneEuroMinCutoff] = useState(1.0)
  const [activeOneEuroBeta, setActiveOneEuroBeta] = useState(0.009)
  const [appliedConfig, setAppliedConfig] = useState<ProcessingConfig>({
    model: 'lite',
    minVisibility: 0.95,
    sampleRate: 30,
    enableVisibilityRecovery: true,
    enableOneEuroFilter: true,
    enableMotionPrediction: false,
    oneEuroMinCutoff: 1.0,
    oneEuroBeta: 0.009,
  })

  useEffect(() => {
    setLandmarks([])
    setLatencyMs(-1)

    const isInitialized = PoseLandmarks.initPoseLandmarker(
      appliedConfig.minVisibility,
      appliedConfig.sampleRate,
      undefined,  // rigidBodyWindowFrames deprecated
      appliedConfig.model === 'lite' ? MODEL_LITE : MODEL_FULL,
      appliedConfig.enableVisibilityRecovery,
      undefined,  // enableRigidBodyConstraint deprecated
      appliedConfig.enableOneEuroFilter,
      appliedConfig.enableMotionPrediction,
      appliedConfig.oneEuroMinCutoff,
      appliedConfig.oneEuroBeta
    )

    setInitialized(isInitialized)

    if (isInitialized) {
      setActiveMinVisibility(appliedConfig.minVisibility)
      setActiveSampleRate(appliedConfig.sampleRate)
      setActiveModel(appliedConfig.model)
      setActiveEnableVisibilityRecovery(appliedConfig.enableVisibilityRecovery)
      setActiveEnableOneEuroFilter(appliedConfig.enableOneEuroFilter)
      setActiveEnableMotionPrediction(appliedConfig.enableMotionPrediction)
      setActiveOneEuroMinCutoff(appliedConfig.oneEuroMinCutoff)
      setActiveOneEuroBeta(appliedConfig.oneEuroBeta)
    }

    const interval = setInterval(() => {
      if (!isInitialized) {
        return
      }

      const buffer = PoseLandmarks.getLandmarksBuffer()
      if (buffer && buffer.length === LANDMARK_COUNT * VALUES_PER_LANDMARK) {
        setLandmarks(buffer)
      }

      setLatencyMs(PoseLandmarks.getLastInferenceTimeMs())
    }, 33)

    return () => {
      clearInterval(interval)
      PoseLandmarks.closePoseLandmarker()
    }
  }, [appliedConfig])

  const renderedLandmarks = useMemo(() => {
    const dots: React.JSX.Element[] = []
    for (let i = 0; i < LANDMARK_COUNT; i++) {
      const base = i * VALUES_PER_LANDMARK
      const xNorm = Math.max(0, Math.min(1, landmarks[base]))
      const yNorm = Math.max(0, Math.min(1, landmarks[base + 1]))
      const x = xNorm * Math.max(0, canvasSize.width - DOT_SIZE)
      const y = yNorm * Math.max(0, canvasSize.height - DOT_SIZE)
      const visibility = landmarks[base + 3]

      if (visibility > activeMinVisibility) {
        dots.push(
          <View
            key={`landmark-${i}`}
            style={[
              styles.dot,
              {
                left: x,
                top: y,
              },
            ]}
          />
        )
      }
    }
    return dots
  }, [activeMinVisibility, canvasSize.height, canvasSize.width, landmarks])

  const onCanvasLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout
    if (width <= 0 || height <= 0) {
      return
    }
    setCanvasSize((prev) => {
      if (Math.abs(prev.width - width) < 0.5 && Math.abs(prev.height - height) < 0.5) {
        return prev
      }
      return { width, height }
    })
  }

  const applyConfig = () => {
    const minVisibility = Number(minVisibilityInput)
    const sampleRate = Number(sampleRateInput)
    const oneEuroMinCutoff = Number(oneEuroMinCutoffInput)
    const oneEuroBeta = Number(oneEuroBetaInput)

    const isValid =
      Number.isFinite(minVisibility) &&
      Number.isFinite(sampleRate) &&
      minVisibility >= 0 &&
      minVisibility <= 1 &&
      sampleRate >= 1 &&
      sampleRate <= 30 &&
      Number.isFinite(oneEuroMinCutoff) &&
      oneEuroMinCutoff >= 0.01 &&
      oneEuroMinCutoff <= 5.0 &&
      Number.isFinite(oneEuroBeta) &&
      oneEuroBeta >= 0 &&
      oneEuroBeta <= 1.0

    if (!isValid) {
      setConfigError('Invalid config. vis 0..1, rate 1..30, cutoff 0.01..5, beta 0..1')
      return
    }

    setConfigError(null)
    setAppliedConfig({
      model,
      minVisibility,
      sampleRate,
      enableVisibilityRecovery,
      enableOneEuroFilter,
      enableMotionPrediction,
      oneEuroMinCutoff,
      oneEuroBeta,
    })
  }

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" />
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.panel}>
          <Text style={styles.title}>Pose Landmarks Lab</Text>
          <Text style={styles.subtitle}>Tune Kotlin pipeline + model selection from JS</Text>

          <Text style={styles.label}>Model</Text>
          <View style={styles.row}>
            <Pressable
              onPress={() => setModel('lite')}
              style={[styles.chip, model === 'lite' ? styles.chipActive : null]}
            >
              <Text style={[styles.chipText, model === 'lite' ? styles.chipTextActive : null]}>
                Lite
              </Text>
            </Pressable>
            <Pressable
              onPress={() => setModel('full')}
              style={[styles.chip, model === 'full' ? styles.chipActive : null]}
            >
              <Text style={[styles.chipText, model === 'full' ? styles.chipTextActive : null]}>
                Full
              </Text>
            </Pressable>
          </View>

          <Text style={styles.label}>Min visibility confidence (0..1)</Text>
          <TextInput
            value={minVisibilityInput}
            onChangeText={setMinVisibilityInput}
            keyboardType="decimal-pad"
            style={styles.input}
            placeholder="0.5"
            placeholderTextColor="#6b7280"
          />

          <Text style={styles.label}>Inference sample rate Hz (1..30)</Text>
          <TextInput
            value={sampleRateInput}
            onChangeText={setSampleRateInput}
            keyboardType="numeric"
            style={styles.input}
            placeholder="30"
            placeholderTextColor="#6b7280"
          />

          <Text style={styles.label}>Post-processing toggles</Text>
          <View style={styles.toggleCard}>
            <View style={styles.toggleRow}>
              <Text style={styles.toggleLabel}>Visibility recovery</Text>
              <Switch
                value={enableVisibilityRecovery}
                onValueChange={setEnableVisibilityRecovery}
                trackColor={{ false: '#334155', true: '#22c55e' }}
                thumbColor="#f8fafc"
              />
            </View>
            <View style={styles.toggleRow}>
              <Text style={styles.toggleLabel}>One Euro filter (smooths jitter)</Text>
              <Switch
                value={enableOneEuroFilter}
                onValueChange={setEnableOneEuroFilter}
                trackColor={{ false: '#334155', true: '#22c55e' }}
                thumbColor="#f8fafc"
              />
            </View>
            <View style={styles.toggleRow}>
              <Text style={styles.toggleLabel}>Motion prediction (causes jitter)</Text>
              <Switch
                value={enableMotionPrediction}
                onValueChange={setEnableMotionPrediction}
                trackColor={{ false: '#334155', true: '#22c55e' }}
                thumbColor="#f8fafc"
              />
            </View>
            {enableOneEuroFilter && (
              <>
                <Text style={styles.label}>One Euro minCutoff (0.01..5)</Text>
                <TextInput
                  value={oneEuroMinCutoffInput}
                  onChangeText={setOneEuroMinCutoffInput}
                  keyboardType="decimal-pad"
                  style={styles.input}
                  placeholder="0.4"
                  placeholderTextColor="#6b7280"
                />
                <Text style={styles.label}>One Euro beta (0..1)</Text>
                <TextInput
                  value={oneEuroBetaInput}
                  onChangeText={setOneEuroBetaInput}
                  keyboardType="decimal-pad"
                  style={styles.input}
                  placeholder="0.007"
                  placeholderTextColor="#6b7280"
                />
              </>
            )}
          </View>

          <Pressable style={styles.applyButton} onPress={applyConfig}>
            <Text style={styles.applyButtonText}>Apply & Reinitialize</Text>
          </Pressable>

          {configError ? <Text style={styles.errorText}>{configError}</Text> : null}

          <View style={styles.metaBlock}>
            <Text style={styles.metaLine}>
              Status: {initialized ? 'active' : 'not active'}
            </Text>
            <Text style={styles.metaLine}>
              Active model: {activeModel}
            </Text>
            <Text style={styles.metaLine}>
              Active config: vis {activeMinVisibility.toFixed(2)} | sample {activeSampleRate}Hz
            </Text>
            <Text style={styles.metaLine}>
              Post: recovery {activeEnableVisibilityRecovery ? 'on' : 'off'} | one-euro {activeEnableOneEuroFilter ? `c=${activeOneEuroMinCutoff},b=${activeOneEuroBeta}` : 'off'} | pred {activeEnableMotionPrediction ? 'on' : 'off'}
            </Text>
            <Text style={styles.metaLine}>Points: {landmarks.length / 4}</Text>
            <Text style={styles.metaLine}>
              Inference: {latencyMs >= 0 ? `${latencyMs.toFixed(1)} ms` : '--'}
            </Text>
          </View>
        </View>

        <View style={styles.canvas} onLayout={onCanvasLayout}>
          {renderedLandmarks}
          {landmarks.length === 0 ? (
            <Text style={styles.placeholder}>Waiting for camera data...</Text>
          ) : null}
        </View>
      </ScrollView>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0b1220',
  },
  scrollContent: {
    paddingBottom: 24,
  },
  panel: {
    margin: 12,
    padding: 14,
    borderRadius: 14,
    backgroundColor: '#111c31',
    borderWidth: 1,
    borderColor: '#1f2f4d',
  },
  title: {
    color: '#f8fafc',
    fontSize: 24,
    fontWeight: '700',
  },
  subtitle: {
    color: '#9fb3d1',
    marginTop: 4,
    marginBottom: 12,
  },
  label: {
    color: '#dbeafe',
    marginTop: 8,
    marginBottom: 6,
    fontSize: 13,
  },
  row: {
    flexDirection: 'row',
    gap: 10,
  },
  chip: {
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: '#35507f',
    backgroundColor: '#152746',
  },
  chipActive: {
    backgroundColor: '#16a34a',
    borderColor: '#16a34a',
  },
  chipText: {
    color: '#dbeafe',
    fontWeight: '600',
  },
  chipTextActive: {
    color: '#04210f',
  },
  input: {
    borderWidth: 1,
    borderColor: '#2f466f',
    borderRadius: 10,
    paddingHorizontal: 10,
    paddingVertical: 10,
    color: '#e5e7eb',
    backgroundColor: '#0f172a',
  },
  toggleCard: {
    borderWidth: 1,
    borderColor: '#2f466f',
    borderRadius: 10,
    backgroundColor: '#0f172a',
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  toggleRow: {
    minHeight: 44,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  toggleLabel: {
    color: '#dbeafe',
    fontSize: 13,
    fontWeight: '600',
  },
  applyButton: {
    marginTop: 14,
    borderRadius: 10,
    paddingVertical: 12,
    alignItems: 'center',
    backgroundColor: '#f59e0b',
  },
  applyButtonText: {
    color: '#1f1300',
    fontWeight: '700',
  },
  errorText: {
    marginTop: 10,
    color: '#fb7185',
  },
  metaBlock: {
    marginTop: 12,
    padding: 10,
    borderRadius: 10,
    backgroundColor: '#0b1327',
    borderWidth: 1,
    borderColor: '#253556',
  },
  metaLine: {
    color: '#a8c2ea',
    marginBottom: 4,
  },
  canvas: {
    minHeight: SCREEN_HEIGHT * 0.5,
    marginHorizontal: 12,
    borderRadius: 14,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: '#1f2f4d',
    backgroundColor: '#030712',
  },
  dot: {
    position: 'absolute',
    width: DOT_SIZE,
    height: DOT_SIZE,
    borderRadius: DOT_SIZE / 2,
    backgroundColor: '#22c55e',
    borderWidth: 1,
    borderColor: '#dcfce7',
  },
  placeholder: {
    color: '#64748b',
    textAlign: 'center',
    marginTop: 24,
    fontSize: 16,
  },
})

export default App
