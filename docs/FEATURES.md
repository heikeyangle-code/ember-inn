# 功能覆盖清单（对照官方 SillyTavern release 8172dcd）

目标：覆盖官方核心功能。优先级：🔴 核心（第一版必须有）· 🟡 进阶（第二版）· ⚪ 远期。

## 1. 角色卡
- 🔴 导入：PNG V2/V3（tEXt/ccv3）、JSON、CharX、YAML/YML、BYAF（5 种全支持）
- 🔴 导出：PNG（chara+ccv3 双写）/ JSON（V2 结构）；CharX 官方仅导入（对齐现状）
- 🔴 字段全量（V3 全字段）：description / personality / scenario / first_mes / mes_example / system_prompt / post_history_instructions / creator_notes / tags / avatar / alternate_greetings / character_book / creator / character_version / extensions（含 depth_prompt）/ assets（icon / background / voice）/ group_only_greetings / skip_personality / skip_example / skip_system / insert_system / nickname / creator_notes_multilingual / source
- 🔴 从 URL 导入角色卡（对齐官方 /api/content/importURL）
- 🔴 头像、标签、文件夹、搜索、排序
- 🟡 属性合并、批量编辑、重复、重命名
- 🟡 卡图自动取色主题（自家增强）

## 2. 聊天
- 🔴 消息 CRUD、编辑、删除、重新生成
- 🔴 滑动回复（swipe）、分支 / 书签（bookmark）
- 🔴 开场白选择（first message + alternate greetings）
- 🔴 会话列表、最近、搜索、导入导出（jsonl）
- 🟡 快照 / 回滚、聊天备份
- 🟡 群聊：多角色、生成模式（顺序 / 随机 / 轮流 / 自动）、群聊设置
- ⚪ 多用户系统（官方为本地单用户，暂不做）

## 3. 世界书（World Info）
- 🔴 条目 CRUD、关键词、二次关键词
- 🔴 注入位置（before / after char）、深度
- 🔴 递归扫描、粘性、冷却、概率、延迟
- 🔴 常驻（Constant）、大小写敏感、选择性
- 🟡 分组评分（group scoring）、向量化（vector）
- 🟡 导入导出（官方格式）
- ⚪ selective logic 扩展字段

## 4. 提示词与上下文
- 🔴 提示词组装：角色字段 + 示例对话 + 世界书 + 作者注释 + 历史（对齐官方 script.js）
- 🔴 token 管理：上下文预算、截断策略、多模型 tokenizer
- 🔴 系统提示词 / 上下文模板、instruct 模式
- 🟡 AI 预设（采样参数预设）导入导出
- 🟡 CFG scale、logit bias、logprobs、停止序列、JSON schema
- 🟡 函数调用 / 工具、多模态输入

## 5. 宏系统（对齐 Macros 2.0）
- 🔴 核心宏：{{user}} {{char}} {{time}} {{date}} {{random}} {{pick}} {{roll}} {{greeting}} 等
- 🔴 条件块 {{if}} / {{else}} / {{/if}}、变量宏 {{getvar}} {{setvar}} {{incvar}}
- 🔴 状态宏：{{lastMessage}} {{idle_duration}} 等
- 🟡 宏浏览器 / 诊断
- ⚪ 自定义宏注册

## 6. 斜杠命令（官方 150+）
- 🔴 消息类：/sys /sendas /send /sysgen /impersonate /inject /trigger /continue /regenerate /swipe /edit /delete /clear
- 🔴 人设类：/persona（官方 persona-set 语义）
- 🟡 变量类（37+）、世界书类、群聊类、设置类
- ⚪ 自定义斜杠脚本

## 7. 人设（Persona）
- 🔴 人设管理、注入位置
- 🟡 模式：off / temp / all / lookup
- ⚪ 人设锁定到聊天

## 8. 官方扩展
- 🔴 TTS（多提供商）、STT（语音输入）
- 🔴 图像生成（SD / NovelAI / Pollinations 等）
- 🔴 翻译（Google / DeepL / 免费）
- 🟡 联网搜索（对齐官方 /api/search：SerpAPI / SearXNG / Tavily / Serper / Z.AI 等）
- 🟡 向量库 / RAG、记忆扩展
- 🟡 统计、token 计数、表情精灵（expressions）
- 🟡 regex 脚本、快捷回复、图库、附件
- ⚪ caption、connection-manager、第三方扩展生态（远期：插件 API / 兼容模式）

## 9. 设置与预设
- 🔴 提供商 / 连接档案（多 provider + 自定义 OpenAI 兼容）
- 🔴 采样参数全量（temp / top_p / top_k / min_p / typical / rep penalty / mirostat / tfs / top_a / stop）
- 🔴 模型管理、多模态开关、思维链显示（reasoning）
- 🟡 自定义请求头 / 请求体、代理
- 🟡 启动行为、主题、字体、语言

## 10. 主题 / UI（自家增强）
- 🔴 角色卡驱动主题（seed → 配色 → 背景）
- 🔴 全局预设主题、浅 / 深 / 跟随系统
- 🔴 毛玻璃 / 液态玻璃表面、MeshGradient 氛围背景
- 🟡 主题配方导出分享、风格档位（克制 / 标准 / 张扬）

## UI 映射（官方功能 → 原生界面）

| 官方功能 | 原生界面位置 |
|---|---|
| 角色列表 / 导入导出 | 首页「角色」Tab |
| 聊天 / 滑动 / 长按 / 分支 | 聊天页 |
| 世界书编辑 | 角色详情页（世界书分组） |
| 宏 / 斜杠 | 引擎层：输入框斜杠触发；宏展开无 UI |
| 正则 / 快捷回复 | 角色设置（或全局设置） |
| 人设 / 作者注释 | 聊天页 ⋮ 菜单 + 主设置 |
| 预设 / 采样 / 提供商 | 主设置 |
| TTS / STT | 输入区 + 消息长按菜单 |
| 图像生成 | 输入区 + 生成面板 |
| 群聊 | 聊天 Tab 新建群聊入口 |
| 统计 / token / 思维链 | 消息详情 / 展开区域 |
| 表情精灵 | 聊天头部 / 消息头像 |

## 开发顺序

- P0：角色卡 + 聊天 + 提示词组装 + tokenizer
- P1：世界书 + 宏 + 斜杠
- P2：群聊 / 预设 / 人设 / 正则 / 作者注释
- P3：TTS / STT / 图像 / 翻译 / 向量
- P4：自有插件 API + 官方行为回归测试
