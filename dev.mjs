// 개발용 DB·메일 수신기를 올리고 서버(8080)와 화면(5173)을 한 터미널에서 띄운다. npm run dev 로 부른다. Ctrl+C 로 둘 다 끈다.
import { spawn, spawnSync } from 'node:child_process'
import { existsSync, realpathSync } from 'node:fs'
import { join } from 'node:path'
import concurrently from 'concurrently'

// Vite 는 경로를 글자 그대로 비교해서 c:\dev 처럼 대소문자가 다르면 React 가 두 번 올라가 화면이 빈다. 디스크에 있는 이름으로 맞춘다.
const root = realpathSync.native(import.meta.dirname)

const ok = (cmd, stdio = 'inherit') => spawnSync(cmd, { cwd: root, shell: true, stdio }).status === 0
const must = (cmd, message) => {
  if (!ok(cmd)) {
    console.error(message)
    process.exit(1)
  }
}

if (!ok('docker info', 'ignore')) {
  console.log('Docker Desktop 을 켠다.')
  spawn(join(process.env.ProgramFiles, 'Docker', 'Docker', 'Docker Desktop.exe'), { detached: true, stdio: 'ignore' }).unref()
  const deadline = Date.now() + 3 * 60_000
  while (!ok('docker info', 'ignore')) {
    if (Date.now() > deadline) must('exit 1', 'Docker 가 3분 안에 뜨지 않았다.')
    await new Promise((resolve) => setTimeout(resolve, 3000))
  }
}

must('docker compose up -d --wait db', 'DB 를 띄우지 못했다.')

let mailPort = '1025'
if (!ok('docker compose up -d mail')) {
  // Oracle(TNSLSNR) 이 1025 를 잡고 있을 때다. Oracle 은 두고 메일 수신기를 1026 에 따로 띄운다(docker compose down 으로는 안 꺼진다: docker stop readme-mail-1026).
  console.log('1025 를 다른 프로그램이 쓰고 있어 메일 수신기를 1026 에 띄운다.')
  mailPort = '1026'
  const running = spawnSync('docker ps -q -f name=^readme-mail-1026$', { shell: true, encoding: 'utf8' }).stdout.trim()
  if (!running) {
    must(
      'docker run -d --rm --name readme-mail-1026 -p 127.0.0.1:1026:1025 -p 127.0.0.1:8025:8025 axllent/mailpit',
      '메일 수신기를 띄우지 못했다.',
    )
  }
}

const frontend = join(root, 'frontend')
if (!existsSync(join(frontend, 'node_modules'))) must('npm ci --prefix frontend', '화면 패키지를 설치하지 못했다.')

console.log('서버 http://localhost:8080 · 화면 http://localhost:5173 · 메일 http://localhost:8025 (서버가 뜨는 데 시간이 걸린다)')

const { result } = concurrently(
  [
    // 하위 셸은 지금 폴더에서 실행 파일을 찾지 않아 gradlew 를 경로째 부른다
    {
      name: 'server',
      command: `"${join(root, 'backend', 'gradlew.bat')}" bootRun`,
      cwd: join(root, 'backend'),
      env: { SPRING_MAIL_PORT: mailPort },
    },
    { name: 'web', command: 'npm run dev:server -- --port 5173 --strictPort', cwd: frontend },
  ],
  { prefixColors: ['blue', 'green'], killOthersOn: ['failure', 'success'] },
)
result.catch(() => (process.exitCode = 1))
