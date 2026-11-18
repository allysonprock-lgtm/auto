# Base Finder (Fabric, Minecraft Java 26.1.2, client-side)

Watches chunks your client loads, finds clusters of chests/barrels/shulker boxes that look like an
intact player base, and reports **only the chunk coordinates** (no per-chest spam).

On detection it: shows an in-game alert (chat + action bar) with a sound, saves to
`config/basefinder/detections.json`, and POSTs to your Discord webhook and/or a custom API.

## Build the jar (GitHub, no local setup)
1. Create a free GitHub repo and upload **everything in this folder** (keep the `.github` folder).
2. Open the repo's **Actions** tab. The "Build mod" workflow runs on every push (or press *Run workflow*).
3. Download the jar from the finished run (**Artifacts → basefinder-jar**) or from the repo's
   **Releases → Latest build** page (public link, no login needed to download).

## Install
1. Install Fabric Loader (0.19.2+) for Minecraft 26.1.2 and **Fabric API** (0.146.1+26.1.2 or newer).
2. Needs **Java 25** (what Minecraft 26.1 uses).
3. Put `basefinder-1.1.0.jar` in `.minecraft/mods/`.
4. Launch once. This creates `.minecraft/config/basefinder/config.json`.

## Settings screen
Press **Right Shift** in game (change with `guiKey`: 75 = K, 0 = off). Dark card layout with toggles for
detection / chat alert / sound alert / griefed-check, the minimum storage count, a Discord webhook box,
a **Test alert** button, and a list of recent detections with confidence %. Esc closes without saving.

## Set up the Discord webhook
Discord channel → Edit Channel → Integrations → Webhooks → New Webhook → Copy URL. Paste it into
`discordWebhookUrl` in `config.json`. The config reloads while the game is running (within ~5 seconds).
Set `"sendTestOnJoin": true`, join a world to test the webhook + sound, then set it back to `false`.

## Custom alert sound
Put a **.wav** file at `config/basefinder/alert.wav` (or set `customSoundFile` to another name / absolute path).
Only uncompressed WAV is supported (convert mp3/ogg to wav first). If the file is missing, a built-in
Minecraft sound plays. `soundVolume` is 0.0 to 1.0.

## Config options (config/basefinder/config.json)
| Option | Meaning |
|---|---|
| `storageBlocks` | Block ids counted as storage (add `minecraft:hopper`, `minecraft:ender_chest`...) |
| `minStorageBlocks` | Storage blocks needed in one cluster (default 8) |
| `clusterRadius` | Max box distance between blocks in a cluster (default 10) |
| `dedupeChunkRadius` | Ignore new detections within this many chunks of a saved one (default 3) |
| `requireIntactCheck` | Skip clusters that look blown up / exposed |
| `minSupportedRatio` | Share of storage blocks with a block below them (default 0.6) |
| `minAvgEnclosure` | Average solid neighbours per storage block, out of 6 (default 2.0) |
| `dimensions` | Dimensions to watch |
| `discordWebhookUrl`, `discordUsername` | Discord output |
| `apiEndpointUrl`, `apiAuthHeader` | Optional generic JSON POST endpoint |
| `includeServerAddress` | Include the server address in webhook/API messages |
| `guiKey`, `alertCooldownSeconds` | Key that opens the screen; minimum seconds between chat/sound alerts |
| `alertChat`, `alertSound`, `customSoundFile`, `soundVolume` | In-game alert |
| `saveLocally` | Save to detections.json |

## Limits worth knowing
- It only sees chunks within your render distance, so fly/travel around to cover more area.
- "Looks un-griefed" is a heuristic (floors under chests, enclosed by blocks). Tune the two intact
  numbers if you get too many or too few hits. Nether bastions and similar generated structures can
  still trigger it.
- Dedupe is per device. Two devices on the same webhook can post the same base twice.
