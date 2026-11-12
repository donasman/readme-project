import {
  DEFAULT_SOUND_SETTINGS,
  setSoundStorage,
} from "@/components/arc/sound/settings"

// Arc ships UI sound on by default. Readme keeps it off until a visitor turns it on.
// Arc reads its settings through this storage adapter: with nothing stored we answer "off",
// and a choice the visitor saved wins. The key stays Arc's own, so tabs still sync.
// Done here instead of editing components/arc/sound/settings.ts, which a re-add would overwrite.
const OFF_BY_DEFAULT = JSON.stringify({ ...DEFAULT_SOUND_SETTINGS, enabled: false })

setSoundStorage({
  get: (key) => {
    try {
      return window.localStorage.getItem(key) ?? OFF_BY_DEFAULT
    } catch {
      return OFF_BY_DEFAULT
    }
  },
  set: (key, value) => {
    try {
      window.localStorage.setItem(key, value)
    } catch {
      /* storage blocked: the choice lasts for this page only */
    }
  },
})
