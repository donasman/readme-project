"use client";

import { Slider } from "../slider/slider";
import { Switch } from "../switch/switch";
import { playUiSound } from "./play";
import { setSoundEnabled, setSoundVolume, useSoundSettings } from "./settings";

/** The sound setting for an About panel or a footer: an on/off switch and a volume slider. The choice is remembered in this browser. */
export function SoundToggle({ className }: { className?: string }) {
  const { enabled, volume } = useSoundSettings();
  return (
    <div className={className} style={{ display: "grid", gap: "var(--space-5)" }}>
      <Switch
        label="효과음"
        checked={enabled}
        onCheckedChange={(next) => { setSoundEnabled(next); if (next) playUiSound("toggle-on"); }}
      />
      <Slider
        label="음량"
        value={Math.round(volume * 100)}
        min={0}
        max={100}
        step={5}
        disabled={!enabled}
        format={(value) => `${Math.round(value)}%`}
        onValueChange={(value) => setSoundVolume(value / 100)}
        onValueCommit={() => playUiSound("tick")}
      />
    </div>
  );
}

export default SoundToggle;
