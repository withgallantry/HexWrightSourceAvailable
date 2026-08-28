# hexdoc-hexwright

Python web book docgen and hexdoc plugin for Hexwright.

## Setup (Windows)

```sh
py -3.11 -m venv .venv
.\.venv\Scripts\activate
pip install -e .[dev]
```

## Local usage

Create a file named `.env` in the repo root:

```sh
GITHUB_REPOSITORY=withgallantry/HexWright
GITHUB_SHA=master
GITHUB_PAGES_URL=https://withgallantry.github.io/HexWright
```

Run docs commands from the repo root:

```sh
hexdoc -h
hexdoc build
hexdoc merge
hexdoc serve
```

Watch mode:

```sh
npx nodemon --config doc/nodemon.json
```

## Notes

- `doc/hexdoc.toml` points at `src/main/resources`, so the existing Patchouli data (`hexwright:folio`) is used directly.
- If the GitHub repo slug differs, update `.env` values accordingly.
