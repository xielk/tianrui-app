# iOS SwiftUI 静态界面对齐设计（Login + F1）

## 1. 背景与目标

现有仓库已具备 Android 原生实现，当前阶段先落地 iOS 原生界面，用于视觉对齐与后续功能迁移的基础工程。

本设计聚焦两页：

1. 登录页（Login）
2. F1 首页（F1 Home）

目标是使用 Swift + SwiftUI 对齐提供的 Android 截图，优先实现高保真静态视觉结果。

## 2. 范围

### 2.1 In Scope

- 新建 iOS SwiftUI 工程骨架
- 实现 `LoginScreen` 静态页
- 实现 `F1HomeScreen` 静态页
- 导入并映射 Android 已有图像资源到 iOS Assets
- 建立基础设计 Token（颜色、间距、圆角、字体、阴影）
- 提供可快速切换页面的预览/入口，便于截图对比

### 2.2 Out of Scope

- 网络请求、登录流程、会话管理
- BLE、OTA、扫码、地图 SDK、推送
- 业务状态联动与交互逻辑
- 深色模式、横屏、iPad 专项适配

## 3. 方案选择

### 3.1 方案 A（推荐并采用）

纯 SwiftUI 静态还原：

- 按截图实现像素级布局
- 本地资源 + 假数据驱动
- 暂不接业务逻辑

采用原因：最快获得可评审结果，同时结构可直接承接后续交互与接口接入。

### 3.2 备选方案（不采用）

- 方案 B：SwiftUI + UIKit 混排（为地图预留）
- 方案 C：先做完整组件库再拼页面

不采用原因：当前阶段仅做静态视觉，复杂度与交付速度不匹配。

## 4. 工程与命名

- 技术栈：Swift + SwiftUI
- Bundle Identifier：`com.xiaochao.app`
- 目录建议：

```text
ios/ZongshenApp/
  App/
  Features/
    Login/
    F1/
  DesignSystem/
  Assets.xcassets/
```

说明：Android 中“包名”语义在 iOS 侧对应 Bundle Identifier；代码目录以模块化组织替代 Java 包路径。

## 5. 视觉对齐规范

### 5.1 基准设备与适配

- 设计基准：iPhone 390pt 宽（如 iPhone 14/15）
- 次级验证：375pt、430pt
- 适配策略：保持核心比例，优先保证结构不破版和视觉一致

### 5.2 Login 页面

- 背景浅灰
- 上部大留白 + 中部 Logo + 标题“宗申智行”
- 两个主操作按钮：
  - 一键登录（蓝底白字）
  - 短信登录（白底蓝边蓝字）
- 协议勾选行：勾选框 + 文案 + 两个链接文案
- 仅静态展示，不响应点击

### 5.3 F1 页面

- 整页浅蓝底
- 顶部设备信息区：设备号、历史总里程、右上图标
- 中区视觉主体：电量胶囊、车辆主图、状态信息块
- 主控白卡：
  - 上排：感应解锁块、中间滑动解锁视觉块、静音块
  - 下排：寻车、设置、用车人三入口
- 地图卡：静态图片占位
- 底部 Tab：首页（选中）、我的（未选中）

## 6. 资源映射

优先复用 Android 资源：

- `android/app/src/main/res/drawable/app_logo.png`
- `android/app/src/main/res/drawable/f2_bike.png`
- `android/app/src/main/res/drawable/f2_*.png`
- `android/app/src/main/res/drawable/nav_*.png`

映射原则：

- iOS 资源名保持语义一致（下划线命名）
- 优先保留原图比例与透明信息
- 缺失资源先用等尺寸占位，后补真图，不改布局结构

## 7. 组件拆分

### 7.1 Login

- `LoginScreen`
- `LogoBlockView`
- `PrimaryActionButtonView`
- `SecondaryActionButtonView`
- `AgreementRowView`

### 7.2 F1

- `F1HomeScreen`
- `F1HeaderView`
- `F1BatteryPillView`
- `F1VehicleHeroView`
- `F1MainControlCardView`
- `F1QuickActionsRowView`
- `F1MapCardView`
- `F1BottomTabView`

### 7.3 Design System

- `AppColor`
- `AppSpacing`
- `AppRadius`
- `AppTypography`
- `AppShadow`

## 8. 验收标准

### 8.1 Login

- Logo 区、标题、两按钮、协议行四个区块层级与位置一致
- 按钮宽高、圆角、颜色与截图视觉一致
- 390/375/430 三个宽度无错位、无遮挡

### 8.2 F1

- 顶部信息、中区主图、主控卡、地图卡、底部 Tab 五大区块一致
- 主控卡上排与下排布局比例一致
- 地图占位与底部 Tab 间距一致，不出现压缩变形

### 8.3 通用

- 无业务逻辑依赖，页面可独立渲染
- 无明显资源拉伸、模糊、裁切

## 9. 风险与控制

- 风险：Android 图在 iOS 显示比例偏差
  - 控制：固定容器比例 + `aspectFit` 处理
- 风险：机型宽度差异导致间距漂移
  - 控制：以 390 为基准，375/430 做小范围常量校正
- 风险：后续接入交互时破坏静态结构
  - 控制：组件化拆分，保持视觉层和状态层分离

## 10. 交付物

- iOS SwiftUI 工程骨架
- `LoginScreen` 静态页
- `F1HomeScreen` 静态页
- 资源导入与映射
- 页面预览入口用于截图比对

## 11. 后续衔接

本设计完成后，下一步进入实现计划阶段，拆分为：

1. 工程初始化与资源导入
2. Login 静态还原
3. F1 静态还原
4. 多机型对齐微调与验收截图
