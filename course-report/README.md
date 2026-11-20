# 课程设计报告

《JAVA 框架技术（一）》课程设计报告的 LaTeX 源文件与编译产物。

- `课程设计报告.tex`：自包含主文件，可直接用 XeLaTeX 编译。
- `课程设计报告.pdf`：已编译的最终 PDF。
- `media/`：全部配图（image1–image61）；其中 `media/current/` 保留迁移前的历史页面截图供参考。
- `preamble.tex`：早期拆分的排版定义，当前主文件已自包含、不再引用，仅作存档。

## 重新编译

使用 XeLaTeX（在 TeX Live 2026 上验证），在本目录执行两遍以生成目录与交叉引用：

```bash
xelatex 课程设计报告.tex
xelatex 课程设计报告.tex
```

图片通过 `\graphicspath{{media/}}` 解析，tex 与 media 需保持在同一目录，请勿单独移动其中一个。