# ForbidAd4TieBa

一个轻量级开源 Lsposed 模块，用于屏蔽百度贴吧广告并精简页面体验。  
**设置入口：** 贴吧「我的」页面 -> 长按右上角「设置」图标  

[最新版本下载](https://github.com/Xposed-Modules-Repo/com.forbidad4tieba.hook/releases/latest)&emsp;&emsp;&emsp;[提交 Issue / PR](https://github.com/aikavvak12una/ForbidAd4TieBa)&emsp;&emsp;&emsp;[加入 TG 群组](https://t.me/+JHa8ARb5mlRlYjYx)

## 功能特性
* **核心框架**
  * 支持动态反混淆
  * 基于libxposed api101，需要使用支持api101版本的lsposed安装使用
* **广告拦截**
  * 屏蔽开屏、信息流、帖子详情页等推广广告
  * 屏蔽评论区广告
* **内容屏蔽**
  * 屏蔽信息流视频帖子
  * 屏蔽首页直播卡片
  * 关键词屏蔽吧
  * 只显示已关注的吧
  * 屏蔽帖子详情页底部进吧横幅
* **界面精简**
  * 首页 Tab 精简（保留推荐、搜索、发帖）
  * 底部 Tab 精简（移除小卖部入口）
  * 进吧页面精简（使用全部的吧替换原界面）
* **扩展功能**
  * 信息流预加载
  * 禁止首页自动刷新
  * 评论自由复制
  * 默认加载原图
  * 消息 Tab 默认为通知页
  * 为首页 Tab 添加我的关注
  * 自定义首页顶部tab（有料、推荐、直播、关注）
  * 浏览历史搜索
  * 收藏搜索
  * 自动签到
## 致谢
- 自动签到功能实现参考：[LuoSue/TiebaSignIn-1](https://github.com/LuoSue/TiebaSignIn-1)

## 兼容性
- 已验证版本：`22.5.1.0`
- 其他版本通过动态扫描适配，但不保证所有功能可用

## 贡献者
- Gemini 3.1 pro
- Claude opus 4.6

## 免责声明
本模块仅供学习与技术研究使用，请勿用于任何违反法律法规的用途。使用本模块可能出现应用卡顿、闪退、账号被封禁等问题，因此在安装使用前应仔细审查[源代码](https://github.com/aikavvak12una/ForbidAd4TieBa)，作者不对使用本模块造成的任何后果承担责任。
