# Vendor

- `seedrandom-3.0.5.js`：David Bau 的 seedrandom v3.0.5（MIT），
  官方 SillyTavern 的 `{{pick}}` 依赖，逐字节同版本，用于生成确定性基准。
  来源：npm seedrandom@3.0.5（`npm pack seedrandom@3.0.5`）。

- `package.json` / `package-lock.json`：`yaml@2.8.3` 与 `sanitize-filename@1.6.3`，
  均为官方 release 依赖的同版本（见 sillytavern-ref/package-lock.json）。
  安装：`cd scripts/diff/vendor && npm ci`（node_modules 不入库）。

- `jszip-3.10.1.min.js`：官方 public/lib/jszip.min.js（JSZip v3.10.1，MIT），
  用于 CharX 差分 fixture 生成/读取 ZIP。
