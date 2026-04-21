const fs = require('fs')
const path = require('path')
const https = require('https')

const ASSETS_DIR = path.join(__dirname, '..', 'example', 'android', 'app', 'src', 'main', 'assets')
const MODELS = [
  { name: 'pose_landmarker_lite.task', url: 'https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task' },
  { name: 'pose_landmarker_full.task', url: 'https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/1/pose_landmarker_full.task' },
]

function downloadFile(url, dest) {
  return new Promise((resolve, reject) => {
    if (fs.existsSync(dest)) {
      console.log(`Skipping ${path.basename(dest)} - already exists`)
      return resolve()
    }
    console.log(`Downloading ${path.basename(dest)}...`)
    const file = fs.createWriteStream(dest)
    https.get(url, (response) => {
      if (response.statusCode === 301 || response.statusCode === 302) {
        return downloadFile(response.headers.location, dest).then(resolve).catch(reject)
      }
      response.pipe(file)
      file.on('finish', () => {
        file.close()
        console.log(`Downloaded ${path.basename(dest)}`)
        resolve()
      })
    }).on('error', (err) => {
      fs.unlink(dest, () => {})
      reject(err)
    })
  })
}

async function main() {
  if (!fs.existsSync(ASSETS_DIR)) {
    fs.mkdirSync(ASSETS_DIR, { recursive: true })
  }
  for (const model of MODELS) {
    const dest = path.join(ASSETS_DIR, model.name)
    await downloadFile(model.url, dest)
  }
  console.log('All MediaPipe models downloaded!')
}

main().catch(console.error)
