<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# i18n-Lookup Changelog

## [Unreleased]

## [1.0.1] - 2026-05-27
### Added
- Recognize chained property access after a translation call: `t('forms').email.placeholder` resolves to the key `forms.email.placeholder` (any number of trailing `.segment` parts is supported).

## [1.0.0] - 2026-05-26
### Added
- Hover tooltip view: see translation(s) in a popup when hovering an i18n key.
- Inline-badge view: see translation(s) rendered as inlay badges right next to the key.
- Supports `t('key')`, `i18n.t('key')`, `$t('key')`, `translate('key')` call shapes plus user-defined regex patterns.
- Single JSON file (one language) or a folder of JSON files (one per language) as the translation source.
- Nested keys (`{"a":{"b":"x"}}` → `a.b`) and flat dotted keys both supported.
- Click-through navigation from popup or badge to the JSON source.
- Live refresh when translation files change — no editor reload required.
- Distinct "no translation" indicators for fully-missing keys and partially-missing translations across languages.
- Configurable from **Settings | Tools | i18n Lookup**.
- Plugin icon (light + dark) and Marketplace metadata.
