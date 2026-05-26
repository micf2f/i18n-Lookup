# i18n-Lookup

![Build](https://github.com/micf2f/i18n-Lookup/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

> See your i18n translations right where you use them — without leaving the file.

i18n-Lookup is a JetBrains IDE plugin for JavaScript and TypeScript projects. It detects `t('key')`-style translation calls in your code and surfaces the resolved translation directly in the editor, as either a hover popup or an inline badge.

## Features

- **Hover tooltip** — hover over a translation key to see the value in a popup.
- **Inline badge** — render the translation as an inlay right next to the key, no hover needed.
- **One language or many** — point it at a single JSON file (one language) or at a folder of JSON files (one row per language).
- **Click-through navigation** — click a language in the popup, or `Ctrl`/`Cmd`+click an inline badge, to jump straight to that key in its JSON file.
- **Nested *and* flat keys** — `{"a":{"b":"x"}}` and `{"a.b":"x"}` both resolve to `a.b`.
- **Live refresh** — edits to your translation files show up immediately, no editor reload needed.
- **Recognized call shapes** — `t('key')`, `i18n.t('key')`, `$t('key')`, `translate('key')`, plus any custom regex you add. Chained property access on the call result is also recognized — e.g. `t('forms').email.placeholder` resolves to `forms.email.placeholder`.
- **"Not found" indicators** — distinct visuals for keys missing in all languages versus keys missing in just some languages.

## Setup

Open **Settings | Tools | i18n Lookup** and configure:

1. **Translation file or folder** — pick a single `*.json` file for one language, or a folder whose direct `*.json` children are each a language (e.g. `en.json`, `fr.json`, `de.json`).
2. **Translations view** — *Tooltip on hover* (default) or *Inline in code*.
3. **Recognized call formats** — toggle which built-in shapes to match.
4. **Custom regex** — add a pattern if your project uses a call style not covered above; wrap the key in the first capture group `(…)`.

## Supported IDEs

Works in any JetBrains IDE that bundles the JavaScript plugin:

- WebStorm
- IntelliJ IDEA Ultimate
- PhpStorm
- PyCharm Professional
- GoLand
- RubyMine
- Rider
- DataSpell, Aqua, RustRover

Free Community editions (IDEA Community, PyCharm Community, Android Studio) don't include JavaScript support and are not supported by this plugin.

Compatible with IDE builds **242 (2024.2)** and newer.

## Installation

- **From the JetBrains Marketplace (recommended):**

  <kbd>Settings</kbd> → <kbd>Plugins</kbd> → <kbd>Marketplace</kbd> → search **i18n-Lookup** → <kbd>Install</kbd>.

- **From a `*.zip` file:**

  Download the [latest release](https://github.com/micf2f/i18n-Lookup/releases/latest), then
  <kbd>Settings</kbd> → <kbd>Plugins</kbd> → <kbd>⚙️</kbd> → <kbd>Install plugin from disk…</kbd>.

## License

MIT — see [LICENSE](./LICENSE).
