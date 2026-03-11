# JDK 下载说明

由于安装包较大，不直接存储在源码库中。

### 下载地址
- **Linux (x64)**: [https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz](https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz)
- **Windows (x64)**: [https://download.oracle.com/java/21/latest/jdk-21_windows-x64_bin.zip](https://download.oracle.com/java/21/latest/jdk-21_windows-x64_bin.zip)

### 自动下载
当执行 `mvn package` 时，如果此目录下没有对应的安装包，Maven 会尝试自动下载到此处。
