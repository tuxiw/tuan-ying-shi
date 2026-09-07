# 项目长期记忆：团影视发布页（C:\Users\Administrator\Downloads\html）

## 项目约定
- 站点域名：`https://tuanyingshi.tuxiwan.cn/`（canonical、OG、sitemap 均用此域名）。
- 入口页 `index.html`（深色主题，粉紫渐变 #FF4D6D → #9D7CE0），文章页 `wechat-article.html`。
- 版本信息源：`v1/update.json`（version / versionCode / changelog / downloads）。页面下载直链与更新日志必须与该文件保持一致。
- 下载渠道：蓝奏云（推荐，密码 8888）+ Gitee Releases；架构包 universal / arm64-v8a / armeabi-v7a / x86_64。
- 仓库：https://github.com/tuxiw/tuan-ying-shi/ 、https://gitee.com/tuxiw/tuan-ying-shi

## 收录与推送
- 百度主动推送已接入：`POST http://data.zz.baidu.com/urls?site=https://tuanyingshi.tuxiwan.cn&token=<密钥>`（text/plain，每行一个 URL）。当天配额约 9 条。
- 待推送 URL 列表维护在 `.workbuddy/baidu-urls.txt`，新增页面后追加并重新推送；token 不写入记忆，需要时向用户索取。
- 站点已部署 robots.txt 与 sitemap.xml，线上均返回 200。

## 落地页/SEO 约束（来自 2026-09-05 检测报告）
- 主体内容占比要够：页面可见文本保持在 6000 字符以上，图片不能压过正文。
- 移动端必须有可见导航（桌面导航在 ≤900px 隐藏时，用横向滚动锚点条替代）。
- 折叠/展开类控件尽量少（FAQ 只保留 2 个折叠点，且不得出现在首屏）。
- 下载区放在 FAQ 之后，首屏只保留 CTA 按钮。
