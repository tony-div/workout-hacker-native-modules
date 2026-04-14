import React, { useEffect, useState, useMemo } from 'react';
import { Text, View, StyleSheet, Dimensions, StatusBar, SafeAreaView } from 'react-native';
import { PoseLandmarks } from 'react-native-pose-landmarks';

const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get('window');

// MediaPipe Pose Landmarks: 33 points, each has 4 values (x, y, z, visibility)
const LANDMARK_COUNT = 33;
const VALUES_PER_LANDMARK = 4;

function App(): React.JSX.Element {
  const [initialized, setInitialized] = useState<boolean | null>(null);
  const [landmarks, setLandmarks] = useState<number[]>([]);
  const [latencyMs, setLatencyMs] = useState<number>(-1);
  useEffect(() => {
    const isInitialized = PoseLandmarks.initPoseLandmarker();
    setInitialized(isInitialized);

    const interval = setInterval(() => {
        if (isInitialized) {
            const buffer = PoseLandmarks.getLandmarksBuffer();
            // Only update if we have data (buffer length should be 132)
            if (buffer && buffer.length === LANDMARK_COUNT * VALUES_PER_LANDMARK) {
                setLandmarks(buffer);
            }
            setLatencyMs(PoseLandmarks.getLastInferenceTimeMs());
        }
    }, 32); // ~30fps polling for visualization

    return () => {
        clearInterval(interval);
        PoseLandmarks.closePoseLandmarker();
    };
  }, []);

  const renderedLandmarks = useMemo(() => {
    const dots = [];
    for (let i = 0; i < LANDMARK_COUNT; i++) {
      const baseIndex = i * VALUES_PER_LANDMARK;
      const x = landmarks[baseIndex] * SCREEN_WIDTH;
      const y = landmarks[baseIndex + 1] * SCREEN_HEIGHT;
      const visibility = landmarks[baseIndex + 3];

      // Only render if it's likely a real landmark (visibility > 0.5)
      if (visibility > 0.5) {
        dots.push(
          <View
            key={`landmark-${i}`}
            style={[
              styles.dot,
              {
                left: x - 4,
                top: y - 4,
              },
            ]}
          />
        );
      }
    }
    return dots;
  }, [landmarks]);

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="dark-content" />
      <View style={styles.header}>
        <Text style={styles.title}>Pose Landmarks</Text>
        <Text style={styles.status}>
          {initialized ? '✅ MediaPipe Active' : '❌ Initializing...'}
        </Text>
        <Text style={styles.count}>Points: {landmarks.length / 4}</Text>
        <Text style={styles.latency}>
          {latencyMs >= 0 ? `Inference: ${latencyMs.toFixed(0)} ms` : 'Inference: --'}
        </Text>
      </View>
      
      <View style={styles.canvas}>
        {renderedLandmarks}
        {landmarks.length === 0 && (
          <Text style={styles.placeholder}>Waiting for camera data...</Text>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#111',
  },
  header: {
    padding: 20,
    backgroundColor: 'rgba(255, 255, 255, 0.1)',
    zIndex: 10,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#fff',
  },
  status: {
    fontSize: 16,
    color: '#aaa',
    marginTop: 4,
  },
  count: {
    fontSize: 14,
    color: '#4CAF50',
    marginTop: 2,
  },
  latency: {
    fontSize: 14,
    color: '#FFA726',
    marginTop: 2,
  },
  canvas: {
    flex: 1,
    width: '100%',
    height: '100%',
  },
  dot: {
    position: 'absolute',
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#00FF00',
    borderWidth: 1,
    borderColor: '#fff',
  },
  placeholder: {
    color: '#666',
    textAlign: 'center',
    marginTop: '50%',
    fontSize: 18,
  }
});

export default App;