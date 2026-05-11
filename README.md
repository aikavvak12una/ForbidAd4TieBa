# ForbidAd4TieBa

<a href="https://github.com/Xposed-Modules-Repo/com.forbidad4tieba.hook/releases/latest"><img alt="GitHub all releases" src="https://img.shields.io/github/downloads/Xposed-Modules-Repo/com.forbidad4tieba.hook/total?label=Downloads"></a>
<a href="https://github.com/Xposed-Modules-Repo/com.forbidad4tieba.hook/releases/latest"><img alt="GitHub latest release" src="https://img.shields.io/github/v/release/Xposed-Modules-Repo/com.forbidad4tieba.hook"></a>
<a href="https://t.me/+JHa8ARb5mlRlYjYx"><img alt="Telegram Group" src="https://img.shields.io/badge/Telegram-群组-blue.svg?logo=telegram"></a>  

ForbidAd4TieBa 是一个贴吧净化 Xposed 模块，用于净化贴吧界面元素，并提供一些扩展增强功能  

## 安装与入口

在支持现代 libxposed api101 的框架中启用模块，并将作用域设置为：

```text
百度贴吧
com.baidu.tieba
```

模块设置入口：

- 「我的」页面：长按右上角设置图标
- 首页侧边栏：长按设置图标

## 主要功能

- 内容屏蔽
  - 首页推荐自定义屏蔽：支持按投票帖、视频帖、直播帖、回复帖、热点帖、带货帖、游戏/购买推广帖、求助/悬赏帖、打分帖等类型过滤信息流卡片
  - 支持屏蔽未关注的吧，以及按吧名关键词过滤推荐内容
- UI 净化
  - 自定义首页顶部 Tab：可配置有料、推荐、直播、关注 Tab 的显示
  - 自定义底部 Tab：可配置首页、进吧、小卖部、消息、我的 Tab 的显示
  - 净化进吧页面，将进吧入口替换为关注的吧
  - 屏蔽帖子底部横幅，禁用帖子页调整字号手势
- 浏览与交互增强
  - 评论自由复制，吧页面禁止自动展开
  - 图片预览默认查看原图，支持添加原生系统分享入口
  - 收藏搜索、浏览历史搜索
  - 禁用图片左滑进吧入口，屏蔽更新升级弹窗
- 扩展体验
  - 信息流和帖子评论预加载下一页
  - 禁止首页自动刷新，消息页默认打开通知页
  - 使用外置浏览器打开链接，清理分享链接追踪参数
  - 一些其他的扩展功能

## 兼容性

当前主要适配版本：

```text
百度贴吧 22.6.1.1
```

或其他22正式版贴吧

## 反馈

反馈问题时建议提供：

- 贴吧版本号和模块版本号
- 失效功能与复现步骤
- 打开详细日志输出后导出日志

[提交 issue / PR](https://github.com/aikavvak12una/ForbidAd4TieBa)  
[加入 TG 群组](https://t.me/+JHa8ARb5mlRlYjYx)

## 致谢

自动签到功能实现参考：[LuoSue/TiebaSignIn-1](https://github.com/LuoSue/TiebaSignIn-1)

## 免责声明

本模块仅供学习与技术研究使用，请勿用于任何违反法律法规的用途。  
使用本模块可能出现应用卡顿、闪退、账号被封禁等问题，因此在安装使用前应仔细审查[源代码](https://github.com/aikavvak12una/ForbidAd4TieBa)，作者不对使用本模块造成的任何后果承担责任。
