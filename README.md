# Yuchuan-Downloader
A multi-thread downloader which supports for HTTP.

## Features
- [x] HTTP/HTTPS download
- [x] multi-thread download
- [x] download thread and disk-io thread separated
- [ ] multi download task
- [x] support for HTTP (resume from break-point)
- [ ] multi-process support
- [ ] other protocol download maybe...

## Run
`app` module is currently deprecated (which will be separated to be an independent downloader use downloader library).
Current active module is `downloader` module.
There are two modes to use this module.
- Use as android Library
> in root directory, config.gradle:
```
ext.runAlone = false
```
This module will be an android library which provides download interface as described below.

- Use as a sample app
> in root directory, config.gradle:
```
ext.runAlone = true
```
This module will be an apk after build. And this apk will show feature of `downloader` library module.

## Licence
```
MIT License

Copyright (c) 2019 Liu

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

```
