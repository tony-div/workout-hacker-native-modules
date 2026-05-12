import React, { useCallback, useEffect, useRef, useState } from 'react'
import {
  Dimensions,
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
import { callback } from 'react-native-nitro-modules'
import { PoseLandmarksView } from 'react-native-pose-landmarks'

const { width: SCREEN_WIDTH } = Dimensions.get('window')

const MODEL_LITE = 1
const MODEL_FULL = 0
const DELEGATE_CPU = 0
const DELEGATE_GPU = 1

type ModelKey = 'lite' | 'full'
type DelegateKey = 'cpu' | 'gpu'

type ProcessingConfig = {
  model: ModelKey
  delegate: DelegateKey
  minVisibility: number
  sampleRate: number
  enableVisibilityRecovery: boolean
  enableOneEuroFilter: boolean
  enableMotionPrediction: boolean
  oneEuroMinCutoff: number
  oneEuroBeta: number
}

function App(): React.JSX.Element {
  const viewRef = useRef<any>(null)

  // Camera & skeleton controls (live, no remount needed)
  const [cameraOn, setCameraOn] = useState(true)
  const [skeletonOn, setSkeletonOn] = useState(true)
  const [skeletonColor, setSkeletonColor] = useState('#00FF00')
  const [landmarkColor, setLandmarkColor] = useState('#FF0000')
  const [trackedLandmarkIndex, setTrackedLandmarkIndex] = useState(0)

  // Config panel state
  const [model, setModel] = useState<ModelKey>('lite')
  const [delegate, setDelegate] = useState<DelegateKey>('cpu')
  const [minVisibilityInput, setMinVisibilityInput] = useState('0.95')
  const [sampleRateInput, setSampleRateInput] = useState('30')
  const [configError, setConfigError] = useState<string | null>(null)
  const [enableVisibilityRecovery, setEnableVisibilityRecovery] = useState(true)
  const [enableOneEuroFilter, setEnableOneEuroFilter] = useState(true)
  const [enableMotionPrediction, setEnableMotionPrediction] = useState(false)
  const [oneEuroMinCutoffInput, setOneEuroMinCutoffInput] = useState('1.0')
  const [oneEuroBetaInput, setOneEuroBetaInput] = useState('0.009')

  const [appliedConfig, setAppliedConfig] = useState<ProcessingConfig>({
    model: 'lite',
    delegate: 'cpu',
    minVisibility: 0.95,
    sampleRate: 30,
    enableVisibilityRecovery: true,
    enableOneEuroFilter: true,
    enableMotionPrediction: false,
    oneEuroMinCutoff: 1.0,
    oneEuroBeta: 0.009,
  })

  // Debug data
  const [landmarksCount, setLandmarksCount] = useState<number>(0)
  const [latencyMs, setLatencyMs] = useState<number>(-1)
  const [sampleLandmark, setSampleLandmark] = useState<string>('--')
  const [rawBufferPreview, setRawBufferPreview] = useState<string>('--')

  // Key forces remount when config changes
  const [viewKey, setViewKey] = useState(0)

  // Poll landmarks from the view's hybridRef
  useEffect(() => {
    const interval = setInterval(() => {
      const ref = viewRef.current
      if (!ref) return

      const buf = ref.getLandmarksBuffer() as number[]
      if (buf && buf.length > 0) {
        const count = buf.length / 4
        setLandmarksCount(count)

        const idx = trackedLandmarkIndex
        if (idx < count) {
          const base = idx * 4
          const xNorm = buf[base]
          const yNorm = buf[base + 1]
          const z = buf[base + 2]
          const v = buf[base + 3]
          setSampleLandmark(
            `#${idx}: (${xNorm.toFixed(3)}, ${yNorm.toFixed(3)}, ${z.toFixed(3)}) v=${v.toFixed(3)}`
          )
        }

        const previewLen = Math.min(16, buf.length)
        let preview = ''
        for (let i = 0; i < previewLen; i++) {
          preview += buf[i].toFixed(3)
          if (i < previewLen - 1) preview += (i + 1) % 4 === 0 ? ' | ' : ', '
        }
        setRawBufferPreview(preview)
      } else {
        setLandmarksCount(0)
        setSampleLandmark('--')
        setRawBufferPreview('--')
      }

      setLatencyMs(ref.getLastInferenceTimeMs())
    }, 33)

    return () => clearInterval(interval)
  }, [trackedLandmarkIndex])

  const handleHybridRef = useCallback(
    callback((ref: any) => {
      viewRef.current = ref
    }),
    []
  )

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
      delegate,
      minVisibility,
      sampleRate,
      enableVisibilityRecovery,
      enableOneEuroFilter,
      enableMotionPrediction,
      oneEuroMinCutoff,
      oneEuroBeta,
    })
    setViewKey((k) => k + 1)
  }

  const delegateLabel = appliedConfig.delegate === 'gpu' ? 'GPU' : 'CPU'

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" />
      <ScrollView contentContainerStyle={styles.scrollContent}>
        {/* Title */}
        <View style={styles.panel}>
          <Text style={styles.title}>Pose Landmarks</Text>
          <Text style={styles.subtitle}>LIVE_STREAM / {delegateLabel}</Text>
        </View>

        {/* LIVE CONTROLS — toggle without remount */}
        <View style={styles.panel}>
          <Text style={styles.sectionTitle}>Live Controls</Text>
          <View style={styles.toggleCard}>
            <View style={styles.toggleRow}>
              <Text style={styles.toggleLabel}>Camera</Text>
              <Switch
                value={cameraOn}
                onValueChange={setCameraOn}
                trackColor={{ false: '#334155', true: '#22c55e' }}
                thumbColor="#f8fafc"
              />
            </View>
            <View style={styles.toggleRow}>
              <Text style={styles.toggleLabel}>Skeleton overlay</Text>
              <Switch
                value={skeletonOn}
                onValueChange={setSkeletonOn}
                trackColor={{ false: '#334155', true: '#22c55e' }}
                thumbColor="#f8fafc"
              />
            </View>
          </View>

          <Text style={styles.label}>Skeleton color</Text>
          <View style={styles.row}>
            {['#00FF00', '#00FFFF', '#FFFF00', '#FF00FF', '#FFFFFF'].map((c) => (
              <Pressable
                key={c}
                onPress={() => setSkeletonColor(c)}
                style={[
                  styles.colorSwatch,
                  { backgroundColor: c },
                  skeletonColor === c && styles.colorSwatchActive,
                ]}
              />
            ))}
          </View>

          <Text style={styles.label}>Landmark dot color</Text>
          <View style={styles.row}>
            {['#FF0000', '#FFA500', '#FF69B4', '#00BFFF', '#FFFFFF'].map((c) => (
              <Pressable
                key={c}
                onPress={() => setLandmarkColor(c)}
                style={[
                  styles.colorSwatch,
                  { backgroundColor: c },
                  landmarkColor === c && styles.colorSwatchActive,
                ]}
              />
            ))}
          </View>
        </View>

        {/* CONFIG PANEL */}
        <View style={styles.panel}>
          <Text style={styles.sectionTitle}>Initialization Config</Text>
          <Text style={styles.configHint}>Changes require remount (Apply button)</Text>

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

          <Text style={styles.label}>Delegate</Text>
          <View style={styles.row}>
            <Pressable
              onPress={() => setDelegate('cpu')}
              style={[styles.chip, delegate === 'cpu' ? styles.chipActive : null]}
            >
              <Text style={[styles.chipText, delegate === 'cpu' ? styles.chipTextActive : null]}>
                CPU
              </Text>
            </Pressable>
            <Pressable
              onPress={() => setDelegate('gpu')}
              style={[styles.chip, delegate === 'gpu' ? styles.chipActive : null]}
            >
              <Text style={[styles.chipText, delegate === 'gpu' ? styles.chipTextActive : null]}>
                GPU
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
              <Text style={styles.toggleLabel}>One Euro filter</Text>
              <Switch
                value={enableOneEuroFilter}
                onValueChange={setEnableOneEuroFilter}
                trackColor={{ false: '#334155', true: '#22c55e' }}
                thumbColor="#f8fafc"
              />
            </View>
            <View style={styles.toggleRow}>
              <Text style={styles.toggleLabel}>Motion prediction</Text>
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
            <Text style={styles.applyButtonText}>Apply & Remount</Text>
          </Pressable>

          {configError ? <Text style={styles.errorText}>{configError}</Text> : null}
        </View>

        {/* NATIVE VIEW — works in both IMAGE and LIVE_STREAM mode */}
        <PoseLandmarksView
          key={viewKey}
          hybridRef={handleHybridRef}
          isActive={cameraOn}
          enableSkeleton={skeletonOn}
          skeletonColor={skeletonColor}
          skeletonBoneThickness={8}
          landmarkColor={landmarkColor}
          minVisibilityConfidence={appliedConfig.minVisibility}
          modelSelection={appliedConfig.model === 'lite' ? MODEL_LITE : MODEL_FULL}
          delegateSelection={appliedConfig.delegate === 'cpu' ? DELEGATE_CPU : DELEGATE_GPU}
          inferenceSampleRateHz={appliedConfig.sampleRate}
          enableVisibilityRecovery={appliedConfig.enableVisibilityRecovery}
          enableOneEuroFilter={appliedConfig.enableOneEuroFilter}
          enableMotionPrediction={appliedConfig.enableMotionPrediction}
          oneEuroMinCutoff={appliedConfig.oneEuroMinCutoff}
          oneEuroBeta={appliedConfig.oneEuroBeta}
          width={SCREEN_WIDTH - 24}
          height={SCREEN_WIDTH * 1.33}
          style={{ width: SCREEN_WIDTH - 24, height: SCREEN_WIDTH * 1.33 }}
        />

        {/* DEBUG INFO */}
        <View style={styles.panel}>
          <Text style={styles.sectionTitle}>Debug Info</Text>

          <Text style={styles.label}>Tracked landmark index</Text>
          <View style={styles.row}>
            {[0, 5, 10, 15, 20, 25, 30].map((i) => (
              <Pressable
                key={i}
                onPress={() => setTrackedLandmarkIndex(i)}
                style={[
                  styles.chip,
                  trackedLandmarkIndex === i ? styles.chipActive : null,
                ]}
              >
                <Text
                  style={[
                    styles.chipText,
                    trackedLandmarkIndex === i ? styles.chipTextActive : null,
                  ]}
                >
                  {i}
                </Text>
              </Pressable>
            ))}
          </View>

          <View style={styles.metaBlock}>
            <Text style={styles.metaLine}>Mode: LIVE_STREAM / {delegateLabel}</Text>
            <Text style={styles.metaLine}>Landmark points: {landmarksCount} / 33</Text>
            <Text style={styles.metaLine}>
              Inference: {latencyMs >= 0 ? `${latencyMs.toFixed(1)} ms` : '--'}
            </Text>
            <Text style={styles.metaLine}>Sample: {sampleLandmark}</Text>
          </View>
        </View>

        {/* RAW BUFFER VIEWER */}
        <View style={styles.panel}>
          <Text style={styles.sectionTitle}>Raw Buffer (getLandmarksBuffer)</Text>
          <Text style={styles.configHint}>First 4 landmarks: x, y, z, v | x, y, z, v | ...</Text>
          <View style={styles.bufferBlock}>
            <Text style={styles.bufferText}>
              {rawBufferPreview}
            </Text>
          </View>
          <Text style={styles.bufferInfo}>
            Buffer length: {landmarksCount > 0 ? landmarksCount * 4 : 0} values ({landmarksCount} landmarks × 4)
          </Text>
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
  sectionTitle: {
    color: '#e2e8f0',
    fontSize: 17,
    fontWeight: '600',
    marginBottom: 8,
  },
  configHint: {
    color: '#64748b',
    fontSize: 12,
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
    flexWrap: 'wrap',
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
  colorSwatch: {
    width: 36,
    height: 36,
    borderRadius: 18,
    borderWidth: 2,
    borderColor: '#334155',
  },
  colorSwatchActive: {
    borderColor: '#f8fafc',
    borderWidth: 3,
  },
  metaBlock: {
    marginTop: 8,
    padding: 10,
    borderRadius: 10,
    backgroundColor: '#0b1327',
    borderWidth: 1,
    borderColor: '#253556',
  },
  metaLine: {
    color: '#a8c2ea',
    marginBottom: 4,
    fontFamily: 'monospace',
    fontSize: 12,
  },
  bufferBlock: {
    padding: 10,
    borderRadius: 10,
    backgroundColor: '#0b1327',
    borderWidth: 1,
    borderColor: '#253556',
  },
  bufferText: {
    color: '#22d3ee',
    fontFamily: 'monospace',
    fontSize: 11,
    lineHeight: 18,
  },
  bufferInfo: {
    marginTop: 8,
    color: '#64748b',
    fontSize: 11,
    fontFamily: 'monospace',
  },
})

export default App
