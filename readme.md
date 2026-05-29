# ForbidAd4TieBa

<a href="https://github.com/Xposed-Modules-Repo/com.forbidad4tieba.hook/releases/latest"><img alt="GitHub all releases" src="https://img.shields.io/github/downloads/Xposed-Modules-Repo/com.forbidad4tieba.hook/total?label=Downloads"></a>
<a href="https://github.com/Xposed-Modules-Repo/com.forbidad4tieba.hook/releases/latest"><img alt="GitHub latest release" src="https://img.shields.io/github/v/release/Xposed-Modules-Repo/com.forbidad4tieba.hook"></a>
<a href="https://t.me/+JHa8ARb5mlRlYjYx"><img alt="Telegram Group" src="https://img.shields.io/badge/Telegram-群组-blue.svg?logo=telegram"></a>  

ForbidAd4TieBa 是一个贴吧净化 Xposed 模块，用于净化贴吧界面元素，并提供一些扩展增强功能  

## 安装与入口

在支持现代 libxposed api101 的框架中启用模块，并将作用域设置为：

`百度贴吧 com.baidu.tieba`

未root设备使用[NPatch](https://github.com/7723mod/NPatch)或其他支持现代 libxposed api101 的免root框架

模块设置入口：

- 「我的」页面：长按右上角设置图标
- 首页侧边栏：长按设置图标

## 主要功能

- 信息流整理：支持按内容类型、关注状态和吧名关键词调整首页推荐内容。
- 界面布局精简：支持配置首页顶部 Tab、底部 Tab、进吧页面和帖子页部分界面元素。
- 浏览交互增强：提供评论复制、图片原图预览、系统分享入口、自动隐藏 Tab 栏等体验优化。
- 搜索与阅读辅助：支持收藏搜索、浏览历史搜索、信息流和帖子评论预加载。
- 个性化设置：支持外置浏览器打开链接、分享链接清理、常用页面默认行为和背景主题配置。

   **以及其它未注明功能**

## 兼容性

当前主要适配版本：`百度贴吧 22.6.5.1`

理论兼容其它22.x.x.x正式版贴吧

## 反馈

反馈问题时提供：

- 贴吧版本号和模块版本号
- 失效功能与复现步骤
- 打开详细日志输出后导出日志

[提交 issue / PR](https://github.com/aikavvak12una/ForbidAd4TieBa)  
[加入 TG 群组](https://t.me/+JHa8ARb5mlRlYjYx)

## 致谢

自动签到功能实现参考：[LuoSue/TiebaSignIn-1](https://github.com/LuoSue/TiebaSignIn-1)

## 免责声明

本模块仅供学习与技术研究使用，请勿用于任何违反法律法规的用途。  
使用本模块可能出现应用卡顿、闪退、账号被封禁等问题，同时模块会收集应用和xposed版本信息用于统计数据，因此在安装使用前应仔细审查[源代码](https://github.com/aikavvak12una/ForbidAd4TieBa)，确保已知模块功能、行为并符合预期，作者不对使用本模块造成的任何后果承担责任。
