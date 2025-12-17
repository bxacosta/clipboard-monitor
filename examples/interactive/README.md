# Interactive Demo

Full-featured interactive console application for testing and demonstrating the Clipboard Monitor library.

## Running

```bash
./gradlew -q --console=plain :examples:interactive:run
```

## Features

- Real-time clipboard change notifications
- Copy text, images, or files to the clipboard
- Read current clipboard content
- View monitoring statistics

## Commands

| Command | Description                              |
|---------|------------------------------------------|
| `1`     | Copy text to clipboard                   |
| `2`     | Copy image to clipboard (from file path) |
| `3`     | Copy file(s) to clipboard                |
| `4`     | Read current clipboard content           |
| `5`     | Show statistics                          |
| `h`     | Show help                                |
| `q`     | Quit                                     |

## File Path Input

When copying files, multiple paths can be specified using semicolon as separator:

```
C:\file1.txt;C:\file2.txt
```
