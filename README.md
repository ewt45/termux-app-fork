原项目：https://github.com/termux/termux-app

更改：
- `build.gradle`
  - 使用自定义的bootstrap（从release下载）
- `debug_build.yml`
  - 删除安卓5的构建。
- `TermuxDocumentsProvider.java`
  - 将文件提供器的根目录从$HOME改为/data/data/com.termux，以便查看usr
