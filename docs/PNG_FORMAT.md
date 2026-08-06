# PNG 角色卡格式规范（官方 1:1，源码核实：src/character-card-parser.js + src/endpoints/characters.js）

## 写入（导出 PNG）

1. 读取角色头像 PNG **原始字节**，用 `png-chunks-extract` 提取全部 chunk
2. 删除现有 keyword 为 `chara` / `ccv3`（不区分大小写）的 tEXt chunk
3. 导出前清理私有字段（`unsetPrivateFields`）：
   - `fav = false`
   - `data.extensions.fav = false`
   - 删除 `chat`
4. 写入 `chara` tEXt chunk：`base64( UTF-8( JSON ) )` —— 原始 JSON 字符串（V2 语义）
5. 写入 `ccv3` tEXt chunk：在 JSON 上加 `spec = "chara_card_v3"`、`spec_version = "3.0"` 后 `base64( UTF-8( JSON ) )`
6. 两个 chunk 都插在 **IEND 之前**，顺序：`… chara, ccv3, IEND`
7. 重编码：`PNG 签名 + [长度(4B BE) + 类型(4B) + 数据 + CRC32(类型+数据)] × N`
   - 其它 chunk（IHDR / IDAT / PLTE 等）**原字节保留，不重新压缩**

## 读取（导入 PNG）

1. 提取全部 tEXt chunk
2. 优先 `ccv3`（不区分大小写）→ base64 解码 → UTF-8 JSON
3. 无 ccv3 则回退 `chara` → base64 解码
4. 都没有 → 报错 `No PNG metadata.`

## 关键点

- V2 卡只有 `chara`；V3 卡导出时**同时写 chara + ccv3 两份**（同一份 JSON，ccv3 版多 spec 字段）
- 读取永远优先 `ccv3`
- 图片像素数据完全不动（只增删元数据 chunk），文件与官方逐字节语义一致
- tEXt chunk 编码：`keyword(1–79 字节 Latin-1) + 0x00 + text`；CRC32 标准实现

## CharX（ZIP）

- 结构：`card.json`（V3 data，含 spec）+ `assets/`（icon / background / voice 等）
- 导出：卡图/资源映射进 assets；`card.json` 即 V3 data
- 对齐官方 `src/charx.js`（assets 顺序、类型、zipPath）

## Kotlin 实现要点

- 需要自写一个 PNG chunk 工具（解析 / 写入 / CRC32 / tEXt 编解码），纯逻辑约 150 行——**无现成 Kotlin 库专做“保留像素注入 tEXt”**
- 导入侧：PNG 解码取图 + 自读 chunk；导出侧：自写 chunk 注入，不重新压缩像素
- 所有导入导出路径必须过“官方互读”回归测试：官方导出的卡我们导入，我们导出的卡官方导入，字段逐字节一致
