# ForbidAd4TieBa 

ForbidAd4TieBa 是一个百度贴吧净化 Xposed 模块，用于净化贴吧界面元素，并提供一些扩展增强功能  
[提交 Issue / PR](https://github.com/aikavvak12una/ForbidAd4TieBa)&emsp;&emsp;&emsp;[加入 TG 群组](https://t.me/+JHa8ARb5mlRlYjYx)

## 下载与安装

[最新版本下载](https://github.com/Xposed-Modules-Repo/com.forbidad4tieba.hook/releases/latest)&emsp;&emsp;&emsp;

安装后请在支持现代 libxposed api101 的框架中启用模块，并把作用域设置为：

`
百度贴吧
com.baidu.tieba
`

启用后强制停止并重新打开贴吧

未root设备尝试使用npatch、lspatch、fpa

## 设置入口

打开贴吧，进入「我的」页面，**长按右上角设置图标**打开模块设置

## 主要功能

### 内容净化

- 屏蔽直播、视频、投票、求助、悬赏、带货、热点等信息流卡片
- 支持按吧名关键词屏蔽帖子
- 支持屏蔽未关注的吧

### 页面精简

- 自定义首页顶部 Tab
- 自定义底部 Tab
- 隐藏帖子详情页底部横幅
- 重定向进吧页面
- 屏蔽升级弹窗

### 实用增强

- 评论自由复制
- 浏览历史搜索
- 收藏搜索
- 默认查看原图
- 图片调用系统分享
- 清理分享链接追踪参数
- 消息页默认打开通知
- 禁止首页信息流自动刷新
- 信息流预加载
- 自动签到
- 屏蔽调整字号手势

## 常见问题

### 找不到设置入口

请确认：

- 模块已启用
- 作用域已勾选百度贴吧
- 已强制停止并重新打开贴吧
- 入口在「我的」页面右上角设置图标的长按操作


### 收藏搜索结果不全

尝试收藏搜索窗口点击「同步缓存」，等待全量缓存完成后再搜索

## 兼容性

已验证版本：百度贴吧 `22.5.3.0`

其它版本不保证所有功能可用

## 反馈

反馈问题时建议提供：

- 失效功能
- 复现步骤
- 反混淆扫描日志

[提交 issue / PR](https://github.com/aikavvak12una/ForbidAd4TieBa)  
[加入 TG 群组](https://t.me/+JHa8ARb5mlRlYjYx)

## 致谢
自动签到功能实现参考：[LuoSue/TiebaSignIn-1](https://github.com/LuoSue/TiebaSignIn-1)

## 免责声明
本模块仅供学习与技术研究使用，请勿用于任何违反法律法规的用途。  
使用本模块可能出现应用卡顿、闪退、账号被封禁等问题，因此在安装使用前应仔细审查[源代码](https://github.com/aikavvak12una/ForbidAd4TieBa)，作者不对使用本模块造成的任何后果承担责任。
