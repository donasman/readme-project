// 층 규칙(frontend/CLAUDE.md "층")을 ESLint로 막는다. 위에서 아래로만 불러온다:
//   app → features/<기능> → domain/<이름> → shared, components
// 새 패키지 없이 typescript-eslint의 no-restricted-imports를 쓴다(타입만 불러오기를 따로 허용할 수 있어서).
//
// 같은 파일에 설정이 여럿 겹치면 규칙 옵션은 마지막 것만 남는다. 그래서 "층 × 깊이 × api.ts인지"마다
// 금지 목록 전체를 만들어 넣는다. 묶음(층이나 기능 하나) 밖은 상대 경로가 아니라 @/ 별칭으로 불러와야
// 이 규칙이 볼 수 있으므로, 묶음 밖으로 올라가는 상대 경로도 막는다.

const RULE = '@typescript-eslint/no-restricted-imports'
const EXT = '{ts,tsx}'
const MAX_DEPTH = 4

/** "@/name"과 그 아래 전부 */
const under = (...names) => names.flatMap((name) => [`@/${name}`, `@/${name}/**`])

const BAN = {
  sharedUp: {
    group: under('domain', 'features', 'app', 'mocks'),
    message: 'shared와 components는 domain, features, app, mocks를 불러오지 않는다.',
  },
  domainUp: { group: under('features', 'app'), message: 'domain은 features와 app을 불러오지 않는다.' },
  featureToApp: { group: under('app'), message: '기능은 app을 불러오지 않는다.' },
  featureToFeature: {
    group: under('features'),
    message: '기능 안은 상대 경로로 불러오고, 다른 기능은 불러오지 않는다. 함께 쓸 것은 domain으로 올린다.',
  },
  appDeepFeature: {
    group: ['@/features/*/**'],
    message: '앱은 기능을 features/<기능>/index.ts로만 불러온다(@/features/<기능>).',
  },
  mocks: { group: under('mocks'), message: '가짜 서버(mocks)는 각 층의 api.ts만 부른다.' },
  mocksUp: { group: under('app'), message: '가짜 서버는 app을 불러오지 않는다.' },
  mocksValues: {
    group: under('domain', 'features'),
    allowTypeImports: true,
    message: '가짜 서버는 shared와 타입(import type)만 불러온다.',
  },
}

/** 묶음 밖으로 levels만큼 올라가는 상대 경로 */
const climbOut = (levels) => ({
  regex: `^(\\.\\./){${levels}}`,
  message: '묶음 밖은 @/ 별칭으로 불러온다. 그래야 층 규칙이 보인다.',
})

/**
 * 묶음 하나의 설정들. root는 묶음의 폴더 glob("src/shared/", "src/features/*\/" …).
 * 깊이마다 파일 glob을 따로 만들어, 그 깊이에서 묶음을 벗어나는 상대 경로를 막는다.
 * apiBans는 api.ts가 아닌 파일에만 더하는 금지(가짜 서버).
 */
function unit(name, root, bans, apiBans = []) {
  const configs = []
  for (let depth = 1; depth <= MAX_DEPTH; depth++) {
    const dir = root + '*/'.repeat(depth - 1)
    const deeper = depth === MAX_DEPTH ? '**/' : ''
    const patterns = [...bans, climbOut(depth)]
    configs.push({
      name: `layers/${name}/depth-${depth}`,
      files: [`${dir}${deeper}*.${EXT}`],
      ignores: apiBans.length ? ['**/api.ts'] : [],
      rules: { [RULE]: ['error', { patterns: [...patterns, ...apiBans] }] },
    })
    if (apiBans.length) {
      configs.push({
        name: `layers/${name}/depth-${depth}/api`,
        files: [`${dir}${deeper}api.ts`],
        rules: { [RULE]: ['error', { patterns }] },
      })
    }
  }
  return configs
}

/** 도메인 사이의 순서. 앞의 도메인은 뒤의 도메인을 불러오지 않는다(group → session → posting → profile → card 한 방향). */
const DOMAIN_ORDER = ['card', 'profile', 'posting', 'session', 'group']

export const layerRules = [
  ...unit('shared', 'src/shared/', [BAN.sharedUp]),
  ...unit('components', 'src/components/', [BAN.sharedUp]),
  ...unit('domain', 'src/domain/*/', [BAN.domainUp], [BAN.mocks]),
  ...DOMAIN_ORDER.flatMap((domain, i) => {
    const later = DOMAIN_ORDER.slice(i + 1)
    if (later.length === 0) return []
    const order = { group: under(...later.map((d) => `domain/${d}`)), message: `도메인은 ${DOMAIN_ORDER.slice().reverse().join(' → ')} 한 방향으로만 불러온다.` }
    return unit(`domain-${domain}`, `src/domain/${domain}/`, [BAN.domainUp, order], [BAN.mocks])
  }),
  ...unit('features', 'src/features/*/', [BAN.featureToApp, BAN.featureToFeature], [BAN.mocks]),
  ...unit('app', 'src/app/', [BAN.appDeepFeature, BAN.mocks]),
  ...unit('mocks', 'src/mocks/', [BAN.mocksUp, BAN.mocksValues]),
]
